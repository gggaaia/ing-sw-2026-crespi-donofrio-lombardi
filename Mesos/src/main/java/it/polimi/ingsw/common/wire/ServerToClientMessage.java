package it.polimi.ingsw.common.wire;

import it.polimi.ingsw.common.dto.GameSnapshot;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * Wire message base per il verso server \u2192 client (trasporto
 * socket). Una classe nested per ogni metodo di {@code VirtualView}.
 *
 * Visitor pattern: il visitor (implementato da
 * {@code Socket_Client} lato client) dispatcha ogni messaggio al
 * metodo {@code visitXxx} corretto.
 */
public abstract class ServerToClientMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public abstract void accept(Visitor v);

    public interface Visitor {
        void visitPlayerJoined(String nick);
        void visitAwaitPlayerCount(String firstPlayerNick);
        void visitPlayerCountSet(int count);
        void visitAwaitMorePlayers(int joined, int total);
        void visitNicknameRejected(String reason);
        void visitColorTurn(String nick, Set<TotemColor> available);
        void visitColorPicked(String nick, TotemColor color);
        void visitMatchStarted(List<String> playerOrder, Era startingEra);
        void visitEraChanged(Era newEra);
        void visitRoundStarted(int round);
        void visitOfferingTileTurn(String nick, int trackSize);
        void visitOfferingTilePicked(String nick, int tileIndex);
        void visitCharacterPickTurn(String nick, int upperBudget, int lowerBudget);
        void visitCharacterCardsPicked(String nick, List<CharacterCard> upper, List<CharacterCard> lower);
        void visitBuildingPickTurn(String nick, int upperRemaining, int lowerRemaining);
        void visitBuildingCardsBought(String nick, List<BuildingCard> upper, List<BuildingCard> lower);
        void visitExtraPickTurn(String nick);
        void visitExtraCardPicked(String nick, Integer cardId, boolean isCharacter);
        void visitPrestigeChanged(String nick, int newPrestige);
        void visitFoodChanged(String nick, int newFood);
        void visitRoundEnded();
        void visitGameEnded(List<String> leaderboard, List<String> winners);
        void visitMatchPaused(String reason);
        void visitMatchResumed();
        void visitPlayerDisconnected(String nick);
        void visitPlayerReconnected(String nick);
        void visitActionRejected(String reason);
        void visitGameSnapshot(GameSnapshot snapshot);
        void visitBoardSnapshot(Board board);
        void visitCommit();
    }

    // === classi nested (una per evento) ===

    public static class PlayerJoined extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        public PlayerJoined(String nick) { this.nick = nick; }
        @Override public void accept(Visitor v) { v.visitPlayerJoined(nick); }
    }

    public static class AwaitPlayerCount extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String firstPlayerNick;
        public AwaitPlayerCount(String firstPlayerNick) { this.firstPlayerNick = firstPlayerNick; }
        @Override public void accept(Visitor v) { v.visitAwaitPlayerCount(firstPlayerNick); }
    }

    public static class PlayerCountSet extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final int count;
        public PlayerCountSet(int count) { this.count = count; }
        @Override public void accept(Visitor v) { v.visitPlayerCountSet(count); }
    }

    public static class AwaitMorePlayers extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final int joined;
        private final int total;
        public AwaitMorePlayers(int joined, int total) { this.joined = joined; this.total = total; }
        @Override public void accept(Visitor v) { v.visitAwaitMorePlayers(joined, total); }
    }

    public static class NicknameRejected extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String reason;
        public NicknameRejected(String reason) { this.reason = reason; }
        @Override public void accept(Visitor v) { v.visitNicknameRejected(reason); }
    }

    public static class ColorTurn extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final Set<TotemColor> available;
        public ColorTurn(String nick, Set<TotemColor> available) { this.nick = nick; this.available = available; }
        @Override public void accept(Visitor v) { v.visitColorTurn(nick, available); }
    }

    public static class ColorPicked extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final TotemColor color;
        public ColorPicked(String nick, TotemColor color) { this.nick = nick; this.color = color; }
        @Override public void accept(Visitor v) { v.visitColorPicked(nick, color); }
    }

    public static class MatchStarted extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final List<String> playerOrder;
        private final Era startingEra;
        public MatchStarted(List<String> playerOrder, Era startingEra) {
            this.playerOrder = playerOrder; this.startingEra = startingEra;
        }
        @Override public void accept(Visitor v) { v.visitMatchStarted(playerOrder, startingEra); }
    }

    public static class EraChanged extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final Era newEra;
        public EraChanged(Era newEra) { this.newEra = newEra; }
        @Override public void accept(Visitor v) { v.visitEraChanged(newEra); }
    }

    public static class RoundStarted extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final int round;
        public RoundStarted(int round) { this.round = round; }
        @Override public void accept(Visitor v) { v.visitRoundStarted(round); }
    }

    public static class OfferingTileTurn extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final int trackSize;
        public OfferingTileTurn(String nick, int trackSize) { this.nick = nick; this.trackSize = trackSize; }
        @Override public void accept(Visitor v) { v.visitOfferingTileTurn(nick, trackSize); }
    }

    public static class OfferingTilePicked extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final int tileIndex;
        public OfferingTilePicked(String nick, int tileIndex) { this.nick = nick; this.tileIndex = tileIndex; }
        @Override public void accept(Visitor v) { v.visitOfferingTilePicked(nick, tileIndex); }
    }

    public static class CharacterPickTurn extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final int upperBudget;
        private final int lowerBudget;
        public CharacterPickTurn(String nick, int upperBudget, int lowerBudget) {
            this.nick = nick; this.upperBudget = upperBudget; this.lowerBudget = lowerBudget;
        }
        @Override public void accept(Visitor v) { v.visitCharacterPickTurn(nick, upperBudget, lowerBudget); }
    }

    public static class CharacterCardsPicked extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final List<CharacterCard> upper;
        private final List<CharacterCard> lower;
        public CharacterCardsPicked(String nick, List<CharacterCard> upper, List<CharacterCard> lower) {
            this.nick = nick; this.upper = upper; this.lower = lower;
        }
        @Override public void accept(Visitor v) { v.visitCharacterCardsPicked(nick, upper, lower); }
    }

    public static class BuildingPickTurn extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final int upperRemaining;
        private final int lowerRemaining;
        public BuildingPickTurn(String nick, int upperRemaining, int lowerRemaining) {
            this.nick = nick; this.upperRemaining = upperRemaining; this.lowerRemaining = lowerRemaining;
        }
        @Override public void accept(Visitor v) { v.visitBuildingPickTurn(nick, upperRemaining, lowerRemaining); }
    }

    public static class BuildingCardsBought extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final List<BuildingCard> upper;
        private final List<BuildingCard> lower;
        public BuildingCardsBought(String nick, List<BuildingCard> upper, List<BuildingCard> lower) {
            this.nick = nick; this.upper = upper; this.lower = lower;
        }
        @Override public void accept(Visitor v) { v.visitBuildingCardsBought(nick, upper, lower); }
    }

    public static class ExtraPickTurn extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        public ExtraPickTurn(String nick) { this.nick = nick; }
        @Override public void accept(Visitor v) { v.visitExtraPickTurn(nick); }
    }

    public static class ExtraCardPicked extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final Integer cardId;
        private final boolean isCharacter;
        public ExtraCardPicked(String nick, Integer cardId, boolean isCharacter) {
            this.nick = nick; this.cardId = cardId; this.isCharacter = isCharacter;
        }
        @Override public void accept(Visitor v) { v.visitExtraCardPicked(nick, cardId, isCharacter); }
    }

    public static class PrestigeChanged extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final int newPrestige;
        public PrestigeChanged(String nick, int newPrestige) { this.nick = nick; this.newPrestige = newPrestige; }
        @Override public void accept(Visitor v) { v.visitPrestigeChanged(nick, newPrestige); }
    }

    public static class FoodChanged extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        private final int newFood;
        public FoodChanged(String nick, int newFood) { this.nick = nick; this.newFood = newFood; }
        @Override public void accept(Visitor v) { v.visitFoodChanged(nick, newFood); }
    }

    public static class RoundEnded extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        public RoundEnded() {}
        @Override public void accept(Visitor v) { v.visitRoundEnded(); }
    }

    public static class GameEnded extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final List<String> leaderboard;
        private final List<String> winners;
        public GameEnded(List<String> leaderboard, List<String> winners) {
            this.leaderboard = leaderboard; this.winners = winners;
        }
        @Override public void accept(Visitor v) { v.visitGameEnded(leaderboard, winners); }
    }

    public static class MatchPaused extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String reason;
        public MatchPaused(String reason) { this.reason = reason; }
        @Override public void accept(Visitor v) { v.visitMatchPaused(reason); }
    }

    public static class MatchResumed extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        public MatchResumed() {}
        @Override public void accept(Visitor v) { v.visitMatchResumed(); }
    }

    public static class PlayerDisconnected extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        public PlayerDisconnected(String nick) { this.nick = nick; }
        @Override public void accept(Visitor v) { v.visitPlayerDisconnected(nick); }
    }

    public static class PlayerReconnected extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String nick;
        public PlayerReconnected(String nick) { this.nick = nick; }
        @Override public void accept(Visitor v) { v.visitPlayerReconnected(nick); }
    }

    public static class ActionRejected extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String reason;
        public ActionRejected(String reason) { this.reason = reason; }
        @Override public void accept(Visitor v) { v.visitActionRejected(reason); }
    }

    public static class GameSnapshotMsg extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final GameSnapshot snapshot;
        public GameSnapshotMsg(GameSnapshot snapshot) { this.snapshot = snapshot; }
        @Override public void accept(Visitor v) { v.visitGameSnapshot(snapshot); }
    }

    public static class BoardSnapshotMsg extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final Board board;
        public BoardSnapshotMsg(Board board) { this.board = board; }
        @Override public void accept(Visitor v) { v.visitBoardSnapshot(board); }
    }

    /**
     * Marker di fine bundle: invita il client a fare il refresh
     * della view e a flushare i log accumulati. Vedi
     * {@code VirtualView#onCommit}.
     */
    public static class Commit extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        public Commit() {}
        @Override public void accept(Visitor v) { v.visitCommit(); }
    }
}
