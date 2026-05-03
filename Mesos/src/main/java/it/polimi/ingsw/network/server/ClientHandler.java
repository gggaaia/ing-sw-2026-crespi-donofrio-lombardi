package it.polimi.ingsw.network.server;

import it.polimi.ingsw.controller.GameController;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.network.shared.ClientCallback;
import it.polimi.ingsw.network.shared.messages.client.ClientMessage;
import it.polimi.ingsw.network.shared.messages.server.ErrorMessage;
import it.polimi.ingsw.network.shared.messages.server.ServerMessage;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Per-connection thread that wraps one client's TCP socket. Knows nothing
 * about the rules; reads {@link ClientMessage}s from the wire and forwards
 * them to the {@link GameController} via the
 * {@link ClientMessage#handle(GameController, ClientCallback) handle}
 * dispatch. Writes {@link ServerMessage}s back through {@link #send}.
 * <p>
 * Implements {@link ClientCallback} so the controller can call back into
 * this handler without ever importing the network/socket code.
 */
public class ClientHandler implements Runnable, ClientCallback {

    /** The shared game controller (one per server process). */
    private final GameController controller;

    /** The TCP socket talking to this client. */
    private final Socket socket;

    /** Wire input stream for incoming {@code ClientMessage}s. */
    private final ObjectInputStream in;

    /** Wire output stream for outgoing {@code ServerMessage}s. */
    private final ObjectOutputStream out;

    // --- ClientCallback state ------------------------------------------

    /** Nickname this client picked, or {@code null} until set. */
    private String nickname;

    /** Color this client picked, or {@code null} until set. */
    private TotemColor pickedColor;

    /** Player object built when this client picks a color, or {@code null}
     *  during all earlier phases. */
    private Player player;

    /**
     * Whether this handler is still wired to a live socket. Flipped to
     * {@code false} by {@link #markDisconnected()} as soon as the read
     * loop sees an EOF / I/O error. Volatile because the controller
     * thread may read it concurrently with the read loop's writes.
     */
    private volatile boolean connected = true;

    /**
     * Initializes the streams. The {@link ObjectOutputStream} is created
     * (and flushed) before the {@link ObjectInputStream} because the
     * Object{Input,Output}Stream protocol writes/reads a header on
     * construction; doing it in the wrong order on both peers deadlocks.
     *
     * @param controller the shared game controller
     * @param socket     the connected client socket
     * @throws IOException if either stream cannot be opened
     */
    public ClientHandler(GameController controller, Socket socket) throws IOException {
        this.controller = controller;
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    /**
     * Read loop: deserialize one {@link ClientMessage} after another and
     * dispatch each to the controller. On clean EOF we just exit; on any
     * other I/O failure we log and exit. The {@code finally} block always
     * notifies the controller of the disconnect and closes the socket.
     */
    @Override
    public void run() {
        try {
            while (!socket.isClosed()) {
                Object object = in.readObject();
                if (!(object instanceof ClientMessage)) {
                    sendError("Received an invalid client message");
                    continue;
                }
                ClientMessage message = (ClientMessage) object;
                message.handle(controller, this);
            }
        } catch (EOFException e) {
            // Normal disconnect.
            System.out.println("Client disconnected: " + describe());
        } catch (IOException e) {
            System.out.println("I/O error with client " + describe()
                    + ": " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found while reading from client "
                    + describe() + ": " + e.getMessage());
        } finally {
            // Mark disconnected BEFORE telling the controller, so the
            // controller's reconnection logic and skip-disconnected
            // helpers immediately see isConnected() == false.
            markDisconnected();
            controller.onClientDisconnected(this);
            close();
        }
    }

    /**
     * Writes a server message to the wire. {@code synchronized} so writes
     * from the controller (which may come from any thread that holds the
     * controller lock) cannot interleave bytes on the underlying stream.
     * <p>
     * The {@link ObjectOutputStream#reset()} call is essential: without
     * it, {@code writeObject} would cache references and a mutated model
     * object would appear unchanged on the receiving side. We always
     * reset so each write produces a fresh deep snapshot.
     */
    @Override
    public synchronized void send(ServerMessage message) {
        // After disconnect we drop the message: writing to a broken socket
        // is pointless and would only spam the log with errors. The
        // controller still calls send() on the disconnected callback during
        // normal broadcasts; this guard makes those calls safe.
        if (!connected) {
            return;
        }
        try {
            out.reset();
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            System.out.println("Could not send message to client "
                    + describe() + ": " + e.getMessage());
        }
    }

    /** Convenience: wraps a string in an {@link ErrorMessage} and sends it. */
    private void sendError(String message) {
        send(new ErrorMessage(message));
    }

    /** Closes the socket, swallowing any I/O error (best-effort cleanup). */
    private void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing useful to do here.
        }
    }

    // ------------------------------------------------------------------
    // ClientCallback implementation
    // ------------------------------------------------------------------

    @Override
    public String getNickname() {
        return nickname;
    }

    @Override
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    @Override
    public TotemColor getPickedColor() {
        return pickedColor;
    }

    @Override
    public void setPickedColor(TotemColor color) {
        this.pickedColor = color;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public String describe() {
        return (nickname == null) ? "<unknown>" : nickname;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void markDisconnected() {
        // Idempotent: only do work the first time.
        if (!connected) {
            return;
        }
        connected = false;
    }
}
