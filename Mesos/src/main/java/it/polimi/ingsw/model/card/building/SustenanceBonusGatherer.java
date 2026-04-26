package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

public class SustenanceBonusGatherer extends EventActivatedBuilding{
    public SustenanceBonusGatherer(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP, owner);
    }

    @Override
    public int sustenanceBonus(Player player) {
        return (player == getOwner() ? player.getGathererCounter() : 0);
    }
}
