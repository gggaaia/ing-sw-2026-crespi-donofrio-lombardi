package it.polimi.ingsw.model.card.event;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.card.character.Artist;
import it.polimi.ingsw.model.enums.Era;

import java.util.List;

public class Paintings extends EventCard {
    private int minPainters;
    private int bonusPoints;
    public Paintings(int cardID, Era era, int minPlayers, int minPainters,  int bonusPoints) {
        super(cardID, era, minPlayers);
        this.minPainters = minPainters;
        this.bonusPoints = bonusPoints;
    }

    public int getMinPainters() {
        return minPainters;
    }
    public int getBonusPoints() {
        return bonusPoints;
    }

    public void setMinPainters(int minPainters) {
        this.minPainters = minPainters;
    }
    public void setBonusPoints(int bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    @Override
    public void applyEvent(List<Player> players) {
        for (Player player : players) {
            int count = player.getPainterCounter();
            if (count >= minPainters) {
                player.changePP(this.bonusPoints*count);
            }
            else { player.changePP(-2); }
            List<BuildingCard> buildings = player.getPlayerBuildings();
            for (BuildingCard card : buildings) {
                player.changeFood(card.hasPaintingBonus(player)? player.getPainterCounter() : 0);
            }
        }
    }
}
