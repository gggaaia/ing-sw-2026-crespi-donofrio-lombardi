package it.polimi.ingsw.model.card;

import it.polimi.ingsw.model.CardRow;
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

    @Override
    public void addToRow(CardRow row){
        row.addBuildingCard(this);
    }

    public int applyStarBonus(Player player) { return 0;}
    public void applyEndEffect(Player player) { }
    public void applyEndRoundEffect(Player player) { }
    public int checkRitualSafe(Player player) {return 1;}
    public int checkDoubleStarRitual(Player player) {return 1;}
    public boolean hasPaintingBonus(Player player) {return false;}
    public boolean hasHuntingBonus(Player player) {return false;}
    public int sustenanceBonus(Player player) {return 0;}
    public boolean hasUpperRowBuilding(Player player) {return false;}

    /**
     * Hook called by {@link it.polimi.ingsw.model.Match#resolveTurnOrderTile()}
     * for every building the player owns, but only when the player's
     * turn-order slot grants strictly positive food.
     * <p>
     * The default implementation does nothing. Subclasses that grant a
     * turn-order food bonus (e.g. {@code TotemFoodBonus}) override this
     * method to mutate the player's resources directly (typically by
     * calling {@link Player#changeFood(int)}).
     *
     * @param player the player whose turn-order slot just resolved with
     *               positive food (always equal to the building's owner
     *               in normal play)
     */
    public void turnOrderBonus(Player player) {}
}
