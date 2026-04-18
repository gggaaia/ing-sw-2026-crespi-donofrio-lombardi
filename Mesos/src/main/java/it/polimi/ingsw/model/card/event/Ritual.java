package it.polimi.ingsw.model.card.event;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.EventCard;
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

    @Override
    public void applyEffect(List<Player> players) {
        int maxStars = players.stream()
                .mapToInt(Player::getStars)
                .max()
                .orElse(0);

        int minStars = players.stream()
                .mapToInt(Player::getStars)
                .min()
                .orElse(0);

        if(maxStars == minStars){
            for(Player player : players){
                player.changePP(this.bonusPoints);
                player.changePP(-this.malusPoints);
            }
            return;
        }

        for(Player player : players){
            if(player.getStars()==maxStars){
                player.changePP(this.bonusPoints);
            }

            if(player.getStars()==minStars){
                player.changePP(-this.malusPoints);
            }
        }
    }
}
