package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.Era;

import java.util.stream.IntStream;

public class UniqueSixSet extends EndOfMatchBuilding{
    public UniqueSixSet(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP, owner);
    }

    @Override
    public void applyEndEffect(Player player) {
        if(player==getOwner()){
            int min = IntStream.of(
                    player.getBuilderCounter(),
                    player.getGathererCounter(),
                    player.getPainterCounter(),
                    player.getHunterCounter(),
                    player.getShamanCounter(),
                    player.getInventorCounter()
            ).min().orElse(0);
            player.changePP(min*6);
        }
    }
}
