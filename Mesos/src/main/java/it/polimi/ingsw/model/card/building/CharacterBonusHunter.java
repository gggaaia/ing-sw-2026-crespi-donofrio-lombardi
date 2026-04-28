package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

public class CharacterBonusHunter extends EndOfMatchBuilding{
    public CharacterBonusHunter(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP, owner);
    }

    @Override
    public void applyEndEffect(Player player) {
        player.changePP(player==getOwner()? 3*(player.getHunterCounter()) : 0);
    }
}
