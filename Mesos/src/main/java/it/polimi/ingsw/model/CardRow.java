package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.*;

import java.util.ArrayList;
import java.util.List;

public class CardRow {
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
     * Removes the card matching the given card's {@code cardID} from this
     * row, searching only in {@link #characterCards} and {@link #buildingCards}
     * (event cards are not searched).
     * <p>
     * Cards are compared via {@link Card#getCardID()}, which is assumed to be
     * unique across the deck.
     * <p>
     * If a single matching card is found, it is removed and the method returns
     * {@code true}. If no matching card is found, the method returns
     * {@code false} and the row is left unchanged. If the same {@code cardID}
     * appears more than once across the two searched lists, the row is left
     * unchanged and {@link IllegalStateException} is thrown, since this
     * indicates an inconsistent game state.
     *
     * @param card the card whose {@code cardID} identifies the card to remove
     * @return {@code true} if a matching card was found and removed,
     *         {@code false} otherwise
     * @throws IllegalArgumentException if {@code card} is {@code null}
     * @throws IllegalStateException    if the same {@code cardID} appears
     *                                  more than once in this row
     */
    public boolean takeCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        int targetId = card.getCardID();

        int charIndex = -1;
        int buildIndex = -1;
        int occurrences = 0;

        for (int i = 0; i < this.characterCards.size(); i++) {
            if (this.characterCards.get(i).getCardID() == targetId) {
                charIndex = i;
                occurrences++;
            }
        }
        for (int i = 0; i < this.buildingCards.size(); i++) {
            if (this.buildingCards.get(i).getCardID() == targetId) {
                buildIndex = i;
                occurrences++;
            }
        }

        if (occurrences == 0) {
            return false;
        }
        if (occurrences > 1) {
            throw new IllegalStateException(
                    "Duplicate cardID " + targetId + " found in CardRow (occurrences: "
                            + occurrences + ")");
        }

        if (charIndex >= 0) {
            this.characterCards.remove(charIndex);
        } else {
            this.buildingCards.remove(buildIndex);
        }
        return true;
    }

    /**
     * Returns whether the card matching the given card's {@code cardID} is
     * present in this row, searching only in {@link #characterCards} and
     * {@link #buildingCards} (event cards are not searched).
     * <p>
     * Cards are compared via {@link Card#getCardID()}, which is assumed to be
     * unique across the deck. The row is not modified.
     *
     * @param card the card whose {@code cardID} is searched for
     * @return {@code true} if a card with the same {@code cardID} is found,
     *         {@code false} otherwise
     * @throws IllegalArgumentException if {@code card} is {@code null}
     */
    public boolean containsCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        int targetId = card.getCardID();

        for (CharacterCard c : this.characterCards) {
            if (c.getCardID() == targetId) {
                return true;
            }
        }
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