package it.polimi.ingsw.model.card.character;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;

public class Shaman extends CharacterCard {
    private int stars;

    public Shaman(int cardID, Era era, int minPlayers, int stars) {
        super(cardID, era, minPlayers);
        this.stars = stars;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public void drawEffect(Player player) {
        player.increaseShamanCounter(1);
        player.increaseStars(stars);
    }
}
