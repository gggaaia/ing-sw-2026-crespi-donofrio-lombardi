package it.polimi.ingsw.model.event;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.event.Paintings;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link Paintings} event card.
 * <p>
 * Verifies the correct distribution of penalties and rewards (Prestige Points
 * and Food) when the Paintings event is resolved. The tests cover scenarios
 * based on the minimum painter requirement, the calculation of bonus points,
 * and the specific food bonuses granted by owning certain buildings.
 */
class PaintingsTest {

    private Paintings paintings;

    @BeforeEach
    @DisplayName("Setup: Initialize Paintings event before each test")
    void setUp() {
        // Initialize the event with minPainters = 2 and bonusPoints = 3
        paintings = new Paintings(1, Era.ERA_1, 2, 2, 3);
    }

    /**
     * Helper method to create a mock building for testing purposes.
     * * @param hasBonus determines whether this mock building should provide
     * the special painting bonus to the player.
     * @return a dummy {@link BuildingCard} with the specified bonus behavior.
     */
    private BuildingCard createHelperBuilding(boolean hasBonus) {
        return new BuildingCard(0, Era.ERA_1, 0, 0, 0, null) {
            @Override
            public boolean hasPaintingBonus(Player p) {
                return hasBonus;
            }
        };
    }

    /**
     * Verifies that a player who fails to meet the minimum painter requirement
     * receives a fixed penalty in Prestige Points (-2 PP) and gains no Food.
     */
    @Test
    @DisplayName("Player with fewer painters than required loses 2 PP")
    void testApplyEvent_NotEnoughPainters() {
        Player p1 = new Player("Alice", null);
        p1.increasePainterCounter(1); // 1 painter is less than the required 2

        paintings.applyEvent(List.of(p1));

        // The player fails the requirement and receives a fixed penalty of -2 PP
        assertEquals(-2, p1.getPrestigePoints());
        assertEquals(0, p1.getFood());
    }

    /**
     * Verifies the standard reward calculation for a player who meets or exceeds
     * the minimum painter requirement. The player should receive Prestige Points
     * equal to their painter count multiplied by the event's bonus modifier.
     */
    @Test
    @DisplayName("Player meeting the painter requirement gets bonus points per painter")
    void testApplyEvent_EnoughPainters() {
        Player p1 = new Player("Bob", null);
        p1.increasePainterCounter(3); // 3 painters meet the requirement of 2

        paintings.applyEvent(List.of(p1));

        // The player gets: 3 painters * 3 bonusPoints = 9 PP
        assertEquals(9, p1.getPrestigePoints());
        assertEquals(0, p1.getFood());
    }

    /**
     * Verifies that owning a regular building (without the specific painting bonus)
     * does not grant any extra Food resources, while the normal Prestige Point
     * rewards for painters remain unaffected.
     */
    @Test
    @DisplayName("Buildings without the painting bonus provide 0 food")
    void testApplyEvent_WithBuildings_NoBonus() {
        Player p1 = new Player("Charlie", null);
        p1.increasePainterCounter(2);

        // Add a building that does NOT have the painting bonus
        p1.addBuildingToHand(createHelperBuilding(false));

        paintings.applyEvent(List.of(p1));

        // The player gets the normal PP from painters (2 * 3 = 6 PP), but no food from the building
        assertEquals(6, p1.getPrestigePoints());
        assertEquals(0, p1.getFood());
    }

    /**
     * Verifies the application of the building bonus. A player owning a building
     * that provides the painting bonus should receive Food equal to their total
     * number of painters, in addition to the standard Prestige Points.
     */
    @Test
    @DisplayName("Buildings with the painting bonus provide food equal to the painter count")
    void testApplyEvent_WithBuildings_WithBonus() {
        Player p1 = new Player("Dave", null);
        p1.increasePainterCounter(3);

        // Add a building that provides the painting bonus
        p1.addBuildingToHand(createHelperBuilding(true));

        paintings.applyEvent(List.of(p1));

        // The player gets 9 PP (3 painters * 3 points)
        // Since the building has the bonus, the player gains food equal to the painter count (3)
        assertEquals(9, p1.getPrestigePoints());
        assertEquals(3, p1.getFood());
    }

    /**
     * Verifies that the event correctly applies to a list of multiple players,
     * accurately computing and assigning independent penalties and rewards
     * for each player based on their unique state (painter count and buildings).
     */
    @Test
    @DisplayName("Event applies correctly across a list of mixed players")
    void testApplyEvent_MultiplePlayers() {
        Player p1 = new Player("P1", null);
        p1.increasePainterCounter(1); // Fails requirement (-2 PP), no buildings

        Player p2 = new Player("P2", null);
        p2.increasePainterCounter(2); // Meets requirement (6 PP), has bonus building (+2 Food)
        p2.addBuildingToHand(createHelperBuilding(true));

        paintings.applyEvent(Arrays.asList(p1, p2));

        // Check P1
        assertEquals(-2, p1.getPrestigePoints());
        assertEquals(0, p1.getFood());

        // Check P2
        assertEquals(6, p2.getPrestigePoints());
        assertEquals(2, p2.getFood());
    }
}