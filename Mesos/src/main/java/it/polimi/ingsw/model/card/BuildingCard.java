package it.polimi.ingsw.model.card;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

public class BuildingCard extends Card {
    private int foodCost;
    private int endOfGamePP;
    private Player owner;

    public BuildingCard(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers);
        this.foodCost = foodCost;
        this.endOfGamePP = endOfGamePP;
        this.owner = owner;
    }

    public int getFoodCost() {
        return foodCost;
    }
    public int getEndOfGamePP() {
        return endOfGamePP;
    }
    public Player getOwner() { return owner; }

    public void setFoodCost(int foodCost) {
        this.foodCost = foodCost;
    }
    public void setEndOfGamePP(int endOfGamePP) {
        this.endOfGamePP = endOfGamePP;
    }
    public void setOwner(Player owner) { this.owner = owner; }

    public void applyEffect(Player player) { }
    public void applyEndEffect(Player player) { }
    public void applyEndRoundEffect(Player player) { }
    public int checkRitualSafe(Player player) {return 1;}
    public int checkDoubleStarRitual(Player player) {return 1;}
    public boolean hasPaintingBonus(Player player) {return false;}
    public boolean hasHuntingBonus(Player player) {return false;}
    public int sustenanceBonus(Player player) {return 0;}
}
