package it.polimi.ingsw.model.character;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.character.Inventor;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.InventorIcon;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link Inventor} character card.
 * <p>
 * Verifies that drawing an Inventor card correctly alters the player's state
 * by strictly incrementing their inventor profession counter. It also ensures
 * that the specific inventor icon assigned during instantiation is properly retained.
 */
class InventorTest {

    private Inventor inventorCard;

    @BeforeEach
    @DisplayName("Setup: Initialize an Inventor card before each test")
    void setUp() {
        // Initialize with cardID=3, Era=ERA_1, minPlayers=2, and a sample icon
        inventorCard = new Inventor(3, Era.ERA_1, 2, InventorIcon.ARROW);
    }

    /**
     * Verifies that executing the draw effect on a player correctly increments
     * their inventor profession counter by exactly 1. Also verifies that the card
     * holds the correct icon, as this is crucial for end-game pair calculations.
     */
    @Test
    @DisplayName("Draw Effect: Correctly increments inventor counter and retains icon")
    void testDrawEffect_AppliesCorrectModifiers() {
        Player player = new Player("Eve", TotemColor.WHITE);

        inventorCard.drawEffect(player);

        // Check the core effect: counter must go up by 1
        assertEquals(1, player.getInventorCounter(), "Inventor counter should increment by exactly 1");

        // Ensure the card correctly stored the icon
        assertEquals(InventorIcon.ARROW, inventorCard.getInventorIcon(), "The card should retain the icon passed in the constructor");
    }
}