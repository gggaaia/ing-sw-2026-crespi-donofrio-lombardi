package it.polimi.ingsw.socket.server;

import it.polimi.ingsw.Server;
import it.polimi.ingsw.common.wire.ClientToServerMessage;
import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.model.enums.TotemColor;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Per-connection wire-message reader on the server side. Lives in its
 * own thread, reads serialized ClientToServerMessage instances from
 * the input stream, and discriminates among them via the visitor
 * pattern (msg.accept(this) routes to the right visitXxx).
 *
 * Identity model: the handler is bound to one socket connection; the
 * very first message must be ClientToServerMessage.Connect, which
 * carries the client's nickname. The handler stores it in
 * boundNickname and uses it to look up the ServerController.ClientData
 * for every subsequent message. The wire never carries the nickname
 * again after Connect (architecture section 2.6).
 *
 * On any read error or EOF (graceful disconnect), the handler exits
 * the loop and calls handleClientDisconnected on the controller from
 * the finally block.
 */
public class Socket_ClientHandler implements ClientToServerMessage.Visitor {

    private final Server server;
    private final ObjectInputStream input;
    private final Socket_VirtualClient virtualClient;
    private String boundNickname;

    public Socket_ClientHandler(Server server,
                                ObjectInputStream input,
                                Socket_VirtualClient virtualClient) {
        this.server = server;
        this.input = input;
        this.virtualClient = virtualClient;
    }

    public void runVirtualServer() {
        try {
            while (true) {
                ClientToServerMessage msg = (ClientToServerMessage) input.readObject();
                msg.accept(this);
            }
        } catch (EOFException eof) {
            // Client disconnected cleanly; nothing to log, fall through to
            // the finally block which signals the controller.
        } catch (IOException io) {
            System.err.println("Socket read error for "
                    + boundNickname + ": " + io.getMessage());
        } catch (ClassNotFoundException | ClassCastException malformed) {
            System.err.println("Malformed wire message for "
                    + boundNickname + ": " + malformed.getMessage());
        } finally {
            handleDisconnect();
        }
    }

    @Override
    public void visitConnect(String nickname) {
        ServerController.ClientData slot = server.register(nickname, virtualClient);
        slot.setView(virtualClient);
        slot.markConnected();
        // Bind the slot to the virtualClient so its notifyXxx methods
        // can read from slot.getLastXxx() to assemble the wire message.
        virtualClient.setBoundClientData(slot);
        this.boundNickname = nickname;
        List<Notification> notifications = server.getServer_controller().handleConnect(slot);
        server.dispatch(slot, notifications);
    }

    @Override
    public void visitSetPlayerCount(int count) {
        ServerController.ClientData slot = server.lookup(boundNickname);
        List<Notification> notifications = server.getServer_controller().handleSetPlayerCount(slot, count);
        server.dispatch(slot, notifications);
    }

    @Override
    public void visitPickColor(TotemColor color) {
        ServerController.ClientData slot = server.lookup(boundNickname);
        List<Notification> notifications = server.getServer_controller().handlePickColor(slot, color);
        server.dispatch(slot, notifications);
    }

    @Override
    public void visitPickOfferingTile(int tileIndex) {
        ServerController.ClientData slot = server.lookup(boundNickname);
        List<Notification> notifications = server.getServer_controller().handlePickOfferingTile(slot, tileIndex);
        server.dispatch(slot, notifications);
    }

    @Override
    public void visitPickCharacterCards(List<Integer> upperRowIds, List<Integer> lowerRowIds) {
        ServerController.ClientData slot = server.lookup(boundNickname);
        List<Notification> notifications = server.getServer_controller()
                .handlePickCharacterCards(slot, upperRowIds, lowerRowIds);
        server.dispatch(slot, notifications);
    }

    @Override
    public void visitPickBuildingCards(List<Integer> upperRowIds, List<Integer> lowerRowIds) {
        ServerController.ClientData slot = server.lookup(boundNickname);
        List<Notification> notifications = server.getServer_controller()
                .handlePickBuildingCards(slot, upperRowIds, lowerRowIds);
        server.dispatch(slot, notifications);
    }

    @Override
    public void visitPickExtraCard(Integer cardId, boolean isCharacter) {
        ServerController.ClientData slot = server.lookup(boundNickname);
        List<Notification> notifications = server.getServer_controller()
                .handlePickExtraCard(slot, cardId, isCharacter);
        server.dispatch(slot, notifications);
    }

    private void handleDisconnect() {
        // boundNickname may be null if the client connection died before
        // sending any message: server.lookup(null) returns null, the
        // controller's stub returns an empty list, and dispatch with a
        // null sender + empty list does nothing. No null check needed.
        ServerController.ClientData slot = server.lookup(boundNickname);
        List<Notification> notifications = server.getServer_controller().handleClientDisconnected(slot);
        server.dispatch(slot, notifications);
    }
}
