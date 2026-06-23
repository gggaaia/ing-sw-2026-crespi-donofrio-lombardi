package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.DrawUpperRow;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for the {@link DrawUpperRow} building card.
 * <p>
 * Verifies the specific ability associated with this card, which grants the
 * owner permission to interact with or draw from the upper row. The tests
 * ensure that this boolean flag correctly evaluates to true exclusively for
 * the legitimate owner, and strictly false for any other player.
 */
class DrawUpperRowTest {

    private Player owner;
    private DrawUpperRow card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the DrawUpperRow card")
    void setUp() {
        owner = new Player("Owner", TotemColor.RED);
        // Initialize with an arbitrary cardID=28, Era=ERA_2, minPlayers=2, foodCost=3, endOfGamePP=1
        card = new DrawUpperRow(28, Era.ERA_2, 2, 3, 1, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the capability check
     * is triggered for a player who is not the card's legitimate owner, the card
     * strictly returns false.
     */
    @Test
    @DisplayName("hasUpperRowBuilding evaluates to false if the player is not the card owner")
    void testHasUpperRowBuilding_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.BLUE);

        boolean hasAbility = card.hasUpperRowBuilding(nonOwner);

        // Non-owner must yield false
        assertFalse(hasAbility, "Non-owner should not be granted the upper row ability.");
    }

    /**
     * Verifies the standard capability check for the card owner.
     * The owner must strictly return true when evaluated.
     */
    @Test
    @DisplayName("hasUpperRowBuilding evaluates to true for the legitimate owner")
    void testHasUpperRowBuilding_Owner() {
        boolean hasAbility = card.hasUpperRowBuilding(owner);

        // Owner must yield true
        assertTrue(hasAbility, "Owner should be granted the upper row ability.");
    }
}