package it.polimi.ingsw.model.card;

import it.polimi.ingsw.model.enums.Era;

public abstract class TribeCard extends Card {
    public TribeCard(int cardID, Era era, int minPlayers) {
        super(cardID, era, minPlayers);
    }
}
