package it.polimi.ingsw.network.shared.messages.client;

import it.polimi.ingsw.controller.GameController;
import it.polimi.ingsw.network.shared.ClientCallback;

/**
 * Sent by the client during the lobby phases to register a nickname (or
 * to retry after a rejection).
 */
public class SetNicknameMessage extends ClientMessage {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** The nickname the client wants to use. */
    private final String nickname;

    /**
     * @param nickname the desired nickname (server validates and trims)
     */
    public SetNicknameMessage(String nickname) {
        this.nickname = nickname;
    }

    @Override
    public void handle(GameController controller, ClientCallback client) {
        controller.handleNickname(client, nickname);
    }
}
