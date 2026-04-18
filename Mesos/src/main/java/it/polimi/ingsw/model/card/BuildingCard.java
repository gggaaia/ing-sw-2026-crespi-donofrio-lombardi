package it.polimi.ingsw.model.card;

import it.polimi.ingsw.model.enums.Era;

public class BuildingCard extends Card {
    private int foodCost;
    private int endOfGamePP;

    public BuildingCard(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP) {
        super(cardID, era, minPlayers);
        this.foodCost = foodCost;
        this.endOfGamePP = endOfGamePP;
    }

    public int getFoodCost() {
        return foodCost;
    }
    public int getEndOfGamePP() {
        return endOfGamePP;
    }

    public void setFoodCost(int foodCost) {
        this.foodCost = foodCost;
    }
    public void setEndOfGamePP(int endOfGamePP) {
        this.endOfGamePP = endOfGamePP;
    }

    public void applyEffect() {
    }

}
