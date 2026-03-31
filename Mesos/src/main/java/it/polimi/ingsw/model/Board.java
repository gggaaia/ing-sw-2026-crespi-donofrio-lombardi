package it.polimi.ingsw.model;

import java.util.List;

public class Board {
    private CardRow upperRow;
    private CardRow lowerRow;
    private List<OfferingTile> offeringTrack;
    private TurnOrderTile turnOrderTile;

    public void setupOfferingTiles(int numPlayers) {
    }

    public void setupTurnOrderTiles(int numPlayers) {
    }

    public void setupLower(Decks d, int numPlayers) {
    }

    public void newUpper(Decks d, int numPlayers) {
    }

    public void shiftUpToLow() {
    }

    public void newUpperBuilding(Decks d) {
    }

    public void shiftBuildingsDown() {
    }

    public void placeInitTotems(List<Player> players) {
    }

    public boolean moveTotem(int index, String nickname) {
        return false;
    }

    public void moveOfferingTrackToTurnOrder() {
    }
}
