package it.polimi.ingsw.socket.client;

import it.polimi.ingsw.common.dto.GameSnapshot;
import it.polimi.ingsw.common.wire.ServerToClientMessage;
import it.polimi.ingsw.controller.client.ClientController;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.view.View;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

/**
 * Adapter client-side per il trasporto socket. Tre cose:
 *   1. crea un {@link Socket_VirtualServer} e lo dà al ClientController
 *      (che lo user\u00e0 come VirtualServer per mandare azioni)
 *   2. fa il prompt iniziale del nickname e manda il primo connect
 *   3. legge in loop i {@link ServerToClientMessage} dal socket e li
 *      dispatcha al ClientController tramite visitor pattern
 *
 * I corpi dei metodi visit* sono identici a quelli di {@code Rmi_Client}:
 * chiamata diretta al ClientController.
 */
public class Socket_Client implements ServerToClientMessage.Visitor {

    private final ObjectInputStream input;
    private final Socket_VirtualServer virtualServer;
    private final ClientController controller;

    public Socket_Client(ObjectInputStream input, ObjectOutputStream output, View view) {
        this.input = input;
        this.virtualServer = new Socket_VirtualServer(output);
        this.controller = new ClientController(view);
        this.controller.setVirtualServer(virtualServer);
    }

    public void run() {
        String nickname = controller.promptNickname();
        controller.setNickname(nickname);

        // Spawn reader thread PRIMA di mandare connect: cos\u00ec se il server
        // ci risponde subito con onGameSnapshot lo possiamo ricevere.
        Thread reader = new Thread(new Runnable() {
            @Override public void run() {
                readLoop();
            }
        }, "socket-client-reader");
        reader.setDaemon(true);
        reader.start();

        try {
            virtualServer.connect(nickname, null);
        } catch (RemoteException e) {
            controller.actionRejected("connect failed: " + e.getMessage());
            return;
        }

        // Aspettiamo qui finch\u00e9 il reader thread riceve l'evento
        // onGameEnded e fa countDown sul latch. Nessun polling: il main
        // thread \u00e8 in wait profonda. Tenere vivo main qui \u00e8
        // ESSENZIALE perch\u00e9 il reader thread \u00e8 daemon: se main
        // ritornasse, il JVM ucciderebbe il reader prima ancora che la
        // partita sia iniziata.
        try {
            controller.awaitGameEnd();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            virtualServer.disconnect(nickname);
        } catch (RemoteException ignored) {}
    }

    private void readLoop() {
        try {
            while (true) {
                ServerToClientMessage msg = (ServerToClientMessage) input.readObject();
                msg.accept(this);
            }
        } catch (EOFException eof) {
            controller.actionRejected("server disconnected");
        } catch (IOException io) {
            controller.actionRejected("socket read error: " + io.getMessage());
        } catch (ClassNotFoundException | ClassCastException malformed) {
            controller.actionRejected("malformed message: " + malformed.getMessage());
        }
    }

    // === visit* ===

    @Override public void visitPlayerJoined(String nick) {
        controller.playerJoined(nick);
    }

    @Override public void visitAwaitPlayerCount(String firstPlayerNick) {
        controller.awaitPlayerCount(firstPlayerNick);
    }

    @Override public void visitPlayerCountSet(int count) {
        controller.playerCountSet(count);
    }

    @Override public void visitAwaitMorePlayers(int joined, int total) {
        controller.awaitMorePlayers(joined, total);
    }

    @Override public void visitColorTurn(String nick, Set<TotemColor> available) {
        controller.colorTurn(nick, available);
    }

    @Override public void visitColorPicked(String nick, TotemColor color) {
        controller.colorPicked(nick, color);
    }

    @Override public void visitMatchStarted(List<String> playerOrder, Era startingEra) {
        controller.matchStarted(playerOrder, startingEra);
    }

    @Override public void visitEraChanged(Era newEra) {
        controller.eraChanged(newEra);
    }

    @Override public void visitRoundStarted(int round) {
        controller.roundStarted(round);
    }

    @Override public void visitOfferingTileTurn(String nick, int trackSize) {
        controller.offeringTileTurn(nick, trackSize);
    }

    @Override public void visitOfferingTilePicked(String nick, int tileIndex) {
        controller.offeringTilePicked(nick, tileIndex);
    }

    @Override public void visitCharacterPickTurn(String nick, int upperBudget, int lowerBudget) {
        controller.characterPickTurn(nick, upperBudget, lowerBudget);
    }

    @Override public void visitCharacterCardsPicked(String nick,
                                                    List<CharacterCard> upper,
                                                    List<CharacterCard> lower) {
        controller.characterCardsPicked(nick, upper, lower);
    }

    @Override public void visitBuildingPickTurn(String nick, int upperRemaining, int lowerRemaining) {
        controller.buildingPickTurn(nick, upperRemaining, lowerRemaining);
    }

    @Override public void visitBuildingCardsBought(String nick,
                                                   List<BuildingCard> upper,
                                                   List<BuildingCard> lower) {
        controller.buildingCardsBought(nick, upper, lower);
    }

    @Override public void visitExtraPickTurn(String nick) {
        controller.extraPickTurn(nick);
    }

    @Override public void visitExtraCardPicked(String nick, Integer cardId, boolean isCharacter) {
        controller.extraCardPicked(nick, cardId, isCharacter);
    }

    @Override public void visitPrestigeChanged(String nick, int newPrestige) {
        controller.prestigeChanged(nick, newPrestige);
    }

    @Override public void visitFoodChanged(String nick, int newFood) {
        controller.foodChanged(nick, newFood);
    }

    @Override public void visitRoundEnded() {
        controller.roundEnded();
    }

    @Override public void visitGameEnded(List<String> leaderboard, List<String> winners) {
        controller.gameEnded(leaderboard, winners);
    }

    @Override public void visitMatchPaused(String reason) {
        controller.matchPaused(reason);
    }

    @Override public void visitMatchResumed() {
        controller.matchResumed();
    }

    @Override public void visitPlayerDisconnected(String nick) {
        controller.playerDisconnected(nick);
    }

    @Override public void visitPlayerReconnected(String nick) {
        controller.playerReconnected(nick);
    }

    @Override public void visitActionRejected(String reason) {
        controller.actionRejected(reason);
    }

    @Override public void visitNicknameRejected(String reason) {
        controller.nicknameRejected(reason);
    }

    @Override public void visitGameSnapshot(GameSnapshot snapshot) {
        controller.gameSnapshot(snapshot);
    }

    @Override public void visitBoardSnapshot(Board board) {
        controller.boardSnapshot(board);
    }
}
