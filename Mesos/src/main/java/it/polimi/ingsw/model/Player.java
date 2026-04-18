package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.character.Inventor;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.model.state.NotPlaying;
import it.polimi.ingsw.model.state.PlayerState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Player {
    private final String nickname;
    private TotemColor playerColor;
    private int prestigePoints;
    private int food;
    private boolean hasBuilding;
    private int redux;
    private int starCounter;
    private List<Card> playerHand;
    private List<Inventor> usedInventors;
    private List<CharacterCard> usedSets;
    private PlayerState playerState;


    public Player(String nickname, TotemColor color) {
        this.nickname = nickname;
        this.playerColor = color;
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

    public void changePP(int amount) {
        this.prestigePoints += amount;
    }

    public void changeFood(int amount) {
        this.food += amount;

        // You cannot have negative food.
        if (this.food < 0) {
            this.food = 0;
        }
    }

    public boolean hasEnoughFood(int cost) {
        return this.food >= cost;
    }

    public void changeRedux(int amount) {
        this.redux += amount;
    }

    public void increaseStars(int amount) {
        this.starCounter += amount;
        if (this.starCounter < 0) {
            this.starCounter = 0;
        }
    }

    public void addCardToHand(Card c) {
        this.playerHand.add(c);
        //flags the boolean if the card is a building
        if (c instanceof BuildingCard) {
            this.hasBuilding = true;
        }
    }

    public int findSet() {
        int setsFormed = 0;
        boolean canFormMoreSets = true;

        while (canFormMoreSets) {
            List<CharacterCard> currentSet = new ArrayList<>();
            List<Class<? extends CharacterCard>> uniqueTypesInSet = new ArrayList<>();

            for (Card c : this.playerHand) {
                //if set isnt full
                if (currentSet.size() < 6) {
                    if (c instanceof CharacterCard) {
                        CharacterCard charCard = (CharacterCard) c;
                        if (!this.usedSets.contains(charCard) && !uniqueTypesInSet.contains(charCard.getClass())) {
                            currentSet.add(charCard);
                            uniqueTypesInSet.add(charCard.getClass());
                        }
                    }

                    if (currentSet.size() == 6) {
                        this.usedSets.addAll(currentSet);
                        setsFormed++;
                    }
                }
            }

            if (currentSet.size() < 6) {
                canFormMoreSets = false;
            }
        }

        return setsFormed;
    }

    public int findInventorPairs() {
        int pairsFormed = 0;
        List<Inventor> currentPair = new ArrayList<>();

        for (Card c : this.playerHand) {


            if (c instanceof Inventor) {
                Inventor inventorCard = (Inventor) c;

                if (!this.usedInventors.contains(inventorCard)) {
                    currentPair.add(inventorCard);

                    if (currentPair.size() == 2) {
                        this.usedInventors.addAll(currentPair);
                        pairsFormed++;

                        currentPair.clear();
                    }
                }
            }
        }

        return pairsFormed;
    }

    public void setPlayerState(PlayerState newState) {
        this.playerState = newState;
    }

    public String getNickname() {
        return nickname;
    }

    public TotemColor getPlayerColor() {
        return playerColor;
    }

    public int getPrestigePoints() {
        return prestigePoints;
    }

    public int getFood() {
        return food;
    }

    public int getRedux() {
        return redux;
    }

    public int getStarCounter() {
        return starCounter;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }

    public boolean hasBuilding() {
        return hasBuilding;
    }

    public List<Card> getPlayerHand() {
        return new ArrayList<>(this.playerHand);
    }
}
