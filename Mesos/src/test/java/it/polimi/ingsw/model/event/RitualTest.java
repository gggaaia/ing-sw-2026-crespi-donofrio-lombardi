package it.polimi.ingsw.model.event;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.event.Ritual;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link Ritual} event card.
 * <p>
 * Verifies the correct distribution of Prestige Points based on the players'
 * star rankings (minimum and maximum stars). The tests cover standard
 * standings, tie scenarios (where players are simultaneously max and min),
 * and the application of specific building modifiers that can double the
 * reward or nullify the penalty.
 */
class RitualTest {

    private Ritual ritual;

    @BeforeEach
    @DisplayName("Setup: Initialize Ritual event before each test")
    void setUp() {
        // Initialize the event with a base bonus of 5 points and a malus of 3 points
        ritual = new Ritual(1, Era.ERA_1, 2, false, 5, 3);
    }

    /**
     * Helper method to create a mock building with custom multipliers for testing purposes.
     * * @param ritualMultiplier the multiplier applied to the winning bonus (e.g., 2 to double it).
     * @param safeMultiplier the multiplier applied to the losing malus (e.g., 0 to nullify it).
     * @return a dummy {@link BuildingCard} with the specified multiplier behaviors.
     */
    private BuildingCard createHelperBuilding(int ritualMultiplier, int safeMultiplier) {
        return new BuildingCard(0, Era.ERA_1, 0, 0, 0, null) {
            @Override
            public int checkDoubleStarRitual(Player p) {
                return ritualMultiplier;
            }

            @Override
            public int checkRitualSafe(Player p) {
                return safeMultiplier;
            }
        };
    }

    /**
     * Verifies the edge case where all players have the exact same number of stars.
     * In this tie scenario, all players are considered both the "maximum" and
     * the "minimum", thus receiving both the bonus and the malus simultaneously.
     */
    @Test
    @DisplayName("All players tied (max == min): Everyone gets both bonus and malus")
    void testApplyEvent_AllPlayersTied_NoBuildings() {
        Player p1 = new Player("Alice", null);
        p1.increaseStars(2);

        Player p2 = new Player("Bob", null);
        p2.increaseStars(2);

        ritual.applyEvent(Arrays.asList(p1, p2));

        // Since all players are tied, they receive both the bonus (+5) and the malus (-3), netting +2 PP
        assertEquals(2, p1.getPrestigePoints(), "Alice should get net 2 PP");
        assertEquals(2, p2.getPrestigePoints(), "Bob should get net 2 PP");
    }

    /**
     * Verifies that building modifiers are applied correctly even when players
     * are tied for both first and last place. Each player should process their
     * respective building modifiers independently on the shared base values.
     */
    @Test
    @DisplayName("All players tied: Building modifiers are applied correctly")
    void testApplyEvent_AllPlayersTied_WithBuildings() {
        Player p1 = new Player("Charlie", null);
        p1.increaseStars(3);
        // Building doubles the bonus but provides no protection against the malus
        p1.addBuildingToHand(createHelperBuilding(2, 1));

        Player p2 = new Player("Dave", null);
        p2.increaseStars(3);
        // Building provides protection against the malus (0x multiplier) but doesn't double the bonus
        p2.addBuildingToHand(createHelperBuilding(1, 0));

        ritual.applyEvent(Arrays.asList(p1, p2));

        // Charlie gets (5 * 2) - (3 * 1) = 7 PP
        assertEquals(7, p1.getPrestigePoints());

        // Dave gets (5 * 1) - (3 * 0) = 5 PP
        assertEquals(5, p2.getPrestigePoints());
    }

    /**
     * Verifies the standard behavior of the Ritual event with distinct rankings.
     * The player with the most stars receives the bonus, the player with the
     * least stars receives the malus, and players in the middle remain unaffected.
     */
    @Test
    @DisplayName("Distinct standings: Max gets bonus, Min gets malus, middle is safe")
    void testApplyEvent_DistinctStandings_NoBuildings() {
        Player maxPlayer = new Player("First", null);
        maxPlayer.increaseStars(5);

        Player midPlayer = new Player("Middle", null);
        midPlayer.increaseStars(3);

        Player minPlayer = new Player("Last", null);
        minPlayer.increaseStars(1);

        ritual.applyEvent(Arrays.asList(maxPlayer, midPlayer, minPlayer));

        // The player with the most stars gains the bonus points
        assertEquals(5, maxPlayer.getPrestigePoints());

        // The player in the middle is ignored and stays at 0 points
        assertEquals(0, midPlayer.getPrestigePoints());

        // The player with the least stars suffers the malus points
        assertEquals(-3, minPlayer.getPrestigePoints());
    }

    /**
     * Verifies that building modifiers work correctly for players with distinct rankings.
     * The top player should be able to double their bonus, and the bottom player
     * should be able to nullify their penalty using the appropriate buildings.
     */
    @Test
    @DisplayName("Distinct standings: Building modifiers work for winners and losers")
    void testApplyEvent_DistinctStandings_WithBuildings() {
        Player maxPlayer = new Player("First", null);
        maxPlayer.increaseStars(6);
        // Building doubles the winning bonus
        maxPlayer.addBuildingToHand(createHelperBuilding(2, 1));

        Player minPlayer = new Player("Last", null);
        minPlayer.increaseStars(0);
        // Building nullifies the losing malus
        minPlayer.addBuildingToHand(createHelperBuilding(1, 0));

        ritual.applyEvent(Arrays.asList(maxPlayer, minPlayer));

        // Max player gets double the points: 5 * 2 = 10
        assertEquals(10, maxPlayer.getPrestigePoints());

        // Min player would lose 3 points, but the building modifier nullifies the penalty: -3 * 0 = 0
        assertEquals(0, minPlayer.getPrestigePoints());
    }
}