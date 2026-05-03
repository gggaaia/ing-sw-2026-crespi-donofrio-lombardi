package it.polimi.ingsw.model.event;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.event.Sustenance;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SustenanceTest {

    private Sustenance sustenance;

    @BeforeEach
    @DisplayName("Setup: Initialize Sustenance event before each test")
    void setUp() {
        sustenance = new Sustenance(1, Era.ERA_1, 2, false, 3);
    }

    @Test
    @DisplayName("Player with sufficient food pays without discounts")
    void testApplyEvent_SufficientFood_NoDiscounts() {
        Player p1 = new Player("Alice", null);
        p1.changeFood(10);

        for (int i = 0; i < 5; i++) {
            p1.addCardToHand(null);
        }

        sustenance.applyEvent(List.of(p1));

        assertEquals(5, p1.getFood(), "Food should decrease by 5");
        assertEquals(0, p1.getPrestigePoints(), "Should not lose any Prestige Points (PP)");
    }

    @Test
    @DisplayName("Player successfully uses Gatherer and Building discounts")
    void testApplyEvent_WithGathererAndBuildingDiscounts() {
        Player p1 = new Player("Bob", null);
        p1.changeFood(10);

        for (int i = 0; i < 10; i++) {
            p1.addCardToHand(null);
        }

        p1.increaseGathererCounter(1);

        BuildingCard helperBuilding = new BuildingCard(0, Era.ERA_1, 0, 0, 0, null) {
            @Override
            public int sustenanceBonus(Player p) {
                return 1;
            }
        };
        p1.addBuildingToHand(helperBuilding);

        sustenance.applyEvent(List.of(p1));

        assertEquals(6, p1.getFood(), "Should spend 4 food (10-4=6)");
        assertEquals(0, p1.getPrestigePoints());
    }

    @Test
    @DisplayName("Cost never drops below zero when discounts exceed hand size")
    void testApplyEvent_CostIsZeroOrNegative() {
        Player p1 = new Player("Charlie", null);
        p1.changeFood(5);

        p1.addCardToHand(null);
        p1.addCardToHand(null);

        p1.increaseGathererCounter(1);

        sustenance.applyEvent(List.of(p1));

        assertEquals(5, p1.getFood(), "Cost is negative, clamped to 0. Food remains 5");
    }

    @Test
    @DisplayName("Player with insufficient food suffers Prestige Points malus")
    void testApplyEvent_InsufficientFood_AppliesMalus() {
        Player p1 = new Player("Dave", null);
        p1.changeFood(2);

        for (int i = 0; i < 5; i++) {
            p1.addCardToHand(null);
        }

        sustenance.applyEvent(List.of(p1));

        assertEquals(0, p1.getFood(), "Food is fully consumed and drops to 0");
        assertEquals(-9, p1.getPrestigePoints(), "Should lose 9 PP for the 3 missing food");
    }

    @Test
    @DisplayName("Event applies correctly to a list of multiple players")
    void testApplyEvent_MultiplePlayers() {
        Player p1 = new Player("P1", null);
        p1.changeFood(10);
        for (int i = 0; i < 5; i++) p1.addCardToHand(null);

        Player p2 = new Player("P2", null);
        p2.changeFood(1);
        for (int i = 0; i < 4; i++) p2.addCardToHand(null);

        sustenance.applyEvent(Arrays.asList(p1, p2));

        // P1 pays 5 food normally
        assertEquals(5, p1.getFood());
        assertEquals(0, p1.getPrestigePoints());

        // P2 must pay 4, but only has 1 food. Missing 3 food results in 9 PP lost.
        assertEquals(0, p2.getFood());
        assertEquals(-9, p2.getPrestigePoints());
    }
}