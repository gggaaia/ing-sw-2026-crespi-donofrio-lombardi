package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CardRow implements Serializable {
    /**
     * Serialization version. Bump only when changing the field layout in a
     * backwards-incompatible way; the serialized form must be readable by
     * both server and client running the same build.
     */
    private static final long serialVersionUID = 1L;
    private final List<CharacterCard> characterCards;
    private final List<EventCard> eventCards;
    private final List<BuildingCard> buildingCards;

    public CardRow() {
        this.characterCards = new ArrayList<>();
        this.eventCards = new ArrayList<>();
        this.buildingCards = new ArrayList<>();
    }

    public void addCard(Card c) {
        c.addToRow(this);
    }

    public void addCharacterCard(CharacterCard cc) {
        this.characterCards.add(cc);
    }

    public void addBuildingCard(BuildingCard cb) {
        this.buildingCards.add(cb);
    }

    public void addEventCard(EventCard ce) {
        this.eventCards.add(ce);
    }


    /**
     * Removes the character card matching the given card's {@code cardID}
     * from this row, searching only in {@link #characterCards}.
     * <p>
     * Cards are compared via {@link Card#getCardID()}, which is assumed to be
     * unique across the deck.
     * <p>
     * If a single matching card is found, it is removed and returned (i.e.
     * the actual {@link Card} instance previously stored in the row, not the
     * argument). If no matching card is found, the method returns
     * {@code null} and the row is left unchanged. If the same {@code cardID}
     * appears more than once in {@link #characterCards}, the row is left
     * unchanged and {@link IllegalStateException} is thrown, since this
     * indicates an inconsistent game state.
     *
     * @param card the card whose {@code cardID} identifies the card to remove
     * @return the removed {@link Card}, or {@code null} if no matching card
     *         was found among the character cards of this row
     * @throws IllegalArgumentException if {@code card} is {@code null}
     * @throws IllegalStateException    if the same {@code cardID} appears
     *                                  more than once among this row's
     *                                  character cards
     */
    public Card takeCharacterCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        int targetId = card.getCardID();

        int charIndex = -1;
        int occurrences = 0;

        for (int i = 0; i < this.characterCards.size(); i++) {
            if (this.characterCards.get(i).getCardID() == targetId) {
                charIndex = i;
                occurrences++;
            }
        }

        if (occurrences == 0) {
            return null;
        }
        if (occurrences > 1) {
            throw new IllegalStateException(
                    "Duplicate cardID " + targetId
                            + " found among character cards in CardRow (occurrences: "
                            + occurrences + ")");
        }

        return this.characterCards.remove(charIndex);
    }

    /**
     * Removes the building card matching the given card's {@code cardID}
     * from this row, searching only in {@link #buildingCards}.
     * <p>
     * Cards are compared via {@link Card#getCardID()}, which is assumed to be
     * unique across the deck.
     * <p>
     * If a single matching card is found, it is removed and returned (i.e.
     * the actual {@link Card} instance previously stored in the row, not the
     * argument). If no matching card is found, the method returns
     * {@code null} and the row is left unchanged. If the same {@code cardID}
     * appears more than once in {@link #buildingCards}, the row is left
     * unchanged and {@link IllegalStateException} is thrown, since this
     * indicates an inconsistent game state.
     *
     * @param card the card whose {@code cardID} identifies the card to remove
     * @return the removed {@link Card}, or {@code null} if no matching card
     *         was found among the building cards of this row
     * @throws IllegalArgumentException if {@code card} is {@code null}
     * @throws IllegalStateException    if the same {@code cardID} appears
     *                                  more than once among this row's
     *                                  building cards
     */
    public Card takeBuildingCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        int targetId = card.getCardID();

        int buildIndex = -1;
        int occurrences = 0;

        for (int i = 0; i < this.buildingCards.size(); i++) {
            if (this.buildingCards.get(i).getCardID() == targetId) {
                buildIndex = i;
                occurrences++;
            }
        }

        if (occurrences == 0) {
            return null;
        }
        if (occurrences > 1) {
            throw new IllegalStateException(
                    "Duplicate cardID " + targetId
                            + " found among building cards in CardRow (occurrences: "
                            + occurrences + ")");
        }

        return this.buildingCards.remove(buildIndex);
    }

    /**
     * Returns whether a character card with the given card's {@code cardID}
     * is present in this row, searching only in {@link #characterCards}.
     * <p>
     * Cards are compared via {@link Card#getCardID()}, which is assumed to be
     * unique across the deck. The row is not modified.
     *
     * @param card the card whose {@code cardID} is searched for
     * @return {@code true} if a character card with the same {@code cardID}
     *         is found in this row, {@code false} otherwise
     * @throws IllegalArgumentException if {@code card} is {@code null}
     */
    public boolean containsCharacterCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        int targetId = card.getCardID();

        for (CharacterCard c : this.characterCards) {
            if (c.getCardID() == targetId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a building card with the given card's {@code cardID}
     * is present in this row, searching only in {@link #buildingCards}.
     * <p>
     * Cards are compared via {@link Card#getCardID()}, which is assumed to be
     * unique across the deck. The row is not modified.
     *
     * @param card the card whose {@code cardID} is searched for
     * @return {@code true} if a building card with the same {@code cardID}
     *         is found in this row, {@code false} otherwise
     * @throws IllegalArgumentException if {@code card} is {@code null}
     */
    public boolean containsBuildingCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        int targetId = card.getCardID();

        for (BuildingCard b : this.buildingCards) {
            if (b.getCardID() == targetId) {
                return true;
            }
        }
        return false;
    }

    public void discardTribeCards() {
        this.characterCards.clear();
        this.eventCards.clear();
    }

    public void discardBuildingCards() {
        this.buildingCards.clear();
    }

    public List<CharacterCard> extractAllCharacterCards() {
        List<CharacterCard> charactersToMove = new ArrayList<>(this.characterCards);
        //after extracted the row is cleared
        this.characterCards.clear();
        return charactersToMove;
    }

    public List<EventCard> extractAllEventCards() {
        List<EventCard> eventsToMove = new ArrayList<>(this.eventCards);
        //after extracted the row is cleared
        this.eventCards.clear();
        return eventsToMove;
    }

    public List<BuildingCard> extractAllBuildingCards() {
        List<BuildingCard> buildingsToMove = new ArrayList<>(this.buildingCards);
        this.buildingCards.clear();
        return buildingsToMove;
    }


    public List<CharacterCard> getCharacterCards() {
        return new ArrayList<>(this.characterCards);
    }

    public List<EventCard> getEventCards() {
        return new ArrayList<>(this.eventCards);
    }

    public List<BuildingCard> getBuildingCards() {
        return new ArrayList<>(this.buildingCards);
    }

}