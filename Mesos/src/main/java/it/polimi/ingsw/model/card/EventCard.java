package it.polimi.ingsw.model.card;

import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.Player;
import java.util.List;


public abstract class EventCard extends TribeCard {
    public EventCard(int cardID, Era era, int minPlayers){
        super(cardID, era, minPlayers);
    }

    public abstract void applyEvent(List<Player> players);

    @Override
    public void addToRow(CardRow row){
        row.addEventCard(this);
    }

    @Override
    public boolean firstRoundChangeRow(CardRow row) {
        row.addEventCard(this);
        return true;
    }

}
