package it.polimi.ingsw.model.state;

import java.io.Serializable;

/**
 * Marker base class for the per-player state machine.
 * <p>
 * Implements {@link Serializable} because every {@link it.polimi.ingsw.model.Player}
 * carries a {@code PlayerState} field that is sent over the wire (and saved
 * to disk by Pass 2's persistence layer).
 */
public abstract class PlayerState implements Serializable {
    /**
     * Serialization version. Inherited by all subclasses; bump only on a
     * backwards-incompatible field-layout change.
     */
    private static final long serialVersionUID = 1L;
}
