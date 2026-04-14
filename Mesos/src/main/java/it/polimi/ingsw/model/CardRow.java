package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.TribeCard;

import java.util.ArrayList;
import java.util.List;

public class CardRow {
    private final List<TribeCard> tribeCards;
    private final List<BuildingCard> buildingCards;

    public CardRow() {
        this.tribeCards = new ArrayList<>();
        this.buildingCards = new ArrayList<>();
    }

    public void addCard(Card c) {
        if (c instanceof TribeCard) {
            tribeCards.add((TribeCard) c);
        } else if (c instanceof BuildingCard) {
            buildingCards.add((BuildingCard) c);
        }
    }

    public TribeCard takeTribeCard(int position) {
        if (position >= 0 && position < this.tribeCards.size()) {
            return this.tribeCards.remove(position);
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
        this.tribeCards.clear();
    }

    public void discardBuildingCards(){
        this.buildingCards.clear();
    }

    public List<TribeCard> extractAllTribeCards() {
        List<TribeCard> toMove = new ArrayList<>(this.tribeCards);
        //after extracted the row is cleared
        this.tribeCards.clear();
        return toMove;
    }

    public List<BuildingCard> extractAllBuildingCards(){
        List<BuildingCard> toMove = new ArrayList<>(this.buildingCards);
        this.buildingCards.clear();
        return toMove;
    }

    public List<TribeCard> getTribeCards() {
        return new ArrayList<>(this.tribeCards); // it's a copy, for safety
    }

    public List<BuildingCard> getBuildingCards() {
        return new ArrayList<>(this.buildingCards);
    }
    }
