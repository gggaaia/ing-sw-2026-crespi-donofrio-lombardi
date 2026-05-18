package it.polimi.ingsw.model.character;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.character.Builder;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link Builder} character card.
 * <p>
 * Verifies that drawing a Builder card correctly modifies the player's state
 * by updating their builder profession counter, building discounts, and
 * end-of-game prestige point bonuses. Also ensures that setters dynamically
 * update the card's inner attributes affecting subsequent effects.
 */
class BuilderTest {

    private Builder builderCard;

    @BeforeEach
    @DisplayName("Setup: Initialize a Builder card before each test")
    void setUp() {
        // Initialize with cardID=1, Era=ERA_1, minPlayers=2, buildingCostRedux=2, prestigePoint=3
        builderCard = new Builder(1, Era.ERA_1, 2, 2, 3);
    }

    /**
     * Verifies that executing the draw effect on a player correctly increments
     * their builder profession counter by exactly 1, and accurately transmits
     * the specific prestige point bonus and building discount to the player's state.
     */
    @Test
    @DisplayName("Draw Effect: Correctly increments player counters and applies bonuses")
    void testDrawEffect_AppliesCorrectModifiers() {
        Player player = new Player("Bob", TotemColor.BLUE);

        builderCard.drawEffect(player);

        assertEquals(1, player.getBuilderCounter(), "Builder counter should increment by 1");
        assertEquals(3, player.getPPBuilderBonus(), "Prestige Point builder bonus should match the card value");
        assertEquals(2, player.getBuildingDiscountBuilder(), "Building discount should match the card value");
    }

    /**
     * Verifies that mutating the card's attributes using the provided setters
     * immediately alters the behavior of the draw effect when applied subsequently
     * to a player.
     */
    @Test
    @DisplayName("Setters: Modifying attributes alters subsequent draw effect rewards")
    void testSetters_AlterDrawEffectBehavior() {
        Player player = new Player("Alice", TotemColor.RED);

        // Mutate the card values
        builderCard.setBuildingCostRedux(5);
        builderCard.setPrestigePoint(4);

        // Verify getters match updated values
        assertEquals(5, builderCard.getBuildingCostRedux());
        assertEquals(4, builderCard.getPrestigePoint());

        // Apply effect and verify mutated values are used
        builderCard.drawEffect(player);

        assertEquals(1, player.getBuilderCounter());
        assertEquals(4, player.getPPBuilderBonus());
        assertEquals(5, player.getBuildingDiscountBuilder());
    }
}