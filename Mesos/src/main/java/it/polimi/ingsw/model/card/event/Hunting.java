package it.polimi.ingsw.model.card.event;

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
        for (Player p : players) {
            int count = p.getPlayerHand().stream()
                    .filter(card -> card instanceof Hunter)
                    .count();
            p.changePP(this.bonusPoint*count);
            p.changeFood(1);
        }
    }
}
