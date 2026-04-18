package it.polimi.ingsw.model.card;

import it.polimi.ingsw.model.enums.Era;

public abstract class Card {
    private int cardID;
    private Era era;
    private int minPlayers;

    public Card(int cardID, Era era, int minPlayers) {
        this.cardID = cardID;
        this.era = era;
        this.minPlayers = minPlayers;
    }

    public int getCardID() {
        return cardID;
    }
    public Era getEra() {
        return era;
    }
    public int getMinPlayers() {
        return minPlayers;
    }

    public void setCardID(int cardID) {
        this.cardID = cardID;
    }
    public void setEra(Era era) {
        this.era = era;
    }
    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }
}
