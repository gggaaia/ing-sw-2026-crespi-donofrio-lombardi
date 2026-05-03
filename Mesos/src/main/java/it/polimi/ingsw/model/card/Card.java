package it.polimi.ingsw.model.card;

import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

import java.io.Serializable;
import java.util.List;

public abstract class Card implements Serializable {
    /**
     * Serialization version. Inherited by every subclass; subclasses don't
     * need to redeclare it unless they actually change their own field
     * layout in a backwards-incompatible way.
     */
    private static final long serialVersionUID = 1L;

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
    public void addToRow(CardRow row) { }
    public boolean firstRoundChangeRow(CardRow row) { return false; }
    public void applyEvent(List<Player> players) { }

}
