package it.polimi.ingsw.exceptions;

public class DeckLoadException extends Exception {
    public DeckLoadException(String message) {
        super(message);
    }
    public DeckLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
