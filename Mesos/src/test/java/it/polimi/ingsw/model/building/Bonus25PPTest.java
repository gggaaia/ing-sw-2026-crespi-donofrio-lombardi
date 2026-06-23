package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.Bonus25PP;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link Bonus25PP} building card.
 * <p>
 * Verifies the end-of-match effect associated with this card, which rewards
 * the designated owner with a massive flat bonus of exactly 25 Prestige Points (PP).
 * The tests ensure that non-owners receive no points and that the bonus is
 * correctly awarded to the legitimate owner.
 */
class Bonus25PPTest {

    private Player owner;
    private Bonus25PP card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the Bonus25PP card")
    void setUp() {
        owner = new Player("Owner", TotemColor.BLUE);
        // Initialize with an arbitrary cardID=25, Era=ERA_2, minPlayers=2, foodCost=5, endOfGamePP=0
        card = new Bonus25PP(25, Era.ERA_2, 2, 5, 0, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the effect is triggered
     * on a player who is not the card's legitimate owner, they receive exactly 0
     * bonus Prestige Points.
     */
    @Test
    @DisplayName("Effect does not trigger if the player is not the card owner")
    void testApplyEndEffect_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.RED);

        // Ensure non-owner starts with 0 PP
        assertEquals(0, nonOwner.getPrestigePoints(), "Non-owner should start with 0 PP.");

        card.applyEndEffect(nonOwner);

        // Non-owner receives no Prestige Points
        assertEquals(0, nonOwner.getPrestigePoints(), "Non-owner should not receive the 25 PP bonus from this card.");
    }

    /**
     * Verifies the standard reward calculation for the card owner.
     * The owner must strictly receive 25 Prestige Points when the end-of-game
     * effect is resolved.
     */
    @Test
    @DisplayName("Owner gets exactly 25 PP when the effect is applied")
    void testApplyEndEffect_Owner() {
        // Ensure owner starts with 0 PP
        assertEquals(0, owner.getPrestigePoints(), "Owner should start with 0 PP.");

        card.applyEndEffect(owner);

        // Owner receives exactly 25 PP
        assertEquals(25, owner.getPrestigePoints(), "Owner should receive exactly 25 PP.");
    }
}