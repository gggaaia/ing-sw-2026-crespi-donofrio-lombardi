package it.polimi.ingsw.model.character;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.character.Artist;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link Artist} character card.
 * <p>
 * Verifies that drawing an Artist card correctly alters the player's state
 * by strictly incrementing their painter profession counter, and ensures
 * the card's base attributes are correctly initialized.
 */
class ArtistTest {

    private Artist artistCard;

    @BeforeEach
    @DisplayName("Setup: Initialize an Artist card before each test")
    void setUp() {
        // Initialize with an arbitrary cardID=7, Era=ERA_1, minPlayers=2
        artistCard = new Artist(7, Era.ERA_1, 2);
    }

    /**
     * Verifies that executing the draw effect on a player correctly increments
     * their painter profession counter by exactly 1.
     */
    @Test
    @DisplayName("Draw Effect: Correctly increments painter counter")
    void testDrawEffect_AppliesCorrectModifiers() {
        Player player = new Player("Vincent", TotemColor.YELLOW);

        // Ensure the player starts with 0 painters for a clean test
        assertEquals(0, player.getPainterCounter(), "Player should start with 0 painters.");

        artistCard.drawEffect(player);

        // Check the core effect: painter counter must go up by 1
        assertEquals(1, player.getPainterCounter(), "Painter counter should increment by exactly 1.");
    }

    /**
     * Verifies that the super constructor properly binds the core attributes
     * of the card (ID, Era, minimum players).
     */
    @Test
    @DisplayName("Constructor & Getters: Correctly initialize base state")
    void testConstructorAndGetters() {
        assertEquals(7, artistCard.getCardID(), "Card ID should match the constructor input.");
        assertEquals(Era.ERA_1, artistCard.getEra(), "Era should match the constructor input.");
        assertEquals(2, artistCard.getMinPlayers(), "Minimum players should match the constructor input.");
    }
}