package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.building.UniqueSixSet;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniqueSixSetTest {

    // Helper method to easily give a player a uniform number of all 6 required character types
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

    @Test
    @DisplayName("Owner gets PP based on the limiting character type (bottleneck) when counts are uneven")
    void testApplyEndEffect_Owner_UnevenSets() {
        Player owner = new Player("Owner", null);
        UniqueSixSet card = new UniqueSixSet(1, Era.ERA_1, 2, 5, 6, owner);

        // Give highly uneven numbers of characters. The lowest is Painter (3)
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