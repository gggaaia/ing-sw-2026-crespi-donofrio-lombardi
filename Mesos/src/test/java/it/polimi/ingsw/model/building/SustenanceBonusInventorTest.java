package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.SustenanceBonusInventor;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link SustenanceBonusInventor} building card.
 * <p>
 * Verifies the event-activated effect associated with this card, which calculates
 * a sustenance bonus (typically food or discounts) based strictly on the number
 * of Inventor characters the owner possesses. The tests ensure that non-owners
 * always evaluate to a zero bonus, and that the owner's bonus correctly scales
 * with their inventor count.
 */
class SustenanceBonusInventorTest {

    private Player owner;
    private SustenanceBonusInventor card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the SustenanceBonusInventor card")
    void setUp() {
        owner = new Player("Owner", TotemColor.BLUE);
        // Initialize with an arbitrary cardID=16, Era=ERA_2, minPlayers=2, foodCost=3, endOfGamePP=2
        card = new SustenanceBonusInventor(16, Era.ERA_2, 2, 3, 2, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the bonus calculation
     * is triggered for a player who is not the card's legitimate owner, the card
     * strictly returns a bonus of 0, ignoring the non-owner's inventor count.
     */
    @Test
    @DisplayName("Bonus evaluates to 0 if the player is not the card owner")
    void testSustenanceBonus_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.RED);

        // Give the non-owner some inventors to prove the block works
        nonOwner.increaseInventorCounter(4);

        int bonus = card.sustenanceBonus(nonOwner);

        // Non-owner must yield 0 bonus
        assertEquals(0, bonus, "Non-owner should receive a sustenance bonus of exactly 0.");
    }

    /**
     * Verifies the calculation when the owner has exactly 0 Inventor counters.
     * The effect should successfully trigger but evaluate to a 0 bonus.
     */
    @Test
    @DisplayName("Owner evaluates to 0 bonus if they possess zero inventors")
    void testSustenanceBonus_Owner_ZeroInventors() {
        // Owner starts with 0 inventors by default
        int bonus = card.sustenanceBonus(owner);

        assertEquals(0, bonus, "Owner should receive a bonus of 0 if they have no inventors.");
    }

    /**
     * Verifies the standard mathematical calculation of the sustenance bonus.
     * The card must return exactly 1 point of bonus for every Inventor counter
     * the owner possesses at the time the method is invoked.
     */
    @Test
    @DisplayName("Owner evaluates to a bonus equal to their total inventor count")
    void testSustenanceBonus_Owner_WithInventors() {
        // Give the owner 5 inventors
        owner.increaseInventorCounter(5);

        int bonus = card.sustenanceBonus(owner);

        // Bonus should exactly match the number of inventors (5)
        assertEquals(5, bonus, "Owner should receive a bonus equal to their inventor count (5).");
    }
}