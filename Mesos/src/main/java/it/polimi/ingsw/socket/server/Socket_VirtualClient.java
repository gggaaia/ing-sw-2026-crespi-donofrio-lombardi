package it.polimi.ingsw.socket.server;

import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.wire.ServerToClientMessage;
import it.polimi.ingsw.controller.servercontroller.ServerController;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;

/**
 * Server-side write-only proxy that, for a single socket-connected
 * client, implements VirtualView. The four notifyXxx methods do
 * exactly what a regular VirtualView does PLUS one extra step,
 * because socket clients do not pull: the implementation reads the
 * payload from this proxy's bound ClientData buffer (which the
 * dispatcher just wrote into) and writes a ServerToClientMessage on
 * the socket carrying the payload. The receiving Socket_Client gets
 * the data on the wire (push), exactly as before this refactor.
 *
 * The bound ClientData reference is set after the very first
 * Connect message, by Socket_ClientHandler.visitConnect, via
 * setBoundClientData(...).
 *
 * If the underlying write fails, RemoteException is thrown so the
 * dispatcher catches it and marks the client disconnected (same code
 * path as for an RMI failure).
 */
public class Socket_VirtualClient implements VirtualView {

    private final ObjectOutputStream output;
    private ServerController.ClientData boundData;

    public Socket_VirtualClient(ObjectOutputStream output) {
        this.output = output;
    }

    /**
     * Binds this proxy to its server-side ClientData slot. Called by
     * Socket_ClientHandler.visitConnect once the slot has been
     * registered. The proxy reads from boundData's buffers when
     * notifyXxx is called.
     */
    public void setBoundClientData(ServerController.ClientData boundData) {
        this.boundData = boundData;
    }

    @Override
    public void notifyStateChanged() throws RemoteException {
        send(new ServerToClientMessage.GameState(boundData.getLastState()));
    }

    @Override
    public void notifyActionResultAvailable() throws RemoteException {
        send(new ServerToClientMessage.ActionResult(boundData.getLastActionResult()));
    }

    @Override
    public void notifyInfoAvailable() throws RemoteException {
        send(new ServerToClientMessage.Info(boundData.getLastInfo()));
    }

    @Override
    public void notifyErrorAvailable() throws RemoteException {
        send(new ServerToClientMessage.Error(boundData.getLastError()));
    }

    @Override
    public void ping() throws RemoteException {
        // Socket transport detects client disconnects via the
        // Socket_ClientHandler read loop (EOF / IOException on
        // input.readObject), so an extra wire ping is redundant. The
        // heartbeat in Server.java still calls this method on every
        // Socket_VirtualClient for uniformity with RMI; the no-op is
        // safe.
    }

    private void send(ServerToClientMessage msg) throws RemoteException {
        try {
            synchronized (output) {
                output.writeObject(msg);
                output.flush();
                // Clears the serialization cache so a subsequent push of a
                // mutated DTO is sent in full instead of as a back-reference.
                output.reset();
            }
        } catch (IOException e) {
            throw new RemoteException("Socket push failed", e);
        }
    }
}
