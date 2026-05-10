package it.polimi.ingsw.rmi.client;

import it.polimi.ingsw.common.VirtualServer;
import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;
import it.polimi.ingsw.controller.clientcontroller.ClientController;
import it.polimi.ingsw.view.View;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * RMI client adapter. Two roles:
 *
 *   - On the way out: Rmi_Client.run() prompts the user for a
 *     nickname, sends the very first connect(nickname, selfStub)
 *     call to the server, and then returns. The JVM stays alive
 *     because UnicastRemoteObject.exportObject(this, 0) starts a
 *     non-daemon RMI listener thread that keeps the process up
 *     waiting for incoming notifyXxx callbacks.
 *
 *   - On the way in: each notifyXxx is fired by the server's
 *     dispatcher whenever a payload is available. The body pulls
 *     the actual data with a getter call back on the server stub
 *     (RMI's pull pattern, flavor B) and hands it to the
 *     ClientController for display.
 */
public class Rmi_Client implements VirtualView {

    private final VirtualServer stub_rmi_server;
    private final ClientController client_controller;
    private final VirtualView selfStub;

    public Rmi_Client(VirtualServer server, View view) throws RemoteException {
        this.stub_rmi_server = server;
        this.client_controller = new ClientController(view);
        // Export this object as a remote VirtualView. The returned
        // stub is what we pass to the server on connect; the server
        // stores it and uses it to invoke the four notifyXxx methods
        // back on this JVM.
        this.selfStub = (VirtualView) UnicastRemoteObject.exportObject(this, 0);
    }

    public void run() throws RemoteException {
        String nickname = client_controller.promptNickname();
        client_controller.setNickname(nickname);
        client_controller.setServerStub(stub_rmi_server);
        stub_rmi_server.connect(nickname, selfStub);
        // Run the interactive input loop on this thread. The loop
        // blocks waiting for state updates from the network thread
        // (the RMI worker that handles the inbound notifyXxx
        // callbacks) and prompts the user for the right input each
        // time. Returns when the visitor sees a GAME_ENDED state.
        client_controller.runInputLoop();
    }

    @Override
    public void notifyStateChanged() throws RemoteException {
        GameStateDto state = stub_rmi_server.getState(client_controller.getNickname());
        client_controller.onGameState(state);
    }

    @Override
    public void notifyActionResultAvailable() throws RemoteException {
        ActionResultDto result = stub_rmi_server.getLastActionResult(client_controller.getNickname());
        client_controller.onActionResult(result);
    }

    @Override
    public void notifyInfoAvailable() throws RemoteException {
        String message = stub_rmi_server.getLastInfo(client_controller.getNickname());
        client_controller.onInfo(message);
    }

    @Override
    public void notifyErrorAvailable() throws RemoteException {
        String message = stub_rmi_server.getLastError(client_controller.getNickname());
        client_controller.onError(message);
    }

    @Override
    public void ping() throws RemoteException {
        // No-op: the very fact that the RMI call reached this method
        // and is about to return success is the liveness signal the
        // server's heartbeat is looking for. If our JVM is gone or
        // the network is broken, RMI throws RemoteException on the
        // server side without this method ever being invoked here.
    }
}
