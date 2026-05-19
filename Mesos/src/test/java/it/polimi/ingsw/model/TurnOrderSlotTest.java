package it.polimi.ingsw.model;

import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the {@link TurnOrderSlot} entity.
 * <p>
 * Verifies the correct behavior of the slot's state transitions,
 * specifically checking totem placement, removal, and occupancy status.
 */
class TurnOrderSlotTest {

    private TurnOrderSlot slot;

    /**
     * Initializes a fresh {@link TurnOrderSlot} instance before each test
     * with arbitrary food and PP bonuses.
     */
    @BeforeEach
    void setUp() {
        slot = new TurnOrderSlot(2, -1);
    }

    /**
     * Tests the initial state and the {@link TurnOrderSlot#isOccupied()} method.
     * <p>
     * Verifies that a newly created slot is empty, and properly reflects its
     * occupied status once a {@link TotemColor} is assigned.
     */
    @Test
    @DisplayName("isOccupied: initial state and state after placement")
    void testIsOccupied() {
        assertFalse(slot.isOccupied(), "A newly created slot should not be occupied.");
        assertNull(slot.getTotemColor(), "The initial totem color should be null.");

        slot.setTotemColor(TotemColor.BLUE);

        assertTrue(slot.isOccupied(), "The slot should be occupied after a totem is placed.");
        assertEquals(TotemColor.BLUE, slot.getTotemColor(), "The slot should hold the correct totem color.");
    }

    /**
     * Tests the {@link TurnOrderSlot#removeTotem()} method.
     * <p>
     * Ensures that removing a totem from an occupied slot clears the color
     * and returns {@code true}, while attempting to remove from an empty slot
     * safely returns {@code false}.
     */
    @Test
    @DisplayName("removeTotem: success and failure branches")
    void testRemoveTotem() {
        // Branch 1: Removing from an empty slot
        boolean removedFromEmpty = slot.removeTotem();

        assertFalse(removedFromEmpty, "Removing from an empty slot should return false.");
        assertFalse(slot.isOccupied(), "The slot should remain unoccupied.");

        // Branch 2: Removing from an occupied slot
        slot.setTotemColor(TotemColor.RED);
        assertTrue(slot.isOccupied(), "Setup: slot must be occupied now.");

        boolean removedFromOccupied = slot.removeTotem();

        assertTrue(removedFromOccupied, "Removing from an occupied slot should return true.");
        assertFalse(slot.isOccupied(), "The slot should no longer be occupied after removal.");
        assertNull(slot.getTotemColor(), "The totem color should be reset to null.");
    }
}