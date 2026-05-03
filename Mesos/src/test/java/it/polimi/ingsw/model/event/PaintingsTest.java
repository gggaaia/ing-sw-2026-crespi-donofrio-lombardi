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

class PaintingsTest {

    private Paintings paintings;

    @BeforeEach
    @DisplayName("Setup: Initialize Paintings event before each test")
    void setUp() {
        // Initialize the event with minPainters = 2 and bonusPoints = 3
        paintings = new Paintings(1, Era.ERA_1, 2, 2, 3);
    }

    // Helper method to create a helper building that either provides the painting bonus or doesn't
    private BuildingCard createHelperBuilding(boolean hasBonus) {
        return new BuildingCard(0, Era.ERA_1, 0, 0, 0, null) {
            @Override
            public boolean hasPaintingBonus(Player p) {
                return hasBonus;
            }
        };
    }

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