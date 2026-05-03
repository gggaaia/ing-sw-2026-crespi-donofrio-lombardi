package it.polimi.ingsw.model.card.character;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;

public class Builder extends CharacterCard {
    private int buildingCostRedux;
    private int prestigePoint;

    public Builder(int cardID, Era era, int minPlayers, int buildingCostRedux, int prestigePoint) {
        super(cardID, era, minPlayers);
        this.buildingCostRedux = buildingCostRedux;
        this.prestigePoint =  prestigePoint;
    }

    public int getBuildingCostRedux() {
        return buildingCostRedux;
    }
    public int getPrestigePoint() { return prestigePoint; }

    public void setBuildingCostRedux(int buildingCostRedux) {this.buildingCostRedux = buildingCostRedux; }
    public void setPrestigePoint(int prestigePoint) { this.prestigePoint = prestigePoint; }

    public void drawEffect(Player player) {
        player.increaseBuilderCounter(1);
        player.increasePPBuilderBonus(prestigePoint);
        player.increaseBuildingDiscount(buildingCostRedux);
    }
}
