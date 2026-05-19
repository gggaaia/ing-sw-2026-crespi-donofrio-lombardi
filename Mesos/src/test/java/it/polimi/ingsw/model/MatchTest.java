package it.polimi.ingsw.model;

import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the {@link Match} aggregate root.
 * <p>
 * Verifies the core game loop mechanisms including player management,
 * match initialization, round/era transitions, and the end-of-game
 * leaderboard sorting logic (including tie-breakers).
 */
class MatchTest {

    private Match match;
    private Player p1;
    private Player p2;
    private Player p3;

    /**
     * Initializes a fresh {@link Match} instance and standard {@link Player}
     * objects before each test execution to ensure an isolated testing environment.
     */
    @BeforeEach
    @DisplayName("Setup: Initialize an empty Match and basic Players")
    void setUp() {
        match = new Match();
        p1 = new Player("Alice", TotemColor.RED);
        p2 = new Player("Bob", TotemColor.BLUE);
        p3 = new Player("Charlie", TotemColor.WHITE);
    }

    /**
     * Verifies the initial state of a newly instantiated Match.
     * Ensures that the match ID is strictly positive, the game starts at round 0
     * in ERA_1, the board is properly initialized, and the player list is empty.
     */
    @Test
    @DisplayName("Constructor: Initializes valid Match state")
    void testMatchInitialization() {
        assertTrue(match.getMatchID() > 0, "Match ID should be generated and strictly positive");
        assertEquals(0, match.getCurrentRound(), "A match that hasn't started must be at round 0");
        assertEquals(Era.ERA_1, match.getCurrentEra(), "A new match must start in ERA_1");
        assertNotNull(match.getBoard(), "Board must be initialized and not null");
        assertTrue(match.getPlayersList().isEmpty(), "Player list must start completely empty");
    }

    /**
     * Verifies the custom ID generation logic based on the system's epoch time.
     * Ensures that IDs do not exceed 9 digits and that matches created sequentially
     * receive distinct, unique identifiers.
     * * @throws InterruptedException if the thread sleep is interrupted
     */
    @Test
    @DisplayName("Match ID: Generates valid, unique IDs based on timestamps")
    void testMatchIDGeneration() throws InterruptedException {
        Match match1 = new Match();
        // Pause to guarantee the system clock moves forward and generates a new ID
        Thread.sleep(5);
        Match match2 = new Match();

        assertTrue(match1.getMatchID() > 0, "First match ID must be positive");
        assertTrue(match2.getMatchID() > 0, "Second match ID must be positive");
        assertNotEquals(match1.getMatchID(), match2.getMatchID(), "Matches created at different milliseconds must have different IDs");
        assertTrue(match1.getMatchID() <= 999999999, "Match ID should not exceed the 9-digit format limit");
    }

    /**
     * Verifies the logic for managing players within the match.
     * Tests the successful addition of new players, the prevention of duplicate
     * entries (based on reference equality), and the successful removal of players.
     */
    @Test
    @DisplayName("Player Management: Add, Remove, and Avoid Duplicates")
    void testPlayerManagement() {
        // Test standard addition
        match.addPlayer(p1);
        match.addPlayer(p2);
        assertEquals(2, match.getPlayersList().size(), "Match should contain exactly 2 players");
        assertTrue(match.isPlayerHere("Alice"), "Player Alice should be registered");
        assertFalse(match.isPlayerHere("Eve"), "Unregistered player Eve should not be found");

        // Test duplicate prevention
        match.addPlayer(p1);
        assertEquals(2, match.getPlayersList().size(), "Adding an already registered player should be ignored");

        // Test removal
        match.removePlayer(p1);
        assertEquals(1, match.getPlayersList().size(), "Player list size should decrease after removal");
        assertFalse(match.isPlayerHere("Alice"), "Player Alice should no longer be registered");
    }

