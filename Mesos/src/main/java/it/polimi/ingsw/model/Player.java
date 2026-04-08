package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.character.Inventor;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.model.state.NotPlaying;
import it.polimi.ingsw.model.state.PlayerState;

import java.util.ArrayList;
import java.util.List;

public class Player {
    private final String nickname;
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

    // FIXME: remember to add color picking logic in controller when creating players
    //        need to make sure eachmatch has 5 colors to pick for and for each match new 5 colors should be created to pick for

    public Player(String nickname, TotemColor color){
        this.nickname = nickname;
        this.color = color;
        this.prestigePoints = 0;
        this.food = 0;
        this.hasBuilding = false;
        this.redux = 0;
        this.starCounter = 0;
        this.playerHand = new ArrayList<>();
        this.usedInventors = new ArrayList<>();
        this.usedSets = new ArrayList<>();
        this.playerState = new NotPlaying();
    }

    public String getNickname() {
        return nickname;
    }

    // FIXME: should add `pp: int` as parameter?
    public void changePP() {
    }

    // FIXME: should add `food: int` as parameter?
    public void changeFood() {
    }

    // FIXME: should add `redux: int` as parameter?
    public void changeRedux() {
    }

    // FIXME: should add `stars: int` as parameter?
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
