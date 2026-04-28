package it.polimi.ingsw.model.card;

import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.Player;
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

    /**
     * Adds this card to the given {@link CardRow}, dispatching to the correct
     * row-specific list based on the concrete card type.
     * <p>
     * Subclasses should override this method to call the appropriate
     * {@code addCharacterCard}, {@code addEventCard}, or {@code addBuildingCard}
     * method on the row.
     *
     * @param row the {@code CardRow} to which this card should be added
     */
    public void addToRow(CardRow row) {
        // TODO: @Simo override in concrete subclasses to add this card to the proper list in the row
    }
}
