package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.InventorIcon;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the {@link Player} entity.
 * <p>
 * Verifies the core business logic of the player, including resource management
 * (food), prestige points calculation, and card set evaluations (e.g., inventors).
 */
class PlayerTest {

    private Player player;

    /**
     * Initializes a fresh {@link Player} instance before each test.
     */
    @BeforeEach
    void setUp() {
        player = new Player("TestUser", TotemColor.RED);
    }

    // =================================================================================
    // HELPER METHODS
    // =================================================================================

    /**
     * Creates a dummy {@link BuildingCard} for testing purposes.
     * <p>
     * The returned card overrides {@link BuildingCard#applyStarBonus(Player)}
     * to provide the specified {@code bonus} without relying on the actual deck.
     *
     * @param bonus the star bonus this dummy building should grant
     * @return a mock {@link BuildingCard} instance
     */
    private BuildingCard createStarBonusBuilding(int bonus) {
        return new BuildingCard(0, null, 0, 0, 0, null) {
            @Override
            public int applyStarBonus(Player p) {
                return bonus;
            }
        };
    }

    /**
     * Creates a dummy {@link CharacterCard} for testing purposes.
     * <p>
     * The returned card overrides {@link CharacterCard#getInventorIcon()}
     * to provide the specified {@code icon}.
     *
     * @param icon the {@link InventorIcon} this dummy character should hold
     * @return a mock {@link CharacterCard} instance
     */
    private CharacterCard createInventorCharacter(InventorIcon icon) {
        return new CharacterCard(0, null, 0) {
            @Override
            public InventorIcon getInventorIcon() {
                return icon;
            }
        };
    }

    /**
     * Tests the {@link Player#changeFood(int)} method.
     * <p>
     * Verifies that positive and negative amounts are correctly applied,
     * and ensures the food count never drops below {@code 0}.
     */
    @Test
    @DisplayName("changeFood: addition, subtraction, and zero boundary")
    void testChangeFood() {
        player.changeFood(5);
        assertEquals(5, player.getFood(), "Food should be 5 after addition.");

        player.changeFood(-2);
        assertEquals(3, player.getFood(), "Food should be 3 after subtraction.");

        player.changeFood(-10);
        assertEquals(0, player.getFood(), "Food must never drop below 0.");
    }

    /**
     * Tests the {@link Player#hasEnoughFood(int)} method.
     * <p>
     * Evaluates conditions where the requested cost is lower, exactly equal,
     * or strictly greater than the current food reserve.
     */
    @Test
    @DisplayName("hasEnoughFood: checking budget for purchases")
    void testHasEnoughFood() {
        player.changeFood(5);

        assertTrue(player.hasEnoughFood(4), "Should return true if cost is lower than food.");
        assertTrue(player.hasEnoughFood(5), "Should return true if cost exactly equals food.");
        assertFalse(player.hasEnoughFood(6), "Should return false if cost exceeds food.");
    }

    /**
     * Tests the {@link Player#getStarCounter()} calculation.
     * <p>
     * Ensures that the base star counter is correctly summed with any
     * dynamic bonuses provided by the player's {@link BuildingCard}s.
     */
    @Test
    @DisplayName("getStarCounter: base stars + dynamic building bonuses")
    void testGetStarCounter() {
        player.increaseStars(2);
        assertEquals(2, player.getStarCounter(), "Without buildings, it should return only base stars.");

        player.addBuildingToHand(createStarBonusBuilding(0));
        assertEquals(2, player.getStarCounter(), "Building without bonus should not alter stars.");

        player.addBuildingToHand(createStarBonusBuilding(3));
        assertEquals(5, player.getStarCounter(), "Should sum base stars (2) with the building bonus (3).");
    }

    /**
     * Tests the {@link Player#countInventorPair()} grouping logic.
     * <p>
     * Verifies that identical {@link InventorIcon}s are correctly grouped into pairs
     * (divided by 2), ignoring incomplete pairs, un-iconed cards, and properly summing
     * multiple distinct pairs.
     */
    @Test
    @DisplayName("countInventorPair: correct counting of grouped pairs")
    void testCountInventorPair() {
        InventorIcon iconA = InventorIcon.values()[0];
        InventorIcon iconB = InventorIcon.values()[1];

        assertEquals(0, player.countInventorPair(), "Empty hand yields 0 pairs.");

        player.addCardToHand(createInventorCharacter(iconA));
        player.addCardToHand(createInventorCharacter(null));
        assertEquals(0, player.countInventorPair(), "No complete pairs exist.");

        player.addCardToHand(createInventorCharacter(iconA));
        assertEquals(1, player.countInventorPair(), "Two identical cards form 1 pair.");

        player.addCardToHand(createInventorCharacter(iconA));
        assertEquals(1, player.countInventorPair(), "Three identical cards still form only 1 pair.");

        player.addCardToHand(createInventorCharacter(iconB));
        player.addCardToHand(createInventorCharacter(iconB));
        assertEquals(2, player.countInventorPair(), "There should be 2 total pairs across different icons.");
    }

    /**
     * Tests the {@link Player#countDistinctInventorIcons()} method.
     * <p>
     * Checks that duplicate icons and {@code null} values are ignored,
     * returning the exact count of unique {@link InventorIcon}s in the player's hand.
     */
    @Test
    @DisplayName("countDistinctInventorIcons: counting unique icons")
    void testCountDistinctInventorIcons() {
        InventorIcon iconA = InventorIcon.values()[0];
        InventorIcon iconB = InventorIcon.values()[1];

        player.addCardToHand(createInventorCharacter(iconA));
        player.addCardToHand(createInventorCharacter(iconA));
        player.addCardToHand(createInventorCharacter(iconB));
        player.addCardToHand(createInventorCharacter(null));

        assertEquals(2, player.countDistinctInventorIcons(), "Should count exactly 2 distinct icons, ignoring duplicates and nulls.");
    }

    /**
     * Tests the {@link Player#addCardToHand(CharacterCard)} method.
     * <p>
     * Verifies that the card is appended to the player's hand list.
     */
    @Test
    @DisplayName("addCardToHand: adding character cards to hand")
    void testAddCardToHand() {
        CharacterCard dummyCard = createInventorCharacter(null);

        player.addCardToHand(dummyCard);

        assertEquals(1, player.getPlayerHand().size(), "Hand should contain 1 card.");
        assertTrue(player.getPlayerHand().contains(dummyCard), "Hand must contain the exact inserted card.");
    }

    /**
     * Tests the {@link Player#addBuildingToHand(BuildingCard)} method.
     * <p>
     * Verifies that the building is appended to the player's buildings list
     * and that the {@link Player#hasBuilding()} flag is toggled to {@code true}.
     */
    @Test
    @DisplayName("addBuildingToHand: inserting buildings and updating flag")
    void testAddBuildingToHand() {
        BuildingCard dummyBuilding = createStarBonusBuilding(0);

        assertFalse(player.hasBuilding(), "Player starts with no buildings.");

        player.addBuildingToHand(dummyBuilding);

        assertEquals(1, player.getPlayerBuildings().size(), "Buildings list should contain 1 element.");
        assertTrue(player.getPlayerBuildings().contains(dummyBuilding), "List must contain the inserted building.");
        assertTrue(player.hasBuilding(), "The hasBuilding flag must become true after adding a building.");
    }
}