package it.polimi.ingsw.model.character;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.character.Hunter;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for the {@link Hunter} character card.
 * <p>
 * Verifies the dual behavior of the Hunter card upon drawing. Specifically,
 * it ensures that the hunter counter is always incremented, but the food
 * reward is conditionally granted based on the card's internal foodBonus flag.
 * It also verifies that modifying this flag alters the card's subsequent effects.
 */
class HunterTest {

    private Hunter hunterWithBonus;
    private Hunter hunterWithoutBonus;

    @BeforeEach
    @DisplayName("Setup: Initialize Hunter cards with and without food bonus")
    void setUp() {
        // Initialize one card WITH the food bonus
        hunterWithBonus = new Hunter(5, Era.ERA_1, 2, true);

        // Initialize one card WITHOUT the food bonus
        hunterWithoutBonus = new Hunter(6, Era.ERA_1, 2, false);
    }

    /**
     * Verifies the draw effect when the card HAS a food bonus.
     * The player should receive exactly 1 food and 1 hunter counter.
     */
    @Test
    @DisplayName("Draw Effect (With Bonus): Grants 1 food and increments counter")
    void testDrawEffect_WithFoodBonus() {
        Player player = new Player("Alice", TotemColor.RED);

        hunterWithBonus.drawEffect(player);

        assertEquals(1, player.getHunterCounter(), "Hunter counter should increment by 1");
        assertEquals(1, player.getFood(), "Player should receive exactly 1 food due to the bonus flag");
    }

    /**
     * Verifies the draw effect when the card DOES NOT HAVE a food bonus.
     * The player should receive 0 food, but still gain 1 hunter counter.
     */
    @Test
    @DisplayName("Draw Effect (Without Bonus): Grants 0 food but increments counter")
    void testDrawEffect_WithoutFoodBonus() {
        Player player = new Player("Bob", TotemColor.BLUE);

        hunterWithoutBonus.drawEffect(player);

        assertEquals(1, player.getHunterCounter(), "Hunter counter should increment by 1 regardless of food bonus");
        assertEquals(0, player.getFood(), "Player should receive 0 food since the bonus flag is false");
    }

    /**
     * Verifies that dynamically changing the food bonus flag using the setter
     * correctly influences the logic evaluated during the next draw effect.
     */
    @Test
    @DisplayName("Setters: Modifying the bonus flag alters subsequent draw effect behavior")
    void testSetters_AlterDrawEffectBehavior() {
        Player player = new Player("Charlie", TotemColor.BLACK);

        // Start with a card that has NO bonus, then mutate it to HAVE the bonus
        hunterWithoutBonus.setFoodBonus(true);

        // Verify getter matches updated value
        assertTrue(hunterWithoutBonus.isFoodBonus(), "Getter should reflect the updated true state");

        // Apply effect and verify mutated logic is used
        hunterWithoutBonus.drawEffect(player);

        assertEquals(1, player.getHunterCounter(), "Hunter counter should increment by 1");
        assertEquals(1, player.getFood(), "Player should now receive 1 food because the flag was set to true");
    }
}