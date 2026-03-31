package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.character.Inventor;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.model.state.PlayerState;

import java.util.List;

public class Player {
    private String nickname;
    private TotemColor color;
    private int prestigePoints;
    private int food;
    private boolean hasBuilding;
    private int redux;
    private int starCounter;
    private List<Card> playerHand;
    private List<Inventor> usedInventors;
    private List<CharacterCard> usedSets;
    private PlayerState playerState;

    public void changePP() {
    }

    public void changeFood() {
    }

    public void changeRedux() {
    }

    public void increaseStars() {
    }

    public void addCardToHand(Card c) {
    }

    public int findSet() {
        return 0;
    }

    public int findInventorPairs() {
        return 0;
    }
}
