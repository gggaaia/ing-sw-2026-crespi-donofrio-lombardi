package it.polimi.ingsw.model;

import it.polimi.ingsw.model.enums.TileLetter;
import it.polimi.ingsw.model.enums.TotemColor;


public class OfferingTile {
    private final TileLetter letter;
    private final int topCardsToGet;
    private final int bottomCardsToGet;
    private final int minPlayers;
    private final int foodGained;

    // --- MUTABLE ATTRIBUTE (Current Totem on the tile) ---
    private TotemColor currentTotemColor;

    public OfferingTile(TileLetter letter, int topCardsToGet, int bottomCardsToGet, int minPlayers, int foodGained) {
        this.letter = letter;
        this.topCardsToGet = topCardsToGet;
        this.bottomCardsToGet = bottomCardsToGet;
        this.minPlayers = minPlayers;
        this.foodGained = foodGained;

        // Initially the tile is empty
        this.currentTotemColor = null;
    }

    public boolean isOccupied() {
        // Returns true if a player is on the tile, false otherwise
        return this.currentTotemColor != null;
    }
    public void setCurrentTotemColor(TotemColor color) {
        this.currentTotemColor = color;
    }

    public void clearTile() {
        this.currentTotemColor = null;
    }

    public TileLetter getLetter() { return letter; }
    public int getTopCardsToGet() { return topCardsToGet; }
    public int getBottomCardsToGet() { return bottomCardsToGet; }
    public int getMinPlayers() { return minPlayers; }
    public int getFoodGained() { return foodGained; }
    public TotemColor getCurrentTotemColor() { return currentTotemColor; }

}
