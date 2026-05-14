package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.InventorIcon;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the {@link Player} entity.
 * <p>
 * Verifies the core mechanics of a player's state during the match.
 * Focuses on critical business logic such as resource management (food limits),
 * dynamic scoring (star bonuses from buildings), inventory safety (defensive copies
 * of card hands), and complex icon counting algorithms for inventors.
 */
class PlayerTest {

    private Player player;

    @BeforeEach
    @DisplayName("Setup: Initialize a fresh Player before each test")
    void setUp() {
        player = new Player("TestPlayer", TotemColor.RED);
    }

    /**
     * Verifies the food management logic. Ensures that adding and removing food
     * works correctly, that the food counter never drops below zero (clamping),
     * and that the check for sufficient food accurately reflects the balance.
     */
    @Test
    @DisplayName("Food Logic: Proper addition, subtraction clamping, and cost verification")
    void testFoodManagement() {
        // Initial state
        assertEquals(0, player.getFood());
        assertFalse(player.hasEnoughFood(1));

        // Add food
        player.changeFood(5);
        assertEquals(5, player.getFood());
        assertTrue(player.hasEnoughFood(5));
        assertTrue(player.hasEnoughFood(3));
        assertFalse(player.hasEnoughFood(6));

        // Subtract food (normal)
        player.changeFood(-2);
        assertEquals(3, player.getFood());

        // Subtract food below zero (clamping)
        player.changeFood(-10);
        assertEquals(0, player.getFood(), "Food should clamp to 0 and never be negative.");
    }

    /**
     * Verifies that adding cards updates the player's internal state (such as the
     * hasBuilding flag) and that the getter methods return defensive copies,
     * protecting the player's internal lists from external unauthorized modifications.
     */
    @Test
    @DisplayName("Card Inventory: State updates and defensive copies protection")
    void testAddCardsAndDefensiveCopies() {
        assertFalse(player.hasBuilding(), "Player should initially have no buildings.");

        CharacterCard mockChar = createDummyCharacter(null);
        BuildingCard mockBuild = createDummyBuilding(0);

        player.addCardToHand(mockChar);
        player.addBuildingToHand(mockBuild);

        assertTrue(player.hasBuilding(), "Flag should update to true after adding a building.");

        List<CharacterCard> hand = player.getPlayerHand();
        List<BuildingCard> buildings = player.getPlayerBuildings();

        assertEquals(1, hand.size());
        assertEquals(1, buildings.size());

        // Test defensive copying: modifying the fetched list shouldn't affect the Player
        hand.clear();
        buildings.clear();

        assertEquals(1, player.getPlayerHand().size(), "Internal hand should be protected by defensive copy.");
        assertEquals(1, player.getPlayerBuildings().size(), "Internal buildings should be protected by defensive copy.");
    }

    /**
     * Verifies the dynamic calculation of Star points. Ensures that the player's
     * total stars are the sum of their internal counter PLUS any bonus stars
     * granted by special building cards in their possession.
     */
    @Test
    @DisplayName("Star Calculation: Base stars plus dynamic building bonuses")
    void testGetStarCounterWithBuildingBonus() {
        player.increaseStars(3);

        // Add a building that grants 0 bonus stars
        player.addBuildingToHand(createDummyBuilding(0));
        assertEquals(3, player.getStarCounter(), "Stars should be 3 (3 base + 0 bonus).");

        // Add a building that grants 2 bonus stars
        player.addBuildingToHand(createDummyBuilding(2));
        assertEquals(5, player.getStarCounter(), "Stars should be 5 (3 base + 2 bonus).");
    }

    /**
     * Verifies the algorithms that count Inventor Icons. Ensures that the system
     * correctly identifies distinct icon types and groups identical icons to form pairs.
     */
    @Test
    @DisplayName("Inventor Logic: Correctly counts distinct icons and matching pairs")
    void testInventorCalculations() {
        // Safe check in case the Enum has no values in the current build environment
        if (InventorIcon.values().length < 2) return;

        InventorIcon iconA = InventorIcon.values()[0];
        InventorIcon iconB = InventorIcon.values()[1];

        // Add 3 cards of Icon A, 2 cards of Icon B, and 1 card with no icon
        player.addCardToHand(createDummyCharacter(iconA));
        player.addCardToHand(createDummyCharacter(iconA));
        player.addCardToHand(createDummyCharacter(iconA));

        player.addCardToHand(createDummyCharacter(iconB));
        player.addCardToHand(createDummyCharacter(iconB));

        player.addCardToHand(createDummyCharacter(null));

        // Distinct check: We have Icon A and Icon B (null is ignored)
        assertEquals(2, player.countDistinctInventorIcons(), "Should find exactly 2 distinct inventor icons.");

        // Pair check: 3 Icon A = 1 pair. 2 Icon B = 1 pair. Total = 2 pairs.
        assertEquals(2, player.countInventorPair(), "Should find exactly 2 pairs of inventor icons.");
    }

    /**
     * Helper method to create a dummy character card with a specific Inventor Icon.
     */
    private CharacterCard createDummyCharacter(InventorIcon icon) {
        return new CharacterCard(0, Era.ERA_1, 0) {
            @Override
            public InventorIcon getInventorIcon() {
                return icon;
            }
        };
    }

    /**
     * Helper method to create a dummy building card that grants a specific star bonus.
     */
    private BuildingCard createDummyBuilding(int starBonus) {
        return new BuildingCard(0, Era.ERA_1, 0, 0, 0, null) {
            @Override
            public int applyStarBonus(Player p) {
                return starBonus;
            }
        };
    }
}