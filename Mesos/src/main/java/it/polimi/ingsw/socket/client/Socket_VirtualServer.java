package it.polimi.ingsw.socket.client;

import it.polimi.ingsw.common.VirtualServer;
import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.wire.ClientToServerMessage;
import it.polimi.ingsw.model.enums.TotemColor;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Client-side {@link VirtualServer} implementation for the socket
 * transport. Each method is a one-liner that wraps its parameters
 * in a {@link ClientToServerMessage} and writes it to the socket.
 *
 * From the
 * {@link it.polimi.ingsw.controller.client.ClientController}'s point
 * of view this is interchangeable with an RMI stub: the controller
 * calls {@code virtualServer.pickColor(...)} and the body here just
 * packs and ships it.
 */
public class Socket_VirtualServer implements VirtualServer {

    private final ObjectOutputStream output;

    public Socket_VirtualServer(ObjectOutputStream output) {
        this.output = output;
    }

    @Override
    public void connect(String nickname, VirtualView client) throws RemoteException {
        send(new ClientToServerMessage.Connect(nickname));
    }

    @Override
    public void disconnect(String nickname) throws RemoteException {
        send(new ClientToServerMessage.Disconnect(nickname));
    }

    @Override
    public void setPlayerCount(String nickname, int count) throws RemoteException {
        send(new ClientToServerMessage.SetPlayerCount(nickname, count));
    }

    @Override
    public void pickColor(String nickname, TotemColor color) throws RemoteException {
        send(new ClientToServerMessage.PickColor(nickname, color));
    }

    @Override
    public void pickOfferingTile(String nickname, int tileIndex) throws RemoteException {
        send(new ClientToServerMessage.PickOfferingTile(nickname, tileIndex));
    }

    @Override
    public void pickCharacterCards(String nickname,
                                   List<Integer> upperRowIds,
                                   List<Integer> lowerRowIds) throws RemoteException {
        send(new ClientToServerMessage.PickCharacterCards(nickname, upperRowIds, lowerRowIds));
    }

    @Override
    public void pickBuildingCards(String nickname,
                                  List<Integer> upperRowIds,
                                  List<Integer> lowerRowIds) throws RemoteException {
        send(new ClientToServerMessage.PickBuildingCards(nickname, upperRowIds, lowerRowIds));
    }

    @Override
    public void pickExtraCard(String nickname,
                              Integer cardId,
                              boolean isCharacter) throws RemoteException {
        send(new ClientToServerMessage.PickExtraCard(nickname, cardId, isCharacter));
    }

    private void send(ClientToServerMessage msg) throws RemoteException {
        try {
            synchronized (output) {
                output.writeObject(msg);
                output.flush();
                output.reset();
            }
        } catch (IOException e) {
            throw new RemoteException("socket send failed", e);
        }
    }
}
