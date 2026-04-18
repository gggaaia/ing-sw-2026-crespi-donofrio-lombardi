package it.polimi.ingsw.model.card.character;

import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;

public class Builder extends CharacterCard {
    private int buildingCostRedux;

    public Builder(int cardID, Era era, int minPlayers, int buildingCostRedux) {
        super(cardID, era, minPlayers);
        this.buildingCostRedux = buildingCostRedux;
    }

    public int getBuildingCostRedux() {
        return buildingCostRedux;
    }

    public void setBuildingCostRedux(int buildingCostRedux) {
        this.buildingCostRedux = buildingCostRedux;
    }
}
