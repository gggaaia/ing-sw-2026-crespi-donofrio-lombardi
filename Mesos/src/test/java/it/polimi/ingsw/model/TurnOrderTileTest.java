package it.polimi.ingsw.model;

import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the {@link TurnOrderTile} entity.
 * <p>
 * Verifies the correct initialization of slots based on player count,
 * bounds checking for array accesses, totem placement/removal logic,
 * and the correct retrieval of slot-specific bonuses.
 */
class TurnOrderTileTest {

    private TurnOrderTile tile3Players;
    private Player playerBlue;
    private Player playerYellow;

    /**
     * Initializes a standard 3-player {@link TurnOrderTile} and some dummy
     * {@link Player} instances before each test.
     */
    @BeforeEach
    void setUp() {
        tile3Players = new TurnOrderTile(3);
        // Usiamo Blu e Giallo per i giocatori "piazzati",
        // lasciando il Rosso libero per testare l'eccezione.
        playerBlue = new Player("Alice", TotemColor.BLUE);
        playerYellow = new Player("Bob", TotemColor.YELLOW);
    }

    /**
     * Tests the {@link TurnOrderTile#TurnOrderTile(int)} constructor.
     * <p>
     * Ensures that valid player counts (2 to 5) correctly initialize the underlying
     * {@link TurnOrderSlot} array with the expected length, and invalid counts
     * throw the appropriate {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("Constructor: valid limits and invalid exceptions")
    void testConstructorBoundaries() {
        TurnOrderTile tile2 = new TurnOrderTile(2);
        assertEquals(2, tile2.getTurnOrderSlots().length, "2-player tile should have 2 slots.");

        TurnOrderTile tile5 = new TurnOrderTile(5);
        assertEquals(5, tile5.getTurnOrderSlots().length, "5-player tile should have 5 slots.");

        assertThrows(IllegalArgumentException.class, () -> new TurnOrderTile(1),
                "Constructor must throw exception for less than 2 players.");

        assertThrows(IllegalArgumentException.class, () -> new TurnOrderTile(6),
                "Constructor must throw exception for more than 5 players.");
    }


    /**
     * Tests the {@link TurnOrderTile#placeTotem(Player, int)} method.
     * <p>
     * Checks successful placement, rejection when targeting an already occupied slot,
     * and bounds checking for invalid slot indices.
     */
    @Test
    @DisplayName("placeTotem: success, occupied rejection, and out of bounds")
    void testPlaceTotem() {
        // Valid placement
        boolean placed = tile3Players.placeTotem(playerBlue, 0);
        assertTrue(placed, "Placing on an empty slot should return true.");
        assertTrue(tile3Players.getTurnOrderSlots()[0].isOccupied(), "Slot 0 should now be occupied.");

        // Occupied slot rejection
        boolean placedAgain = tile3Players.placeTotem(playerYellow, 0);
        assertFalse(placedAgain, "Placing on an already occupied slot should return false.");
        assertEquals(TotemColor.BLUE, tile3Players.getTurnOrderSlots()[0].getTotemColor(),
                "Slot 0 should still hold the original BLUE totem.");

        // Index out of bounds
        assertThrows(IndexOutOfBoundsException.class, () -> tile3Players.placeTotem(playerYellow, -1),
                "Placing at negative index must throw exception.");
        assertThrows(IndexOutOfBoundsException.class, () -> tile3Players.placeTotem(playerYellow, 3),
                "Placing at index >= array length must throw exception.");
    }

    /**
     * Tests the {@link TurnOrderTile#clearSlot(Player)} method.
     * <p>
     * Verifies that a player's totem is successfully removed if present,
     * and that the method safely returns {@code false} if the player is not found.
     */
    @Test
    @DisplayName("clearSlot: successful removal and player not found")
    void testClearSlot() {
        tile3Players.placeTotem(playerBlue, 1);

        // Player is present
        boolean cleared = tile3Players.clearSlot(playerBlue);
        assertTrue(cleared, "Should return true when removing a placed player.");
        assertFalse(tile3Players.getTurnOrderSlots()[1].isOccupied(), "Slot 1 should be empty after clearing.");

        // Player is not present
        boolean clearedNotPresent = tile3Players.clearSlot(playerYellow);
        assertFalse(clearedNotPresent, "Should return false if the player is not on the tile.");
    }


    /**
     * Tests the {@link TurnOrderTile#addFood(Player)} and {@link TurnOrderTile#addPP(Player)} methods.
     * <p>
     * Ensures that the bonuses returned match the hardcoded values for a specific
     * slot index, and that an exception is thrown if the player is missing.
     */
    @Test
    @DisplayName("addFood and addPP: retrieval and missing player exception")
    void testGetBonuses() {
        // In a 3-player setup, slot 0 gives {2 food, 0 PP} and slot 2 gives {-1 food, -2 PP}
        tile3Players.placeTotem(playerBlue, 0);
        tile3Players.placeTotem(playerYellow, 2);

        assertEquals(2, tile3Players.addFood(playerBlue), "Blue in slot 0 should get 2 food.");
        assertEquals(0, tile3Players.addPP(playerBlue), "Blue in slot 0 should get 0 PP.");

        assertEquals(-1, tile3Players.addFood(playerYellow), "Yellow in slot 2 should get -1 food.");
        assertEquals(-2, tile3Players.addPP(playerYellow), "Yellow in slot 2 should get -2 PP.");

        // Test exception for an unplaced player (RED is free, so the search will fail and throw the exception)
        Player unplacedPlayer = new Player("Charlie", TotemColor.RED);

        assertThrows(IllegalArgumentException.class, () -> tile3Players.addFood(unplacedPlayer),
                "Requesting food for an unplaced player must throw an exception.");
        assertThrows(IllegalArgumentException.class, () -> tile3Players.addPP(unplacedPlayer),
                "Requesting PP for an unplaced player must throw an exception.");
    }
}