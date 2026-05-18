package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.CharacterBonusBuilder;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link CharacterBonusBuilder} building card.
 * <p>
 * Verifies the end-of-game effect associated with this card, which rewards
 * the designated owner with Prestige Points (PP) based strictly on the number
 * of Builder characters they possess. The tests ensure that non-owners receive
 * no points and that the mathematical multiplier (4 PP per Builder) is correctly applied.
 */
class CharacterBonusBuilderTest {

    private Player owner;
    private CharacterBonusBuilder card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the CharacterBonusBuilder card")
    void setUp() {
        owner = new Player("Owner", TotemColor.RED);
        // Initialize with cardID=10, Era=ERA_2, minPlayers=2, foodCost=3, base endOfGamePP=0
        card = new CharacterBonusBuilder(10, Era.ERA_2, 2, 3, 0, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the effect is triggered
     * on a player who is not the card's legitimate owner, they receive exactly 0
     * Prestige Points, regardless of how many Builders they possess.
     */
    @Test
    @DisplayName("Effect does not trigger if the player is not the card owner")
    void testApplyEndEffect_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.BLUE);
        // Give the non-owner some builders to prove the block works
        nonOwner.increaseBuilderCounter(3);

        card.applyEndEffect(nonOwner);

        // Non-owner receives no Prestige Points
        assertEquals(0, nonOwner.getPrestigePoints(), "Non-owner should not receive any PP from this card.");
    }

    /**
     * Verifies the calculation when the owner has exactly 0 Builder counters.
     * The effect should successfully trigger but award 0 Prestige Points (4 * 0).
     */
    @Test
    @DisplayName("Owner gets 0 PP if they possess zero builders")
    void testApplyEndEffect_Owner_ZeroBuilders() {
        // Owner starts with 0 builders by default

        card.applyEndEffect(owner);

        // 0 builders * 4 PP = 0 PP
        assertEquals(0, owner.getPrestigePoints(), "Owner should receive 0 PP if they have no builders.");
    }

    /**
     * Verifies the standard mathematical calculation of the end-game reward.
     * The owner must receive exactly 4 Prestige Points for every Builder counter
     * they possess at the time the effect is resolved.
     */
    @Test
    @DisplayName("Owner gets 4 PP multiplied by their total builder count")
    void testApplyEndEffect_Owner_WithBuilders() {
        // Give the owner 3 builders
        owner.increaseBuilderCounter(3);

        card.applyEndEffect(owner);

        // 3 builders * 4 PP = 12 PP
        assertEquals(12, owner.getPrestigePoints(), "Owner should receive 12 PP (3 builders * 4).");
    }
}