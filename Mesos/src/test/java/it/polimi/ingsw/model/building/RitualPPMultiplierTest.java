package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.RitualPPMultiplier;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link RitualPPMultiplier} building card.
 * <p>
 * Verifies the event-activated effect associated with this card, which provides
 * a multiplier for the Ritual event's Prestige Point (PP) bonus. The tests
 * ensure that the legitimate owner receives a multiplier of 2 (doubling the bonus),
 * while non-owners receive a neutral multiplier of 1 (no change).
 */
class RitualPPMultiplierTest {

    private Player owner;
    private RitualPPMultiplier card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the RitualPPMultiplier card")
    void setUp() {
        owner = new Player("Owner", TotemColor.BLACK);
        // Initialize with an arbitrary cardID=27, Era=ERA_2, minPlayers=2, foodCost=3, endOfGamePP=2
        card = new RitualPPMultiplier(27, Era.ERA_2, 2, 3, 2, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the multiplier calculation
     * is triggered for a player who is not the card's legitimate owner, the card
     * strictly returns a neutral multiplier of 1.
     */
    @Test
    @DisplayName("checkDoubleStarRitual evaluates to 1 (neutral) if the player is not the card owner")
    void testCheckDoubleStarRitual_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.WHITE);

        int multiplier = card.checkDoubleStarRitual(nonOwner);

        // Non-owner must yield a neutral multiplier of 1
        assertEquals(1, multiplier, "Non-owner should receive a neutral multiplier of 1.");
    }

    /**
     * Verifies the standard reward multiplier for the card owner.
     * The owner must strictly receive a multiplier of 2 when evaluating the Ritual event.
     */
    @Test
    @DisplayName("checkDoubleStarRitual evaluates to 2 (doubled) for the legitimate owner")
    void testCheckDoubleStarRitual_Owner() {
        int multiplier = card.checkDoubleStarRitual(owner);

        // Owner must yield a doubling multiplier of 2
        assertEquals(2, multiplier, "Owner should receive a doubling multiplier of 2.");
    }
}