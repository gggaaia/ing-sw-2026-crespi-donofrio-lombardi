package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.enums.Era;

public class DrawUpperRow extends BuildingCard {
    public DrawUpperRow(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP, owner);
    }

    public boolean hasUpperRowBuilding(Player player) {
        return(player==getOwner()? true : false);
    }
}
