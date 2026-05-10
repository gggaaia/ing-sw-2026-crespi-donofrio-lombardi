package it.polimi.ingsw.controller.servercontroller.phase;

import it.polimi.ingsw.common.dto.ActionLabel;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.ActionResultNotification;
import it.polimi.ingsw.controller.servercontroller.notification.GameStateNotification;
import it.polimi.ingsw.controller.servercontroller.notification.InfoNotification;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.controller.servercontroller.notification.Target;

import java.util.ArrayList;
import java.util.List;

/**
 * PAUSED_WAITING_RECONNECT phase. The match is paused because all
 * its players are disconnected, or only one of them is left
 * connected. Only one input is meaningful here: a connect call
 * carrying the nickname of a slot that the controller already
 * knows. Any other input gets the default rejection from
 * PhaseStrategy.
 *
 * Reconnection mechanics:
 *   - Adapter does Server.register(nickname, view): if the
 *     nickname is already in the registry, register returns the
 *     existing slot (not a fresh one), so the slot reference the
 *     adapter passes to handleConnect is the SAME instance that has
 *     been sitting in this controller's clients list since the
 *     player joined the lobby. We detect "this is a reconnection"
 *     by clients.contains(slot).
 *   - The adapter has already done slot.setView(view) and
 *     slot.markConnected() before we get here, so the slot is
 *     marked connected when this method runs.
 *   - If the reconnection brings the connected count to >= 2, we
 *     call ServerController.resumeFromPause() which restores the
 *     pre-pause phase + strategy and skips the current actor if
 *     they are still disconnected.
 */
public class PausedWaitingReconnectStrategy extends PhaseStrategy {

    @Override
    public boolean isPaused() {
        return true;
    }

    @Override
    public List<Notification> handleConnect(ServerController c,
                                            ServerController.ClientData slot) {
        // A brand-new client (slot is not in our list) cannot enter the
        // match while it is paused: only known-nickname reconnections.
        if (!c.getClients().contains(slot)) {
            return rejectAction(c, slot, ActionLabel.NICKNAME,
                    "match is paused; only reconnections allowed");
        }
        // Reconnection accepted. The adapter already set the new view
        // and marked the slot connected.
        int connected = c.countConnectedClients();
        boolean resumed = false;
        if (connected >= 2) {
            c.resumeFromPause();
            resumed = true;
        } else {
            // connected == 1 (the just-reconnected slot is the only
            // one). This is the 0 -> 1 transition: when the second-to-
            // last client disconnected the lone-player timer was
            // cancelled, so we MUST rearm it here. Without this rearm
            // the only connected client would wait forever in pause
            // because no one is counting down to declare them winner
            // by default. startLonePlayerTimeout cancels any prior
            // timer first, so the call is also safe in the (rare)
            // case where one was somehow still active.
            c.startLonePlayerTimeout();
        }
        // Build notifications. Note: if we resumed, the broadcast game
        // state will reflect the post-resume phase, not the paused one.
        List<Notification> out = new ArrayList<>();
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.NICKNAME, true,
                        resumed ? "Reconnected; match resumed"
                                : "Reconnected; waiting for more players to come back")));
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " reconnected"
                        + (resumed ? "; match resumed" : "")));
        out.add(new GameStateNotification(
                Target.BROADCAST,
                c.buildGameState(resumed
                        ? "Match resumed"
                        : "Waiting for more players to reconnect")));
        return out;
    }

    @Override
    public List<Notification> handleClientDisconnected(ServerController c,
                                                       ServerController.ClientData slot) {
        slot.markDisconnected();
        // If the disconnection brings us to 0 connected clients, cancel
        // the lone-player timer (no point in counting down without a
        // potential winner).
        if (c.countConnectedClients() == 0) {
            c.cancelLonePlayerTimeoutIfActive();
        }
        List<Notification> out = new ArrayList<>();
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " disconnected during pause"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState("Match still paused")));
        return out;
    }
}
