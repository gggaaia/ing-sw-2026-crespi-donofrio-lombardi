package it.polimi.ingsw.common;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Helpers to discover the local machine's "real" LAN IPv4 address, used
 * to set {@code java.rmi.server.hostname} automatically.
 *
 * <p>Background: when an object is exported via
 * {@code UnicastRemoteObject.exportObject}, RMI stamps the resulting stub
 * with an address other JVMs must call back on. If left to its defaults,
 * RMI often picks {@code 127.0.0.1} / {@code 127.0.1.1} (common on Linux)
 * or a virtual interface such as the Docker bridge ({@code 172.17.x.x}),
 * which a remote peer cannot reach. The connection then hangs or is
 * refused. Both the server and the RMI client export objects, so both
 * must advertise a routable address.
 *
 * <p>This class avoids guessing by asking the OS which local address it
 * would actually use to reach a given destination (the same idea as
 * {@code ip route get <dst>}). A {@link DatagramSocket#connect} call on a
 * UDP socket performs no traffic but binds the socket to the egress
 * interface, whose address we then read back.
 */
public final class NetworkUtils {

    private NetworkUtils() {
    }

    /**
     * Returns the local IPv4 address the OS would use to reach
     * {@code targetHost}:{@code targetPort}, or {@code null} if it cannot
     * be determined. This is the most accurate choice for the client,
     * which already knows the server it is connecting to.
     */
    public static String localAddressFor(String targetHost, int targetPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName(targetHost), targetPort);
            InetAddress local = socket.getLocalAddress();
            if (isUsable(local)) {
                return local.getHostAddress();
            }
        } catch (Exception ignored) {
            // Fall through to the generic detection below.
        }
        return bestLanAddress();
    }

    /**
     * Best-effort detection of a routable LAN IPv4 address for this host,
     * used by the server (which does not know its clients in advance).
     * Tries an outbound probe first, then falls back to scanning the
     * network interfaces while skipping loopback, down, and virtual
     * adapters. Returns {@code null} if nothing suitable is found.
     */
    public static String bestLanAddress() {
        // Probe: a public IP we never actually send to; reveals the
        // primary egress interface's address.
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress local = socket.getLocalAddress();
            if (isUsable(local)) {
                return local.getHostAddress();
            }
        } catch (Exception ignored) {
            // Fall through to interface scanning.
        }
        return scanInterfaces();
    }

    private static String scanInterfaces() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!isCandidateInterface(nic)) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (isUsable(addr)) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
            // Nothing else we can do.
        }
        return null;
    }

    private static boolean isCandidateInterface(NetworkInterface nic) {
        try {
            if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || nic.isPointToPoint()) {
                return false;
            }
        } catch (SocketException e) {
            return false;
        }
        String name = nic.getName() == null ? "" : nic.getName().toLowerCase();
        String display = nic.getDisplayName() == null ? "" : nic.getDisplayName().toLowerCase();
        // Skip Docker/virtualization bridges and tunnels.
        String[] virtualMarkers = {"docker", "veth", "br-", "virbr", "vmnet", "vbox", "utun", "tun", "tap", "zt"};
        for (String marker : virtualMarkers) {
            if (name.contains(marker) || display.contains(marker)) {
                return false;
            }
        }
        return true;
    }

    /** A usable address is a non-loopback, non-link-local IPv4 address. */
    private static boolean isUsable(InetAddress addr) {
        return addr instanceof Inet4Address
                && !addr.isLoopbackAddress()
                && !addr.isLinkLocalAddress()
                && !addr.isAnyLocalAddress();
    }
}
