package it.polimi.ingsw.model.character;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.character.Gatherer;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link Gatherer} character card.
 * <p>
 * Verifies that drawing a Gatherer card correctly alters the player's state
 * by strictly incrementing their gatherer profession counter.
 */
class GathererTest {

    private Gatherer gathererCard;

    @BeforeEach
    @DisplayName("Setup: Initialize a Gatherer card before each test")
    void setUp() {
        // Initialize with cardID=4, Era=ERA_1, minPlayers=2
        gathererCard = new Gatherer(4, Era.ERA_1, 2);
    }

    /**
     * Verifies that executing the draw effect on a player correctly increments
     * their gatherer profession counter by exactly 1.
     */
    @Test
    @DisplayName("Draw Effect: Correctly increments gatherer counter")
    void testDrawEffect_AppliesCorrectModifiers() {
        Player player = new Player("Frank", TotemColor.BLUE);

        gathererCard.drawEffect(player);

        // Check the core effect: gatherer counter must go up by 1
        assertEquals(1, player.getGathererCounter(), "Gatherer counter should increment by exactly 1");
    }
}