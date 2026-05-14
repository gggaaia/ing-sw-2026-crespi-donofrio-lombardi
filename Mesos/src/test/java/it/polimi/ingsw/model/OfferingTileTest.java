package it.polimi.ingsw.model;

import it.polimi.ingsw.model.enums.TileLetter;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the {@link OfferingTile} entity.
 * <p>
 * Verifies the state transitions related to totem placement and removal,
 * as well as the correct generation of the standard game tiles via the
 * static factory method.
 */
class OfferingTileTest {

    private OfferingTile tile;

    /**
     * Initializes a standard {@link OfferingTile} before each test.
     */
    @BeforeEach
    void setUp() {
        tile = new OfferingTile(TileLetter.A, 0, 0, 5, 3);
    }

    /**
     * Tests the state transitions of the tile's occupancy.
     * <p>
     * Ensures that the tile is initially empty, correctly updates its state
     * when a {@link TotemColor} is set, and properly resets when cleared.
     */
    @Test
    @DisplayName("Occupancy state: initialization, placement, and clearing")
    void testStateTransitions() {
        assertFalse(tile.isOccupied(), "A newly created tile should not be occupied.");
        assertNull(tile.getCurrentTotemColor(), "The initial totem color should be null.");

        tile.setCurrentTotemColor(TotemColor.BLUE);
        assertTrue(tile.isOccupied(), "The tile should be occupied after a totem is placed.");
        assertEquals(TotemColor.BLUE, tile.getCurrentTotemColor(), "The tile should hold the assigned totem color.");

        tile.clearTile();
        assertFalse(tile.isOccupied(), "The tile should not be occupied after being cleared.");
        assertNull(tile.getCurrentTotemColor(), "The totem color should be reset to null after clearing.");
    }

    /**
     * Tests the {@link OfferingTile#createAllTiles()} static factory method.
     * <p>
     * Verifies that the factory generates exactly 7 tiles and exhaustively
     * checks the properties of every single tile (Tile A through Tile G)
     * to ensure they match the hardcoded game rules.
     */
    @Test
    @DisplayName("createAllTiles: verifies correct generation of every single tile")
    void testCreateAllTilesExhaustive() {
        List<OfferingTile> tiles = OfferingTile.createAllTiles();

        assertNotNull(tiles, "The returned list should not be null.");
        assertEquals(7, tiles.size(), "The factory should create exactly 7 offering tiles.");

        // Tile A: 0 top, 0 bottom, 5 minPlayers, 3 food
        OfferingTile tileA = tiles.get(0);
        assertEquals(TileLetter.A, tileA.getLetter());
        assertEquals(0, tileA.getTopCardsToGet());
        assertEquals(0, tileA.getBottomCardsToGet());
        assertEquals(5, tileA.getMinPlayers());
        assertEquals(3, tileA.getFoodGained());

        // Tile B: 0 top, 1 bottom, 2 minPlayers, 0 food
        OfferingTile tileB = tiles.get(1);
        assertEquals(TileLetter.B, tileB.getLetter());
        assertEquals(0, tileB.getTopCardsToGet());
        assertEquals(1, tileB.getBottomCardsToGet());
        assertEquals(2, tileB.getMinPlayers());
        assertEquals(0, tileB.getFoodGained());

        // Tile C: 1 top, 0 bottom, 2 minPlayers, 0 food
        OfferingTile tileC = tiles.get(2);
        assertEquals(TileLetter.C, tileC.getLetter());
        assertEquals(1, tileC.getTopCardsToGet());
        assertEquals(0, tileC.getBottomCardsToGet());
        assertEquals(2, tileC.getMinPlayers());
        assertEquals(0, tileC.getFoodGained());

        // Tile D: 0 top, 2 bottom, 3 minPlayers, 0 food
        OfferingTile tileD = tiles.get(3);
        assertEquals(TileLetter.D, tileD.getLetter());
        assertEquals(0, tileD.getTopCardsToGet());
        assertEquals(2, tileD.getBottomCardsToGet());
        assertEquals(3, tileD.getMinPlayers());
        assertEquals(0, tileD.getFoodGained());

        // Tile E: 1 top, 1 bottom, 2 minPlayers, 0 food
        OfferingTile tileE = tiles.get(4);
        assertEquals(TileLetter.E, tileE.getLetter());
        assertEquals(1, tileE.getTopCardsToGet());
        assertEquals(1, tileE.getBottomCardsToGet());
        assertEquals(2, tileE.getMinPlayers());
        assertEquals(0, tileE.getFoodGained());

        // Tile F: 2 top, 0 bottom, 4 minPlayers, 0 food
        OfferingTile tileF = tiles.get(5);
        assertEquals(TileLetter.F, tileF.getLetter());
        assertEquals(2, tileF.getTopCardsToGet());
        assertEquals(0, tileF.getBottomCardsToGet());
        assertEquals(4, tileF.getMinPlayers());
        assertEquals(0, tileF.getFoodGained());

        // Tile G: 2 top, 1 bottom, 3 minPlayers, 0 food
        OfferingTile tileG = tiles.get(6);
        assertEquals(TileLetter.G, tileG.getLetter());
        assertEquals(2, tileG.getTopCardsToGet());
        assertEquals(1, tileG.getBottomCardsToGet());
        assertEquals(3, tileG.getMinPlayers());
        assertEquals(0, tileG.getFoodGained());
    }

}