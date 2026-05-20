package it.polimi.ingsw.rmi.server;

import it.polimi.ingsw.Server;
import it.polimi.ingsw.common.VirtualServer;
import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.controller.server.ServerController;
import it.polimi.ingsw.model.enums.TotemColor;

import java.rmi.RemoteException;
import java.util.List;

/**
 * Server-side {@link VirtualServer} implementation for the RMI
 * transport. Each method is a one-liner: registration (for
 * {@code connect}) + a direct call to the {@link ServerController}.
 *
 * RMI exports this as a remote stub inside
 * {@code Server.startRmiServer}. When an RMI client invokes a method
 * on the stub, the RMI runtime spins up a worker thread that runs
 * the body below, which in turn enters the {@code ServerController}
 * monitor.
 */
public class Rmi_Server implements VirtualServer {

    private final Server server;
    private final ServerController controller;

    public Rmi_Server(Server server) {
        this.server = server;
        this.controller = server.getController();
    }

    @Override
    public void connect(String nickname, VirtualView client) throws RemoteException {
        // Phase check FIRST: if the controller isn't accepting new
        // connections, drop the attempt with "no game" WITHOUT
        // registering. See the matching comment in
        // {@code Socket_ClientHandler.visitConnect}.
        if (!controller.isAcceptingNewConnections()) {
            try {
                client.onNicknameRejected("no game currently accepting players");
            } catch (RemoteException ignored) {
                // Duplicate's stub already gone; nothing else to do.
            }
            return;
        }
        // Phase accepts: if the nickname is already taken, the
        // registry refuses the overwrite so the original client
        // stays intact, and we send the rejection straight to the
        // duplicate's stub.
        if (!server.registerRmi(nickname, client)) {
            try {
                client.onNicknameRejected("nickname already in use");
            } catch (RemoteException ignored) {
                // Duplicate's stub already gone; nothing else to do.
            }
            return;
        }
        controller.connect(nickname, client);
    }

    @Override
    public void disconnect(String nickname) throws RemoteException {
        controller.disconnect(nickname);
    }

    @Override
    public void setPlayerCount(String nickname, int count) throws RemoteException {
        controller.setPlayerCount(nickname, count);
    }

    @Override
    public void pickColor(String nickname, TotemColor color) throws RemoteException {
        controller.pickColor(nickname, color);
    }

    @Override
    public void pickOfferingTile(String nickname, int tileIndex) throws RemoteException {
        controller.pickOfferingTile(nickname, tileIndex);
    }

    @Override
    public void pickCharacterCards(String nickname,
                                   List<Integer> upperRowIds,
                                   List<Integer> lowerRowIds) throws RemoteException {
        controller.pickCharacterCards(nickname, upperRowIds, lowerRowIds);
    }

    @Override
    public void pickBuildingCards(String nickname,
                                  List<Integer> upperRowIds,
                                  List<Integer> lowerRowIds) throws RemoteException {
        controller.pickBuildingCards(nickname, upperRowIds, lowerRowIds);
    }

    @Override
    public void pickExtraCard(String nickname,
                              Integer cardId,
                              boolean isCharacter) throws RemoteException {
        controller.pickExtraCard(nickname, cardId, isCharacter);
    }
}
