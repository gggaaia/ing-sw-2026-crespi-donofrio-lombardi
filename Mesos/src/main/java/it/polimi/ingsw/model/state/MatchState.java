package it.polimi.ingsw.model.state;

import it.polimi.ingsw.model.Match;
import it.polimi.ingsw.model.Player;

import java.io.Serializable;

/**
 * Base class of the per-match state hierarchy
 * ({@link LobbyState} / {@link ActiveState} / {@link FinishedState}).
 * <p>
 * Implements {@link Serializable} because every {@link Match} carries a
 * {@code MatchState} field that is written to disk by the Pass 2
 * persistence layer (and may travel over the wire as part of
 * {@code GameStateDto.board}'s reachable graph in the future).
 * Subclasses inherit Serializability and only need to declare their own
 * {@code serialVersionUID} if they introduce backwards-incompatible
 * fields.
 */
public abstract class MatchState implements Serializable {
    /**
     * Serialization version. Inherited by every subclass.
     */
    private static final long serialVersionUID = 1L;

    public abstract void addPlayer(Match m, Player p);
    public abstract void removePlayer(Match m, Player p);
    public abstract void startMatch(Match m);
    public abstract void endMatch(Match m);
}
