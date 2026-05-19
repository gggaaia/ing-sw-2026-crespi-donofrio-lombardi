package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.enums.Era;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the abstract {@link EventCard} entity.
 * <p>
 * Verifies the concrete base behaviors inherited by all specific event cards,
 * specifically their initialization and their structural interactions with
 * the {@link CardRow} during standard play and first-round setups.
 */
class EventCardTest {

    private EventCard eventCard;

    /**
     * Initializes an isolated testing environment.
     * Instantiates the abstract EventCard using an anonymous inner class
     * to provide a dummy implementation of the applyEvent hook.
     */
    @BeforeEach
    @DisplayName("Setup: Initialize a generic EventCard")
    void setUp() {
        // Initialize with cardID=201, Era=ERA_2, minPlayers=3
        eventCard = new EventCard(201, Era.ERA_2, 3) {
            @Override
            public void applyEvent(List<Player> players) {
                // Dummy implementation: intentionally left blank as the specific
                // event logic is not the target of this base class test.
            }
        };
    }

    /**
     * Verifies that the super constructor properly binds the core attributes
     * of the card (ID, Era, minimum players).
     */
    @Test
    @DisplayName("Constructor & Getters: Correctly initialize base state")
    void testConstructorAndGetters() {
        assertEquals(201, eventCard.getCardID(), "Card ID should match the constructor input");
        assertEquals(Era.ERA_2, eventCard.getEra(), "Era should match the constructor input");
        assertEquals(3, eventCard.getMinPlayers(), "Minimum players should match the constructor input");
    }

    /**
     * Verifies the standard interaction with the CardRow.
     * Ensures that calling addToRow successfully dispatches and appends the
     * event card to the row's internal event list.
     */
    @Test
    @DisplayName("addToRow: Dispatches itself to the CardRow's event list")
    void testAddToRow() {
        CardRow row = new CardRow();

        // Ensure the event row is empty at the start
        assertTrue(row.getEventCards().isEmpty(), "CardRow's event list should start empty");

        // Execute the dispatch method
        eventCard.addToRow(row);

        // Verify the interaction worked properly
        List<EventCard> events = row.getEventCards();
        assertEquals(1, events.size(), "CardRow should contain exactly 1 event card after dispatch");
        assertTrue(events.contains(eventCard), "The event card should be successfully added to the row's list");
    }

    /**
     * Verifies the specific interaction with the CardRow during the first round.
     * Ensures that calling firstRoundChangeRow successfully adds the card to the
     * row and explicitly returns true to signal the completed action.
     */
    @Test
    @DisplayName("firstRoundChangeRow: Adds to row and correctly returns true")
    void testFirstRoundChangeRow() {
        CardRow row = new CardRow();

        // Execute the first round specific dispatch method
        boolean result = eventCard.firstRoundChangeRow(row);

        // Verify the boolean flag logic
        assertTrue(result, "firstRoundChangeRow must unconditionally return true for EventCards");

        // Verify the structural change to the row
        List<EventCard> events = row.getEventCards();
        assertEquals(1, events.size(), "CardRow should contain exactly 1 event card after first round dispatch");
        assertTrue(events.contains(eventCard), "The event card should be successfully added to the row's list");
    }
}