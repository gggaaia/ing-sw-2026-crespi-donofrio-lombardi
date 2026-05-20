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
 * Client-side adapter for the socket transport. Three things:
 *   1. builds a {@link Socket_VirtualServer} and hands it to the
 *      ClientController (which uses it as the VirtualServer for
 *      outgoing actions)
 *   2. does the initial nickname prompt and sends the first connect
 *   3. loops reading {@link ServerToClientMessage} off the socket
 *      and dispatches each one to the ClientController via the
 *      visitor pattern
 *
 * The visit* bodies are basically identical to {@code Rmi_Client}'s:
 * a direct call to the ClientController.
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
        // Install the retry-connect callback BEFORE the first connect:
        // if the server rejects a retryable case (duplicate nickname,
        // bad syntax), the ClientController will re-prompt and run
        // this to resend connect with the new nickname.
        controller.setRetryConnect(new Runnable() {
            @Override public void run() {
                try {
                    virtualServer.connect(controller.getMyNickname(), null);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        String nickname = controller.promptNickname();
        controller.setNickname(nickname);

        // Spawn the reader thread BEFORE sending connect: that way if
        // the server replies immediately with onGameSnapshot we can
        // actually receive it.
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

        // Park here until the reader thread receives the onGameEnded
        // event and counts down the latch. No polling: main is in a
        // proper wait. Keeping main alive here is ESSENTIAL because
        // the reader thread is a daemon: if main returned, the JVM
        // would kill the reader before the match even starts.
        try {
            controller.awaitGameEnd();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        // Skip the disconnect if the server refused our connect: the
        // nickname isn't ours, and shipping disconnect(nickname) would
        // kick the legitimate holder out.
        if (!controller.wasConnectionRejected()) {
            try {
                virtualServer.disconnect(nickname);
            } catch (RemoteException ignored) {}
        }
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

    @Override public void visitCommit() {
        controller.commit();
    }
}
