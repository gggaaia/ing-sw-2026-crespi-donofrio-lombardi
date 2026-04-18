package it.polimi.ingsw.model.card.event;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.card.character.Gatherer;
import it.polimi.ingsw.model.card.character.Hunter;
import it.polimi.ingsw.model.enums.Era;

import java.util.List;

public class Sustenance extends EventCard {
    private boolean isFinalEvent;
    private int malusPoints;
    public Sustenance(int cardID, Era era, int minPlayers, boolean isFinalEvent,  int malusPoints) {
        super(cardID, era, minPlayers);
        this.isFinalEvent = isFinalEvent;
        this.malusPoints = malusPoints;
    }

    public boolean isFinalEvent() {
        return isFinalEvent;
    }
    public int getMalusPoints() {
        return malusPoints;
    }

    public void setFinalEvent(boolean isFinalEvent) {
        this.isFinalEvent = isFinalEvent;
    }
    public void setMalusPoints(int malusPoints) {
        this.malusPoints = malusPoints;
    }

    @Override
    public void applyEffect(List<Player> players) {
        for (Player p : players) {
            int count = p.getPlayerHand().stream()
                    .filter(card->card instanceof CharacterCard)
                    .count();
            int discount = p.getPlayerHand().stream()
                    .filter(card->card instanceof Gatherer)
                    .count();
            int cost= count-(3*discount);
            cost= Math.max(cost, 0);
           if(cost<p.getFood()){
               p.changeFood(-cost);
           }
           else{
               cost= cost-p.getFood();
               p.setFood(0);
               p.changePP(-cost*malusPoints);
           }
        }
    }
}
