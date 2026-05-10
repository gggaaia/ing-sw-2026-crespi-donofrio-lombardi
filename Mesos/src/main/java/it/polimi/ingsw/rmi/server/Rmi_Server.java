package it.polimi.ingsw.rmi.server;

import it.polimi.ingsw.Server;
import it.polimi.ingsw.common.VirtualServer;
import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;
import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.model.enums.TotemColor;

import java.rmi.RemoteException;
import java.util.List;

/**
 * RMI entry point. Two kinds of methods:
 *
 *   - The 7 "do something" methods (connect / setPlayerCount / ...)
 *     follow the same three-line pattern: lookup slot by nickname,
 *     call ServerController.handleXxx(slot, ...) which returns a
 *     List<Notification>, hand the list to Server.dispatch(slot, ...).
 *
 *   - The 4 "pull" getters (getState / getLastActionResult / ... )
 *     just read the per-slot buffer and return it. They are the
 *     pull-side of the notifyXxx callbacks pushed by the dispatcher.
 *     They never call into the ServerController.
 *
 * The class is exported as VirtualServer in
 * Server.startRmiServer(...). No game logic lives here.
 */
public class Rmi_Server implements VirtualServer {
    final Server java_server;

    public Rmi_Server(Server server) {
        this.java_server = server;
    }

    // --- "do something" methods (call the controller) -----------------

    @Override
    public void connect(String nickname, VirtualView client) throws RemoteException {
        ServerController.ClientData slot = java_server.register(nickname, client);
        // For brand-new slots the constructor already set view and
        // connected = true; for an existing slot (reconnection or
        // impersonation under the trust assumption) we rebind the new
        // view and mark the slot connected. The controller decides
        // afterwards whether to accept or reject.
        slot.setView(client);
        slot.markConnected();
        List<Notification> notifications = java_server.getServer_controller().handleConnect(slot);
        java_server.dispatch(slot, notifications);
    }

    @Override
    public void setPlayerCount(String nickname, int count) throws RemoteException {
        ServerController.ClientData slot = java_server.lookup(nickname);
        List<Notification> notifications = java_server.getServer_controller().handleSetPlayerCount(slot, count);
        java_server.dispatch(slot, notifications);
    }

    @Override
    public void pickColor(String nickname, TotemColor color) throws RemoteException {
        ServerController.ClientData slot = java_server.lookup(nickname);
        List<Notification> notifications = java_server.getServer_controller().handlePickColor(slot, color);
        java_server.dispatch(slot, notifications);
    }

    @Override
    public void pickOfferingTile(String nickname, int tileIndex) throws RemoteException {
        ServerController.ClientData slot = java_server.lookup(nickname);
        List<Notification> notifications = java_server.getServer_controller().handlePickOfferingTile(slot, tileIndex);
        java_server.dispatch(slot, notifications);
    }

    @Override
    public void pickCharacterCards(String nickname,
                                   List<Integer> upperRowIds,
                                   List<Integer> lowerRowIds) throws RemoteException {
        ServerController.ClientData slot = java_server.lookup(nickname);
        List<Notification> notifications = java_server.getServer_controller()
                .handlePickCharacterCards(slot, upperRowIds, lowerRowIds);
        java_server.dispatch(slot, notifications);
    }

    @Override
    public void pickBuildingCards(String nickname,
                                  List<Integer> upperRowIds,
                                  List<Integer> lowerRowIds) throws RemoteException {
        ServerController.ClientData slot = java_server.lookup(nickname);
        List<Notification> notifications = java_server.getServer_controller()
                .handlePickBuildingCards(slot, upperRowIds, lowerRowIds);
        java_server.dispatch(slot, notifications);
    }

    @Override
    public void pickExtraCard(String nickname,
                              Integer cardId,
                              boolean isCharacter) throws RemoteException {
        ServerController.ClientData slot = java_server.lookup(nickname);
        List<Notification> notifications = java_server.getServer_controller()
                .handlePickExtraCard(slot, cardId, isCharacter);
        java_server.dispatch(slot, notifications);
    }

    // --- pull getters (read the per-slot buffer) ----------------------

    @Override
    public GameStateDto getState(String nickname) throws RemoteException {
        ServerController.ClientData slot = java_server.lookup(nickname);
        return slot != null ? slot.getLastState() : null;
    }

    @Override
    public ActionResultDto getLastActionResult(String nickname) throws RemoteException {
        ServerController.ClientData slot = java_server.lookup(nickname);
        return slot != null ? slot.getLastActionResult() : null;
    }

    @Override
    public String getLastInfo(String nickname) throws RemoteException {
        ServerController.ClientData slot = java_server.lookup(nickname);
        return slot != null ? slot.getLastInfo() : null;
    }

    @Override
    public String getLastError(String nickname) throws RemoteException {
        ServerController.ClientData slot = java_server.lookup(nickname);
        return slot != null ? slot.getLastError() : null;
    }
}
