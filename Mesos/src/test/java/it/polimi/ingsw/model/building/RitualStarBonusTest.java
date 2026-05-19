package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.RitualStarBonus;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the {@link RitualStarBonus} building card.
 * <p>
 * Verifies that the specific ability of this building (granting a 3-star bonus)
 * is exclusively applied to the legitimate owner of the card and correctly
 * denies the bonus to any other player.
 */
class RitualStarBonusTest {

    private Player owner;
    private Player nonOwner;
    private RitualStarBonus ritualCard;

    /**
     * Initializes the players and the specific building card before each test.
     */
    @BeforeEach
    @DisplayName("Setup: Initialize players and the RitualStarBonus card")
    void setUp() {
        owner = new Player("Alice", TotemColor.RED);
        nonOwner = new Player("Bob", TotemColor.BLUE);

        // Initialize the card assigning 'Alice' as the legitimate owner
        ritualCard = new RitualStarBonus(301, Era.ERA_2, 2, 4, 5, owner);
    }

    /**
     * Verifies that the card correctly inherits and initializes base attributes.
     */
    @Test
    @DisplayName("Constructor: Correctly initializes base attributes")
    void testConstructorAndBaseAttributes() {
        assertEquals(301, ritualCard.getCardID(), "Card ID should match the constructor input");
        assertEquals(Era.ERA_2, ritualCard.getEra(), "Era should match the constructor input");
        assertEquals(owner, ritualCard.getOwner(), "Owner should be exactly the one assigned during creation");
    }

    /**
     * Verifies the core logic of the applyStarBonus method.
     * Ensures that only the legitimate owner receives the 3-star bonus,
     * while other players receive 0.
     */
    @Test
    @DisplayName("Star Bonus Logic: Grants 3 stars ONLY to the legitimate owner")
    void testApplyStarBonus() {
        // 1. Positive Case: The legitimate owner asks for the bonus
        int ownerBonus = ritualCard.applyStarBonus(owner);
        assertEquals(3, ownerBonus, "The legitimate owner must receive exactly 3 bonus stars");

        // 2. Negative Case: A different player (non-owner) asks for the bonus
        int nonOwnerBonus = ritualCard.applyStarBonus(nonOwner);
        assertEquals(0, nonOwnerBonus, "Any player who is not the owner must receive 0 bonus stars");

        // 3. Null Safety Case (Optional but highly recommended):
        // What happens if the system accidentally checks a null player?
        // Since (null == owner) evaluates to false, it should safely return 0 without crashing.
        int nullPlayerBonus = ritualCard.applyStarBonus(null);
        assertEquals(0, nullPlayerBonus, "Passing a null player should safely evaluate to false and return 0");
    }
}