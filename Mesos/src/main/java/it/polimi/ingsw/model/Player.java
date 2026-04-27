package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.model.state.NotPlaying;
import it.polimi.ingsw.model.state.PlayerState;
import java.util.ArrayList;
import java.util.List;

public class Player {
    private final String nickname;
    private TotemColor playerColor;
    private int prestigePoints;
    private int food;
    private boolean hasBuilding;
    private int builderCounter;
    private int gathererCounter;
    private int inventorCounter;
    private int painterCounter;
    private int hunterCounter;
    private int shamanCounter;
    private int starCounter;
    private int buildingDiscountBuilder;
    private int foodDiscountGatherer;
    private int ppBuilderBonus;
    private int sixSetCounter;
    private List<CharacterCard> playerHand;
    private List<BuildingCard> playerBuildings;
    private PlayerState playerState;


    public Player(String nickname, TotemColor color) {
        this.nickname = nickname;
        this.playerColor = color;
        this.prestigePoints = 0;
        this.food = 0;
        this.builderCounter = 0;
        this.gathererCounter = 0;
        this.inventorCounter = 0;
        this.painterCounter = 0;
        this.hunterCounter = 0;
        this.shamanCounter = 0;
        this.starCounter = 0;
        this.buildingDiscountBuilder = 0;
        this.foodDiscountGatherer = 0;
        this.ppBuilderBonus = 0;
        this.sixSetCounter = 0;
        this.hasBuilding = false;
        this.playerHand = new ArrayList<>();
        this.playerBuildings = new ArrayList<>();
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

    public void addCardToHand(CharacterCard cc) {
        this.playerHand.add(cc);
    }

    public void addBuildingToHand(BuildingCard cb) {
        this.playerBuildings.add(cb);
        this.hasBuilding = true;
    }

    public void increaseBuilderCounter(int amount) {
        this.builderCounter += amount;
    }

    public void increaseGathererCounter(int amount) {
        this.gathererCounter += amount;
    }

    public void increaseInventorCounter(int amount) {
        this.inventorCounter += amount;
    }

    public void increasePainterCounter(int amount) {
        this.painterCounter += amount;
    }

    public void increaseHunterCounter(int amount) {
        this.hunterCounter += amount;
    }

    public void increaseShamanCounter(int amount) {
        this.shamanCounter += amount;
    }

    public void increaseStars(int amount) {
        this.starCounter += amount;
    }

    public void increaseBuildingDiscount(int amount) {
        this.buildingDiscountBuilder += amount;
    }

    public void increaseFoodDiscount(int amount) {
        this.foodDiscountGatherer += amount;
    }

    public void increasePPBuilderBonus(int amount) {
        this.ppBuilderBonus += amount;
    }

    public void increaseSixSetCounter(int amount) {
        this.sixSetCounter += amount;
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

    public PlayerState getPlayerState() {
        return playerState;
    }

    public boolean hasBuilding() {
        return hasBuilding;
    }

    public List<CharacterCard> getPlayerHand() {
        return new ArrayList<>(this.playerHand);
    }

    public List<BuildingCard> getPlayerBuildings() {
        return new ArrayList<>(this.playerBuildings);
    }

    public int getBuilderCounter() {
        return builderCounter;
    }

    public int getGathererCounter() {
        return gathererCounter;
    }

    public int getInventorCounter() {
        return inventorCounter;
    }

    public int getPainterCounter() {
        return painterCounter;
    }

    public int getHunterCounter() {
        return hunterCounter;
    }

    public int getShamanCounter() {
        return shamanCounter;
    }

    public int getStarCounter() {
        return starCounter;
    }

    public int getBuildingDiscountBuilder() {
        return buildingDiscountBuilder;
    }

    public int getFoodDiscountGatherer() {
        return foodDiscountGatherer;
    }

    public int getPpBuilderBonus() {
        return ppBuilderBonus;
    }

    public int getSixSetCounter() {
        return sixSetCounter;
    }
}
