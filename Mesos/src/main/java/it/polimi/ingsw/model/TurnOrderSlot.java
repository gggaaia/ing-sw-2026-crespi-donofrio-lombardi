package it.polimi.ingsw.model;

import it.polimi.ingsw.model.enums.TotemColor;

public class TurnOrderSlot {
    private int food;
    private int PP;
    private TotemColor totemColor;

    /**
     * Constructs a TurnOrderSlot with specified food and PP bonuses.
     * The slot is initialized as unoccupied (no totem).
     *
     * @param food the food bonus associated with the slot
     * @param PP the PP (Power Point) bonus associated with the slot
     */
    public TurnOrderSlot(int food, int PP) {
        this.food = food;
        this.PP = PP;
        this.totemColor = null;
    }

    public int getFood() {
        return food;
    }

    public int getPP() {
        return PP;
    }

    public TotemColor getTotemColor() {
        return totemColor;
    }


    public void setTotemColor(TotemColor totemColor) {
        this.totemColor = totemColor;
    }


    /**
     * Checks if this turn order slot is occupied by a player's totem.
     *
     * @return true if the slot is occupied (totemColor is not null), false otherwise
     */
    public boolean isOccupied() {
        return totemColor != null;
    }


    /**
     * Removes the totem from this turn order slot.
     * If the slot is not occupied, returns false.
     * If the slot is occupied, removes the totem and returns true.
     *
     * @return true if the totem was removed, false if the slot was not occupied
     */
    public boolean removeTotem() {
        if (!isOccupied()) {
            return false;
        }
        this.totemColor = null;
        return true;
    }
}
