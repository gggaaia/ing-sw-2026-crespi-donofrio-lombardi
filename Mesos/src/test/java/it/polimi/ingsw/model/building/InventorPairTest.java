package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.InventorPair;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link InventorPair} building card.
 * <p>
 * Verifies the end-of-round effect associated with this card, which rewards
 * the owner with food based on the number of new inventor pairs they have
 * collected. The tests ensure that only the rightful owner receives the
 * reward, and that pairs scored in previous rounds are correctly tracked
 * and excluded from subsequent payouts.
 */
class InventorPairTest {

    /**
     * Helper method to create a custom Player instance for testing.
     * <p>
     * Overrides the default behavior to explicitly control the total number
     * of inventor pairs the player possesses, allowing for isolated testing
     * of the reward logic without managing the complexities of a full card hand.
     *
     * @param name                 the nickname of the dummy player
     * @param totalPairs           the simulated total number of inventor pairs the player has
     * @param alreadyRecordedPairs the simulated number of pairs already scored in previous rounds
     * @return a mock {@link Player} configured for specific test scenarios
     */
    private Player createHelperPlayer(String name, int totalPairs, int alreadyRecordedPairs) {
        Player p = new Player(name, null) {
            @Override
            public int countInventorPair() {
                return totalPairs;
            }
        };

        // Simulate pairs that were already processed in previous rounds
        if (alreadyRecordedPairs > 0) {
            p.increaseInventorPairs(alreadyRecordedPairs);
        }

        return p;
    }

    /**
     * Verifies the ownership check. Ensures that if the effect is triggered
     * on a player who is not the designated owner of the card, no action is
     * taken, and neither food nor tracked pairs are modified.
     */
    @Test
    @DisplayName("Effect does not trigger if the player is not the card owner")
    void testApplyEndRoundEffect_NotOwner() {
        Player actualOwner = new Player("Owner", null);
        // Initialized with values: 3 food cost, 4 end-of-game PP
        InventorPair card = new InventorPair(1, Era.ERA_1, 2, 3, 4, actualOwner);

        Player nonOwner = createHelperPlayer("Alice", 1, 0);

        card.applyEndRoundEffect(nonOwner);

        // Since the player is not the owner, they receive no food and recorded pairs remain unchanged
        assertEquals(0, nonOwner.getFood());
        assertEquals(0, nonOwner.getInventorPairs());
    }

    /**
     * Verifies the standard reward calculation for the card owner. Ensures
     * that the owner receives the correct amount of food
     * and that these newly discovered pairs are properly registered to
     * prevent double-scoring in the future.
     */
    @Test
    @DisplayName("Owner gets 3 food per new pair and records them")
    void testApplyEndRoundEffect_Owner_NewPairs() {
        Player owner = createHelperPlayer("Owner", 2, 0);
        InventorPair card = new InventorPair(1, Era.ERA_1, 2, 3, 4, owner);

        card.applyEndRoundEffect(owner);

        // Reward: 2 new pairs * 3 food = 6 food
        assertEquals(6, owner.getFood());

        // The pairs are now recorded so they aren't scored again in future rounds
        assertEquals(2, owner.getInventorPairs());
    }

    /**
     * Verifies the tracking mechanism for existing pairs. If a player triggers
     * the effect but has not acquired any new pairs since the last activation,
     * they should receive zero food and their tracked pair count must remain stable.
     */
    @Test
    @DisplayName("Owner ignores already recorded pairs from previous rounds")
    void testApplyEndRoundEffect_Owner_AlreadyRecordedPairs() {
        Player owner = createHelperPlayer("Owner", 2, 2);
        InventorPair card = new InventorPair(1, Era.ERA_1, 2, 3, 4, owner);

        card.applyEndRoundEffect(owner);

        // No new pairs found, so no new food is given
        assertEquals(0, owner.getFood());

        // Recorded pairs remain exactly the same
        assertEquals(2, owner.getInventorPairs());
    }

    /**
     * Verifies the differential reward calculation. If the owner has a mix
     * of previously recorded pairs and newly acquired pairs, they must only
     * be rewarded for the new ones, while the total tracking count is
     * subsequently updated.
     */
    @Test
    @DisplayName("Owner gets food ONLY for newly formed pairs, ignoring old ones")
    void testApplyEndRoundEffect_Owner_MixedPairs() {
        Player owner = createHelperPlayer("Owner", 3, 1);
        InventorPair card = new InventorPair(1, Era.ERA_1, 2, 3, 4, owner);

        card.applyEndRoundEffect(owner);

        // Calculation: 3 total - 1 old = 2 new pairs. Reward: 2 * 3 = 6 food.
        assertEquals(6, owner.getFood());

        // Total recorded pairs is properly updated to include the new ones
        assertEquals(3, owner.getInventorPairs());
    }
}