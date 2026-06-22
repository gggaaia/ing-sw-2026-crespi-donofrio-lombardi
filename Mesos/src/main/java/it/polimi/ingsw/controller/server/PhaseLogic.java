package it.polimi.ingsw.controller.server;

import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.dto.Phase;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.List;

/**
 * Per-phase logic for the {@code ServerController}. One abstract base
 * + 11 nested classes, one per game phase.
 *
 * Each concrete phase overrides only the actions it actually accepts;
 * the rest just inherit the default "reject". Per-phase dispatch is
 * polymorphic, no explicit type checks needed.
 *
 * The {@code ServerController} keeps a {@code PhaseLogic phase} field
 * that gets swapped whenever the phase changes. When an action comes
 * in, the controller calls {@code phase.onAction(this, ...)} which
 * dispatches to whatever concrete subclass is currently active.
 *
 * Concrete subclasses don't do fine-grained state validation or
 * model mutations: they delegate to the {@code ServerController}
 * through {@code accept*} methods that own the real validation +
 * mutations. The phase classes only answer one question: "is this
 * method allowed in this phase?".
 *
 * Every subclass carries its own {@link Phase} DTO (passed to the
 * base constructor) and exposes it via {@link #getPhaseDto()}. That
 * way a {@code GameSnapshot} can fill its phase field without
 * having to inspect the subclass type.
 */
public abstract class PhaseLogic {

    private final Phase phaseDto;

    protected PhaseLogic(Phase phaseDto) {
        this.phaseDto = phaseDto;
    }

    /** Matching {@link Phase} value, used inside {@code GameSnapshot}. */
    public Phase getPhaseDto() {
        return phaseDto;
    }

    // === default: every action is rejected ===

    public void onConnect(ServerController c, String nick, VirtualView view) {
        // If someone connects while the game is already running, the
        // only ok case is a player who was in the game and is coming
        // back. So if this nickname is one of the players, let them
        // reconnect. If not, it is a stranger, so we tell them they
        // cannot join (the client shows the error and asks again).
        if (c.isActiveMatchMember(nick)) {
            c.acceptReconnect(nick, view);
        } else {
            c.rejectNickname(nick, view, "cannot join right now");
        }
    }

    public void onDisconnect(ServerController c, String nick) {
        // Disconnect is always accepted; the controller takes care of the cascade.
        c.acceptDisconnect(nick);
    }

    public void onSetPlayerCount(ServerController c, String nick, int count) {
        c.rejectAction(nick, "cannot set player count right now");
    }

    public void onPickColor(ServerController c, String nick, TotemColor color) {
        c.rejectAction(nick, "cannot pick color right now");
    }

    public void onPickOfferingTile(ServerController c, String nick, int tileIndex) {
        c.rejectAction(nick, "cannot pick offering tile right now");
    }

    public void onPickCharacterCards(ServerController c, String nick,
                                     List<Integer> upper, List<Integer> lower) {
        c.rejectAction(nick, "cannot pick character cards right now");
    }

    public void onPickBuildingCards(ServerController c, String nick,
                                    List<Integer> upper, List<Integer> lower) {
        c.rejectAction(nick, "cannot buy buildings right now");
    }

    public void onPickExtraCard(ServerController c, String nick,
                                Integer cardId, boolean isCharacter) {
        c.rejectAction(nick, "cannot pick extra card right now");
    }

    /**
     * Only true on {@code PausedWaitingReconnect}. Used by the
     * lone-player timer to check we're still paused before forcing
     * a win.
     */
    public boolean isPaused() {
        return false;
    }

    /**
     * True only for phases that meaningfully accept new connects:
     * the very first connect ({@code WaitingForNickname}), additional
     * lobby joins ({@code WaitingForPlayers}), and reconnects while
     * paused ({@code PausedWaitingReconnect}).
     *
     * Network adapters consult this BEFORE doing the nickname registry
     * check, so a duplicate nickname coming in during a phase that
     * rejects everyone (e.g. {@code WaitingForPlayerCount}) gets the
     * proper "no game currently accepting players" error instead of
     * the misleading "nickname already in use" — and, critically, the
     * duplicate never registers, so it can't later disconnect and kick
     * the legitimate holder out.
     */
    public boolean acceptsNewConnections() {
        return false;
    }

    /**
     * Only true on {@code GameEnded}. Used by {@code acceptDisconnect}
     * to avoid bouncing the phase back to pause after the game is
     * already over.
     */
    public boolean isFinished() {
        return false;
    }

    /**
     * Called after the current player has dropped (or right after a
     * resume from pause). Phases that have a "current actor" to skip
     * do that here; others stick with the default no-op.
     */
    public void advanceCurrent(ServerController c) {
        // default: no-op
    }

    /**
     * Re-sends the {@code *Turn} event matching the current phase to
     * a single client {@code nick}, so they can (if it's their turn)
     * re-prompt the user. Called after {@code onActionRejected} to
     * avoid leaving the client hanging without a prompt, and after
     * {@code resumeFromPause} / a reconnect's {@code onGameSnapshot}
     * so whoever comes back holding the turn gets prompted right
     * away.
     *
     * Phases without a {@code *Turn} (lobby, resolve, paused, ended)
     * just keep the default no-op.
     */
    public void emitCurrentTurnTo(ServerController c, String nick) {
        // default: no-op
    }

    // === concrete phases ===

