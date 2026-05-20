package it.polimi.ingsw.model.card.event;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.card.building.RitualSave;
import it.polimi.ingsw.model.enums.Era;

import java.util.List;

public class Ritual extends EventCard {
    private boolean isFinalEvent;
    private int bonusPoints;
    private int malusPoints;

    public Ritual(int cardID, Era era, int minPlayers, boolean isFinalEvent, int bonusPoints, int malusPoints) {
        super(cardID, era, minPlayers);
        this.isFinalEvent = isFinalEvent;
        this.bonusPoints = bonusPoints;
        this.malusPoints = malusPoints;
    }

    public boolean isFinalEvent() {
        return isFinalEvent;
    }
    public int getBonusPoints() {
        return bonusPoints;
    }
    public int getMalusPoints() {
        return malusPoints;
    }

    public void setFinalEvent(boolean isFinalEvent) {
        this.isFinalEvent = isFinalEvent;
    }
    public void setBonusPoints(int bonusPoints) {this.bonusPoints = bonusPoints;}
    public void setMalusPoints(int malusPoints) {this.malusPoints = malusPoints;}
//  FIXME: @Screpes wire the building effects into the workflow
    @Override
    public void applyEvent(List<Player> players) {
        int maxStars = players.stream()
                .mapToInt(Player::getStarCounter)
                .max()
                .orElse(0);

        int minStars = players.stream()
                .mapToInt(Player::getStarCounter)
                .min()
                .orElse(0);

        if(maxStars == minStars){
            for(Player player : players){
                List<BuildingCard> buildings = player.getPlayerBuildings();
                int bonusVal=1;
                int safeVal=1;
                for (BuildingCard card : buildings) {
                    //bonusVal return 2 if the player
                    bonusVal *= card.checkDoubleStarRitual(player);
                    safeVal *= card.checkRitualSafe(player);
                }
                player.changePP(this.bonusPoints*bonusVal);
                player.changePP(-this.malusPoints* safeVal);
            }
            return;
        }

        for(Player player : players){
            if(player.getStarCounter()==maxStars){
                List<BuildingCard> buildings = player.getPlayerBuildings();
                int bonusVal=1;
                for (BuildingCard card : buildings) {
                    //bonusVal return 2 if the player
                    bonusVal *= card.checkDoubleStarRitual(player);
                }
                player.changePP(this.bonusPoints*bonusVal);
            }

            if(player.getStarCounter()==minStars){
                List<BuildingCard> buildings = player.getPlayerBuildings();
                int safeVal=1;
                for (BuildingCard card : buildings) {
                    safeVal *= card.checkRitualSafe(player);
                }
                player.changePP(-this.malusPoints* safeVal);
            }
        }
    }
}
