package it.polimi.ingsw.model;

import java.io.Serializable;

public class TurnOrderTile implements Serializable {
    /**
     * Serialization version for the wire/snapshot format.
     */
    private static final long serialVersionUID = 1L;


    // [playerCount-2][slotIndex] = {food, PP}
    private static final int[][][] SLOT_VALUES = {
            { { 1, 0 }, { -1, -2 } }, // 2 players
            { { 2, 0 }, { 0, 0 }, { -1, -2 } }, // 3 players
            { { 2, 0 }, { 1, 0 }, { 0, 0 }, { -1, -2 } }, // 4 players
            { { 3, 0 }, { 1, 0 }, { 0, 0 }, { 0, 0 }, { -1, -2 } } // 5 players
    };

    private TurnOrderSlot[] turnOrderSlots;


    public TurnOrderSlot[] getTurnOrderSlots() {
        return turnOrderSlots;
    }


    /**
     * Creates a TurnOrderTile and initializes its slots for the given number of players.
     *
     * @param playersNumber the number of players (2–5)
     * @throws IllegalArgumentException if playersNumber is not between 2 and 5
     */
    public TurnOrderTile(int playersNumber) {
        if (playersNumber < 2 || playersNumber > 5) {
            throw new IllegalArgumentException("Player count must be between 2 and 5, got: " + playersNumber);
        }
        createTurnOrderSlots(playersNumber);
    }


    /**
     * Returns the index of the turn order slot occupied by the given player,
     * or -1 if the player has no totem on the tile.
     *
     * @param p the Player to search for
     * @return the slot index, or -1 if not found
     */
    private int getPlayersTurnOrderSlotIndex(Player p) {
        for (int i = 0; i < turnOrderSlots.length; i++) {
            if (turnOrderSlots[i].isOccupied() && turnOrderSlots[i].getTotemColor().equals(p.getPlayerColor())) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Initializes the turn order slots based on the number of players.
     * Each slot is assigned predefined food and PP bonus values that vary
     * by both slot position and total player count, as defined in
     * {@link #SLOT_VALUES}.
     */
    private void createTurnOrderSlots(int playersNumber) {
        int[][] values = SLOT_VALUES[playersNumber - 2];
        turnOrderSlots = new TurnOrderSlot[values.length];
        for (int i = 0; i < values.length; i++) {
            turnOrderSlots[i] = new TurnOrderSlot(values[i][0], values[i][1]);
        }
    }





    /**
     * Returns the food bonus for the turn order slot occupied by the given player.
     *
     * @param p the Player whose slot to look up
     * @return the food bonus of the player's slot
     * @throws IllegalArgumentException if the player has no totem on the tile
     */
    public int addFood(Player p) {
        int index = getPlayersTurnOrderSlotIndex(p);
        if (index == -1) {
            throw new IllegalArgumentException("Player " + p.getNickname() + " has no totem on the turn order tile.");
        }
        return turnOrderSlots[index].getFood();
    }

    /**
     * Returns the PP bonus for the turn order slot occupied by the given player.
     *
     * @param p the Player whose slot to look up
     * @return the PP bonus of the player's slot
     * @throws IllegalArgumentException if the player has no totem on the tile
     */
    public int addPP(Player p) {
        int index = getPlayersTurnOrderSlotIndex(p);
        if (index == -1) {
            throw new IllegalArgumentException("Player " + p.getNickname() + " has no totem on the turn order tile.");
        }
        return turnOrderSlots[index].getPP();
    }



    /**
     * Places a totem (player's color) on the specified slot in the turn order tile.
     * If the target slot is already occupied, the totem is not placed and the method returns false.
     * Otherwise, sets the player's totem color in the slot and returns true.
     *
     * @param p the Player whose totem should be placed
     * @param position_index the index of the slot in which to place the totem
     * @return true if the totem was successfully placed, false if the slot is already occupied
     * @throws IndexOutOfBoundsException if position_index is out of range
     */
    public boolean placeTotem(Player p, int position_index) {
        if (position_index < 0 || position_index >= turnOrderSlots.length) {
            throw new IndexOutOfBoundsException("Invalid slot index: " + position_index + ", must be between 0 and " + (turnOrderSlots.length - 1));
        }
        if (turnOrderSlots[position_index].isOccupied()) {
            return false;
        }
        turnOrderSlots[position_index].setTotemColor(p.getPlayerColor());
        return true;
    }



    /**
     * Removes the player's totem from the turn order tile.
     * Searches through the turnOrderSlots to find a slot occupied by the player with the given totem color.
     * If found, removes the totem (by clearing the totem color from the slot) and returns true.
     * If the player is not found in any slot, returns false.
     *
     * @param p the Player whose totem should be removed
     * @return true if the player's totem was removed, false otherwise
     */
    public boolean clearSlot(Player p) {
        int index = getPlayersTurnOrderSlotIndex(p);
        if (index == -1) {
            return false;
        }
        return turnOrderSlots[index].removeTotem();
    }

}
