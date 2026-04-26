package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.enums.Era;

import java.util.stream.IntStream;

public class SixSet extends BuildingCard {
    public SixSet(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP, owner);
    }

    @Override
    public void applyEffect(Player player) {
        if (player == getOwner()) {
            int min = IntStream.of(
                    player.getBuilderCounter(),
                    player.getGathererCounter(),
                    player.getArtistCounter(),
                    player.getHunterCounter(),
                    player.getShamanCounter(),
                    player.getInventorCounter()
            ).min().orElse(0);
            int newSet = min - player.getSixSetCounter();

            player.changeFood((newSet > 0) ? 5 * newSet : 0);
            player.increaseSixSetCounter(newSet);
        }
    }
}