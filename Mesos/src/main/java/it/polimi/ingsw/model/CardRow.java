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


    public CharacterCard takeCharacterCard(int position) {
        if (position >= 0 && position < this.characterCards.size()) {
            return this.characterCards.remove(position);
        }
        return null;
    }

    public BuildingCard takeBuildingCard(int position) {
        if (position >= 0 && position < this.buildingCards.size()) {
            return this.buildingCards.remove(position);
        }
        return null;
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