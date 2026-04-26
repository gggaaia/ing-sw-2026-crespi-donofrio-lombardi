package it.polimi.ingsw.model.card.event;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.card.character.Hunter;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.Player;
import java.util.List;

public class Hunting extends EventCard {
    private int bonusPoint;
    public Hunting(int cardID, Era era, int minPlayers, int bonusPoint) {
        super(cardID, era, minPlayers);
        this.bonusPoint = bonusPoint;
    }

    public int getBonusPoint() {
        return bonusPoint;
    }

    public void setBonusPoint(int bonusPoint) {
        this.bonusPoint = bonusPoint;
    }

    @Override
    public void applyEffect(List<Player> players) {
        for (Player player : players) {
            int count = player.getCounterHunter();
            List<BuildingCard> buildings = player.getPlayerBuildings();
            for (BuildingCard card : buildings) {
                count=(card.hasHuntingBonus(player)? count*2 : count);
            }
            player.changePP(this.bonusPoint*count);
            player.changeFood(count);
        }
    }
}
