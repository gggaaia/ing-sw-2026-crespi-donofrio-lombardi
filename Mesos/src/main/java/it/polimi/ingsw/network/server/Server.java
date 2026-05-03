package it.polimi.ingsw.network.server;

import it.polimi.ingsw.controller.GameController;
import it.polimi.ingsw.controller.GameSnapshot;
import it.polimi.ingsw.controller.PersistenceManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Tiny accept-loop server. Owns a single {@link GameController} and a
 * {@link ServerSocket}; for every accepted connection it builds a
 * {@link ClientHandler}, hands it to the controller's
 * {@code onClientConnected} hook, then starts the handler on its own
 * thread.
 * <p>
 * Run with {@code java it.polimi.ingsw.network.server.Server [port] [--fresh]};
 * <ul>
 *   <li>The optional {@code port} argument defaults to {@value #DEFAULT_PORT}.</li>
 *   <li>The optional {@code --fresh} flag (Pass 2) tells the server to
 *       ignore any saved snapshot on disk and start a brand new lobby.
 *       Without it the server tries to load the previous match and
 *       resume; players reconnect by nickname.</li>
 * </ul>
 */
public class Server {

    /** Default TCP port if the caller did not pass one. */
    private static final int DEFAULT_PORT = 12345;

    /** CLI flag that disables snapshot restore (Pass 2). */
    private static final String FRESH_FLAG = "--fresh";

    /** Shared controller (one match per server instance). */
    private final GameController controller;

    /** Listening socket. */
    private final ServerSocket serverSocket;

    /**
     * Builds the server on the given port using the supplied controller.
     * The controller is injected (rather than constructed inside) so the
     * caller can preload it from a snapshot before the accept loop starts.
     *
     * @param port       TCP port to listen on
     * @param controller the game controller to wire each accepted client to
     * @throws IOException if the port cannot be bound
     */
    public Server(int port, GameController controller) throws IOException {
        this.controller = controller;
        this.serverSocket = new ServerSocket(port);
        System.out.println("Server listening on port " + port);
    }

    /**
     * Blocks accepting connections forever. Each accepted socket is
     * wrapped in a fresh {@link ClientHandler} and handed off to its own
     * thread; the controller is informed via
     * {@link GameController#onClientConnected(it.polimi.ingsw.network.shared.ClientCallback)}
     * so it can send the initial state snapshot.
     */
    public void run() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                System.out.println("New connection from " + socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(controller, socket);
                controller.onClientConnected(handler);
                Thread t = new Thread(handler, "ClientHandler-" + socket.getPort());
                t.setDaemon(false);
                t.start();
            } catch (IOException e) {
                System.out.println("Accept failed: " + e.getMessage());
                // Keep the server alive: a single bad connection should
                // not crash the whole process.
            }
        }
    }

    /**
     * Entry point.
     * <p>
     * Args:
     * <ul>
     *   <li>The first numeric arg is the port (default {@value #DEFAULT_PORT}).</li>
     *   <li>The literal string {@value #FRESH_FLAG} (in any position) skips
     *       the snapshot restore.</li>
     * </ul>
     *
     * @param args see above
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        boolean fresh = false;

        // Iterate the args by hand so we can support either order
        // ("12345 --fresh" or "--fresh 12345"). We only recognise one
        // numeric arg (port) and the --fresh flag; anything else is
        // ignored with a log line.
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals(FRESH_FLAG)) {
                fresh = true;
            } else {
                try {
                    port = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    System.out.println("Ignoring unknown argument: " + arg);
                }
            }
        }

        // Build the controller; restore from disk if a snapshot exists
        // and --fresh wasn't given.
        GameController controller = new GameController();
        if (!fresh && PersistenceManager.exists()) {
            try {
                GameSnapshot snapshot = PersistenceManager.load();
                controller.restoreFrom(snapshot);
                System.out.println("Restored snapshot from "
                        + PersistenceManager.FILENAME
                        + " (waiting for players to reconnect by nickname).");
            } catch (Exception e) {
                System.out.println("Failed to restore snapshot: "
                        + e.getMessage());
                System.out.println("Starting fresh.");
            }
        } else if (fresh) {
            // Explicit fresh start: also delete any stale snapshot.
            PersistenceManager.delete();
            System.out.println("--fresh: ignoring any saved snapshot.");
        }

        try {
            new Server(port, controller).run();
        } catch (IOException e) {
            System.out.println("Could not start server: " + e.getMessage());
            System.exit(1);
        }
    }
}
