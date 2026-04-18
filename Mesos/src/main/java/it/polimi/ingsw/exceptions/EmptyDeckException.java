package it.polimi.ingsw.exceptions;

public class EmptyDeckException extends Exception {
    public EmptyDeckException(String message) {
        super(message);
    }
    public EmptyDeckException(String message, Throwable cause) {
        super(message, cause);
    }
}
