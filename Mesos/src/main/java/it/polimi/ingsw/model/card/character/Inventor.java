package it.polimi.ingsw.model.card.character;

import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.InventorIcon;

public class Inventor extends CharacterCard {
    private InventorIcon inventorIcon;

    public Inventor(int cardID, Era era, int minPlayers, InventorIcon inventorIcon) {
        super(cardID, era, minPlayers);
        this.inventorIcon = inventorIcon;
    }
}
