package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.enums.Era;

// TODO: implement effect logic
public class InventorPair extends BuildingCard {
    public InventorPair(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP, owner);
    }
}
