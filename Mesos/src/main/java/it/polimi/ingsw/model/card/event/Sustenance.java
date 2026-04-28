package it.polimi.ingsw.model.card.event;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.card.building.SustenanceBonusGatherer;
import it.polimi.ingsw.model.card.building.SustenanceBonusPainter;
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
    public void applyEvent(List<Player> players) {
        for (Player player : players) {
            int count = player.getPlayerHand().size();
            int discount = player.getGathererCounter();
            List<BuildingCard> buildings = player.getPlayerBuildings();
            for (BuildingCard card : buildings) {
                discount+=card.sustenanceBonus(player);
            }
            int cost= count-(3*discount);
            cost= Math.max(cost, 0);
           if(cost<=player.getFood()){
               player.changeFood(-cost);
           }
           else{
               cost= cost-player.getFood();
               player.changeFood(-player.getFood());
               player.changePP(-cost*malusPoints);
           }
        }
    }
}
