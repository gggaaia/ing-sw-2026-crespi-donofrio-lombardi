package it.polimi.ingsw.socket.server;

import it.polimi.ingsw.Server;
import it.polimi.ingsw.common.wire.ClientToServerMessage;
import it.polimi.ingsw.controller.server.ServerController;
import it.polimi.ingsw.model.enums.TotemColor;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Server-side, one instance per socket connection. Loops reading
 * {@link ClientToServerMessage} off the stream and dispatches each
 * to the {@link ServerController} via the visitor pattern.
 *
 * The visit* bodies match {@code Rmi_Server}'s: registration +
 * direct call to the controller.
 */
public class Socket_ClientHandler implements ClientToServerMessage.Visitor {

    private final Server server;
    private final ServerController controller;
    private final ObjectInputStream input;
    private final Socket_VirtualClient virtualView;
    private String boundNickname;

    public Socket_ClientHandler(Server server,
                                ObjectInputStream input,
                                Socket_VirtualClient virtualView) {
        this.server = server;
        this.controller = server.getController();
        this.input = input;
        this.virtualView = virtualView;
    }

    public void runVirtualServer() {
        try {
            while (true) {
                ClientToServerMessage msg = (ClientToServerMessage) input.readObject();
                msg.accept(this);
            }
        } catch (EOFException eof) {
            // clean disconnect; handled in the finally block
        } catch (IOException io) {
            System.err.println("Socket read error for " + boundNickname + ": " + io.getMessage());
        } catch (ClassNotFoundException | ClassCastException malformed) {
            System.err.println("Malformed wire message for " + boundNickname + ": " + malformed.getMessage());
        } finally {
            handleDisconnect();
        }
    }

    @Override
    public void visitConnect(String nickname) {
        // Let the player in only if the game is taking new players, or
        // if this is a player coming back to a game that is already
        // running (canReconnect). If neither is true, tell them there
        // is no game to join and stop here, without registering them.
        // We do not register a name that is already taken, so a second
        // person with the same name can't kick out the real player.
        if (!server.getController().isAcceptingNewConnections()
                && !server.getController().canReconnect(nickname)) {
            try {
                virtualView.onNicknameRejected("no game currently accepting players");
            } catch (RemoteException ignored) {
                // Duplicate's socket is gone; nothing else to do.
            }
            return;
        }
        // Phase accepts connects: now try to claim the nickname.
        // If it's already taken, the registration is refused (the
        // existing client's view is preserved): we then send the
        // rejection straight to the duplicate's view, leave
        // boundNickname null so the finally-block disconnect doesn't
        // cascade onto the legitimate holder, and let the read loop
        // terminate naturally when the client closes.
        if (!server.registerSocket(nickname, virtualView)) {
            try {
                virtualView.onNicknameRejected("nickname already in use");
            } catch (RemoteException ignored) {
                // Duplicate's socket is gone; nothing else to do.
            }
            return;
        }
        this.boundNickname = nickname;
        controller.connect(nickname, virtualView);
    }

    @Override
    public void visitDisconnect(String nickname) {
        // Only honor disconnects for the nickname this socket is
        // actually bound to. Without this guard, a client that was
        // rejected at connect (and therefore never set boundNickname)
        // could still ship a disconnect(<someone else's nick>) and
        // kick the legitimate holder out.
        if (boundNickname == null || !boundNickname.equals(nickname)) {
            return;
        }
        controller.disconnect(nickname);
    }

    @Override
    public void visitSetPlayerCount(String nickname, int count) {
        controller.setPlayerCount(nickname, count);
    }

    @Override
    public void visitPickColor(String nickname, TotemColor color) {
        controller.pickColor(nickname, color);
    }

    @Override
    public void visitPickOfferingTile(String nickname, int tileIndex) {
        controller.pickOfferingTile(nickname, tileIndex);
    }

    @Override
    public void visitPickCharacterCards(String nickname, List<Integer> upper, List<Integer> lower) {
        controller.pickCharacterCards(nickname, upper, lower);
    }

    @Override
    public void visitPickBuildingCards(String nickname, List<Integer> upper, List<Integer> lower) {
        controller.pickBuildingCards(nickname, upper, lower);
    }

    @Override
    public void visitPickExtraCard(String nickname, Integer cardId, boolean isCharacter) {
        controller.pickExtraCard(nickname, cardId, isCharacter);
    }

    private void handleDisconnect() {
        if (boundNickname != null) {
            controller.disconnect(boundNickname);
        }
    }
}
