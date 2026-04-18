package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.TribeCard;
import it.polimi.ingsw.exceptions.EmptyDeckException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Decks {
    private List<TribeCard> tribeDeck;
    private List<BuildingCard> buildingDeck;

    public Decks() {
        this.tribeDeck = new ArrayList<>();
        this.buildingDeck = new ArrayList<>();
    }

    public void populateDecks(int playersNumber) {
        //lancia IOEexcpetion se il file JSON manca
        /*if (card.minPlayers <= playersNumber) {
            buildingDeck.add(card);
        }*/
    }

    public void shuffleDecks() {
        // FIXME: shuffle per era @Simo
        Collections.shuffle(this.tribeDeck);
        this.tribeDeck.sort(Comparator.comparing(Card::getEra));

        Collections.shuffle(this.buildingDeck);
        this.buildingDeck.sort(Comparator.comparing(Card::getEra));
    }

    public Card drawTribe() throws EmptyDeckException {
        if (this.tribeDeck.isEmpty()) {
            throw new EmptyDeckException("The tribe deck is empty");
        }
        return this.tribeDeck.remove(0);
    }

    public Card drawBuilding() throws EmptyDeckException {
        if (this.buildingDeck.isEmpty()) {
            throw new EmptyDeckException("The building deck is empty");
        }
        return this.buildingDeck.remove(0);
    }
}
