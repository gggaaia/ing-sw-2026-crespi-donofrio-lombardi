package it.polimi.ingsw.model;

import it.polimi.ingsw.model.enums.TileLetter;

public class OfferingTile {
    private TileLetter letter;
    private int topCardsToGet;
    private int bottomCardsToGet;
    private int minPlayers;
    private int foodGained;
    private String nickname;

    public boolean isOccupied() {
        return false;
    }

    public void clearTile() {
    }
}
