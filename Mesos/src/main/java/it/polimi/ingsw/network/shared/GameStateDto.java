package it.polimi.ingsw.network.shared;

import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of the full game state, sent from the server to all clients
 * inside a {@code GameStateMessage}.
 * <p>
 * The DTO is immutable on purpose: every broadcast creates a fresh
 * instance. The internal collections passed in are stored by reference, so
 * the controller is expected to defensively copy anything it might mutate
 * after the broadcast (the controller does so in {@code createGameStateDto}).
 * <p>
 * Several fields are only meaningful in certain phases. Where that's the
 * case the field's Javadoc says so explicitly.
 */
public class GameStateDto implements Serializable {
    /**
     * Serialization version. Bump only on backwards-incompatible field
     * layout changes.
     */
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // Phase + lobby fields
    // ------------------------------------------------------------------

    /** Current phase the controller is in. */
    private final GamePhase phase;

    /** Nicknames of every connected player, in connection order. */
    private final List<String> players;

    /** Map from each player's nickname to the totem color they picked.
     *  May not contain every nickname yet (in COLOR_PICKING). */
    private final Map<String, TotemColor> playerColors;

    /** Connection-order queue of players who still have to pick a color
     *  (only meaningful in {@link GamePhase#COLOR_PICKING}). */
    private final List<String> colorPickTurnOrder;

    /** Nicknames of the players still on the turn-order tile, in slot
     *  order. Empty during lobby phases. */
    private final List<String> playersTurnOrderTile;

    /** Nickname of the player whose input the server is currently
     *  expecting. {@code null} when the phase has no per-player input
     *  (e.g. {@link GamePhase#RESOLVE_BUILDINGS_AND_EVENTS}). */
    private final String currentPlayerNickname;

    /** Totem colors that nobody has picked yet (only meaningful in
     *  {@link GamePhase#COLOR_PICKING}). */
    private final List<TotemColor> availableColors;

    // ------------------------------------------------------------------
    // Match-level fields (null until COLOR_PICKING is over)
    // ------------------------------------------------------------------

    /** The full {@link Board} reference. {@code null} until the controller
     *  has built the {@code Match}. */
    private final Board board;

    /** The current era. {@code null} until the match has started. */
    private final Era era;

    /** The current round number. Zero before the match starts. */
    private final int round;

    // ------------------------------------------------------------------
    // Per-turn budgets (only meaningful during PICK_CARDS_* phases)
    // ------------------------------------------------------------------

    /** How many cards the current player can take from the upper row in
     *  {@link GamePhase#TURN_PICK_CARDS_CHARACTER} (= the offering tile's
     *  top count). */
    private final int upperRowCardsToDraw;

    /** How many cards the current player can take from the lower row in
     *  {@link GamePhase#TURN_PICK_CARDS_CHARACTER} (= the offering tile's
     *  bottom count). */
    private final int lowerRowCardsToDraw;

    /** How many upper-row buildings the current player can still buy in
     *  {@link GamePhase#TURN_PICK_CARDS_BUILDING}. Equal to the offering
     *  tile's top count minus the upper-row characters already drawn. */
    private final int upperRowCardsRemainingForBuilding;

    /** How many lower-row buildings the current player can still buy in
     *  {@link GamePhase#TURN_PICK_CARDS_BUILDING}. Equal to the offering
     *  tile's bottom count minus the lower-row characters already drawn. */
    private final int lowerRowCardsRemainingForBuilding;

    // ------------------------------------------------------------------
    // Per-player resources (always populated once the match has started)
    // ------------------------------------------------------------------

    /** Map from each player's nickname to their current food. */
    private final Map<String, Integer> playerFood;

    /** Map from each player's nickname to their current prestige points. */
    private final Map<String, Integer> playerPrestige;

    // ------------------------------------------------------------------
    // End-of-game (only populated in GamePhase.GAME_ENDED)
    // ------------------------------------------------------------------

    /** Nicknames sorted from best to worst, per
     *  {@link it.polimi.ingsw.model.Match#createLeaderboard()}. */
    private final List<String> leaderboard;

    /** Nicknames of the winning player(s). Length 1 in the normal case; a
     *  longer list represents a shared victory (PP and food both tied at
     *  the top). */
    private final List<String> winners;

    // ------------------------------------------------------------------
    // Free-form message (always populated, mostly for debug / CLI logs)
    // ------------------------------------------------------------------

    /** Short textual description of why this snapshot was sent. */
    private final String message;

    /**
     * Builds an immutable game-state snapshot. The constructor takes every
     * field as an argument so callers can't forget to set anything; the
     * controller's helper {@code createGameStateDto(...)} centralises the
     * normalisation/copy logic.
     */
    public GameStateDto(
            GamePhase phase,
            List<String> players,
            Map<String, TotemColor> playerColors,
            List<String> colorPickTurnOrder,
            List<String> playersTurnOrderTile,
            String currentPlayerNickname,
            List<TotemColor> availableColors,
            Board board,
            Era era,
            int round,
            int upperRowCardsToDraw,
            int lowerRowCardsToDraw,
            int upperRowCardsRemainingForBuilding,
            int lowerRowCardsRemainingForBuilding,
            Map<String, Integer> playerFood,
            Map<String, Integer> playerPrestige,
            List<String> leaderboard,
            List<String> winners,
            String message
    ) {
        this.phase = phase;
        this.players = players;
        this.playerColors = playerColors;
        this.colorPickTurnOrder = colorPickTurnOrder;
        this.playersTurnOrderTile = playersTurnOrderTile;
        this.currentPlayerNickname = currentPlayerNickname;
        this.availableColors = availableColors;
        this.board = board;
        this.era = era;
        this.round = round;
        this.upperRowCardsToDraw = upperRowCardsToDraw;
        this.lowerRowCardsToDraw = lowerRowCardsToDraw;
        this.upperRowCardsRemainingForBuilding = upperRowCardsRemainingForBuilding;
        this.lowerRowCardsRemainingForBuilding = lowerRowCardsRemainingForBuilding;
        this.playerFood = playerFood;
        this.playerPrestige = playerPrestige;
        this.leaderboard = leaderboard;
        this.winners = winners;
        this.message = message;
    }

    public GamePhase getPhase() { return phase; }
    public List<String> getPlayers() { return players; }
    public Map<String, TotemColor> getPlayerColors() { return playerColors; }
    public List<String> getColorPickTurnOrder() { return colorPickTurnOrder; }
    public List<String> getPlayersTurnOrderTile() { return playersTurnOrderTile; }
    public String getCurrentPlayerNickname() { return currentPlayerNickname; }
    public List<TotemColor> getAvailableColors() { return availableColors; }
    public Board getBoard() { return board; }
    public Era getEra() { return era; }
    public int getRound() { return round; }
    public int getUpperRowCardsToDraw() { return upperRowCardsToDraw; }
    public int getLowerRowCardsToDraw() { return lowerRowCardsToDraw; }
    public int getUpperRowCardsRemainingForBuilding() { return upperRowCardsRemainingForBuilding; }
    public int getLowerRowCardsRemainingForBuilding() { return lowerRowCardsRemainingForBuilding; }
    public Map<String, Integer> getPlayerFood() { return playerFood; }
    public Map<String, Integer> getPlayerPrestige() { return playerPrestige; }
    public List<String> getLeaderboard() { return leaderboard; }
    public List<String> getWinners() { return winners; }
    public String getMessage() { return message; }
}
