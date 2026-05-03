package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.InventorIcon;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.model.state.NotPlaying;
import it.polimi.ingsw.model.state.PlayerState;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class Player implements Serializable {
    /**
     * Serialization version for the wire/snapshot format.
     */
    private static final long serialVersionUID = 1L;

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
    private int InventorPairs;
    private List<CharacterCard> playerHand;
    private List<BuildingCard> playerBuildings;
    private PlayerState playerState;

    /**
     * Constructs a new Player with the specified nickname and totem color.
     * Initializes resources and counters to zero, creates empty lists for cards and buildings,
     * and sets the initial state to NotPlaying.
     *
     * @param nickname the unique identifier for the player
     * @param color the TotemColor chosen by the player
     */
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
        this.InventorPairs = 0;
        this.hasBuilding = false;
        this.playerHand = new ArrayList<>();
        this.playerBuildings = new ArrayList<>();
        this.playerState = new NotPlaying();
    }

    /**
     * Modifies the player's prestige points by the specified amount.
     *
     * @param amount the number of prestige points to add (positive) or subtract (negative)
     */
    public void changePP(int amount) {
        this.prestigePoints += amount;
    }

    /**
     * Modifies the player's food reserve by the specified amount.
     * Prevents the food count from dropping below zero.
     *
     * @param amount the amount of food to add (positive) or consume (negative)
     */
    public void changeFood(int amount) {
        this.food += amount;

        // You cannot have negative food.
        if (this.food < 0) {
            this.food = 0;
        }
    }

    /**
     * Checks if the player possesses at least the specified amount of food.
     *
     * @param cost the amount of food required
     * @return true if the player has enough food, false otherwise
     */
    public boolean hasEnoughFood(int cost) {
        return this.food >= cost;
    }

    /**
     * Adds a drafted CharacterCard to the player's hand.
     *
     * @param cc the CharacterCard to be added
     */
    public void addCardToHand(CharacterCard cc) {
        this.playerHand.add(cc);
    }

    /**
     * Adds a BuildingCard to the player's built buildings.
     * Updates the player's status to indicate they now possess at least one building.
     *
     * @param cb the BuildingCard to be added
     */
    public void addBuildingToHand(BuildingCard cb) {
        this.playerBuildings.add(cb);
        this.hasBuilding = true;
    }

    /**
     * Increases the player's builder counter by the specified amount.
     *
     * @param amount the value to add to the current builder counter
     */
    public void increaseBuilderCounter(int amount) {
        this.builderCounter += amount;
    }

    /**
     * Increases the player's gatherer counter by the specified amount.
     *
     * @param amount the value to add to the current gatherer counter
     */
    public void increaseGathererCounter(int amount) {
        this.gathererCounter += amount;
    }

    /**
     * Increases the player's inventor counter by the specified amount.
     *
     * @param amount the value to add to the current inventor counter
     */
    public void increaseInventorCounter(int amount) {
        this.inventorCounter += amount;
    }

    /**
     * Increases the player's painter counter by the specified amount.
     *
     * @param amount the value to add to the current painter counter
     */
    public void increasePainterCounter(int amount) {
        this.painterCounter += amount;
    }

    /**
     * Increases the player's hunter counter by the specified amount.
     *
     * @param amount the value to add to the current hunter counter
     */
    public void increaseHunterCounter(int amount) {
        this.hunterCounter += amount;
    }

    /**
     * Increases the player's shaman counter by the specified amount.
     *
     * @param amount the value to add to the current shaman counter
     */
    public void increaseShamanCounter(int amount) {
        this.shamanCounter += amount;
    }

    /**
     * Increases the player's star counter by the specified amount.
     *
     * @param amount the value to add to the current star counter
     */
    public void increaseStars(int amount) {
        this.starCounter += amount;
    }

    /**
     * Increases the player's building discount (provided by builders) by the specified amount.
     *
     * @param amount the value to add
     */
    public void increaseBuildingDiscount(int amount) {
        this.buildingDiscountBuilder += amount;
    }

    /**
     * Increases the player's food discount (provided by gatherers) by the specified amount.
     *
     * @param amount the value to add
     */
    public void increaseFoodDiscount(int amount) {
        this.foodDiscountGatherer += amount;
    }

    /**
     * Increases the player's prestige points bonus for builders by the specified amount.
     *
     * @param amount the value to add
     */
    public void increasePPBuilderBonus(int amount) {
        this.ppBuilderBonus += amount;
    }

    /**
     * Increases the player's counter for sets of 6 different professions by the specified amount.
     *
     * @param amount the value to add
     */
    public void increaseSixSetCounter(int amount) {
        this.sixSetCounter += amount;
    }
    public void increaseInventorPairs(int amount) {this.InventorPairs += amount;}

    /**
     * Updates the current state of the player during the match.
     *
     * @param newState the new PlayerState to assign to the player
     */
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

    public int getPPBuilderBonus() {
        return ppBuilderBonus;
    }

    /**
     * Returns the prestige-point bonus this player gets from owning the
     * special "upper-row building" at end-of-game scoring time.
     * <p>
     * <b>STUB</b>: this is a placeholder so the project compiles. The
     * intended logic is to walk {@link #playerBuildings} and check each
     * one's {@link BuildingCard#hasUpperRowBuilding(Player)}; for now we
     * return {@code 0} so the call in {@code Match#resolveEndMatch()}
     * adds nothing. Replace the body when you wire up the real bonus
     * value.
     *
     * @return the end-of-game bonus contributed by the upper-row
     *         building (currently always {@code 0})
     */
    public int hasUpperRowBuilding() {
        return 0;
    }

    public int getSixSetCounter() {
        return sixSetCounter;
    }

    public int getInventorPairs() { return InventorPairs; }

    //Return the number of inventorPair present at the moment in the player hand
    public int countInventorPair(){
        EnumMap<InventorIcon, Integer> iconCounts = new EnumMap<>(InventorIcon.class);
        for(CharacterCard card : this.playerHand){
            InventorIcon inventorIcon=card.getInventorIcon();
            if(inventorIcon!=null){
                iconCounts.merge(inventorIcon, 1, Integer::sum);
            }
        }
        return iconCounts.values().stream().mapToInt(count->count/2).sum();
    }

    public int countDistinctInventorIcons() {
        java.util.Set<InventorIcon> distinctIcons = new java.util.HashSet<>();
        for (CharacterCard card : this.playerHand) {
            InventorIcon icon = card.getInventorIcon();
            if (icon != null) {
                distinctIcons.add(icon);
            }
        }
        return distinctIcons.size();
    }
}