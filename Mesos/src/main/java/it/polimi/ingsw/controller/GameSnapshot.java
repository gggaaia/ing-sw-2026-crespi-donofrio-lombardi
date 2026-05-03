package it.polimi.ingsw.controller;

import it.polimi.ingsw.model.Match;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.network.shared.GamePhase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable bundle of everything {@link PersistenceManager} writes to disk.
 * <p>
 * Pass 2 only persists snapshots that were taken during in-match phases
 * (PICK_OFFERING_TILE and beyond). Lobby snapshots are not useful: if the
 * server crashes during the lobby, players have not committed yet, so we
 * just start fresh on restart.
 * <p>
 * Player references inside the snapshot point to the same {@link Player}
 * instances stored in {@link #match}; Java object serialization preserves
 * this identity within one {@code writeObject} call, so on load the
 * controller's scratch fields keep referencing the right player objects.
 */
public class GameSnapshot implements Serializable {
    /**
     * Serialization version. Bump only on backwards-incompatible changes
     * to the field layout.
     */
    private static final long serialVersionUID = 1L;

    /** The whole match (board, decks, players, round, era, ...). */
    public final Match match;

    /** Phase the controller was in when the snapshot was taken. */
    public final GamePhase phase;

    /** If {@link #phase} is {@code PAUSED_WAITING_RECONNECT}, the phase we
     *  should resume to once enough players reconnect. {@code null} when
     *  not paused. */
    public final GamePhase phaseBeforePause;

    /** Nickname of the player whose input was being awaited at snapshot
     *  time, or {@code null} if no specific player was awaited. */
    public final String currentPlayerNickname;

    /** Snapshot of the per-round offering-tile picker order (in turn-order
     *  tile slot order). */
    public final List<Player> turnOrderForOfferingPick;

    /** Index into {@link #turnOrderForOfferingPick}. */
    public final int currentOfferingPickIndex;

    /** Snapshot of the in-progress resolution order (in offering-track
     *  order). */
    public final List<Player> resolutionOrder;

    /** Index into {@link #resolutionOrder}. */
    public final int currentResolutionIndex;

    /** Per-turn budgets at snapshot time. See
     *  {@code GameController}'s same-named fields for semantics. */
    public final int currentUpperBudget;
    public final int currentLowerBudget;
    public final int currentUpperRemainingForBuilding;
    public final int currentLowerRemainingForBuilding;

    /** Player who owns the special "extra pick" building (only relevant
     *  during {@code RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING}). */
    public final Player extraPickPlayer;

    /**
     * Builds an immutable snapshot. The controller calls this with all of
     * its current fields right before passing the result to
     * {@link PersistenceManager#save(GameSnapshot)}.
     */
    public GameSnapshot(
            Match match,
            GamePhase phase,
            GamePhase phaseBeforePause,
            String currentPlayerNickname,
            List<Player> turnOrderForOfferingPick,
            int currentOfferingPickIndex,
            List<Player> resolutionOrder,
            int currentResolutionIndex,
            int currentUpperBudget,
            int currentLowerBudget,
            int currentUpperRemainingForBuilding,
            int currentLowerRemainingForBuilding,
            Player extraPickPlayer
    ) {
        // Defensive copies for the lists so the snapshot doesn't share
        // mutable state with the caller after construction.
        this.match = match;
        this.phase = phase;
        this.phaseBeforePause = phaseBeforePause;
        this.currentPlayerNickname = currentPlayerNickname;
        this.turnOrderForOfferingPick = (turnOrderForOfferingPick == null)
                ? new ArrayList<Player>()
                : new ArrayList<Player>(turnOrderForOfferingPick);
        this.currentOfferingPickIndex = currentOfferingPickIndex;
        this.resolutionOrder = (resolutionOrder == null)
                ? new ArrayList<Player>()
                : new ArrayList<Player>(resolutionOrder);
        this.currentResolutionIndex = currentResolutionIndex;
        this.currentUpperBudget = currentUpperBudget;
        this.currentLowerBudget = currentLowerBudget;
        this.currentUpperRemainingForBuilding = currentUpperRemainingForBuilding;
        this.currentLowerRemainingForBuilding = currentLowerRemainingForBuilding;
        this.extraPickPlayer = extraPickPlayer;
    }
}
