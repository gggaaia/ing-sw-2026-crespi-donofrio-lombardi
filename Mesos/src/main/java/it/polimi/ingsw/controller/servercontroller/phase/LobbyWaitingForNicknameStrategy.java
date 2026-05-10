package it.polimi.ingsw.controller.servercontroller.phase;

import it.polimi.ingsw.common.dto.ActionLabel;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.Phase;
import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.ActionResultNotification;
import it.polimi.ingsw.controller.servercontroller.notification.GameStateNotification;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.controller.servercontroller.notification.Target;

import java.util.ArrayList;
import java.util.List;

/**
 * Initial lobby phase: zero clients in the controller's list. Accepts
 * only the very first connect call. After it succeeds the controller
 * transitions to WAITING_FOR_PLAYER_COUNT.
 *
 * Option C target choice: at this point there is exactly one client
 * (the sender), so Target.BROADCAST and Target.SENDER are
 * functionally equivalent. We use Target.SENDER for clarity and to
 * avoid producing notifications that the sender then processes a
 * second time.
 */
public class LobbyWaitingForNicknameStrategy extends PhaseStrategy {

    static final int MAX_NICKNAME_LENGTH = 16;

    @Override
    public List<Notification> handleConnect(ServerController c, ServerController.ClientData slot) {
        String nick = slot.getNickname();
        String error = validateSyntax(nick);
        if (error != null) {
            return rejectAction(c, slot, ActionLabel.NICKNAME, error);
        }
        // First joiner: clients list is empty in this phase, so this slot
        // is necessarily new. There is no other client to inform.
        c.getClients().add(slot);
        c.setFirstPlayerNickname(nick);
        c.setCurrentPlayerNickname(nick);
        c.setPhase(Phase.WAITING_FOR_PLAYER_COUNT, new LobbyWaitingForPlayerCountStrategy());

        List<Notification> out = new ArrayList<>();
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.NICKNAME, true,
                        "Welcome, " + nick + ". You are the first player.")));
        out.add(new GameStateNotification(
                Target.SENDER,
                c.buildGameState("Choose the number of participants (2 to 5)")));
        return out;
    }

    /**
     * Used by the other lobby strategies too: nickname must be
     * non-null, non-empty after trim, and at most
     * MAX_NICKNAME_LENGTH characters. Returns null on success, or a
     * short reason if the nickname is invalid.
     */
    static String validateSyntax(String nick) {
        if (nick == null) {
            return "nickname is null";
        }
        String trimmed = nick.trim();
        if (trimmed.isEmpty()) {
            return "nickname is empty";
        }
        if (trimmed.length() > MAX_NICKNAME_LENGTH) {
            return "nickname is too long (max " + MAX_NICKNAME_LENGTH + ")";
        }
        return null;
    }
}
