package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.SixSet;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link SixSet} building card.
 * <p>
 * Verifies the end-of-round effect associated with this card, which rewards
 * the owner with food for every complete set of all six distinct character
 * types collected. The tests ensure that ownership restrictions apply,
 * incomplete sets yield no rewards, and previously scored sets are
 * correctly tracked to prevent duplicate payouts in subsequent rounds.
 */
class SixSetTest {

    /**
     * Helper method to create a custom Player instance for testing.
     * <p>
     * Uniformly populates all six character counters (Builder, Gatherer,
     * Painter, Hunter, Shaman, Inventor) to easily simulate complete sets
     * without manually adding individual cards to the player's hand.
     *
     * @param name                  the nickname of the dummy player
     * @param uniformCharacterCount the amount to add to every character counter
     * @param alreadyRecordedSets   the simulated number of sets already scored in previous rounds
     * @return a mock {@link Player} configured for specific test scenarios
     */
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

    /**
     * Verifies the ownership check. Ensures that if the effect is triggered
     * on a player who is not the designated owner of the card, no action is
     * taken, and neither food nor tracked sets are modified.
     */
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

    /**
     * Verifies the strict requirement of the set. Ensures that if a player
     * possesses multiple characters of various types but is missing at least
     * one necessary type (in this case, Inventor is 0), the minimum set
     * calculation correctly evaluates to 0 and no reward is granted.
     */
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

    /**
     * Verifies the standard reward calculation for the card owner. Ensures
     * that the owner receives the correct amount of food (5 per complete set)
     * and that these newly formed sets are properly registered to prevent
     * double-scoring in the future.
     */
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

    /**
     * Verifies the tracking mechanism for existing sets. If a player triggers
     * the effect but has not formed any new complete sets since the last
     * activation, they should receive zero food and their tracked set count
     * must remain stable.
     */
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

    /**
     * Verifies the differential reward calculation. If the owner has a mix
     * of previously recorded sets and newly formed sets, they must only
     * be rewarded for the new ones, while the total tracking count is
     * appropriately updated.
     */
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