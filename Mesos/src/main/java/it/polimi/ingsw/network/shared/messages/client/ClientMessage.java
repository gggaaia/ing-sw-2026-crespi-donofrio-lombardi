package it.polimi.ingsw.network.shared.messages.client;

import it.polimi.ingsw.controller.GameController;
import it.polimi.ingsw.network.shared.ClientCallback;

import java.io.Serializable;

/**
 * Base type of every message a client sends to the server.
 * <p>
 * Each subclass knows which {@link GameController} method it should
 * trigger; the server-side dispatcher just calls
 * {@link #handle(GameController, ClientCallback)} and lets the message
 * pick the right handler. This is a simple visitor / double-dispatch
 * pattern: the dispatcher does not need a {@code switch} on the message
 * type, and adding a new message type is a one-file change.
 */
public abstract class ClientMessage implements Serializable {
    /**
     * Serialization version. Inherited by every concrete message; bump
     * only on backwards-incompatible field-layout changes.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Routes this message to the appropriate {@link GameController}
     * method.
     *
     * @param controller the game controller that owns the FSM
     * @param client     the callback for the client that sent this message
     */
    public abstract void handle(GameController controller, ClientCallback client);
}
