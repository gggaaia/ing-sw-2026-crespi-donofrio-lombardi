package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.TotemFoodBonus;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link TotemFoodBonus} building card.
 * <p>
 * Verifies the turn-order bonus effect associated with this card, which grants
 * a flat amount of food (1 unit) to the owner when the turn order is evaluated.
 * The tests ensure that the ownership restriction is strictly enforced, preventing
 * non-owners from receiving the food bonus.
 */
class TotemFoodBonusTest {

    private Player owner;
    private TotemFoodBonus card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the TotemFoodBonus card")
    void setUp() {
        owner = new Player("Owner", TotemColor.RED);
        // Initialize with cardID=20, Era=ERA_2, minPlayers=2, foodCost=4, endOfGamePP=3
        card = new TotemFoodBonus(20, Era.ERA_2, 2, 4, 3, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the turn-order bonus
     * is triggered for a player who is not the designated owner of the card,
     * they receive absolutely no food.
     */
    @Test
    @DisplayName("Effect does not grant food if the player is not the card owner")
    void testTurnOrderBonus_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.BLUE);
        // Ensure non-owner starts with 0 food
        assertEquals(0, nonOwner.getFood(), "Non-owner should start with 0 food.");

        card.turnOrderBonus(nonOwner);

        // Non-owner must still have 0 food
        assertEquals(0, nonOwner.getFood(), "Non-owner should not receive the food bonus.");
    }

    /**
     * Verifies the standard reward calculation for the card owner. Ensures that
     * when the effect is triggered for the legitimate owner, they receive exactly
     * 1 unit of food as defined by the card's logic.
     */
    @Test
    @DisplayName("Owner gets exactly 1 food when the bonus is triggered")
    void testTurnOrderBonus_Owner() {
        // Ensure owner starts with 0 food
        assertEquals(0, owner.getFood(), "Owner should start with 0 food.");

        card.turnOrderBonus(owner);

        // Owner receives 1 food
        assertEquals(1, owner.getFood(), "Owner should receive exactly 1 food.");
    }
}