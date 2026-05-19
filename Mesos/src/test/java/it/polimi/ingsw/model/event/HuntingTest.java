package it.polimi.ingsw.model.event;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.event.Hunting;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link Hunting} event card.
 * <p>
 * Verifies the correct distribution of Prestige Points (PP) and Food
 * when the Hunting event is resolved. The tests cover scenarios with
 * different amounts of hunters and ensure that the presence of specific
 * bonus-providing buildings correctly alters (e.g., doubles) the rewards.
 */
class HuntingTest {

    private Hunting hunting;

    @BeforeEach
    void setUp() {
        hunting = new Hunting(1, Era.ERA_1, 2, 2);
    }

    /**
     * Helper method to create a mock building for testing purposes.
     * * @param hasBonus determines whether this mock building should provide
     * the special hunting bonus to the player.
     * @return a dummy {@link BuildingCard} with the specified bonus behavior.
     */
    private BuildingCard createHelperBuilding(boolean hasBonus) {
        return new BuildingCard(0, Era.ERA_1, 0, 0, 0, null) {
            @Override
            public boolean hasHuntingBonus(Player p) {
                return hasBonus;
            }
        };
    }

    /**
     * Verifies that a player with exactly zero hunters does not receive
     * any Prestige Points or Food when the event is applied.
     */
    @Test
    @DisplayName("Player with 0 hunters gains no points and no food")
    void testApplyEvent_NoHunters() {
        Player p1 = new Player("George", null);
        //HunterCouter is = 0 by default
        hunting.applyEvent(List.of(p1));

        assertEquals(0, p1.getPrestigePoints(), "Should have 0 PP");
        assertEquals(0, p1.getFood(), "Should have 0 food");
    }

    /**
     * Verifies the standard reward calculation for a player who has hunters
     * but owns no buildings. The player should receive the base amount of
     * Prestige Points and Food multiplied by their hunter counter.
     */
    @Test
    @DisplayName("Player with hunters but no buildings gets standard points and food")
    void testApplyEvent_WithHunters_NoBuildings() {

        Player p1 = new Player("Pierino", null);
        p1.increaseHunterCounter(3);

        hunting.applyEvent(List.of(p1));

        assertEquals(6, p1.getPrestigePoints());
        assertEquals(3, p1.getFood());
    }

    /**
     * Verifies that owning a regular building (without the specific hunting bonus)
     * does not alter the standard reward calculation for the hunters.
     */
    @Test
    @DisplayName("Player with a building without bonus gets standard rewards")
    void testApplyEvent_WithHunters_BuildingNoBonus() {
        Player p1 = new Player("Mark", null);
        p1.increaseHunterCounter(2); // has 2 hunters

        p1.addBuildingToHand(createHelperBuilding(false));

        hunting.applyEvent(List.of(p1));

        assertEquals(4, p1.getPrestigePoints());
        assertEquals(2, p1.getFood());
    }

    /**
     * Verifies the application of the building bonus. A player owning a building
     * that provides the hunting bonus should receive doubled rewards (both
     * Prestige Points and Food) based on their hunter counter.
     */
    @Test
    @DisplayName("Player with a bonus building gets doubled rewards")
    void testApplyEvent_WithHunters_BuildingWithBonus() {
        Player p1 = new Player("Dave", null);
        p1.increaseHunterCounter(3); // 3 hunters

        p1.addBuildingToHand(createHelperBuilding(true));

        hunting.applyEvent(List.of(p1));

        assertEquals(12, p1.getPrestigePoints());
        assertEquals(6, p1.getFood());
    }

    /**
     * Verifies that the event correctly applies to a list of multiple players,
     * accurately computing and assigning independent rewards for each player
     * based on their unique state (hunter count and building bonuses).
     */
    @Test
    @DisplayName("Event applies correctly to a list of multiple players")
    void testApplyEvent_MultiplePlayers() {
        // 1. ARRANGE
        Player p1 = new Player("P1", null);
        p1.increaseHunterCounter(3); // Without building bonus (Food=3, PP=6)

        Player p2 = new Player("P2", null);
        p2.increaseHunterCounter(2);
        p2.addBuildingToHand(createHelperBuilding(true)); // With bonus (Food=4, PP=8)

        hunting.applyEvent(Arrays.asList(p1, p2));

        assertEquals(6, p1.getPrestigePoints());
        assertEquals(3, p1.getFood());

        assertEquals(8, p2.getPrestigePoints());
        assertEquals(4, p2.getFood());
    }
}