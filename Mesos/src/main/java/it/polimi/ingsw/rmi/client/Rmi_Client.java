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
 * Implementazione lato client di {@link VirtualView} per il trasporto
 * RMI. Ogni metodo \u00e8 una riga: chiamata diretta al
 * {@link ClientController}, che aggiorna il mini-stato e fa il
 * prompt all'utente se serve.
 *
 * RMI esporta questo oggetto e ne fornisce uno stub al server tramite
 * la chiamata {@code connect(nickname, selfStub)}.
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
        String nickname = controller.promptNickname();
        controller.setNickname(nickname);
        remoteServer.connect(nickname, selfStub);
        // Aspettiamo qui finch\u00e9 il network thread (un RMI worker)
        // riceve l'evento onGameEnded e fa countDown sul latch.
        // Nessun polling: il main thread \u00e8 in wait profonda.
        try {
            controller.awaitGameEnd();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            remoteServer.disconnect(nickname);
        } catch (RemoteException ignored) {
            // server gi\u00e0 a posto
        }
        // Senza unexport, il listener RMI (thread non-daemon) terrebbe
        // vivo il JVM dopo che main \u00e8 ritornato. Lo togliamo perch\u00e9
        // il client esca pulito.
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ignored) {
            // gi\u00e0 unexportato
        }
    }

    // === VirtualView: 1 riga per ogni evento ===

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
        // no-op: la mera invocazione \u00e8 il segnale di liveness
    }

    @Override public void onCommit() throws RemoteException {
        controller.commit();
    }
}
