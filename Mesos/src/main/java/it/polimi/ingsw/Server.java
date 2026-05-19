package it.polimi.ingsw;

import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.controller.server.ServerController;
import it.polimi.ingsw.rmi.server.Rmi_Server;
import it.polimi.ingsw.socket.server.Socket_Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Processo server. Tre responsabilit\u00e0:
 *
 *   1. Registry dei client connessi: una {@code Map<String, VirtualView>}
 *      pi\u00f9 un {@code Set<String>} per i client RMI (gli unici per cui
 *      facciamo dispatch asincrono).
 *
 *   2. Helper di dispatch verso un singolo client: {@link #send}.
 *      Per i client RMI spawna un thread; per i client socket fa la
 *      chiamata inline (writeObject veloce). Il chiamante \u00e8
 *      sempre il {@link ServerController}, da dentro i suoi metodi
 *      synchronized.
 *
 *   3. Bootstrap: avvia il listener RMI, il listener socket, e il
 *      timer di heartbeat (ping periodico di tutti i client per
 *      detectare disconnessioni silenziose).
 *
 * Il {@link ServerController} \u00e8 il "cervello"; questo Server \u00e8
 * solo infrastruttura di rete.
 */
public class Server {

    // === Registry condiviso ===

    private final Map<String, VirtualView> views = new HashMap<>();
    private final Set<String> rmiClients = new HashSet<>();

    // === Controller ===

    private final ServerController controller;

    public Server() {
        this.controller = new ServerController(this);
    }

    public ServerController getController() {
        return controller;
    }

    // === Registry API (chiamata dagli adapter) ===

    public synchronized void registerRmi(String nick, VirtualView v) {
        views.put(nick, v);
        rmiClients.add(nick);
    }

    public synchronized void registerSocket(String nick, VirtualView v) {
        views.put(nick, v);
    }

    public synchronized void unregister(String nick) {
        views.remove(nick);
        rmiClients.remove(nick);
    }

    /**
     * Versione atomica di {@code unregister} che restituisce {@code true}
     * solo se il client era effettivamente registrato (e ora non lo \u00e8
     * pi\u00f9). Usata dal {@code ServerController} per garantire
     * l'idempotenza della cascata di disconnect quando due thread di
     * send falliscono in parallelo verso lo stesso client.
     */
    public synchronized boolean unregisterIfPresent(String nick) {
        boolean wasPresent = views.containsKey(nick);
        if (wasPresent) {
            views.remove(nick);
            rmiClients.remove(nick);
        }
        return wasPresent;
    }

    public synchronized VirtualView getView(String nick) {
        return views.get(nick);
    }

    public synchronized boolean isConnected(String nick) {
        return views.containsKey(nick);
    }

    /** Snapshot della mappa views (copia difensiva). */
    public synchronized Map<String, VirtualView> snapshotViews() {
        return new HashMap<>(views);
    }

    // === Dispatch helper ===

    /**
     * Manda una task verso il client con il nickname dato.
     * Per RMI: spawna un thread fresco (la chiamata pu\u00f2 essere lenta).
     * Per socket: esegue inline (writeObject \u00e8 veloce).
     *
     * NB: il monitor del Server non viene preso; il check su rmiClients
     * \u00e8 una lettura best-effort di un HashSet — \u00e8 sicuro perch\u00e9
     * le modifiche al set avvengono solo all'interno dei metodi
     * register/unregister sopra (synchronized).
     */
    public void send(String nick, Runnable task) {
        boolean rmi;
        synchronized (this) {
            rmi = rmiClients.contains(nick);
        }
        if (rmi) {
            Thread t = new Thread(task, "send-" + nick);
            t.setDaemon(true);
            t.start();
        } else {
            task.run();
        }
    }

    // === Heartbeat ===

    private static final long HEARTBEAT_INTERVAL_MS = 5_000L;
    private Timer heartbeatTimer;

    private void startHeartbeat() {
        heartbeatTimer = new Timer("server-heartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                heartbeat();
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
    }

    private void heartbeat() {
        List<Map.Entry<String, VirtualView>> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(views.entrySet());
        }
        for (final Map.Entry<String, VirtualView> e : snapshot) {
            final String nick = e.getKey();
            final VirtualView v = e.getValue();
            send(nick, new Runnable() {
                @Override public void run() {
                    try {
                        v.ping();
                    } catch (RemoteException ex) {
                        controller.handleDeliveryFailure(nick);
                    } catch (RuntimeException ex) {
                        System.err.println("Heartbeat ping unexpectedly threw: " + ex.getMessage());
                    }
                }
            });
        }
    }

    // === Bootstrap ===

    public void start(int rmiPort, int socketPort) throws Exception {
        startRmiServer(rmiPort);
        startSocketServer(socketPort);
        startHeartbeat();
        System.out.println("Server started: RMI port " + rmiPort + ", socket port " + socketPort);
    }

    private void startRmiServer(int port) throws RemoteException {
        Rmi_Server rmiServer = new Rmi_Server(this);
        Object stub = UnicastRemoteObject.exportObject(rmiServer, 0);
        Registry rmiRegistry = LocateRegistry.createRegistry(port);
        rmiRegistry.rebind("AdderServer", (it.polimi.ingsw.common.VirtualServer) stub);
    }

    private void startSocketServer(int port) throws IOException {
        ServerSocket listenSocket = new ServerSocket(port);
        final Socket_Server socketServer = new Socket_Server(listenSocket, this);
        Thread acceptorThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    socketServer.run();
                } catch (IOException e) {
                    System.err.println("Socket server error: " + e.getMessage());
                }
            }
        }, "socket-acceptor");
        acceptorThread.setDaemon(true);
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
