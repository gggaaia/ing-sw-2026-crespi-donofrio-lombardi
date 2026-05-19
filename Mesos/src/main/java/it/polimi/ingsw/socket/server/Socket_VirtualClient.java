package it.polimi.ingsw.socket.server;

import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.dto.GameSnapshot;
import it.polimi.ingsw.common.wire.ServerToClientMessage;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

/**
 * Implementazione lato server di {@link VirtualView} per i client
 * collegati via socket. Ogni metodo: una riga, impacchetta i
 * parametri in un {@link ServerToClientMessage} e scrive sul socket.
 *
 * Dalla prospettiva del {@link it.polimi.ingsw.controller.server.ServerController},
 * questa classe \u00e8 identica a uno stub RMI di {@link VirtualView}:
 * lui chiama {@code view.onColorPicked(...)}, qui dentro il body
 * impacchetta e manda.
 */
public class Socket_VirtualClient implements VirtualView {

    private final ObjectOutputStream output;

    public Socket_VirtualClient(ObjectOutputStream output) {
        this.output = output;
    }

    // Ogni metodo wrappa i parametri in un ServerToClientMessage e li scrive sul socket.

    @Override public void onPlayerJoined(String nick) throws RemoteException {
        send(new ServerToClientMessage.PlayerJoined(nick));
    }

    @Override public void onAwaitPlayerCount(String firstPlayerNick) throws RemoteException {
        send(new ServerToClientMessage.AwaitPlayerCount(firstPlayerNick));
    }

    @Override public void onPlayerCountSet(int count) throws RemoteException {
        send(new ServerToClientMessage.PlayerCountSet(count));
    }

    @Override public void onAwaitMorePlayers(int joined, int total) throws RemoteException {
        send(new ServerToClientMessage.AwaitMorePlayers(joined, total));
    }

    @Override public void onColorTurn(String nick, Set<TotemColor> available) throws RemoteException {
        send(new ServerToClientMessage.ColorTurn(nick, available));
    }

    @Override public void onColorPicked(String nick, TotemColor color) throws RemoteException {
        send(new ServerToClientMessage.ColorPicked(nick, color));
    }

    @Override public void onMatchStarted(List<String> playerOrder, Era startingEra) throws RemoteException {
        send(new ServerToClientMessage.MatchStarted(playerOrder, startingEra));
    }

    @Override public void onEraChanged(Era newEra) throws RemoteException {
        send(new ServerToClientMessage.EraChanged(newEra));
    }

    @Override public void onRoundStarted(int round) throws RemoteException {
        send(new ServerToClientMessage.RoundStarted(round));
    }

    @Override public void onOfferingTileTurn(String nick, int trackSize) throws RemoteException {
        send(new ServerToClientMessage.OfferingTileTurn(nick, trackSize));
    }

    @Override public void onOfferingTilePicked(String nick, int tileIndex) throws RemoteException {
        send(new ServerToClientMessage.OfferingTilePicked(nick, tileIndex));
    }

    @Override public void onCharacterPickTurn(String nick, int upperBudget, int lowerBudget) throws RemoteException {
        send(new ServerToClientMessage.CharacterPickTurn(nick, upperBudget, lowerBudget));
    }

    @Override public void onCharacterCardsPicked(String nick,
                                                 List<CharacterCard> upper,
                                                 List<CharacterCard> lower) throws RemoteException {
        send(new ServerToClientMessage.CharacterCardsPicked(nick, upper, lower));
    }

    @Override public void onBuildingPickTurn(String nick, int upperRemaining, int lowerRemaining) throws RemoteException {
        send(new ServerToClientMessage.BuildingPickTurn(nick, upperRemaining, lowerRemaining));
    }

    @Override public void onBuildingCardsBought(String nick,
                                                List<BuildingCard> upper,
                                                List<BuildingCard> lower) throws RemoteException {
        send(new ServerToClientMessage.BuildingCardsBought(nick, upper, lower));
    }

    @Override public void onExtraPickTurn(String nick) throws RemoteException {
        send(new ServerToClientMessage.ExtraPickTurn(nick));
    }

    @Override public void onExtraCardPicked(String nick, Integer cardId, boolean isCharacter) throws RemoteException {
        send(new ServerToClientMessage.ExtraCardPicked(nick, cardId, isCharacter));
    }

    @Override public void onPrestigeChanged(String nick, int newPrestige) throws RemoteException {
        send(new ServerToClientMessage.PrestigeChanged(nick, newPrestige));
    }

    @Override public void onFoodChanged(String nick, int newFood) throws RemoteException {
        send(new ServerToClientMessage.FoodChanged(nick, newFood));
    }

    @Override public void onRoundEnded() throws RemoteException {
        send(new ServerToClientMessage.RoundEnded());
    }

    @Override public void onGameEnded(List<String> leaderboard, List<String> winners) throws RemoteException {
        send(new ServerToClientMessage.GameEnded(leaderboard, winners));
    }

    @Override public void onMatchPaused(String reason) throws RemoteException {
        send(new ServerToClientMessage.MatchPaused(reason));
    }

    @Override public void onMatchResumed() throws RemoteException {
        send(new ServerToClientMessage.MatchResumed());
    }

    @Override public void onPlayerDisconnected(String nick) throws RemoteException {
        send(new ServerToClientMessage.PlayerDisconnected(nick));
    }

    @Override public void onPlayerReconnected(String nick) throws RemoteException {
        send(new ServerToClientMessage.PlayerReconnected(nick));
    }

    @Override public void onActionRejected(String reason) throws RemoteException {
        send(new ServerToClientMessage.ActionRejected(reason));
    }

    @Override public void onNicknameRejected(String reason) throws RemoteException {
        send(new ServerToClientMessage.NicknameRejected(reason));
    }

    @Override public void onGameSnapshot(GameSnapshot snapshot) throws RemoteException {
        send(new ServerToClientMessage.GameSnapshotMsg(snapshot));
    }

    @Override public void onBoardSnapshot(Board board) throws RemoteException {
        send(new ServerToClientMessage.BoardSnapshotMsg(board));
    }

    @Override public void ping() throws RemoteException {
        // Per i socket l'heartbeat \u00e8 una writeObject che pu\u00f2 fallire
        // se il client \u00e8 morto; un messaggio nuovo non serve perch\u00e9
        // la disconnessione viene catturata dal reader nel
        // Socket_ClientHandler. Lasciamo il body vuoto.
    }

    private void send(ServerToClientMessage msg) throws RemoteException {
        try {
            synchronized (output) {
                output.writeObject(msg);
                output.flush();
                output.reset();
            }
        } catch (IOException e) {
            throw new RemoteException("socket send failed", e);
        }
    }
}
