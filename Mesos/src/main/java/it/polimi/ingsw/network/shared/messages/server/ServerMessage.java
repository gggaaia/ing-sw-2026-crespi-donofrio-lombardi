package it.polimi.ingsw.network.shared.messages.server;

import it.polimi.ingsw.network.client.Client;

import java.io.Serializable;

public abstract class ServerMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public abstract void handle(Client client);
}