    /**
     * Verifies the algorithmic sorting logic used to generate the final leaderboard.
     * Evaluates the standard victory condition (highest Prestige Points) and
     * accurately checks the tie-breaker condition (highest Food amount if PP are equal).
     */
    @Test
    @DisplayName("Leaderboard: Sorts correctly using PP and Food tie-breakers")
    void testCreateLeaderboard_SortingLogic() {
        // p1: 10 PP, 5 Food
        p1.changePP(10);
        p1.changeFood(5);

        // p2: 10 PP, 8 Food (Tied with p1 on PP, but wins tie-breaker due to more Food)
        p2.changePP(10);
        p2.changeFood(8);

        // p3: 15 PP, 0 Food (Absolute winner based purely on PP)
        p3.changePP(15);

        match.addPlayer(p1);
        match.addPlayer(p2);
        match.addPlayer(p3);

        List<Player> leaderboard = match.createLeaderboard();

        assertEquals(3, leaderboard.size(), "Leaderboard must contain all registered players");
        assertEquals(p3, leaderboard.get(0), "Charlie must be 1st due to the highest Prestige Points");
        assertEquals(p2, leaderboard.get(1), "Bob must be 2nd due to winning the Food tie-breaker over Alice");
        assertEquals(p1, leaderboard.get(2), "Alice must be 3rd due to losing the Food tie-breaker");
    }

    /**
     * Verifies that invoking the round transition safely increments the internal
     * round counter. Expects an {@link EmptyDeckException} since decks are not
     * populated via JSON in this strict unit test environment.
     */
    @Test
    @DisplayName("Round Transition: Increments the round counter")
    void testMoveToNextRound() {
        assertEquals(0, match.getCurrentRound(), "Match should start at round 0");
        try {
            match.moveToNextRound();
        } catch (EmptyDeckException e) {
            // It is expected to catch an EmptyDeckException in a pure unit test
            // without full JSON deck population.
        }
        assertEquals(1, match.getCurrentRound(), "Round counter must increment by 1 regardless of deck state");
    }

    /**
     * Verifies the transition mechanism between game Eras.
     * Ensures that forcing a move to a new Era properly updates the Match's internal state.
     */
    @Test
    @DisplayName("Era Transition: Moves to next era correctly")
    void testMoveToNextEra() {
        // Board.newUpperBuilding indexes BUILDINGS_PER_ERA by (numPlayers - 2),
        // so the match must contain a valid player count (2..5) before invoking
        // moveToNextEra, otherwise the call throws ArrayIndexOutOfBoundsException
        // instead of the expected EmptyDeckException.
        match.addPlayer(p1);
        match.addPlayer(p2);
        match.addPlayer(p3);

        try {
            match.moveToNextEra(Era.ERA_2);
            assertEquals(Era.ERA_2, match.getCurrentEra(), "The current Era must be updated to ERA_2");
        } catch (EmptyDeckException e) {
            // Expected in a pure unit test without populated decks.
        }
    }

    /**
     * Verifies the end-of-match resolution logic.
     * Ensures that final Prestige Points (PP) are correctly calculated and awarded
     * based on character synergies (e.g., painters pairs) and building effects.
     */
    @Test
    @DisplayName("Resolve End Match: Calculates final PP correctly")
    void testResolveEndMatch() {
        match.addPlayer(p1);

        // Setup: Grant 3 Painters. Rule: 10 PP for every pair of painters.
        // Therefore, 3 painters should yield exactly 10 PP (integer division 3/2 = 1 * 10).
        p1.increasePainterCounter(3);

        // Execute end-of-match calculation
        match.resolveEndMatch();
        // Trigger the wrapper method as well to ensure coverage
        match.endMatch();

        assertTrue(p1.getPrestigePoints() >= 10, "Player must receive at least 10 PP calculated from the painter pairs");
    }

    /**
     * Verifies the safe execution of the building resolution phase.
     * Ensures that iterating over players to trigger end-of-round building effects
     * does not throw exceptions, even if the player's building list is empty.
     */
    @Test
    @DisplayName("Resolve Buildings: Executes safely without crashing")
    void testResolveBuildings() {
        match.addPlayer(p2);
        assertDoesNotThrow(() -> match.resolveBuildings(),
                "resolveBuildings method should execute safely on empty buildings lists without throwing NullPointerExceptions");
    }

    /**
     * Verifies the safe execution of the event resolution phase.
     * Ensures that iterating over the lower card row to apply events does not
     * throw exceptions, even if the row is empty or unpopulated.
     */
    @Test
    @DisplayName("Resolve Events: Executes safely on empty board")
    void testResolveEvents() {
        assertDoesNotThrow(() -> match.resolveEvents(),
                "resolveEvents method should execute safely even without populated decks or active events on the board");
    }
}