package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.TribeCard;

import java.util.List;

public class CardRow {
    private List<TribeCard> tribeCards;
    private List<BuildingCard> buildingCards;

    public void addCard(Card c) {
    }

    public Card takeCard(int cardPosition, String nickname) {
        return null;
    }

    public void discardCharactersAndEvents() {
    }

    public List<Card> extractAllCards() {
        return null;
    }
}
