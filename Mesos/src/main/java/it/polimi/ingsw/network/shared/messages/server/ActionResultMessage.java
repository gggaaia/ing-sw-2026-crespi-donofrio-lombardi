package it.polimi.ingsw.network.shared.messages.server;

import it.polimi.ingsw.network.client.Client;

public class ActionResultMessage extends ServerMessage {
    private static final long serialVersionUID = 1L;

    private final String action;
    private final boolean success;
    private final String message;

    public ActionResultMessage(String action, boolean success, String message) {
        this.action = action;
        this.success = success;
        this.message = message;
    }

    @Override
    public void handle(Client client) {
        client.onActionResult(action, success, message);
    }
}
