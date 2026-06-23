package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.RitualSave;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link RitualSave} building card.
 * <p>
 * Verifies the event-activated effect associated with this card, which provides
 * immunity (a multiplier of 0) to the Prestige Point malus during the Ritual event.
 * The tests ensure that the legitimate owner receives the immunity multiplier of 0,
 * while non-owners receive the standard multiplier of 1 (meaning they still suffer the malus).
 */
class RitualSaveTest {

    private Player owner;
    private RitualSave card;

    @BeforeEach
    @DisplayName("Setup: Initialize owner and the RitualSave card")
    void setUp() {
        owner = new Player("Owner", TotemColor.RED);
        // Initialize with an arbitrary cardID=32, Era=ERA_2, minPlayers=2, foodCost=3, endOfGamePP=2
        card = new RitualSave(32, Era.ERA_2, 2, 3, 2, owner);
    }

    /**
     * Verifies the ownership check restriction. Ensures that if the immunity calculation
     * is triggered for a player who is not the card's legitimate owner, the card
     * strictly returns the standard multiplier of 1 (no immunity).
     */
    @Test
    @DisplayName("checkRitualSafe evaluates to 1 (no immunity) if the player is not the card owner")
    void testCheckRitualSafe_NotOwner() {
        Player nonOwner = new Player("Alice", TotemColor.BLUE);

        int multiplier = card.checkRitualSafe(nonOwner);

        // Non-owner must yield a neutral multiplier of 1
        assertEquals(1, multiplier, "Non-owner should receive the standard multiplier of 1.");
    }

    /**
     * Verifies the standard immunity multiplier for the card owner.
     * The owner must strictly receive a multiplier of 0, neutralizing the Ritual malus.
     */
    @Test
    @DisplayName("checkRitualSafe evaluates to 0 (immunity) for the legitimate owner")
    void testCheckRitualSafe_Owner() {
        int multiplier = card.checkRitualSafe(owner);

        // Owner must yield an immunity multiplier of 0
        assertEquals(0, multiplier, "Owner should receive an immunity multiplier of 0.");
    }
}