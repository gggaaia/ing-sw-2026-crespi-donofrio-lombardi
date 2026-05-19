package it.polimi.ingsw.model.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.building.UniqueSixSet;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the {@link UniqueSixSet} building card.
 * <p>
 * Verifies the end-of-game effect associated with this card, which rewards
 * the owner with Prestige Points (PP) for every complete set of all six distinct
 * character types collected throughout the game. The tests ensure that ownership
 * restrictions apply, incomplete sets yield no points, and the final reward
 * is correctly bottlenecked by the rarest character type in the player's possession.
 */
class UniqueSixSetTest {

    /**
     * Helper method to create a custom Player instance for testing.
     * <p>
     * Uniformly populates all six character counters (Builder, Gatherer,
     * Painter, Hunter, Shaman, Inventor) to easily simulate complete sets
     * without manually adding individual cards to the player's hand.
     *
     * @param name                  the nickname of the dummy player
     * @param uniformCharacterCount the amount to add to every character counter
     * @return a mock {@link Player} configured for specific test scenarios
     */
    private Player createHelperPlayer(String name, int uniformCharacterCount) {
        Player p = new Player(name, null);

        if (uniformCharacterCount > 0) {
            p.increaseBuilderCounter(uniformCharacterCount);
            p.increaseGathererCounter(uniformCharacterCount);
            p.increasePainterCounter(uniformCharacterCount);
            p.increaseHunterCounter(uniformCharacterCount);
            p.increaseShamanCounter(uniformCharacterCount);
            p.increaseInventorCounter(uniformCharacterCount);
        }

        return p;
    }

    /**
     * Verifies the ownership check. Ensures that if the effect is triggered
     * on a player who is not the designated owner of the card, no action is
     * taken, and the player receives no bonus Prestige Points.
     */
    @Test
    @DisplayName("Effect does not trigger if the player is not the card owner")
    void testApplyEndEffect_NotOwner() {
        Player actualOwner = new Player("Owner", null);
        // Initialized with realistic values: 5 food cost, 6 end-of-game PP
        UniqueSixSet card = new UniqueSixSet(1, Era.ERA_1, 2, 5, 6, actualOwner);

        // Alice has 1 full set of 6 characters
        Player nonOwner = createHelperPlayer("Alice", 1);

        card.applyEndEffect(nonOwner);

        // Non-owner receives no Prestige Points (PP)
        assertEquals(0, nonOwner.getPrestigePoints());
    }

    /**
     * Verifies the strict requirement of the set for end-of-game scoring.
     * Ensures that if a player possesses multiple characters of various types
     * but is missing entirely at least one necessary type (in this case, Inventor is 0),
     * the set calculation correctly evaluates to 0 and no bonus PP are awarded.
     */
    @Test
    @DisplayName("Owner gets 0 bonus PP if the set is incomplete (missing at least one character type)")
    void testApplyEndEffect_Owner_IncompleteSet() {
        Player owner = new Player("Owner", null);
        UniqueSixSet card = new UniqueSixSet(1, Era.ERA_1, 2, 5, 6, owner);

        // Give the owner some characters, but exactly 0 Inventors
        owner.increaseBuilderCounter(1);
        owner.increaseGathererCounter(2);
        owner.increasePainterCounter(1);
        owner.increaseHunterCounter(3);
        owner.increaseShamanCounter(1);
        // Inventor stays at 0

        card.applyEndEffect(owner);

        // Since the minimum value across all 6 types is 0, no bonus PP are awarded
        assertEquals(0, owner.getPrestigePoints());
    }

    /**
     * Verifies the standard reward calculation for the card owner. Ensures
     * that the owner receives the correct amount of Prestige Points (e.g., 6 PP)
     * multiplied exactly by the number of perfectly formed character sets.
     */
    @Test
    @DisplayName("Owner gets 6 PP multiplied by the number of complete sets formed")
    void testApplyEndEffect_Owner_CompleteSets() {
        // Owner has exactly 2 of each character type
        Player owner = createHelperPlayer("Owner", 2);
        UniqueSixSet card = new UniqueSixSet(1, Era.ERA_1, 2, 5, 6, owner);

        card.applyEndEffect(owner);

        // Minimum is 2 sets. Reward: 2 sets * 6 PP = 12 PP
        assertEquals(12, owner.getPrestigePoints());
    }

    /**
     * Verifies the bottleneck (limiting factor) logic. If the owner has an
     * uneven distribution of characters, the total number of complete sets
     * must be dictated exclusively by the character type they have the least of.
     */
    @Test
    @DisplayName("Owner gets PP based on the limiting character type (bottleneck) when counts are uneven")
    void testApplyEndEffect_Owner_UnevenSets() {
        Player owner = new Player("Owner", null);
        UniqueSixSet card = new UniqueSixSet(1, Era.ERA_1, 2, 5, 6, owner);

        // Give uneven numbers of characters. The lowest is Painter (3)
        owner.increaseBuilderCounter(5);
        owner.increaseGathererCounter(4);
        owner.increasePainterCounter(3); // Bottleneck
        owner.increaseHunterCounter(6);
        owner.increaseShamanCounter(4);
        owner.increaseInventorCounter(5);

        card.applyEndEffect(owner);

        // The limiting factor is 3 sets. Reward: 3 sets * 6 PP = 18 PP
        assertEquals(18, owner.getPrestigePoints());
    }
}