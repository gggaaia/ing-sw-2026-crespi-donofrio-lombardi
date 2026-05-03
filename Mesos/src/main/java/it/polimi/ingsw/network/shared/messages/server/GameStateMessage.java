package it.polimi.ingsw.network.shared.messages.server;

import it.polimi.ingsw.network.client.Client;
import it.polimi.ingsw.network.shared.GameStateDto;

public class GameStateMessage extends ServerMessage {
    private static final long serialVersionUID = 1L;

    private final GameStateDto gameState;

    public GameStateMessage(GameStateDto gameState) {
        this.gameState = gameState;
    }

    @Override
    public void handle(Client client) {
        client.onGameState(gameState);
    }
}
