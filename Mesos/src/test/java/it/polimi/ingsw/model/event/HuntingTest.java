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

class HuntingTest {

    private Hunting hunting;

    @BeforeEach
    void setUp() {
        hunting = new Hunting(1, Era.ERA_1, 2, 2);
    }


    // Crea un edificio finto per i test. Il parametro 'hasBonus' decide
    // se questo edificio fornisce o meno il bonus di caccia.
    private BuildingCard createHelperBuilding(boolean hasBonus) {
        return new BuildingCard(0, Era.ERA_1, 0, 0, 0, null) {
            @Override
            public boolean hasHuntingBonus(Player p) {
                return hasBonus;
            }
        };
    }

    @Test
    @DisplayName("Player with 0 hunters gains no points and no food")
    void testApplyEvent_NoHunters() {
        Player p1 = new Player("George", null);
        //HunterCouter is = 0 by default
        hunting.applyEvent(List.of(p1));

        assertEquals(0, p1.getPrestigePoints(), "Should have 0 PP");
        assertEquals(0, p1.getFood(), "Should have 0 food");
    }

    @Test
    @DisplayName("Player with hunters but no buildings gets standard points and food")
    void testApplyEvent_WithHunters_NoBuildings() {

        Player p1 = new Player("Pierino", null);
        p1.increaseHunterCounter(3);

        hunting.applyEvent(List.of(p1));

        assertEquals(6, p1.getPrestigePoints());
        assertEquals(3, p1.getFood());
    }

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