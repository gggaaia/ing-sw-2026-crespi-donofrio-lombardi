package it.polimi.ingsw.socket.server;

import it.polimi.ingsw.Server;
import it.polimi.ingsw.common.wire.ClientToServerMessage;
import it.polimi.ingsw.controller.server.ServerController;
import it.polimi.ingsw.model.enums.TotemColor;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Lato server, una istanza per ogni connessione socket. Legge in loop
 * {@link ClientToServerMessage} dallo stream e li dispatcha al
 * {@link ServerController} tramite il visitor pattern.
 *
 * I corpi dei metodi visit* sono identici a quelli di {@code Rmi_Server}:
 * registrazione + chiamata diretta al controller.
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
            // disconnect pulito; gestito nel finally
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
        this.boundNickname = nickname;
        server.registerSocket(nickname, virtualView);
        controller.connect(nickname, virtualView);
    }

    @Override
    public void visitDisconnect(String nickname) {
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
