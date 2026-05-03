package it.polimi.ingsw.model;

import it.polimi.ingsw.model.enums.TileLetter;
import it.polimi.ingsw.model.enums.TotemColor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class OfferingTile implements Serializable {
    /**
     * Serialization version for the wire/snapshot format.
     */
    private static final long serialVersionUID = 1L;

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

    public static List<OfferingTile> createAllTiles() {
        return new ArrayList<>(Arrays.asList(
                new OfferingTile(TileLetter.A, 0, 0, 5, 3),
                new OfferingTile(TileLetter.B, 0, 1, 2, 0),
                new OfferingTile(TileLetter.C, 1, 0, 2, 0),
                new OfferingTile(TileLetter.D, 0, 2, 3, 0),
                new OfferingTile(TileLetter.E, 1, 1, 2, 0),
                new OfferingTile(TileLetter.F, 2, 0, 4, 0),
                new OfferingTile(TileLetter.G, 2, 1, 3, 0)
        ));
    }
}
