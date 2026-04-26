package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

public class RitualSave extends EventActivatedBuilding {
    public RitualSave(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP,  owner);
    }

    @Override
    //FIXME: occhio alla possibilità che player==null==getOwner()==null, potresti dover usare una optional
    public int checkRitualSafe(Player player){ return((player == getOwner() ? 0 : 1));}
}
