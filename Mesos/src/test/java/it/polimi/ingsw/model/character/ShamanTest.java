package it.polimi.ingsw.model.character;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.character.Shaman;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link Shaman} character card.
 * <p>
 * Verifies that drawing a Shaman card correctly alters the player's state
 * by incrementing their shaman profession counter and adding the specific
 * number of stars. It also verifies that mutating the card's state via setters
 * correctly influences any subsequent effect execution.
 */
class ShamanTest {

    private Shaman shamanCard;

    @BeforeEach
    @DisplayName("Setup: Initialize a Shaman card before each test")
    void setUp() {
        // Initialize with cardID=2, Era=ERA_1, minPlayers=2, stars=3
        shamanCard = new Shaman(2, Era.ERA_1, 2, 3);
    }

    /**
     * Verifies that executing the draw effect on a player correctly increments
     * their shaman profession counter by exactly 1, and accurately increases
     * the player's star counter by the amount defined in the card.
     */
    @Test
    @DisplayName("Draw Effect: Correctly increments shaman counter and adds stars")
    void testDrawEffect_AppliesCorrectModifiers() {
        Player player = new Player("Charlie", TotemColor.WHITE);

        shamanCard.drawEffect(player);

        assertEquals(1, player.getShamanCounter(), "Shaman counter should increment by 1");
        assertEquals(3, player.getStarCounter(), "Star counter should increase by the card's base star value");
    }

    /**
     * Verifies that modifying the card's star value using the setter method
     * immediately changes the reward distributed to a player when the draw
     * effect is subsequently triggered.
     */
    @Test
    @DisplayName("Setters: Modifying stars alters subsequent draw effect rewards")
    void testSetters_AlterDrawEffectBehavior() {
        Player player = new Player("Diana", TotemColor.YELLOW);

        // Mutate the card values
        shamanCard.setStars(5);

        // Verify getter matches updated value
        assertEquals(5, shamanCard.getStars(), "Getter should return the updated star count");

        // Apply effect and verify mutated value is used
        shamanCard.drawEffect(player);

        assertEquals(1, player.getShamanCounter(), "Shaman counter should still increment by 1");
        assertEquals(5, player.getStarCounter(), "Star counter should reflect the updated mutated value");
    }
}