package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.PaintingFoodBonus;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for the {@link PaintingFoodBonus} building card.
 * <p>
 * Verifies the specific ability associated with this card, which grants the
 * owner a food bonus during the Painting. The tests ensure that this
 * boolean flag correctly evaluates to true exclusively for the legitimate
 * owner, and strictly false for any other player.
 */
class PaintingFoodBonusTest {

    private Player owner;
    private PaintingFoodBonus card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the PaintingFoodBonus card")
    void setUp() {
        owner = new Player("Owner", TotemColor.YELLOW);
        // Initialize with an arbitrary cardID=29, Era=ERA_2, minPlayers=2, foodCost=2, endOfGamePP=2
        card = new PaintingFoodBonus(29, Era.ERA_2, 2, 2, 2, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the capability check
     * is triggered for a player who is not the card's legitimate owner, the card
     * strictly returns false.
     */
    @Test
    @DisplayName("hasPaintingBonus evaluates to false if the player is not the card owner")
    void testHasPaintingBonus_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.YELLOW);

        boolean hasBonus = card.hasPaintingBonus(nonOwner);

        // Non-owner must yield false
        assertFalse(hasBonus, "Non-owner should not be granted the painting food bonus.");
    }

    /**
     * Verifies the standard capability check for the card owner.
     * The owner must strictly return true when evaluated.
     */
    @Test
    @DisplayName("hasPaintingBonus evaluates to true for the legitimate owner")
    void testHasPaintingBonus_Owner() {
        boolean hasBonus = card.hasPaintingBonus(owner);

        // Owner must yield true
        assertTrue(hasBonus, "Owner should be granted the painting food bonus.");
    }
}