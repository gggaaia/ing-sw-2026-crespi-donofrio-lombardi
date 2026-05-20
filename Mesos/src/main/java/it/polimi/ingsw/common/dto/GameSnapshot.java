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
 * Full snapshot of the game state. The server only sends one in
 * two cases:
 *
 *   1. When a new player joins the lobby (first connection)
 *   2. When a player reconnects after a disconnect
 *
 * During normal play, updates travel as typed events on
 * {@code VirtualView} (one method per event), not as snapshots.
 *
 * All fields are final and immutable from the client's point of
 * view. Many are meaningful only in certain phases: for example
 * {@code board} is null in lobby, and {@code leaderboard} is null
 * outside of {@code GAME_ENDED}. The client deals with these
 * nulls by reading them only when the current phase requires it.
 */
public class GameSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Phase                                currentPhase;
    private final String                               currentPlayer;
    private final List<String>                         playerOrder;
    private final Map<String, TotemColor>              colors;
    private final Map<String, Integer>                 prestige;
    private final Map<String, Integer>                 food;
    private final Map<String, List<CharacterCard>>     hands;
    private final Map<String, List<BuildingCard>>      buildings;
    private final Board                                board;
    private final Era                                  era;
    private final int                                  round;
    private final int                                  upperBudget;
    private final int                                  lowerBudget;
    private final int                                  upperRemaining;
    private final int                                  lowerRemaining;
    private final Set<TotemColor>                      availableColors;
    private final int                                  expectedPlayers;
    private final int                                  joinedPlayers;
    private final List<String>                         leaderboard;
    private final List<String>                         winners;

    public GameSnapshot(Phase currentPhase,
                        String currentPlayer,
                        List<String> playerOrder,
                        Map<String, TotemColor> colors,
                        Map<String, Integer> prestige,
                        Map<String, Integer> food,
                        Map<String, List<CharacterCard>> hands,
                        Map<String, List<BuildingCard>> buildings,
                        Board board,
                        Era era,
                        int round,
                        int upperBudget,
                        int lowerBudget,
                        int upperRemaining,
                        int lowerRemaining,
                        Set<TotemColor> availableColors,
                        int expectedPlayers,
                        int joinedPlayers,
                        List<String> leaderboard,
                        List<String> winners) {
        this.currentPhase = currentPhase;
        this.currentPlayer = currentPlayer;
        this.playerOrder = playerOrder;
        this.colors = colors;
        this.prestige = prestige;
        this.food = food;
        this.hands = hands;
        this.buildings = buildings;
        this.board = board;
        this.era = era;
        this.round = round;
        this.upperBudget = upperBudget;
        this.lowerBudget = lowerBudget;
        this.upperRemaining = upperRemaining;
        this.lowerRemaining = lowerRemaining;
        this.availableColors = availableColors;
        this.expectedPlayers = expectedPlayers;
        this.joinedPlayers = joinedPlayers;
        this.leaderboard = leaderboard;
        this.winners = winners;
    }

    public Phase getCurrentPhase()                              { return currentPhase; }
    public String getCurrentPlayer()                            { return currentPlayer; }
    public List<String> getPlayerOrder()                        { return playerOrder; }
    public Map<String, TotemColor> getColors()                  { return colors; }
    public Map<String, Integer> getPrestige()                   { return prestige; }
    public Map<String, Integer> getFood()                       { return food; }
    public Map<String, List<CharacterCard>> getHands()          { return hands; }
    public Map<String, List<BuildingCard>> getBuildings()       { return buildings; }
    public Board getBoard()                                     { return board; }
    public Era getEra()                                         { return era; }
    public int getRound()                                       { return round; }
    public int getUpperBudget()                                 { return upperBudget; }
    public int getLowerBudget()                                 { return lowerBudget; }
    public int getUpperRemaining()                              { return upperRemaining; }
    public int getLowerRemaining()                              { return lowerRemaining; }
    public Set<TotemColor> getAvailableColors()                 { return availableColors; }
    public int getExpectedPlayers()                             { return expectedPlayers; }
    public int getJoinedPlayers()                               { return joinedPlayers; }
    public List<String> getLeaderboard()                        { return leaderboard; }
    public List<String> getWinners()                            { return winners; }
}
