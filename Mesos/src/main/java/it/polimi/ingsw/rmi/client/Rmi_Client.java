package it.polimi.ingsw.rmi.client;

import it.polimi.ingsw.common.VirtualServer;
import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.dto.GameSnapshot;
import it.polimi.ingsw.controller.client.ClientController;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.view.View;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Set;

/**
 * Client-side {@link VirtualView} implementation for the RMI
 * transport. Each method is a one-liner that calls the
 * {@link ClientController}, which updates the mini-state and prompts
 * the user when needed.
 *
 * RMI exports this object and hands a stub of it to the server via
 * the {@code connect(nickname, selfStub)} call.
 */
public class Rmi_Client implements VirtualView {

    private final VirtualServer remoteServer;
    private final ClientController controller;
    private final VirtualView selfStub;

    public Rmi_Client(VirtualServer server, View view) throws RemoteException {
        this.remoteServer = server;
        this.controller = new ClientController(view);
        this.selfStub = (VirtualView) UnicastRemoteObject.exportObject(this, 0);
        this.controller.setVirtualServer(server);
    }

    public void run() throws RemoteException {
        // Install the retry-connect callback BEFORE the first connect:
        // if the server rejects a retryable case (duplicate nickname,
        // bad syntax), the ClientController will re-prompt and run
        // this to resend connect with the new nickname.
        controller.setRetryConnect(new Runnable() {
            @Override public void run() {
                try {
                    remoteServer.connect(controller.getMyNickname(), selfStub);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        String nickname = controller.promptNickname();
        controller.setNickname(nickname);
        remoteServer.connect(nickname, selfStub);
        // Park here until the network thread (some RMI worker)
        // receives the onGameEnded event and counts down the latch.
        // No polling: the main thread is in a real wait.
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
                remoteServer.disconnect(nickname);
            } catch (RemoteException ignored) {
                // server already cleaned up, nothing to do
            }
        }
        // Without unexport, the RMI listener (a non-daemon thread)
        // would keep the JVM alive after main returns. Unexport here
        // so the client process actually exits.
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ignored) {
            // already unexported
        }
    }

    // === VirtualView: one line per event ===

    @Override public void onPlayerJoined(String nickname) throws RemoteException {
        controller.playerJoined(nickname);
    }

    @Override public void onAwaitPlayerCount(String firstPlayerNickname) throws RemoteException {
        controller.awaitPlayerCount(firstPlayerNickname);
    }

    @Override public void onPlayerCountSet(int count) throws RemoteException {
        controller.playerCountSet(count);
    }

    @Override public void onAwaitMorePlayers(int joined, int total) throws RemoteException {
        controller.awaitMorePlayers(joined, total);
    }

    @Override public void onColorTurn(String nickname, Set<TotemColor> available) throws RemoteException {
        controller.colorTurn(nickname, available);
    }

    @Override public void onColorPicked(String nickname, TotemColor color) throws RemoteException {
        controller.colorPicked(nickname, color);
    }

    @Override public void onMatchStarted(List<String> playerOrder, Era startingEra) throws RemoteException {
        controller.matchStarted(playerOrder, startingEra);
    }

    @Override public void onEraChanged(Era newEra) throws RemoteException {
        controller.eraChanged(newEra);
    }

    @Override public void onRoundStarted(int round) throws RemoteException {
        controller.roundStarted(round);
    }

    @Override public void onOfferingTileTurn(String nickname, int trackSize) throws RemoteException {
        controller.offeringTileTurn(nickname, trackSize);
    }

    @Override public void onOfferingTilePicked(String nickname, int tileIndex) throws RemoteException {
        controller.offeringTilePicked(nickname, tileIndex);
    }

    @Override public void onCharacterPickTurn(String nickname, int upperBudget, int lowerBudget) throws RemoteException {
        controller.characterPickTurn(nickname, upperBudget, lowerBudget);
    }

    @Override public void onCharacterCardsPicked(String nickname,
                                                 List<CharacterCard> upper,
                                                 List<CharacterCard> lower) throws RemoteException {
        controller.characterCardsPicked(nickname, upper, lower);
    }

    @Override public void onBuildingPickTurn(String nickname, int upperRemaining, int lowerRemaining) throws RemoteException {
        controller.buildingPickTurn(nickname, upperRemaining, lowerRemaining);
    }

    @Override public void onBuildingCardsBought(String nickname,
                                                List<BuildingCard> upper,
                                                List<BuildingCard> lower) throws RemoteException {
        controller.buildingCardsBought(nickname, upper, lower);
    }

    @Override public void onExtraPickTurn(String nickname) throws RemoteException {
        controller.extraPickTurn(nickname);
    }

    @Override public void onExtraCardPicked(String nickname, Integer cardId, boolean isCharacter) throws RemoteException {
        controller.extraCardPicked(nickname, cardId, isCharacter);
    }

    @Override public void onPrestigeChanged(String nickname, int newPrestige) throws RemoteException {
        controller.prestigeChanged(nickname, newPrestige);
    }

    @Override public void onFoodChanged(String nickname, int newFood) throws RemoteException {
        controller.foodChanged(nickname, newFood);
    }

    @Override public void onRoundEnded() throws RemoteException {
        controller.roundEnded();
    }

    @Override public void onGameEnded(List<String> leaderboard, List<String> winners) throws RemoteException {
        controller.gameEnded(leaderboard, winners);
    }

    @Override public void onMatchPaused(String reason) throws RemoteException {
        controller.matchPaused(reason);
    }

    @Override public void onMatchResumed() throws RemoteException {
        controller.matchResumed();
    }

    @Override public void onPlayerDisconnected(String nickname) throws RemoteException {
        controller.playerDisconnected(nickname);
    }

    @Override public void onPlayerReconnected(String nickname) throws RemoteException {
        controller.playerReconnected(nickname);
    }

    @Override public void onActionRejected(String reason) throws RemoteException {
        controller.actionRejected(reason);
    }

    @Override public void onNicknameRejected(String reason) throws RemoteException {
        controller.nicknameRejected(reason);
    }

    @Override public void onGameSnapshot(GameSnapshot snapshot) throws RemoteException {
        controller.gameSnapshot(snapshot);
    }

    @Override public void onBoardSnapshot(Board board) throws RemoteException {
        controller.boardSnapshot(board);
    }

    @Override public void ping() throws RemoteException {
        // no-op: the invocation itself is the liveness signal
    }

    @Override public void onCommit() throws RemoteException {
        controller.commit();
    }
}
