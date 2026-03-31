package it.polimi.ingsw.model;

public class TurnOrderSlot {
    private int food;
    private int PP;
    private String nickname;

    public boolean isOccupied() {
        return false;
    }

    public boolean placeTotem(Player p, int positionIndex) {
        return false;
    }

    public boolean removeTotem() {
        return false;
    }
}
