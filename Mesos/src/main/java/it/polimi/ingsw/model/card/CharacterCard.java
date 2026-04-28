package it.polimi.ingsw.model.card;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.InventorIcon;

public abstract class CharacterCard extends TribeCard {
    public CharacterCard (int cardID, Era era, int minPlayers){
        super(cardID, era, minPlayers);
    }

    public void drawEffect(Player player){}
    public InventorIcon getInventorIcon() { return null; }
}
