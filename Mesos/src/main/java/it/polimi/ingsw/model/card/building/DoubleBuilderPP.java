package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

public class DoubleBuilderPP extends EndOfMatchBuilding{
    public DoubleBuilderPP(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP, owner);
    }
    @Override
    //Double the PP builder bonus
    public void applyEndEffect(Player player) {
        player.increasePPBuilderBonus(player==getOwner()? player.getPPBuilderBonus() : 0);
    }
}