    /** Initial phase: waiting for the first connect. */
    public static final class WaitingForNickname extends PhaseLogic {
        public WaitingForNickname() { super(Phase.WAITING_FOR_NICKNAME); }
        @Override
        public void onConnect(ServerController c, String nick, VirtualView view) {
            c.acceptFirstPlayerJoin(nick, view);
        }
        @Override
        public boolean acceptsNewConnections() { return true; }
    }

    /** The first player has to set the participant count. */
    public static final class WaitingForPlayerCount extends PhaseLogic {
        public WaitingForPlayerCount() { super(Phase.WAITING_FOR_PLAYER_COUNT); }
        @Override
        public void onConnect(ServerController c, String nick, VirtualView view) {
            // No match is accepting players until the first one has
            // set the count: just reject and the client shows the
            // error and closes the connection.
            c.rejectNickname(nick, view, "no game currently accepting players");
        }

        @Override
        public void onSetPlayerCount(ServerController c, String nick, int count) {
            c.acceptSetPlayerCount(nick, count);
        }
    }

    /** Waiting for more players to join. */
    public static final class WaitingForPlayers extends PhaseLogic {
        public WaitingForPlayers() { super(Phase.WAITING_FOR_PLAYERS); }
        @Override
        public void onConnect(ServerController c, String nick, VirtualView view) {
            c.acceptAdditionalPlayerJoin(nick, view);
        }
        @Override
        public boolean acceptsNewConnections() { return true; }
    }

    /** Color picking: everyone picks their colour in order. */
    public static final class ColorPicking extends PhaseLogic {
        public ColorPicking() { super(Phase.COLOR_PICKING); }
        @Override
        public void onPickColor(ServerController c, String nick, TotemColor color) {
            c.acceptColorPick(nick, color);
        }
        @Override
        public void emitCurrentTurnTo(ServerController c, String nick) {
            c.sendColorTurnTo(nick);
        }
    }

    /** Placing the totem on the offering track. */
    public static final class PickOfferingTile extends PhaseLogic {
        public PickOfferingTile() { super(Phase.PICK_OFFERING_TILE); }
        @Override
        public void onPickOfferingTile(ServerController c, String nick, int tileIndex) {
            c.acceptOfferingTilePick(nick, tileIndex);
        }
        @Override
        public void advanceCurrent(ServerController c) {
            c.advanceOfferingPicker();
        }
        @Override
        public void emitCurrentTurnTo(ServerController c, String nick) {
            c.sendOfferingTileTurnTo(nick);
        }
    }

    /** Drawing character cards. */
    public static final class TurnPickCardsCharacter extends PhaseLogic {
        public TurnPickCardsCharacter() { super(Phase.TURN_PICK_CARDS_CHARACTER); }
        @Override
        public void onPickCharacterCards(ServerController c, String nick,
                                         List<Integer> upper, List<Integer> lower) {
            c.acceptCharacterCardsPick(nick, upper, lower);
        }
        @Override
        public void advanceCurrent(ServerController c) {
            c.advanceResolver();
        }
        @Override
        public void emitCurrentTurnTo(ServerController c, String nick) {
            c.sendCharacterPickTurnTo(nick);
        }
    }

    /** Buying building cards. */
    public static final class TurnPickCardsBuilding extends PhaseLogic {
        public TurnPickCardsBuilding() { super(Phase.TURN_PICK_CARDS_BUILDING); }
        @Override
        public void onPickBuildingCards(ServerController c, String nick,
                                        List<Integer> upper, List<Integer> lower) {
            c.acceptBuildingCardsPick(nick, upper, lower);
        }
        @Override
        public void advanceCurrent(ServerController c) {
            c.advanceResolver();
        }
        @Override
        public void emitCurrentTurnTo(ServerController c, String nick) {
            c.sendBuildingPickTurnTo(nick);
        }
    }

    /** Optional extra pick for the owner of the special building. */
    public static final class ResetTurnOrderAndPickExtraBuilding extends PhaseLogic {
        public ResetTurnOrderAndPickExtraBuilding() { super(Phase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING); }
        @Override
        public void onPickExtraCard(ServerController c, String nick,
                                    Integer cardId, boolean isCharacter) {
            c.acceptExtraCardPick(nick, cardId, isCharacter);
        }
        @Override
        public void advanceCurrent(ServerController c) {
            c.skipExtraPick();
        }
        @Override
        public void emitCurrentTurnTo(ServerController c, String nick) {
            c.sendExtraPickTurnTo(nick);
        }
    }

    /** Automatic resolution of buildings + events. No input expected. */
    public static final class ResolveBuildingsAndEvents extends PhaseLogic {
        public ResolveBuildingsAndEvents() { super(Phase.RESOLVE_BUILDINGS_AND_EVENTS); }
        // everything stays on default reject; this phase passes
        // through without waiting on client input
    }

    /** Match paused, waiting for reconnects. */
    public static final class PausedWaitingReconnect extends PhaseLogic {
        public PausedWaitingReconnect() { super(Phase.PAUSED_WAITING_RECONNECT); }
        @Override
        public boolean isPaused() {
            return true;
        }

        @Override
        public void onConnect(ServerController c, String nick, VirtualView view) {
            // While paused we ONLY accept reconnects of nicknames already in the match.
            c.acceptReconnect(nick, view);
        }
        @Override
        public boolean acceptsNewConnections() { return true; }
    }

    /** Match over, terminal phase. */
    public static final class GameEnded extends PhaseLogic {
        public GameEnded() { super(Phase.GAME_ENDED); }
        @Override
        public boolean isFinished() {
            return true;
        }
        // everything stays on default reject; nothing more to do
    }
}
