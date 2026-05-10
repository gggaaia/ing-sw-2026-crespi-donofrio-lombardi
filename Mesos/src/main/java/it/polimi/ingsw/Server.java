package it.polimi.ingsw;

import it.polimi.ingsw.common.VirtualServer;
import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.controller.servercontroller.notification.TargetVisitor;
import it.polimi.ingsw.rmi.server.Rmi_Server;
import it.polimi.ingsw.socket.server.Socket_Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process entry point of the server side. Two responsibilities, kept
 * strictly separate:
 *
 *   - Bootstrap: build the single ServerController, start the RMI
 *     listener and the socket listener on the same controller.
 *
 *   - Registry of clients + dispatch of notifications. The registry
 *     is a ConcurrentHashMap keyed by nickname (the trust-based
 *     identity, see VirtualServer's javadoc). Concurrent reads from
 *     the RMI worker threads handling getState/getLastXxx are safe
 *     against the dispatcher's iteration without external locking.
 *
 * Dispatching uses a small visitor:
 *   - notification.getTarget().accept(visitor) routes polymorphically
 *     to visitSender / visitBroadcast / visitToNickname.
 *   - notification.deliver(recipient) on each picked recipient stores
 *     the payload in the recipient's per-slot buffer (so RMI clients
 *     can pull it via VirtualServer.getXxx) and calls the matching
 *     no-payload notifyXxx method on the view (RMI: triggers the
 *     pull; socket: Socket_VirtualClient writes the wire message).
 *
 * No instanceof, no switch, no if/else for type discrimination.
 */
public class Server {

    private final ServerController server_controller = new ServerController();

    public Server() {
        // Wire the controller so its lone-player timer thread can call
        // back into this dispatcher.
        server_controller.setServer(this);
    }

    /**
     * The only registry of clients in the process. Adapters call
     * register(nickname, view) when a client opens a connection (and
     * sends its first 'connect' call), and unregister(nickname) when
     * the connection terminates. The dispatcher iterates
     * registry.values() to deliver Broadcast notifications and looks
     * up by key to deliver ToNickname notifications and to serve RMI
     * pull-getters in O(1).
     */
    private final Map<String, ServerController.ClientData> registry = new ConcurrentHashMap<>();

    /**
     * Builds a fresh ClientData for the given nickname / view and
     * adds it to the registry. If a slot already exists for that
     * nickname the existing slot is returned untouched, so the caller
     * (the adapter, or the controller via the adapter) can decide
     * whether to reject the call or rebind the view onto the existing
     * slot for a reconnection.
     */
    public ServerController.ClientData register(String nickname, VirtualView view) {
        ServerController.ClientData fresh = new ServerController.ClientData(nickname, view);
        ServerController.ClientData existing = registry.putIfAbsent(nickname, fresh);
        return existing != null ? existing : fresh;
    }

    /**
     * Removes the entry associated with the given nickname. Does
     * nothing if not present.
     */
    public void unregister(String nickname) {
        registry.remove(nickname);
    }

    /**
     * O(1) lookup of the ClientData associated with the given
     * nickname. Used by the adapters to convert "the nickname the
     * client passed in this method call" into the slot they hand to
     * ServerController, and by the RMI getter methods on Rmi_Server
     * to read the per-slot payload buffers.
     */
    public ServerController.ClientData lookup(String nickname) {
        return registry.get(nickname);
    }

    public ServerController getServer_controller() {
        return server_controller;
    }

    /**
     * Walks each notification in the list and delivers it to the
     * recipients selected by its target (Sender, Broadcast,
     * ToNickname).
     */
    public void dispatch(ServerController.ClientData sender, List<Notification> notifications) {
        Dispatcher dispatcher = new Dispatcher(sender);
        for (Notification n : notifications) {
            dispatcher.dispatch(n);
        }
    }

    /**
     * Inner dispatcher. Implements TargetVisitor so target
     * discrimination is double-dispatch.
     */
    private final class Dispatcher implements TargetVisitor {

        private final ServerController.ClientData sender;
        private Notification currentNotification;

        Dispatcher(ServerController.ClientData sender) {
            this.sender = sender;
        }

        void dispatch(Notification notification) {
            this.currentNotification = notification;
            notification.getTarget().accept(this);
        }

        @Override
        public void visitSender() {
            deliverTo(sender, currentNotification);
        }

        @Override
        public void visitBroadcast() {
            for (ServerController.ClientData client : registry.values()) {
                deliverTo(client, currentNotification);
            }
        }

        @Override
        public void visitBroadcastExceptSender() {
            for (ServerController.ClientData client : registry.values()) {
                // Skip the sender by reference: registry stores the same
                // ClientData instance Server.register returned to the adapter,
                // so a == comparison is safe and exact.
                if (client != sender) {
                    deliverTo(client, currentNotification);
                }
            }
        }

