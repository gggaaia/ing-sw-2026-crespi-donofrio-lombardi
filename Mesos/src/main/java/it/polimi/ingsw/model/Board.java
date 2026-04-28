package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.TribeCard;
import it.polimi.ingsw.model.enums.Era;

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

    public void newUpperBuilding(Decks d, Era era) {
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

    /**
     * Scans every tribe card currently sitting in the upper row and returns
     * the highest {@link Era} found among them.
     * <p>
     * The comparison relies on the natural order of the {@code Era} enum
     * (i.e. {@code ERA_1 < ERA_2 < ERA_3}). If the upper row is empty, the
     * earliest era ({@link Era#ERA_1}) is returned as a default.
     *
     * @return the highest {@code Era} present among the tribe cards in the upper
     *         row
     */
    public Era getCurrentEra() {
        Era highest = Era.ERA_1;

        for (TribeCard card : upperRow.getCharacterCards()) {
            if (card.getEra().compareTo(highest) > 0) {
                highest = card.getEra();
            }
        }

        return highest;
    }



}
