package it.polimi.ingsw.model.card;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

public abstract class CharacterCard extends TribeCard {
    public CharacterCard (int cardID, Era era, int minPlayers){
        super(cardID, era, minPlayers);
    }

    public void drawEffect(Player player){}
}
