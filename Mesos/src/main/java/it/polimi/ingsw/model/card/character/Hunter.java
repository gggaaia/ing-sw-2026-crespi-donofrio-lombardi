package it.polimi.ingsw.model.card.character;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;

public class Hunter extends CharacterCard {
    private boolean foodBonus;

    public Hunter(int cardID, Era era, int minPlayers, boolean foodBonus) {
        super(cardID, era, minPlayers);
        this.foodBonus = foodBonus;
    }

    public boolean isFoodBonus() {
        return foodBonus;
    }

    public void setFoodBonus(boolean foodBonus) {
        this.foodBonus = foodBonus;
    }

    public void drawEffect(Player player) {
        player.changeFood(foodBonus ? 1 : 0);
    }

}
