package it.polimi.ingsw.model;

public class TurnOrderSlot {
    private int food;
    private int PP;
    private String nickname;

    /**
     * Constructs a TurnOrderSlot with specified food and PP bonuses.
     * The slot is initialized as unoccupied (no nickname).
     *
     * @param food the food bonus associated with the slot
     * @param PP the PP (Power Point) bonus associated with the slot
     */
    public TurnOrderSlot(int food, int PP) {
        this.food = food;
        this.PP = PP;
        this.nickname = null;
    }

    public int getFood() {
        return food;
    }

    public int getPP() {
        return PP;
    }

    public String getNickname() {
        return nickname;
    }


    public void setNickname(String nickname) {
        this.nickname = nickname;
    }


    /**
     * Checks if this turn order slot is occupied by a player's totem (nickname).
     *
     * @return true if the slot is occupied (nickname is not null), false otherwise
     */
    public boolean isOccupied() {
        return nickname != null;
    }


    /**
     * Removes the totem (player's nickname) from this turn order slot.
     * If the slot is not occupied, returns false.
     * If the slot is occupied, removes the nickname and returns true.
     *
     * @return true if the totem was removed, false if the slot was not occupied
     */
    public boolean removeTotem() {
        if (!isOccupied()) {
            return false;
        }
        this.nickname = null;
        return true;
    }
}
