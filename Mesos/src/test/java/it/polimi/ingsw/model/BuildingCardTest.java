package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the {@link BuildingCard} base entity.
 * <p>
 * Verifies that the base state (cost, PP, owner) is correctly initialized and mutable.
 * More importantly, it acts as a regression safety net to ensure that all default
 * hook methods (which are meant to be overridden by specific buildings) continue
 * to return their strict default values (e.g., 0, 1, false) and do not crash.
 */
class BuildingCardTest {

    private BuildingCard buildingCard;
    private Player owner;

    /**
     * Initializes a standard Player and a generic BuildingCard before each test
     * to guarantee an isolated testing environment.
     */
    @BeforeEach
    @DisplayName("Setup: Initialize a generic BuildingCard and its Owner")
    void setUp() {
        owner = new Player("Alice", TotemColor.RED);
        // Initialize with cardID=101, Era=ERA_1, minPlayers=2, foodCost=3, endOfGamePP=5
        buildingCard = new BuildingCard(101, Era.ERA_1, 2, 3, 5, owner);
    }

    /**
     * Verifies that the constructor correctly assigns all parameters to the
     * internal state and that getters retrieve them accurately.
     */
    @Test
    @DisplayName("Constructor & Getters: Correctly initialize state")
    void testConstructorAndGetters() {
        assertEquals(101, buildingCard.getCardID(), "Card ID should match constructor input");
        assertEquals(Era.ERA_1, buildingCard.getEra(), "Era should match constructor input");
        assertEquals(2, buildingCard.getMinPlayers(), "Min players should match constructor input");

        assertEquals(3, buildingCard.getFoodCost(), "Food cost should initially be 3");
        assertEquals(5, buildingCard.getEndOfGamePP(), "End of game PP should initially be 5");
        assertEquals(owner, buildingCard.getOwner(), "Owner should match the one passed in constructor");
    }

    /**
     * Verifies that the setter methods correctly mutate the card's internal state.
     */
    @Test
    @DisplayName("Setters: Correctly modify card attributes")
    void testSetters() {
        Player newOwner = new Player("Bob", TotemColor.BLUE);

        buildingCard.setFoodCost(1);
        buildingCard.setEndOfGamePP(10);
        buildingCard.setOwner(newOwner);

        assertEquals(1, buildingCard.getFoodCost(), "Food cost should update to 1");
        assertEquals(10, buildingCard.getEndOfGamePP(), "End of game PP should update to 10");
        assertEquals(newOwner, buildingCard.getOwner(), "Owner should update to Bob");
    }

    /**
     * Verifies that the default hook methods return their expected, hardcoded
     * neutral values. This ensures that subclasses relying on these defaults
     * do not experience unexpected behavior if this base class is accidentally altered.
     */
    @Test
    @DisplayName("Default Hooks: Return standard neutral values")
    void testDefaultHookMethods() {
        // Test methods returning integers
        assertEquals(0, buildingCard.applyStarBonus(owner), "Default star bonus must be 0");
        assertEquals(1, buildingCard.checkRitualSafe(owner), "Default ritual safe check must be 1");
        assertEquals(1, buildingCard.checkDoubleStarRitual(owner), "Default double star ritual must be 1");
        assertEquals(0, buildingCard.sustenanceBonus(owner), "Default sustenance bonus must be 0");

        // Test methods returning booleans
        assertFalse(buildingCard.hasPaintingBonus(owner), "Default painting bonus must be false");
        assertFalse(buildingCard.hasHuntingBonus(owner), "Default hunting bonus must be false");
        assertFalse(buildingCard.hasUpperRowBuilding(owner), "Default upper row building check must be false");

        // Test void methods to ensure they execute cleanly without throwing exceptions
        assertDoesNotThrow(() -> buildingCard.applyEndEffect(owner), "applyEndEffect should safely do nothing");
        assertDoesNotThrow(() -> buildingCard.applyEndRoundEffect(owner), "applyEndRoundEffect should safely do nothing");
        assertDoesNotThrow(() -> buildingCard.turnOrderBonus(owner), "turnOrderBonus should safely do nothing");
    }

    /**
     * Verifies the interaction with the CardRow.
     * Ensures that calling addToRow successfully dispatches and adds the
     * building card to the row's internal list, utilizing the defensive copy getter.
     */
    @Test
    @DisplayName("addToRow: Dispatches itself to the CardRow")
    void testAddToRow() {
        CardRow row = new CardRow();

        // Ensure it's empty at the start
        assertTrue(row.getBuildingCards().isEmpty(), "CardRow should start empty");

        // Execute the dispatch method
        buildingCard.addToRow(row);

        // Verify the interaction worked perfectly using the getter
        List<BuildingCard> updatedList = row.getBuildingCards();
        assertEquals(1, updatedList.size(), "CardRow should contain exactly 1 card");
        assertTrue(updatedList.contains(buildingCard), "The building card should be successfully added to the row's internal list");
    }
}