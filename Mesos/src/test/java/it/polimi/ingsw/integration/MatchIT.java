package it.polimi.ingsw.integration;

import it.polimi.ingsw.exceptions.DeckLoadException;
import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.*;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test class for the {@link Match} aggregate root.
 * <p>
 * This class verifies the interaction between multiple
 * instantiated components (Match, Board, Decks, Cards) and relies on the loading of the JSON file.
 * <p>
 * It simulates real game flows, such as the complete setup of the board
 * and the transition between complex game states.
 */
class MatchIT {

    private Match match;
    private Player p1;
    private Player p2;
    private Player p3;

    /**
     * Prepares a valid Match environment with 3 players before each integration test.
     */
    @BeforeEach
    @DisplayName("Setup: Initialize Match and add Players")
    void setUp() {
        match = new Match();
        p1 = new Player("Alice", TotemColor.RED);
        p2 = new Player("Bob", TotemColor.BLUE);
        p3 = new Player("Charlie", TotemColor.BLACK);

        match.addPlayer(p1);
        match.addPlayer(p2);
        match.addPlayer(p3);
    }

    /**
     * Integrates Match, Decks, and Board to verify the complete game setup.
     * <p>
     * This test ensures that calling startMatch() correctly reads the JSON file,
     * populates the decks, sets the round to 1, and physically deals the cards
     * and tiles onto the Board according to the number of players.
     *
     * @throws DeckLoadException if the JSON files are missing or malformed
     * @throws EmptyDeckException if there aren't enough cards to fill the board
     */
    @Test
    @DisplayName("Integration: startMatch() sets up the entire Board and Decks")
    void testStartMatch_FullGameSetup() throws DeckLoadException, EmptyDeckException {
        // 1. EXECUTE THE FULL SETUP
        assertDoesNotThrow(() -> match.startMatch(),
                "startMatch should not throw exceptions if JSON files are correctly placed in resources");

        // 2. VERIFY MATCH STATE
        assertEquals(1, match.getCurrentRound(), "The game should officially start at Round 1");

        // 3. VERIFY BOARD POPULATION
        Board board = match.getBoard();
        assertNotNull(board, "Board must exist");

        // Check Turn Order Tiles setup
        TurnOrderSlot[] turnOrderSlots = board.getTurnOrderTile().getTurnOrderSlots();
        assertNotNull(turnOrderSlots, "Turn order slots should be generated");
        assertTrue(turnOrderSlots.length >= 3, "There should be enough slots for all 3 players");

        // Check Lower Row (Events/Characters)
        CardRow lowerRow = board.getLowerRow();
        assertNotNull(lowerRow, "Lower row must be initialized");

        //Check characters fill the lower row after setup
        assertFalse(lowerRow.getCharacterCards().isEmpty(),
                "Lower row should be populated with character cards right after the setup");

        // Ensure no other card types are present in the lower row at the start
        assertTrue(lowerRow.getEventCards().isEmpty(),
                "Lower row should be completely empty of event cards during the initial setup");
        assertTrue(lowerRow.getBuildingCards().isEmpty(),
                "Lower row should be completely empty of building cards during the initial setup");
        // Check Totem Placement
        boolean atLeastOneTotemPlaced = false;
        for (TurnOrderSlot slot : turnOrderSlots) {
            if (slot.isOccupied()) {
                atLeastOneTotemPlaced = true;
                break;
            }
        }
        assertTrue(atLeastOneTotemPlaced, "At least one player's totem should be placed on the turn order tile during setup");
    }

    /**
     * Simulates the transition from Round 1 to Round 2.
     * <p>
     * Verifies that after a full setup, the game can successfully transition
     * rounds, moving cards correctly between the upper and
     * lower rows on the Board.
     */
    @Test
    @DisplayName("Integration: moveToNextRound() properly shifts cards and updates state")
    void testMoveToNextRound_IntegrationFlow() throws DeckLoadException, EmptyDeckException {
        // Setup the game first
        match.startMatch();
        assertEquals(1, match.getCurrentRound());

        // Execute the round transition
        assertDoesNotThrow(() -> match.moveToNextRound(),
                "moveToNextRound should execute without errors on a fully populated board");

        // Verify the state change
        assertEquals(2, match.getCurrentRound(), "Round counter must increment to 2");

        //TODO
        // Note: A more specific assertion here would check if the Upper Row cards
        // correctly moved to the Lower Row, but that depends on specific Board getters.
    }

    /**
     * Verifies the resolution of the Turn Order Tile effect on players.
     * <p>
     * In the game totems are placed on the tile, and the
     * Match must cross-reference the tile slots with the Player instances
     * to correctly award Food and Prestige Points.
     */
    @Test
    @DisplayName("Integration: resolveTurnOrderTile() correctly awards resources to players")
    void testResolveTurnOrderTile_Integration() throws DeckLoadException, EmptyDeckException {
        // Setup the game
        match.startMatch();

        // Ensure players start with 0 resources for a clean test
        assertEquals(0, p1.getFood());
        assertEquals(0, p1.getPrestigePoints());

        // Execute the resolution
        // Note: startMatch() should have placed the initial totems via board.placeInitTotems()
        match.resolveTurnOrderTile();

        // Verify that AT LEAST ONE player received something (since they are on the slots)
        // Since slot rewards vary, check the aggregate state of the players.
        int totalFoodInGame = p1.getFood() + p2.getFood() + p3.getFood();
        int totalPPInGame = p1.getPrestigePoints() + p2.getPrestigePoints() + p3.getPrestigePoints();

        assertTrue(totalFoodInGame > 0 || totalPPInGame > 0,
                "Players should have received some resources or PP from the turn order tile resolution");
    }
}