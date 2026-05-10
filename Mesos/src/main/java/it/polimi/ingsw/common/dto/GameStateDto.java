package it.polimi.ingsw.common.dto;

import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A full snapshot of the game state visible to clients. Travels over the
 * network by Java serialization (every field is a primitive, a
 * Serializable value class such as Phase, a List/Map/Set of those, or a
 * Serializable model object such as Board).
 *
 * Some fields only make sense in some phases:
 *   - board, currentEra, currentRound, the four budget fields and the
 *     food / prestige maps: only meaningful while a match is running;
 *     null or zero in lobby phases.
 *   - leaderboard, winners: only meaningful when the phase is
 *     Phase.GAME_ENDED (use a PhaseVisitor to discriminate, never ==).
 */
public class GameStateDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Phase phase;
    private final List<String> connectedNicknames;
    private final Map<String, TotemColor> nicknameToColor;
    private final List<String> colorPickQueueOrder;
    private final List<String> turnOrderTileOrder;
    private final String currentPlayerNickname;
    private final Set<TotemColor> availableColors;
    private final Board board;
    private final Era currentEra;
    private final int currentRound;
    private final int currentUpperBudget;
    private final int currentLowerBudget;
    private final int currentUpperRemaining;
    private final int currentLowerRemaining;
    private final Map<String, Integer> foodByNickname;
    private final Map<String, Integer> prestigeByNickname;
    /**
     * Per-player character cards drafted so far (the "hand"). Empty
     * for clients that have not joined the match yet (lobby phases)
     * and for players that have not drafted any card yet. Travels
     * over the wire because every concrete CharacterCard subclass is
     * Serializable through Card.
     */
    private final Map<String, List<CharacterCard>> handsByNickname;
    /**
     * Per-player building cards bought so far. Same lifecycle and
     * serialization story as handsByNickname.
     */
    private final Map<String, List<BuildingCard>> buildingsByNickname;
    private final List<String> leaderboard;
    private final List<String> winners;
    private final String description;

    public GameStateDto(Phase phase,
                        List<String> connectedNicknames,
                        Map<String, TotemColor> nicknameToColor,
                        List<String> colorPickQueueOrder,
                        List<String> turnOrderTileOrder,
                        String currentPlayerNickname,
                        Set<TotemColor> availableColors,
                        Board board,
                        Era currentEra,
                        int currentRound,
                        int currentUpperBudget,
                        int currentLowerBudget,
                        int currentUpperRemaining,
                        int currentLowerRemaining,
                        Map<String, Integer> foodByNickname,
                        Map<String, Integer> prestigeByNickname,
                        Map<String, List<CharacterCard>> handsByNickname,
                        Map<String, List<BuildingCard>> buildingsByNickname,
                        List<String> leaderboard,
                        List<String> winners,
                        String description) {
        this.phase = phase;
        this.connectedNicknames = connectedNicknames;
        this.nicknameToColor = nicknameToColor;
        this.colorPickQueueOrder = colorPickQueueOrder;
        this.turnOrderTileOrder = turnOrderTileOrder;
        this.currentPlayerNickname = currentPlayerNickname;
        this.availableColors = availableColors;
        this.board = board;
        this.currentEra = currentEra;
        this.currentRound = currentRound;
        this.currentUpperBudget = currentUpperBudget;
        this.currentLowerBudget = currentLowerBudget;
        this.currentUpperRemaining = currentUpperRemaining;
        this.currentLowerRemaining = currentLowerRemaining;
        this.foodByNickname = foodByNickname;
        this.prestigeByNickname = prestigeByNickname;
        this.handsByNickname = handsByNickname;
        this.buildingsByNickname = buildingsByNickname;
        this.leaderboard = leaderboard;
        this.winners = winners;
        this.description = description;
    }

    public Phase getPhase() {
        return phase;
    }

    public List<String> getConnectedNicknames() {
        return connectedNicknames;
    }

    public Map<String, TotemColor> getNicknameToColor() {
        return nicknameToColor;
    }

    public List<String> getColorPickQueueOrder() {
        return colorPickQueueOrder;
    }

    public List<String> getTurnOrderTileOrder() {
        return turnOrderTileOrder;
    }

    public String getCurrentPlayerNickname() {
        return currentPlayerNickname;
    }

    public Set<TotemColor> getAvailableColors() {
        return availableColors;
    }

    public Board getBoard() {
        return board;
    }

    public Era getCurrentEra() {
        return currentEra;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getCurrentUpperBudget() {
        return currentUpperBudget;
    }

    public int getCurrentLowerBudget() {
        return currentLowerBudget;
    }

    public int getCurrentUpperRemaining() {
        return currentUpperRemaining;
    }

    public int getCurrentLowerRemaining() {
        return currentLowerRemaining;
    }

    public Map<String, Integer> getFoodByNickname() {
        return foodByNickname;
    }

    public Map<String, Integer> getPrestigeByNickname() {
        return prestigeByNickname;
    }

    public Map<String, List<CharacterCard>> getHandsByNickname() {
        return handsByNickname;
    }

    public Map<String, List<BuildingCard>> getBuildingsByNickname() {
        return buildingsByNickname;
    }

    public List<String> getLeaderboard() {
        return leaderboard;
    }

    public List<String> getWinners() {
        return winners;
    }

    public String getDescription() {
        return description;
    }
}
