package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.CharacterBonusInventor;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link CharacterBonusInventor} building card.
 * <p>
 * Verifies the end-of-game effect associated with this card, which rewards
 * the designated owner with Prestige Points (PP) based strictly on the number
 * of Inventor characters they possess. The tests ensure that non-owners receive
 * no points and that the mathematical multiplier (2 PP per Inventor) is correctly applied.
 */
class CharacterBonusInventorTest {

    private Player owner;
    private CharacterBonusInventor card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the CharacterBonusInventor card")
    void setUp() {
        owner = new Player("Owner", TotemColor.RED);
        // Initialize with cardID=13, Era=ERA_2, minPlayers=2, foodCost=3, base endOfGamePP=0
        card = new CharacterBonusInventor(13, Era.ERA_2, 2, 3, 0, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the effect is triggered
     * on a player who is not the card's legitimate owner, they receive exactly 0
     * Prestige Points, regardless of how many Inventors they possess.
     */
    @Test
    @DisplayName("Effect does not trigger if the player is not the card owner")
    void testApplyEndEffect_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.BLUE);

        // Give the non-owner some inventors to prove the block works
        nonOwner.increaseInventorCounter(3);

        card.applyEndEffect(nonOwner);

        // Non-owner receives no Prestige Points
        assertEquals(0, nonOwner.getPrestigePoints(), "Non-owner should not receive any PP from this card.");
    }

    /**
     * Verifies the calculation when the owner has exactly 0 Inventor counters.
     * The effect should successfully trigger but award 0 Prestige Points (2 * 0).
     */
    @Test
    @DisplayName("Owner gets 0 PP if they possess zero inventors")
    void testApplyEndEffect_Owner_ZeroInventors() {
        // Owner starts with 0 inventors by default
        card.applyEndEffect(owner);

        // 0 inventors * 2 PP = 0 PP
        assertEquals(0, owner.getPrestigePoints(), "Owner should receive 0 PP if they have no inventors.");
    }

    /**
     * Verifies the standard mathematical calculation of the end-game reward.
     * The owner must receive exactly 2 Prestige Points for every Inventor counter
     * they possess at the time the effect is resolved.
     */
    @Test
    @DisplayName("Owner gets 2 PP multiplied by their total inventor count")
    void testApplyEndEffect_Owner_WithInventors() {
        // Give the owner 5 inventors
        owner.increaseInventorCounter(5);

        card.applyEndEffect(owner);

        // 5 inventors * 2 PP = 10 PP
        assertEquals(10, owner.getPrestigePoints(), "Owner should receive 10 PP (5 inventors * 2).");
    }
}