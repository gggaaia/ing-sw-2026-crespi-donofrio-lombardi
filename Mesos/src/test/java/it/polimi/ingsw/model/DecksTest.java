package it.polimi.ingsw.model;

import it.polimi.ingsw.exceptions.DeckLoadException;
import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.TribeCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Decks#populateDecks(int)}.
 *
 * The Decks class does not expose its internal lists, so the tests verify
 * behaviour by repeatedly drawing until the deck is exhausted (signalled by
 * an EmptyDeckException). Each test uses its own Decks instance, so the
 * draining side-effect does not leak between tests.
 */
class DecksTest {


    /** Drains every card from the tribe deck. Side-effect: empties the deck. */
    private static List<Card> drainTribeDeck(Decks decks) {
        List<Card> drawn = new ArrayList<>();
        while (true) {
            try {
                drawn.add(decks.drawTribe());
            } catch (EmptyDeckException e) {
                return drawn;
            }
        }
    }

    /** Drains every card from the building deck. Side-effect: empties the deck. */
    private static List<Card> drainBuildingDeck(Decks decks) {
        List<Card> drawn = new ArrayList<>();
        while (true) {
            try {
                drawn.add(decks.drawBuilding());
            } catch (EmptyDeckException e) {
                return drawn;
            }
        }
    }

    // Test invalid player numbers

    @Test
    @DisplayName("populateDecks rejects too few players")
    void populateDecks_tooFewPlayers_throws() {
        Decks decks = new Decks();
        try {
            decks.populateDecks(1);
            assertTrue(false, "Should throw exception for 1 player");
        } catch (DeckLoadException ex) {
            System.out.println("Expected exception caught: " + ex);
        }
    }

    @Test
    @DisplayName("populateDecks rejects too many players")
    void populateDecks_tooManyPlayers_throws() {
        Decks decks = new Decks();
        try {
            decks.populateDecks(6);
            assertTrue(false, "Should throw exception for 6 players");
        } catch (DeckLoadException ex) {
            System.out.println("Expected exception caught: " + ex);
        }
    }

    // Test valid player numbers

    @Test
    @DisplayName("populateDecks works for valid player counts")
    void populateDecks_validPlayers_works() throws DeckLoadException {
        for (int players = 2; players <= 5; players++) {
            Decks decks = new Decks();
            decks.populateDecks(players);
            // If we reach here, no exception was thrown for this player count
        }
    }

    @Test
    @DisplayName("Both decks have cards after populateDecks")
    void populateDecks_createsBothDecks() throws DeckLoadException, EmptyDeckException {
        Decks decks = new Decks();
        decks.populateDecks(3);

        Card tribeCard = decks.drawTribe();
        assertTrue(tribeCard != null, "Tribe deck has cards");

        Card buildingCard = decks.drawBuilding();
        assertTrue(buildingCard != null, "Building deck has cards");
    }

    // Test card filtering

    @Test
    @DisplayName("Cards match player count")
    void populateDecks_correctCards() throws DeckLoadException {
        Decks decks = new Decks();
        decks.populateDecks(2);

        List<Card> tribeCards = drainTribeDeck(decks);
        for (Card card : tribeCards) {
            assertTrue(card.getMinPlayers() <= 2, "Card works for 2 players");
        }
    }

    // Test card types

    @Test
    @DisplayName("Creates correct card types")
    void populateDecks_correctTypes() throws DeckLoadException {
        Decks decks = new Decks();
        decks.populateDecks(5);

        List<Card> tribeCards = drainTribeDeck(decks);
        Card firstTribe = tribeCards.get(0);
        assertTrue(firstTribe instanceof TribeCard, "Creates TribeCard");

        List<Card> buildingCards = drainBuildingDeck(decks);
        Card firstBuilding = buildingCards.get(0);
        assertTrue(firstBuilding instanceof BuildingCard, "Creates BuildingCard");
    }

    // Test same results

    @Test
    @DisplayName("Same input gives same result")
    void populateDecks_sameResult() throws DeckLoadException {
        Decks decks1 = new Decks();
        decks1.populateDecks(3);
        int size1 = drainTribeDeck(decks1).size();

        Decks decks2 = new Decks();
        decks2.populateDecks(3);
        int size2 = drainTribeDeck(decks2).size();

        assertEquals(size1, size2, "Same size both times");
    }
}
