package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

public class RitualPPMultiplier extends EventActivatedBuilding{
    public RitualPPMultiplier(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP,  owner);
    }
    @Override
    public int checkDoubleStarRitual(Player player) { return((player == getOwner() ? 2 : 1)); }
}
