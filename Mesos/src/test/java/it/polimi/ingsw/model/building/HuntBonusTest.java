package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.HuntBonus;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for the {@link HuntBonus} building card.
 * <p>
 * Verifies the specific ability associated with this card, which grants the
 * owner a bonus during the Hunting event. The tests ensure that this
 * boolean flag correctly evaluates to true exclusively for the legitimate
 * owner, and strictly false for any other player.
 */
class HuntBonusTest {

    private Player owner;
    private HuntBonus card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the HuntBonus card")
    void setUp() {
        owner = new Player("Owner", TotemColor.RED);
        // Initialize with an arbitrary cardID=30, Era=ERA_2, minPlayers=2, foodCost=3, endOfGamePP=2
        card = new HuntBonus(30, Era.ERA_2, 2, 3, 2, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the capability check
     * is triggered for a player who is not the card's legitimate owner, the card
     * strictly returns false.
     */
    @Test
    @DisplayName("hasHuntingBonus evaluates to false if the player is not the card owner")
    void testHasHuntingBonus_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.BLUE);

        boolean hasBonus = card.hasHuntingBonus(nonOwner);

        // Non-owner must yield false
        assertFalse(hasBonus, "Non-owner should not be granted the hunting bonus.");
    }

    /**
     * Verifies the standard capability check for the card owner.
     * The owner must strictly return true when evaluated.
     */
    @Test
    @DisplayName("hasHuntingBonus evaluates to true for the legitimate owner")
    void testHasHuntingBonus_Owner() {
        boolean hasBonus = card.hasHuntingBonus(owner);

        // Owner must yield true
        assertTrue(hasBonus, "Owner should be granted the hunting bonus.");
    }
}