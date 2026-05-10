package it.polimi.ingsw.controller.servercontroller.phase;

import it.polimi.ingsw.common.dto.ActionLabel;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.Phase;
import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.ActionResultNotification;
import it.polimi.ingsw.controller.servercontroller.notification.GameStateNotification;
import it.polimi.ingsw.controller.servercontroller.notification.InfoNotification;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.controller.servercontroller.notification.Target;

import java.util.ArrayList;
import java.util.List;

/**
 * Lobby phase where one or more players are present but the first
 * player has not yet set the expected participant count. Accepts:
 * connect (additional joiners + reconnections), setPlayerCount (only
 * from the first player), and disconnect.
 *
 * Per Option C, every event sends a personalized state to the actor
 * (Target.SENDER) and a third-person announcement to the rest of the
 * lobby (Target.BROADCAST_EXCEPT_SENDER), so no client ever receives
 * the same payload twice.
 */
public class LobbyWaitingForPlayerCountStrategy extends PhaseStrategy {

    static final int MIN_PLAYERS = 2;
    static final int MAX_PLAYERS = 5;

    @Override
    public List<Notification> handleConnect(ServerController c, ServerController.ClientData slot) {
        String nick = slot.getNickname();
        String error = LobbyWaitingForNicknameStrategy.validateSyntax(nick);
        if (error != null) {
            return rejectAction(c, slot, ActionLabel.NICKNAME, error);
        }
        // Already in our list = reconnection (or same-name reconnect under trust).
        if (c.getClients().contains(slot)) {
            List<Notification> out = new ArrayList<>();
            out.add(new ActionResultNotification(
                    Target.SENDER,
                    new ActionResultDto(ActionLabel.NICKNAME, true, "Reconnected")));
            out.add(new GameStateNotification(
                    Target.SENDER,
                    c.buildGameState("Welcome back. Waiting for the first player to set the participant count.")));
            return out;
        }
        // Brand-new joiner. Stay in WAITING_FOR_PLAYER_COUNT — first player still has to set count.
        c.getClients().add(slot);

        List<Notification> out = new ArrayList<>();
        // Personal to the joiner.
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.NICKNAME, true, "Welcome, " + nick)));
        out.add(new GameStateNotification(
                Target.SENDER,
                c.buildGameState("You joined the lobby. Waiting for "
                        + c.getFirstPlayerNickname() + " to set the participant count.")));
        // Third-person announcement for the others.
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                nick + " joined the lobby"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState(nick + " joined the lobby. Waiting for "
                        + c.getFirstPlayerNickname() + " to set the participant count.")));
        return out;
    }

    @Override
    public List<Notification> handleSetPlayerCount(ServerController c,
                                                   ServerController.ClientData slot,
                                                   int count) {
        // Only the first player may set the count.
        if (slot.getNickname() == null
                || !slot.getNickname().equalsIgnoreCase(c.getFirstPlayerNickname())) {
            return rejectAction(c, slot, ActionLabel.PLAYER_COUNT,
                    "only the first player can set the count");
        }
        if (count < MIN_PLAYERS) {
            return rejectAction(c, slot, ActionLabel.PLAYER_COUNT,
                    "count must be at least " + MIN_PLAYERS);
        }
        if (count > MAX_PLAYERS) {
            return rejectAction(c, slot, ActionLabel.PLAYER_COUNT,
                    "count must be at most " + MAX_PLAYERS);
        }
        if (count < c.getClients().size()) {
            return rejectAction(c, slot, ActionLabel.PLAYER_COUNT,
                    "count cannot be less than the number of players already in the lobby");
        }
        c.setExpectedPlayers(count);

        // Lobby exactly full → COLOR_PICKING.
        if (c.getClients().size() == count) {
            // enterColorPicking() populates the colorPickQueue, sets
            // currentPlayerNickname to the head of the queue, and switches
            // strategy to ColorPickingStrategy.
            c.enterColorPicking();
            String firstPicker = c.getCurrentPlayerNickname();
            List<Notification> out = new ArrayList<>();
            // Sender is the first player AND the first to pick a color.
            out.add(new ActionResultNotification(
                    Target.SENDER,
                    new ActionResultDto(ActionLabel.PLAYER_COUNT, true,
                            "OK, lobby is full. Pick your color first.")));
            out.add(new GameStateNotification(
                    Target.SENDER,
                    c.buildGameState("Pick your color")));
            // Others are now waiting for sender to pick.
            out.add(new InfoNotification(
                    Target.BROADCAST_EXCEPT_SENDER,
                    "Lobby is full. " + firstPicker + " picks first."));
            out.add(new GameStateNotification(
                    Target.BROADCAST_EXCEPT_SENDER,
                    c.buildGameState("Waiting for " + firstPicker + " to pick a color")));
            return out;
        }

        // Otherwise wait for more players. Sender still has to wait too,
        // but does not have to act, so the personal state just confirms the count.
        c.setCurrentPlayerNickname(null);
        c.setPhase(Phase.WAITING_FOR_PLAYERS, new LobbyWaitingForPlayersStrategy());
        int missing = count - c.getClients().size();
        List<Notification> out = new ArrayList<>();
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.PLAYER_COUNT, true,
                        "OK, count set to " + count + ". Waiting for " + missing + " more.")));
        // Same description for everyone here: the count is news to all.
        out.add(new GameStateNotification(
                Target.BROADCAST,
                c.buildGameState("Player count set to " + count
                        + ". Waiting for " + missing + " more player(s) to join.")));
        return out;
    }

    @Override
    public List<Notification> handleClientDisconnected(ServerController c,
                                                       ServerController.ClientData slot) {
        c.getClients().remove(slot);
        // Lobby empty: roll back to WAITING_FOR_NICKNAME.
        if (c.getClients().isEmpty()) {
            c.setFirstPlayerNickname(null);
            c.setExpectedPlayers(0);
            c.setCurrentPlayerNickname(null);
            c.setPhase(Phase.WAITING_FOR_NICKNAME, new LobbyWaitingForNicknameStrategy());
            // No one is left to receive a notification. Returning an empty list
            // is fine: the dispatcher just walks zero entries.
            return new ArrayList<>();
        }
        // First player left: promote the next.
        if (slot.getNickname() != null
                && slot.getNickname().equalsIgnoreCase(c.getFirstPlayerNickname())) {
            String newFirst = c.getClients().get(0).getNickname();
            c.setFirstPlayerNickname(newFirst);
            c.setCurrentPlayerNickname(newFirst);
            List<Notification> out = new ArrayList<>();
            // Sender is the disconnected one; notification to them is wasted (their
            // view is dead), so we use BROADCAST_EXCEPT_SENDER for everyone else.
            out.add(new InfoNotification(
                    Target.BROADCAST_EXCEPT_SENDER,
                    slot.getNickname() + " (first player) left; new first player is " + newFirst));
            out.add(new GameStateNotification(
                    Target.BROADCAST_EXCEPT_SENDER,
                    c.buildGameState("New first player must set the participant count")));
            return out;
        }
        // Otherwise just announce.
        List<Notification> out = new ArrayList<>();
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " left the lobby"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState("Waiting for the first player to set the participant count")));
        return out;
    }
}
