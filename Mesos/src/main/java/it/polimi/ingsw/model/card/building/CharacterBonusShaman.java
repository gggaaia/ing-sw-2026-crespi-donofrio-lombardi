package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

public class CharacterBonusShaman extends EndOfMatchBuilding{
    public CharacterBonusShaman(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP, owner);
    }

    @Override
    public void applyEffect(Player player) {
        player.changePP(player==getOwner()? 4*(player.getCounterShaman()) : 0);
    }
}
