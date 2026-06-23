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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Server process. Three jobs:
 *
 *   1. Registry of connected clients: a {@code Map<String, VirtualView>}
 *      plus a {@code Set<String>} tracking the RMI ones (the only
 *      clients we dispatch to asynchronously).
 *
 *   2. Per-client dispatch helper: {@link #send}. For RMI clients it
 *      hands off to a dedicated executor; for socket clients it
 *      runs inline (writeObject is quick). The caller is always the
 *      {@link ServerController}, from inside its synchronized methods.
 *
 *   3. Bootstrap: spin up the RMI listener, the socket listener, and
 *      the heartbeat timer (periodic ping of every client to detect
 *      silent disconnects).
 *
 * The {@link ServerController} is the brain; this Server is just
 * the networking plumbing.
 */
public class Server {

    // === Shared registry ===

    private final Map<String, VirtualView> views = new HashMap<>();
    private final Set<String> rmiClients = new HashSet<>();

    /**
     * One single-thread executor per RMI client, so every {@link #send}
     * to the same client runs in FIFO submit order. Replaces the
     * old {@code new Thread(task).start()} approach, which fired N
     * parallel threads and could reorder events on the client side
     * (e.g. onCharacterCardsPicked could overtake onBuildingPickTurn).
     * Socket clients aren't tracked here: they're already naturally
     * serialised (inline task.run() + writeObject under synchronized).
     */
    private final Map<String, ExecutorService> rmiSenders = new HashMap<>();

    // === Controller ===

    private final ServerController controller;

    public Server() {
        this.controller = new ServerController(this);
    }

    public ServerController getController() {
        return controller;
    }

    // === Registry API (called by the network adapters) ===

    /**
     * Registers an RMI client under {@code nick}. Returns {@code false}
     * (and DOES NOT overwrite the existing entry) if {@code nick} is
     * already in use. The caller is expected to send an
     * {@code onNicknameRejected} to the new view in that case.
     *
     * Refusing to overwrite is critical: a silent replace would orphan
     * the original client (its view would be evicted from the
     * registry, and a follow-up disconnect of the duplicate would
     * cascade as if the original had left).
     */
    public synchronized boolean registerRmi(String nick, VirtualView v) {
        if (views.containsKey(nick)) {
            return false;
        }
        views.put(nick, v);
        rmiClients.add(nick);
        return true;
    }

    /**
     * Socket counterpart of {@link #registerRmi}. Same contract: no
     * overwrite, returns {@code false} on collision.
     */
    public synchronized boolean registerSocket(String nick, VirtualView v) {
        if (views.containsKey(nick)) {
            return false;
        }
        views.put(nick, v);
        return true;
    }

    public void unregister(String nick) {
        ExecutorService toShutdown;
        synchronized (this) {
            views.remove(nick);
            rmiClients.remove(nick);
            toShutdown = rmiSenders.remove(nick);
        }
        if (toShutdown != null) {
            // shutdownNow: the client is already considered gone, so any
            // pending events in the queue are irrelevant. Called outside
            // the monitor to avoid holding the Server lock across a
            // potentially blocking call.
            toShutdown.shutdownNow();
        }
    }

    /**
     * Atomic flavour of {@code unregister}: returns {@code true} only
     * if the client was actually registered (and is now removed).
     * Used by {@code ServerController} to keep the disconnect cascade
     * idempotent when two send threads fail in parallel against the
     * same client.
     */
    public boolean unregisterIfPresent(String nick) {
        boolean wasPresent;
        ExecutorService toShutdown;
        synchronized (this) {
            wasPresent = views.containsKey(nick);
            if (wasPresent) {
                views.remove(nick);
                rmiClients.remove(nick);
                toShutdown = rmiSenders.remove(nick);
            } else {
                toShutdown = null;
            }
        }
        if (toShutdown != null) {
            toShutdown.shutdownNow();
        }
        return wasPresent;
    }

    public synchronized VirtualView getView(String nick) {
        return views.get(nick);
    }

    public synchronized boolean isConnected(String nick) {
        return views.containsKey(nick);
    }

    /** Defensive copy of the views map. */
    public synchronized Map<String, VirtualView> snapshotViews() {
        return new HashMap<>(views);
    }

    // === Dispatch helper ===

    /**
     * Dispatch a task towards the client identified by {@code nick}.
     *
     * For RMI: submit it on that client's dedicated
     * {@link Executors#newSingleThreadExecutor() single thread
     * executor}, so multiple sends to the same client run in FIFO
     * order on the same thread. This preserves the expected event
     * ordering (e.g. {@code onCharacterCardsPicked} before
     * {@code onBuildingPickTurn}, {@code onCommit} closing a bundle,
     * etc.). Different RMI clients use different executors, so
     * delivery across clients still happens in parallel.
     *
     * For socket: just runs inline ({@code task.run()}). Socket writes
     * are already serialised in practice: {@code Server.send} is
     * invoked while the {@code ServerController} monitor is held, and
     * the actual write inside {@code Socket_VirtualClient.send} is
     * guarded by {@code synchronized(output)}, so two sends to the
     * same socket can't interleave anyway.
     */
    public void send(String nick, Runnable task) {
        ExecutorService exec = rmiSenderFor(nick);
        if (exec == null) {
            // Socket: inline write, already serialised by the caller
            // (ServerController) and by synchronized(output) inside
            // Socket_VirtualClient.send.
            task.run();
            return;
        }
        try {
            exec.execute(task);
        } catch (RejectedExecutionException ignored) {
            // Executor already shut down (client gone): drop the task,
            // nothing to do here.
        }
    }

    /**
     * Returns the executor dedicated to {@code nick} if it's an RMI
     * client, lazily creating it on first use. For socket clients
     * returns {@code null} (writes happen inline).
     */
    private synchronized ExecutorService rmiSenderFor(String nick) {
        if (!rmiClients.contains(nick)) {
            return null;
        }
        ExecutorService exec = rmiSenders.get(nick);
        if (exec == null) {
            exec = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "send-" + nick);
                t.setDaemon(true);
                return t;
            });
            rmiSenders.put(nick, exec);
        }
        return exec;
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

    private static final int DEFAULT_RMI_PORT = 52345;
    private static final int DEFAULT_SOCKET_PORT = 52346;

    public static void main(String[] args) throws Exception {
        int rmiPort = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_RMI_PORT;
        int socketPort = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_SOCKET_PORT;
        new Server().start(rmiPort, socketPort);
    }
}
