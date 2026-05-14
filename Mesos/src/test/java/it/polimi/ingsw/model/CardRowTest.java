package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.EventCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the {@link CardRow} entity.
 * <p>
 * Verifies the correct behavior of card insertion, lookup (contains),
 * removal (take), bulk extractions, and edge cases such as null arguments
 * and duplicate ID detection.
 */
class CardRowTest {

    private CardRow row;
    private CharacterCard char1;
    private CharacterCard char2;
    private BuildingCard build1;
    private EventCard event1;

    /**
     * Initializes an empty {@link CardRow} and creates some dummy cards
     * with unique IDs before each test.
     */
    @BeforeEach
    @DisplayName("Setup: Initialize CardRow and dummy cards")
    void setUp() {
        row = new CardRow();
        char1 = createDummyCharacter(101);
        char2 = createDummyCharacter(102);
        build1 = createDummyBuilding(201);
        event1 = createDummyEvent(301);
    }

    /**
     * Verifies that different types of cards (Character, Building, Event)
     * are successfully added to the row and correctly sorted into their
     * respective internal lists.
     */
    @Test
    @DisplayName("Add and Get: cards should be correctly stored in their respective lists")
    void testAddAndGetCards() {
        row.addCharacterCard(char1);
        row.addBuildingCard(build1);
        row.addEventCard(event1);

        assertEquals(1, row.getCharacterCards().size(), "Should contain exactly 1 character card.");
        assertEquals(1, row.getBuildingCards().size(), "Should contain exactly 1 building card.");
        assertEquals(1, row.getEventCards().size(), "Should contain exactly 1 event card.");

        assertEquals(101, row.getCharacterCards().get(0).getCardID());
        assertEquals(201, row.getBuildingCards().get(0).getCardID());
        assertEquals(301, row.getEventCards().get(0).getCardID());
    }

    /**
     * Verifies the lookup logic for cards within the row. Ensures that
     * the row accurately identifies present cards, returns false for
     * missing cards, and throws an {@link IllegalArgumentException} if
     * a null reference is passed.
     */
    @Test
    @DisplayName("Contains: checks presence of cards and null exceptions")
    void testContainsCard() {
        row.addCharacterCard(char1);
        row.addBuildingCard(build1);

        // Success cases
        assertTrue(row.containsCharacterCard(char1), "Row should contain char1.");
        assertTrue(row.containsBuildingCard(build1), "Row should contain build1.");

        // Not found cases
        assertFalse(row.containsCharacterCard(char2), "Row should NOT contain char2.");

        // Null parameter exceptions
        assertThrows(IllegalArgumentException.class, () -> row.containsCharacterCard(null),
                "containsCharacterCard must throw exception on null.");
        assertThrows(IllegalArgumentException.class, () -> row.containsBuildingCard(null),
                "containsBuildingCard must throw exception on null.");
    }

    /**
     * Verifies the removal logic of a specific card from the row. Checks
     * that a present card is successfully returned and removed from the list,
     * attempting to remove a missing card returns null, and passing a null
     * parameter throws an {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("Take: successful removal, not found, and null exception")
    void testTakeCardSuccessAndNotFound() {
        row.addCharacterCard(char1);

        // Successful take
        Card taken = row.takeCharacterCard(char1);
        assertNotNull(taken, "Taken card should not be null.");
        assertEquals(101, taken.getCardID(), "Taken card ID should match.");
        assertTrue(row.getCharacterCards().isEmpty(), "Row should be empty after taking the only card.");

        // Not found
        Card takenMissing = row.takeCharacterCard(char2);
        assertNull(takenMissing, "Taking a missing card should return null.");

        // Null parameter
        assertThrows(IllegalArgumentException.class, () -> row.takeCharacterCard(null),
                "takeCharacterCard must throw exception on null.");
    }

    /**
     * Verifies the integrity check during card removal. If the row enters
     * a corrupted state where multiple cards share the same ID, the removal
     * process must immediately abort and throw an {@link IllegalStateException}.
     */
    @Test
    @DisplayName("Take: IllegalStateException thrown on duplicate IDs")
    void testTakeCardDuplicateException() {
        // We add the same card twice to simulate a corrupted state (duplicate IDs)
        row.addCharacterCard(char1);
        row.addCharacterCard(char1);

        assertThrows(IllegalStateException.class, () -> row.takeCharacterCard(char1),
                "Taking a card that appears multiple times must throw IllegalStateException.");
    }

    /**
     * Verifies the selective clearing of the card row. Ensures that discarding
     * tribe cards only affects Characters and Events (leaving Buildings intact),
     * and that discarding buildings correctly empties the building list.
     */
    @Test
    @DisplayName("Discard: tribe and building cards are cleared correctly")
    void testDiscardCards() {
        row.addCharacterCard(char1);
        row.addEventCard(event1);
        row.addBuildingCard(build1);

        row.discardTribeCards();
        assertTrue(row.getCharacterCards().isEmpty(), "Characters should be discarded.");
        assertTrue(row.getEventCards().isEmpty(), "Events should be discarded.");
        assertFalse(row.getBuildingCards().isEmpty(), "Buildings should NOT be affected by discardTribeCards.");

        row.discardBuildingCards();
        assertTrue(row.getBuildingCards().isEmpty(), "Buildings should now be discarded.");
    }

    /**
     * Verifies the bulk extraction logic used when moving cards. Ensures
     * that extracting cards returns the full list of elements and simultaneously
     * clears the original internal lists to prevent accidental card duplication.
     */
    @Test
    @DisplayName("Extract: returns all elements and clears the original lists")
    void testExtractAllCards() {
        row.addCharacterCard(char1);
        row.addBuildingCard(build1);

        List<CharacterCard> extractedChars = row.extractAllCharacterCards();
        List<BuildingCard> extractedBuilds = row.extractAllBuildingCards();

        // Check extracted lists
        assertEquals(1, extractedChars.size(), "Extracted list should contain 1 character.");
        assertEquals(1, extractedBuilds.size(), "Extracted list should contain 1 building.");

        // Check original lists are empty
        assertTrue(row.getCharacterCards().isEmpty(), "Row should be empty after extraction.");
        assertTrue(row.getBuildingCards().isEmpty(), "Row should be empty after extraction.");
    }

    /**
     * Helper method to create a dummy character card for testing purposes.
     *
     * @param id the unique identifier for the card
     * @return a mock {@link CharacterCard}
     */
    private CharacterCard createDummyCharacter(int id) {
        return new CharacterCard(id, null, 0) {
            @Override
            public int getCardID() { return id; }
        };
    }

    /**
     * Helper method to create a dummy building card for testing purposes.
     *
     * @param id the unique identifier for the card
     * @return a mock {@link BuildingCard}
     */
    private BuildingCard createDummyBuilding(int id) {
        return new BuildingCard(id, null, 0, 0, 0, null) {
            @Override
            public int getCardID() { return id; }
        };
    }

    /**
     * Helper method to create a dummy event card for testing purposes.
     *
     * @param id the unique identifier for the card
     * @return a mock {@link EventCard}
     */
    private EventCard createDummyEvent(int id) {
        return new EventCard(id, null, 0) {
            @Override
            public void applyEvent(List<Player> players) {
                // Do nothing for CardRow tests
            }

            @Override
            public int getCardID() { return id; }
        };
    }
}