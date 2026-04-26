package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

public class RitualStarBonus extends EventActivatedBuilding{
    public RitualStarBonus(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP,  owner);
    }
    public void applyEffect(Player player) {
        player.increaseStars(3);
    }
}
