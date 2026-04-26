package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

public class HuntBonus extends EventActivatedBuilding{
    public HuntBonus(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP, owner);
    }
    public boolean hasHuntingBonus(Player player) {
        return(player == getOwner()? true:false);
    }
}
