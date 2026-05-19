package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the {@link Board} entity.
 * <p>
 * Verifies board initialization based on player count, totem movement logic
 * between the turn order tile and the offering track, card lookup/removal
 * across rows, and the row-shifting mechanics used at the end of rounds.
 */
class BoardTest {

    private Board board;
    private Player playerRed;
    private Player playerBlue;

    /**
     * Initializes a fresh {@link Board} and dummy players before each test.
     */
    @BeforeEach
    @DisplayName("Setup: Initialize Board and dummy players before each test")
    void setUp() {
        board = new Board();
        playerRed = new Player("Alice", TotemColor.RED);
        playerBlue = new Player("Bob", TotemColor.BLUE);
    }

    /**
     * Verifies that the board correctly initializes the offering track and the
     * turn order tile based on the number of players. Ensures that no offering
     * tiles requiring more players than the current match size are included.
     */
    @Test
    @DisplayName("Setup: Offering track and Turn order tile correctly initialized")
    void testSetupTiles() {
        // Test for a 3-player game
        board.setupOfferingTiles(3);
        board.setupTurnOrderTiles(3);

        assertNotNull(board.getOfferingTrack(), "Offering track should be initialized.");
        assertFalse(board.getOfferingTrack().isEmpty(), "Offering track should contain tiles.");

        // Verify filtering: no tile should require more than 3 players
        for (OfferingTile tile : board.getOfferingTrack()) {
            assertTrue(tile.getMinPlayers() <= 3, "Tile min players should be <= 3.");
        }

        assertNotNull(board.getTurnOrderTile(), "Turn order tile should be initialized.");
        assertEquals(3, board.getTurnOrderTile().getTurnOrderSlots().length,
                "Turn order tile should have exactly 3 slots for 3 players.");
    }

    /**
     * Verifies the complete lifecycle of totem movements during a round.
     * Checks the initial placement on the turn order tile, the movement to
     * specific offering tiles, the correct retrieval of the player order
     * from both locations, and finally the reset back to the turn order tile.
     */
    @Test
    @DisplayName("Totem Logic: Full flow of placing, moving, and reading player order")
    void testTotemMovementFlow() {
        board.setupOfferingTiles(3);
        board.setupTurnOrderTiles(3);
        List<Player> players = Arrays.asList(playerRed, playerBlue);

        // 1. Initial Placement
        board.placeInitTotems(players);
        List<Player> turnOrder = board.getPlayersTurnOrder(players);
        assertEquals(2, turnOrder.size(), "Two players should be on the turn order tile.");
        assertTrue(turnOrder.contains(playerRed) && turnOrder.contains(playerBlue));

        // 2. Move one player to the offering track
        boolean moved = board.moveTotem(0, playerRed);
        assertTrue(moved, "Red player should successfully move to tile 0.");
        assertEquals(0, board.getOfferingTrackIndex(playerRed), "Red player should be at index 0.");

        // Move the second player
        boolean movedBlue = board.moveTotem(1, playerBlue);
        assertTrue(movedBlue, "Blue player should successfully move to tile 1.");

        // 3. Check offering track order
        List<Player> offeringOrder = board.getOfferingTrackPlayersOrder(players);
        assertEquals(2, offeringOrder.size(), "Two players should be on the offering track.");
        assertEquals(playerRed, offeringOrder.get(0), "Red should be first on the offering track.");
        assertEquals(playerBlue, offeringOrder.get(1), "Blue should be second.");

        // 4. Move back to turn order tile
        board.moveOfferingTrackToTurnOrder(players);
        List<Player> newTurnOrder = board.getPlayersTurnOrder(players);
        assertEquals(2, newTurnOrder.size(), "Players should be back on the turn order tile.");

        // Offering track should be cleared from both totems
        assertEquals(-1, board.getOfferingTrackIndex(playerRed), "Offering track should be empty for Red.");
        assertEquals(-1, board.getOfferingTrackIndex(playerBlue), "Offering track should be empty for Blue.");

    }

