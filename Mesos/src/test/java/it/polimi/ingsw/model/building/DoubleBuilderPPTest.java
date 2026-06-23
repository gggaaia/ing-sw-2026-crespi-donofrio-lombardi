package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.DoubleBuilderPP;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link DoubleBuilderPP} building card.
 * <p>
 * Verifies the end-of-match effect associated with this card, which rewards
 * the designated owner by doubling their current Prestige Point (PP) builder bonus.
 * The tests ensure that non-owners receive no bonus modification and that the
 * mathematical doubling logic is accurately applied to the legitimate owner.
 */
class DoubleBuilderPPTest {

    private Player owner;
    private DoubleBuilderPP card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the DoubleBuilderPP card")
    void setUp() {
        owner = new Player("Owner", TotemColor.BLACK);
        // Initialize with an arbitrary cardID=26, Era=ERA_2, minPlayers=2, foodCost=4, endOfGamePP=0
        card = new DoubleBuilderPP(26, Era.ERA_2, 2, 4, 0, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the effect is triggered
     * on a player who is not the card's legitimate owner, their existing builder
     * bonus remains strictly unchanged.
     */
    @Test
    @DisplayName("Effect does not trigger if the player is not the card owner")
    void testApplyEndEffect_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.RED);

        // Give the non-owner an initial builder bonus of 5 to prove the block works
        nonOwner.increasePPBuilderBonus(5);

        card.applyEndEffect(nonOwner);

        // Non-owner's bonus must remain exactly 5, not doubled
        assertEquals(5, nonOwner.getPPBuilderBonus(), "Non-owner's PP builder bonus should remain unchanged.");
    }

    /**
     * Verifies the calculation when the owner has an initial builder bonus of 0.
     * The effect should successfully trigger but result in 0 (0 + 0 = 0).
     */
    @Test
    @DisplayName("Owner gets 0 bonus if their initial builder bonus is zero")
    void testApplyEndEffect_Owner_ZeroBonus() {
        // Owner starts with 0 builder bonus by default
        assertEquals(0, owner.getPPBuilderBonus(), "Owner should start with 0 PP builder bonus.");

        card.applyEndEffect(owner);

        // 0 doubled is still 0
        assertEquals(0, owner.getPPBuilderBonus(), "Owner's bonus should remain 0 when doubling a 0 base value.");
    }

    /**
     * Verifies the standard mathematical calculation of the end-game reward.
     * The owner's existing builder bonus must be exactly doubled.
     */
    @Test
    @DisplayName("Owner's existing builder bonus is exactly doubled")
    void testApplyEndEffect_Owner_PositiveBonus() {
        // Give the owner an initial builder bonus of 4
        owner.increasePPBuilderBonus(4);

        card.applyEndEffect(owner);

        // The initial 4 should be doubled to 8
        assertEquals(8, owner.getPPBuilderBonus(), "Owner's PP builder bonus should be exactly doubled (from 4 to 8).");
    }
}