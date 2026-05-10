package it.polimi.ingsw.socket.client;

import it.polimi.ingsw.common.VirtualServer;
import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;
import it.polimi.ingsw.common.wire.ClientToServerMessage;
import it.polimi.ingsw.model.enums.TotemColor;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Client-side write-only proxy that, for a single socket-connected
 * server, implements VirtualServer. Each "do something" method wraps
 * its argument in the matching ClientToServerMessage subclass and
 * writes it on the stream owned by this proxy.
 *
 * The 4 getter methods (getState / getLastActionResult / getLastInfo
 * / getLastError) belong to the RMI pull pattern and have no socket
 * equivalent: socket clients receive the payload directly on the
 * wire, inside ServerToClientMessage subclasses, so they never need
 * to pull. Implementations throw UnsupportedOperationException to
 * make the mistake loud if anyone ever calls them.
 *
 * Two parameters from VirtualServer never appear on the socket wire:
 *   - the VirtualView client argument of connect: on socket the
 *     server-side handler already holds its own Socket_VirtualClient,
 *     so the stub passed by the caller would be redundant (and
 *     VirtualView is not Serializable anyway).
 *   - the String nickname argument of every method except connect:
 *     the server-side handler binds itself to the nickname after the
 *     first Connect message and uses that binding for every
 *     subsequent message.
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
    public void setPlayerCount(String nickname, int count) throws RemoteException {
        send(new ClientToServerMessage.SetPlayerCount(count));
    }

    @Override
    public void pickColor(String nickname, TotemColor color) throws RemoteException {
        send(new ClientToServerMessage.PickColor(color));
    }

    @Override
    public void pickOfferingTile(String nickname, int tileIndex) throws RemoteException {
        send(new ClientToServerMessage.PickOfferingTile(tileIndex));
    }

    @Override
    public void pickCharacterCards(String nickname,
                                   List<Integer> upperRowIds,
                                   List<Integer> lowerRowIds) throws RemoteException {
        send(new ClientToServerMessage.PickCharacterCards(upperRowIds, lowerRowIds));
    }

    @Override
    public void pickBuildingCards(String nickname,
                                  List<Integer> upperRowIds,
                                  List<Integer> lowerRowIds) throws RemoteException {
        send(new ClientToServerMessage.PickBuildingCards(upperRowIds, lowerRowIds));
    }

    @Override
    public void pickExtraCard(String nickname,
                              Integer cardId,
                              boolean isCharacter) throws RemoteException {
        send(new ClientToServerMessage.PickExtraCard(cardId, isCharacter));
    }

    @Override
    public GameStateDto getState(String nickname) throws RemoteException {
        throw new UnsupportedOperationException(
                "Socket_VirtualServer.getState: socket clients receive state via wire push");
    }

    @Override
    public ActionResultDto getLastActionResult(String nickname) throws RemoteException {
        throw new UnsupportedOperationException(
                "Socket_VirtualServer.getLastActionResult: socket clients receive results via wire push");
    }

    @Override
    public String getLastInfo(String nickname) throws RemoteException {
        throw new UnsupportedOperationException(
                "Socket_VirtualServer.getLastInfo: socket clients receive info via wire push");
    }

    @Override
    public String getLastError(String nickname) throws RemoteException {
        throw new UnsupportedOperationException(
                "Socket_VirtualServer.getLastError: socket clients receive errors via wire push");
    }

    private void send(ClientToServerMessage msg) throws RemoteException {
        try {
            synchronized (output) {
                output.writeObject(msg);
                output.flush();
                output.reset();
            }
        } catch (IOException e) {
            throw new RemoteException("Socket send failed", e);
        }
    }
}
