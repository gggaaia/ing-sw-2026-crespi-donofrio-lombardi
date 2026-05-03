package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.building.SixSet;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SixSetTest {

    // Helper method to easily give a player a uniform number of all 6 required character types
    private Player createHelperPlayer(String name, int uniformCharacterCount, int alreadyRecordedSets) {
        Player p = new Player(name, null);

        if (uniformCharacterCount > 0) {
            p.increaseBuilderCounter(uniformCharacterCount);
            p.increaseGathererCounter(uniformCharacterCount);
            p.increasePainterCounter(uniformCharacterCount);
            p.increaseHunterCounter(uniformCharacterCount);
            p.increaseShamanCounter(uniformCharacterCount);
            p.increaseInventorCounter(uniformCharacterCount);
        }

        if (alreadyRecordedSets > 0) {
            p.increaseSixSetCounter(alreadyRecordedSets);
        }

        return p;
    }

    @Test
    @DisplayName("Effect does not trigger if the player is not the card owner")
    void testApplyEndRoundEffect_NotOwner() {
        Player actualOwner = new Player("Owner", null);
        // Card initialized: 4 food cost, 3 end-of-game PP
        SixSet card = new SixSet(1, Era.ERA_1, 2, 4, 3, actualOwner);

        // Alice has 1 full set of 6 characters
        Player nonOwner = createHelperPlayer("Alice", 1, 0);

        card.applyEndRoundEffect(nonOwner);

        // Non-owner receives no food and their recorded sets counter doesn't change
        assertEquals(0, nonOwner.getFood());
        assertEquals(0, nonOwner.getSixSetCounter());
    }

    @Test
    @DisplayName("Owner gets no food if the set is incomplete (missing at least one character type)")
    void testApplyEndRoundEffect_Owner_IncompleteSet() {
        Player owner = new Player("Owner", null);
        SixSet card = new SixSet(1, Era.ERA_1, 2, 4, 3, owner);

        // Give the owner plenty of 5 types, but 0 of the last type (Inventor)
        owner.increaseBuilderCounter(2);
        owner.increaseGathererCounter(3);
        owner.increasePainterCounter(1);
        owner.increaseHunterCounter(4);
        owner.increaseShamanCounter(2);
        // Inventor stays at 0

        card.applyEndRoundEffect(owner);

        // Since the minimum value across all 6 types is 0, no set is formed
        assertEquals(0, owner.getFood());
        assertEquals(0, owner.getSixSetCounter());
    }

    @Test
    @DisplayName("Owner gets 5 food per completely new set of 6 characters")
    void testApplyEndRoundEffect_Owner_NewSets() {
        // Owner has exactly 2 of each character type, making 2 complete sets
        Player owner = createHelperPlayer("Owner", 2, 0);
        SixSet card = new SixSet(1, Era.ERA_1, 2, 4, 3, owner);

        card.applyEndRoundEffect(owner);

        // Reward: 2 new complete sets * 5 food = 10 food
        assertEquals(10, owner.getFood());

        // The 2 sets must be properly recorded
        assertEquals(2, owner.getSixSetCounter());
    }

    @Test
    @DisplayName("Owner ignores sets that were already recorded in previous rounds")
    void testApplyEndRoundEffect_Owner_AlreadyRecordedSets() {
        // Owner has 1 full set, but it was already recorded previously
        Player owner = createHelperPlayer("Owner", 1, 1);
        SixSet card = new SixSet(1, Era.ERA_1, 2, 4, 3, owner);

        card.applyEndRoundEffect(owner);

        // No new sets found, so food stays 0
        assertEquals(0, owner.getFood());

        // Recorded sets remain exactly 1
        assertEquals(1, owner.getSixSetCounter());
    }

    @Test
    @DisplayName("Owner gets food ONLY for newly formed sets, ignoring old ones")
    void testApplyEndRoundEffect_Owner_MixedSets() {
        // Owner has 3 full sets in total, but 1 was already scored in a past round
        Player owner = createHelperPlayer("Owner", 3, 1);
        SixSet card = new SixSet(1, Era.ERA_1, 2, 4, 3, owner);

        card.applyEndRoundEffect(owner);

        // Calculation: 3 total sets - 1 old set = 2 new sets. Reward: 2 * 5 = 10 food.
        assertEquals(10, owner.getFood());

        // Total recorded sets correctly updates to 3
        assertEquals(3, owner.getSixSetCounter());
    }
}