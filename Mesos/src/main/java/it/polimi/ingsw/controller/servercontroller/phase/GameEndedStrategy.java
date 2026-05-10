package it.polimi.ingsw.controller.servercontroller.phase;

import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.InfoNotification;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.controller.servercontroller.notification.Target;

import java.util.ArrayList;
import java.util.List;

/**
 * GAME_ENDED phase. Per GAME_FLOW.md section 7 Caso B, no player
 * input is accepted (every handle method falls back to the default
 * rejection on PhaseStrategy). The only thing this phase reacts to
 * is a disconnection, which it acknowledges by marking the slot
 * disconnected and broadcasting an info note to the rest. No state
 * transition; no model writes.
 */
public class GameEndedStrategy extends PhaseStrategy {

    @Override
    public List<Notification> handleClientDisconnected(ServerController c,
                                                       ServerController.ClientData slot) {
        // Even though the match is over, marking the slot disconnected
        // matters for two reasons:
        //   1. it keeps subsequent broadcast game states accurate (the
        //      leaderboard view of who is still around);
        //   2. it triggers Server.handleDeliveryFailure's idempotency
        //      guard so a recurring heartbeat ping failure does not
        //      run the disconnect cascade more than once for the same
        //      gone client.
        slot.markDisconnected();
        List<Notification> out = new ArrayList<>();
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " disconnected (game already ended)"));
        return out;
    }
}
