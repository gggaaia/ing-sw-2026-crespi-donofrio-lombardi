package it.polimi.ingsw.model.card.character;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;

public class Artist extends CharacterCard {
    public Artist(int cardID, Era era, int minPlayers) {
        super(cardID, era, minPlayers);
    }

    public void drawEffect(Player player) {
        player.increaseCounterArtist(1);
    }
}
