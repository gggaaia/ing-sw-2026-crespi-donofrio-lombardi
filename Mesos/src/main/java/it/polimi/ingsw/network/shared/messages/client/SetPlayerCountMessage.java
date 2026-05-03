package it.polimi.ingsw.network.shared.messages.client;

import it.polimi.ingsw.controller.GameController;
import it.polimi.ingsw.network.shared.ClientCallback;

/**
 * Sent by the first player to set the total number of players the lobby
 * should wait for (range 2..5).
 */
public class SetPlayerCountMessage extends ClientMessage {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** Desired total number of players (server validates the range). */
    private final int playerCount;

    /**
     * @param playerCount the desired total player count
     */
    public SetPlayerCountMessage(int playerCount) {
        this.playerCount = playerCount;
    }

    @Override
    public void handle(GameController controller, ClientCallback client) {
        controller.handlePlayerCount(client, playerCount);
    }
}