        @Override
        public void visitToNickname(String nickname) {
            ServerController.ClientData target = registry.get(nickname);
            // Null check, not type discrimination: the requested
            // nickname may simply not be in the registry.
            if (target != null) {
                deliverTo(target, currentNotification);
            }
        }

        private void deliverTo(ServerController.ClientData recipient, Notification notification) {
            try {
                notification.deliver(recipient);
            } catch (RemoteException e) {
                handleDeliveryFailure(recipient, e);
            }
        }
    }

    /**
     * Triggered both by Dispatcher.deliverTo (when a notification push
     * fails) and by the heartbeat timer (when ping fails). Marks the
     * slot disconnected, forwards to the controller's
     * handleClientDisconnected, and dispatches whatever notifications
     * the controller produces in response (e.g. the
     * "match paused; lone player timer started" broadcast that
     * PickOfferingTileStrategy returns when the connected count drops
     * to 1).
     *
     * Recursively safe: if the cascade tries to push to ANOTHER
     * disconnected client, that push fails and re-enters here, which
     * just marks the second slot disconnected. The controller's
     * handleClientDisconnected is synchronized on the controller
     * monitor and Java's monitor reentrancy lets the same thread
     * re-enter without deadlock.
     */
    private void handleDeliveryFailure(ServerController.ClientData recipient, Exception e) {
        if (!recipient.isConnected()) {
            // Already marked disconnected on a previous failure; do
            // not run the cascade twice.
            return;
        }
        recipient.markDisconnected();
        System.err.println("Delivery failed to "
                + recipient.getNickname()
                + "; entry marked disconnected: "
                + e.getMessage());
        List<Notification> followUp = server_controller.handleClientDisconnected(recipient);
        dispatch(recipient, followUp);
    }

    public void start(int rmiPort, int socketPort) throws Exception {
        startRmiServer(rmiPort);
        startSocketServer(socketPort);
        startHeartbeat();

        System.out.println("Unified server started:");
        System.out.println("  RMI    port " + rmiPort);
        System.out.println("  Socket port " + socketPort);
        System.out.println("  Heartbeat interval " + HEARTBEAT_INTERVAL_MS + " ms");
    }

    private static final long HEARTBEAT_INTERVAL_MS = 5_000L;
    private java.util.Timer heartbeatTimer;

    /**
     * Schedules a fixed-rate task that calls ping() on every
     * connected VirtualView. Failure → handleDeliveryFailure runs
     * the same cascade as a failed notification push: mark the slot
     * disconnected, forward to ServerController.handleClientDisconnected,
     * and dispatch the resulting notifications.
     *
     * This is the ONLY way the server detects RMI clients that
     * disappeared silently (e.g. process exit) when no other push is
     * in flight to fail. Without it, a player who disconnects on
     * their own turn during PICK_OFFERING_TILE would keep the
     * controller waiting forever — no pause, no lone-player timer.
     */
    private void startHeartbeat() {
        heartbeatTimer = new java.util.Timer("server-heartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                heartbeat();
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
    }

    private void heartbeat() {
        // Take a snapshot of currently-connected slots and ping each
        // outside any lock. ConcurrentHashMap iteration is safe; we
        // copy to a List to avoid concurrent modification if the
        // cascade following a failure removes/replaces entries.
        java.util.List<ServerController.ClientData> snapshot =
                new java.util.ArrayList<>(registry.values());
        for (ServerController.ClientData slot : snapshot) {
            if (!slot.isConnected()) {
                continue;
            }
            try {
                slot.getView().ping();
            } catch (RemoteException e) {
                handleDeliveryFailure(slot, e);
            } catch (RuntimeException e) {
                // Defensive: ping() implementations are no-ops, but a
                // bug should not kill the whole heartbeat thread.
                System.err.println("Heartbeat ping unexpectedly threw: " + e.getMessage());
            }
        }
    }

    private void startRmiServer(int port) throws RemoteException {
        Rmi_Server rmiServer = new Rmi_Server(this);
        VirtualServer stub = (VirtualServer) UnicastRemoteObject.exportObject(rmiServer, 0);
        Registry rmiRegistry = LocateRegistry.createRegistry(port);
        rmiRegistry.rebind("AdderServer", stub);
    }

    private void startSocketServer(int port) throws IOException {
        ServerSocket listenSocket = new ServerSocket(port);
        final Socket_Server socketServer = new Socket_Server(listenSocket, this);

        Thread acceptorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socketServer.run();
                } catch (IOException e) {
                    System.err.println("Socket server error: " + e.getMessage());
                }
            }
        });
        acceptorThread.start();
    }

    private static final int DEFAULT_RMI_PORT = 1234;
    private static final int DEFAULT_SOCKET_PORT = 8080;

    public static void main(String[] args) throws Exception {
        int rmiPort = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_RMI_PORT;
        int socketPort = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_SOCKET_PORT;
        new Server().start(rmiPort, socketPort);
    }
}
