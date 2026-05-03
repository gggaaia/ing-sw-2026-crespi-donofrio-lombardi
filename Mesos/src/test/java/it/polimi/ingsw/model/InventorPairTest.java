package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.building.InventorPair;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventorPairTest {

    // Helper method to create a custom Player instance.
    // This allows us to control exactly how many total pairs the player has
    // without relying on the internal implementation of how cards are added to the hand.
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