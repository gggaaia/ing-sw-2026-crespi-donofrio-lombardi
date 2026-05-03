package it.polimi.ingsw.network.shared;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.network.shared.messages.server.ServerMessage;

/**
 * Abstraction over a single connected client, used by the controller layer
 * so it does not have to know about sockets, threads, or any other detail
 * of the network transport.
 * <p>
 * The only concrete implementation in Pass 1 is
 * {@code it.polimi.ingsw.network.server.ClientHandler}, which wraps one
 * TCP socket and uses Java object serialization. Tests can supply a fake
 * implementation that records the messages sent without touching the
 * network at all.
 * <p>
 * Lifecycle of a callback (with the corresponding controller transitions):
 * <ol>
 *   <li>The client just connected. {@link #getNickname()},
 *       {@link #getPickedColor()} and {@link #getPlayer()} all return
 *       {@code null}.</li>
 *   <li>The client sent {@code SetNicknameMessage}: the controller calls
 *       {@link #setNickname(String)}. {@link #getPlayer()} is still
 *       {@code null} (the {@code Player} object is built later, at color
 *       pick time).</li>
 *   <li>The client sent {@code PickColorMessage}: the controller calls
 *       {@link #setPickedColor(TotemColor)} and then
 *       {@link #setPlayer(Player)} with the freshly built {@code Player}
 *       instance ({@code new Player(nickname, color)}).</li>
 *   <li>From this point on, the controller always uses {@link #getPlayer()}
 *       to talk about that client.</li>
 * </ol>
 */
public interface ClientCallback {

    /**
     * Sends the given message to this client. Implementations are expected
     * to be thread-safe (the controller may call this from any thread that
     * holds its lock).
     *
     * @param message the message to send (must not be {@code null})
     */
    void send(ServerMessage message);

    /**
     * @return the nickname this client picked, or {@code null} if no
     *         nickname has been set yet
     */
    String getNickname();

    /**
     * Stores the nickname this client picked. Should only be called by the
     * controller, after the nickname has been validated.
     *
     * @param nickname the validated nickname
     */
    void setNickname(String nickname);

    /**
     * @return the totem color this client picked, or {@code null} if no
     *         color has been picked yet
     */
    TotemColor getPickedColor();

    /**
     * Stores the totem color this client picked. Should only be called by
     * the controller, after the color has been validated and removed from
     * the available pool.
     *
     * @param color the validated color
     */
    void setPickedColor(TotemColor color);

    /**
     * @return the {@link Player} instance bound to this client, or
     *         {@code null} if no player has been built for this client yet
     *         (i.e. the color picking phase has not completed for it)
     */
    Player getPlayer();

    /**
     * Binds a {@link Player} instance to this client. Called by the
     * controller right after {@code new Player(nickname, color)} when the
     * client picks its color.
     *
     * @param player the player instance to bind (or {@code null} to clear)
     */
    void setPlayer(Player player);

    /**
     * @return a short human-readable label for logs (typically the nickname
     *         if known, otherwise something like {@code "<unknown>"})
     */
    String describe();

    /**
     * @return {@code true} if this callback is still wired to a live socket
     *         (i.e. the client has not disconnected); {@code false} after
     *         {@link #markDisconnected()} has been called
     */
    boolean isConnected();

    /**
     * Marks this callback as disconnected. From then on {@link #send} is a
     * no-op (the data would be lost on the broken socket anyway) and the
     * controller's skip-disconnected logic will start ignoring this client.
     * <p>
     * Idempotent: calling it more than once has no extra effect.
     */
    void markDisconnected();
}
