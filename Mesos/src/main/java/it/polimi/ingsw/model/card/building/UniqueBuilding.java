package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.enums.Era;

public class UniqueBuilding extends BuildingCard {
    public UniqueBuilding(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP){
        super(cardID, era, minPlayers, foodCost, endOfGamePP);
    }
}
