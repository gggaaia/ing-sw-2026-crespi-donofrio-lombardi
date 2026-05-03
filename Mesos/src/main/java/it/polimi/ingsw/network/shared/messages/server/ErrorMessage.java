package it.polimi.ingsw.network.shared.messages.server;

import it.polimi.ingsw.network.client.Client;

public class ErrorMessage extends ServerMessage {
    private static final long serialVersionUID = 1L;

    private final String message;

    public ErrorMessage(String message) {
        this.message = message;
    }

    @Override
    public void handle(Client client) {
        client.onError(message);
    }
}
