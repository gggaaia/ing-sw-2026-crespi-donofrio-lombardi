package it.polimi.ingsw.controller.servercontroller.phase;

import it.polimi.ingsw.common.dto.ActionLabel;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.ActionResultNotification;
import it.polimi.ingsw.controller.servercontroller.notification.GameStateNotification;
import it.polimi.ingsw.controller.servercontroller.notification.InfoNotification;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.controller.servercontroller.notification.Target;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-phase behavior of the ServerController. One concrete subclass
 * per phase listed in GAME_FLOW.md section 3. Each subclass overrides
 * only the inputs it accepts; everything else defaults to a "reject"
 * response that sends a negative ActionResult plus the current game
 * state back to the calling client.
 *
 * Disconnection has its own default of doing nothing because phases
 * handle it differently (lobby removes the slot from the controller's
 * clients list; in-match phases mark the slot disconnected and may
 * pause the match).
 */
public abstract class PhaseStrategy {

    public List<Notification> handleConnect(ServerController c, ServerController.ClientData slot) {
        return rejectAction(c, slot, ActionLabel.NICKNAME, "you cannot join right now");
    }

    public List<Notification> handleSetPlayerCount(ServerController c, ServerController.ClientData slot, int count) {
        return rejectAction(c, slot, ActionLabel.PLAYER_COUNT, "you cannot set the player count right now");
    }

    public List<Notification> handlePickColor(ServerController c, ServerController.ClientData slot, TotemColor color) {
        return rejectAction(c, slot, ActionLabel.PICK_COLOR, "you cannot pick a color right now");
    }

    public List<Notification> handlePickOfferingTile(ServerController c, ServerController.ClientData slot, int tileIndex) {
        return rejectAction(c, slot, ActionLabel.PICK_OFFERING_TILE, "you cannot pick an offering tile right now");
    }

    public List<Notification> handlePickCharacterCards(ServerController c,
                                                       ServerController.ClientData slot,
                                                       List<Integer> upper,
                                                       List<Integer> lower) {
        return rejectAction(c, slot, ActionLabel.PICK_CHARACTERS, "you cannot pick character cards right now");
    }

    public List<Notification> handlePickBuildingCards(ServerController c,
                                                      ServerController.ClientData slot,
                                                      List<Integer> upper,
                                                      List<Integer> lower) {
        return rejectAction(c, slot, ActionLabel.PICK_BUILDINGS, "you cannot buy buildings right now");
    }

    public List<Notification> handlePickExtraCard(ServerController c,
                                                  ServerController.ClientData slot,
                                                  Integer cardId,
                                                  boolean isCharacter) {
        return rejectAction(c, slot, ActionLabel.PICK_EXTRA, "you cannot pick an extra card right now");
    }

    public List<Notification> handleClientDisconnected(ServerController c, ServerController.ClientData slot) {
        return Collections.emptyList();
    }

    /**
     * True only on PausedWaitingReconnectStrategy. Used by the
     * lone-player-timeout callback (a separate thread) to verify
     * that the controller is still paused before forcing a victory,
     * and by reconnection logic to know whether resumeFromPause is
     * appropriate. Polymorphic on purpose so the dispatcher does not
     * need instanceof / phase == checks.
     */
    public boolean isPaused() {
        return false;
    }

    /**
     * Called by ServerController.resumeFromPause() right after
     * restoring the saved phase + strategy. Each in-match strategy
     * overrides this to skip its current player if that player is
     * no longer connected (e.g. they disconnected during the pause).
     * Default is a no-op so phases without a single "current actor"
     * (the lobby phases, GAME_ENDED) need no special handling.
     */
    public void advanceCurrentPlayerIfDisconnected(ServerController c) {
        // default: no-op
    }

    /**
     * Helper for the rejection response: a negative ActionResult to
     * the sender, plus the current game state to the sender so they
     * can re-orient.
     */
    protected List<Notification> rejectAction(ServerController c,
                                              ServerController.ClientData slot,
                                              ActionLabel label,
                                              String reason) {
        List<Notification> out = new ArrayList<>();
        out.add(new ActionResultNotification(Target.SENDER, new ActionResultDto(label, false, reason)));
        out.add(new GameStateNotification(Target.SENDER, c.buildGameState(reason)));
        return out;
    }

    /**
     * Helper for in-match reconnection (GAME_FLOW.md section 4 set
     * nickname, Caso A — riconnessione a uno slot esistente).
     *
     * Used by all in-match strategies (PickOfferingTile,
     * TurnPickCardsCharacter, TurnPickCardsBuilding,
     * ResetTurnOrderAndPickExtraBuilding) when the connect call
     * carries the nickname of a slot that already lives in
     * controller.clients but was previously flagged disconnected.
     *
     * Preconditions guaranteed by the adapter: the slot's view has
     * been replaced with the new stub (slot.setView) and the slot
     * has been re-marked connected (slot.markConnected). The Player
     * object attached to the slot was never removed from
     * match.getPlayersList(), and the player's totem stayed on
     * whichever board element it was on when the disconnect
     * happened — the Turn Order Tile if they had not yet placed
     * this round, or the offering tile they had picked.
     *
     * The reconnecting player gets to play their next un-skipped
     * turn normally; the round in progress keeps the skip-on-
     * disconnect penalty (their totem did not move when their turn
     * came up). Next round, getPlayersTurnOrder will pick them up
     * from whatever turn-order slot ended up theirs (typically a
     * worse slot, which the spec calls "una penalità ragionevole
     * per essere stati disconnessi").
     *
     * No model writes; the controller stays in the current phase.
     */
    protected List<Notification> acceptInMatchReconnection(ServerController c,
                                                           ServerController.ClientData slot) {
        List<Notification> out = new ArrayList<>();
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.NICKNAME, true,
                        "Reconnected; rejoining the match")));
        out.add(new GameStateNotification(
                Target.SENDER,
                c.buildGameState("Welcome back " + slot.getNickname())));
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " reconnected"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState(slot.getNickname() + " reconnected")));
        return out;
    }

    /**
     * Standard handleConnect override for the four in-match
     * strategies. Detection is by reference identity: the registry
     * in Server.java uses putIfAbsent keyed by nickname, so when a
     * known nickname reconnects the adapter ALWAYS hands us the
     * same ClientData instance the controller already has in its
     * clients list. clients.contains(slot) is therefore a clean
     * "is this a reconnection" predicate.
     *
     * If the slot is unknown (a brand-new joiner trying to slip in
     * mid-match), we reject with the standard "you cannot join
     * right now" error.
     */
    protected List<Notification> handleInMatchConnect(ServerController c,
                                                      ServerController.ClientData slot) {
        if (c.getClients().contains(slot)) {
            return acceptInMatchReconnection(c, slot);
        }
        return rejectAction(c, slot, ActionLabel.NICKNAME,
                "match in progress; cannot join now");
    }
}