    /**
     * Verifies that the board can correctly locate and remove specific cards
     * (Character or Building) regardless of whether they are placed in the
     * upper or lower row.
     */
    @Test
    @DisplayName("Lookup & Removal: Checks presence across both rows and safe removal")
    void testCardLookupAndRemoval() {
        CharacterCard upperChar = createDummyCharacter(101, Era.ERA_1);
        BuildingCard lowerBuild = createDummyBuilding(201 );

        board.getUpperRow().addCharacterCard(upperChar);
        board.getLowerRow().addBuildingCard(lowerBuild);

        // Contains checks
        assertTrue(board.containsCharacterCard(upperChar), "Board should find character in upper row.");
        assertTrue(board.containsBuildingCard(lowerBuild), "Board should find building in lower row.");

        // Era check
        assertEquals(Era.ERA_1, board.getCurrentEra(), "Current era should be ERA_1 based on upper row cards.");

        // Removal check
        Card removed = board.removeCardFromRows(lowerBuild);
        assertNotNull(removed, "Card should be successfully removed.");
        assertEquals(201, removed.getCardID(), "Removed card should match the target ID.");
        assertFalse(board.containsBuildingCard(lowerBuild), "Card should no longer be on the board.");
    }

    /**
     * Verifies that the current era of the board is accurately determined by
     * finding the highest era among the tribe cards currently present in the
     * upper row.
     */
    @Test
    @DisplayName("Current Era: Returns the highest era from the upper row")
    void testGetCurrentEra() {
        CharacterCard charEra1 = createDummyCharacter(101, Era.ERA_1);
        CharacterCard charEra3 = createDummyCharacter(102, Era.ERA_3);

        board.getUpperRow().addCharacterCard(charEra1);
        board.getUpperRow().addCharacterCard(charEra3);

        assertEquals(Era.ERA_3, board.getCurrentEra(), "Board should return the highest era (ERA_3).");
    }

    /**
     * Verifies the end-of-round shifting mechanics. Ensures that all tribe
     * cards and building cards are correctly moved from the upper row down
     * to the lower row, leaving the upper row completely empty.
     */
    @Test
    @DisplayName("Row Shifting: Correctly moves cards from upper to lower row")
    void testRowShifting() {
        CharacterCard upperChar = createDummyCharacter(101, Era.ERA_1);
        BuildingCard upperBuild = createDummyBuilding(201);

        board.getUpperRow().addCharacterCard(upperChar);
        board.getUpperRow().addBuildingCard(upperBuild);

        // Shift tribe cards (Characters/Events)
        board.shiftUpToLow();
        assertTrue(board.getUpperRow().getCharacterCards().isEmpty(), "Upper row characters should be empty.");
        assertTrue(board.getLowerRow().containsCharacterCard(upperChar), "Lower row should now contain the character.");

        // Shift building cards
        board.shiftBuildingsDown();
        assertTrue(board.getUpperRow().getBuildingCards().isEmpty(), "Upper row buildings should be empty.");
        assertTrue(board.getLowerRow().containsBuildingCard(upperBuild), "Lower row should now contain the building.");
    }

    /**
     * Helper method to create a dummy character card for testing purposes.
     *
     * @param id  the unique identifier for the card
     * @param era the era of the card
     * @return a mock {@link CharacterCard}
     */
    private CharacterCard createDummyCharacter(int id, Era era) {
        return new CharacterCard(id, era, 0) {
            @Override
            public int getCardID() { return id; }
            @Override
            public Era getEra() { return era; }
        };
    }

    /**
     * Helper method to create a dummy building card for testing purposes.
     *
     * @param id the unique identifier for the card
     * @return a mock {@link BuildingCard}
     */
    private BuildingCard createDummyBuilding(int id) {
        return new BuildingCard(id, null, 0, 0, 0, null) {
            @Override
            public int getCardID() { return id; }
        };
    }
}