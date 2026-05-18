package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.SustenanceBonusGatherer;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link SustenanceBonusGatherer} building card.
 * <p>
 * Verifies the event-activated effect associated with this card, which calculates
 * a sustenance bonus (typically food or discounts) based strictly on the number
 * of Gatherer characters the owner possesses. The tests ensure that non-owners
 * always evaluate to a zero bonus, and that the owner's bonus correctly scales
 * with their gatherer count.
 */
class SustenanceBonusGathererTest {

    private Player owner;
    private SustenanceBonusGatherer card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the SustenanceBonusGatherer card")
    void setUp() {
        owner = new Player("Owner", TotemColor.YELLOW);
        // Initialize with cardID=11, Era=ERA_2, minPlayers=2, foodCost=3, endOfGamePP=2
        card = new SustenanceBonusGatherer(11, Era.ERA_2, 2, 3, 2, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the bonus calculation
     * is triggered for a player who is not the card's legitimate owner, the card
     * strictly returns a bonus of 0, ignoring the non-owner's gatherer count.
     */
    @Test
    @DisplayName("Bonus evaluates to 0 if the player is not the card owner")
    void testSustenanceBonus_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.YELLOW);
        // Give the non-owner some gatherers to prove the block works
        nonOwner.increaseGathererCounter(4);

        int bonus = card.sustenanceBonus(nonOwner);

        // Non-owner must yield 0 bonus
        assertEquals(0, bonus, "Non-owner should receive a sustenance bonus of exactly 0.");
    }

    /**
     * Verifies the calculation when the owner has exactly 0 Gatherer counters.
     * The effect should successfully trigger but evaluate to a 0 bonus.
     */
    @Test
    @DisplayName("Owner evaluates to 0 bonus if they possess zero gatherers")
    void testSustenanceBonus_Owner_ZeroGatherers() {
        // Owner starts with 0 gatherers by default

        int bonus = card.sustenanceBonus(owner);

        assertEquals(0, bonus, "Owner should receive a bonus of 0 if they have no gatherers.");
    }

    /**
     * Verifies the standard mathematical calculation of the sustenance bonus.
     * The card must return exactly 1 point of bonus for every Gatherer counter
     * the owner possesses at the time the method is invoked.
     */
    @Test
    @DisplayName("Owner evaluates to a bonus equal to their total gatherer count")
    void testSustenanceBonus_Owner_WithGatherers() {
        // Give the owner 5 gatherers
        owner.increaseGathererCounter(5);

        int bonus = card.sustenanceBonus(owner);

        // Bonus should exactly match the number of gatherers (5)
        assertEquals(5, bonus, "Owner should receive a bonus equal to their gatherer count (5).");
    }
}