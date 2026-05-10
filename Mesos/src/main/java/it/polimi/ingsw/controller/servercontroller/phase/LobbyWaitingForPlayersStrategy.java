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
 * Lobby phase where the participant count has been fixed and we are
 * waiting for additional players to fill the lobby. Accepts: connect
 * (until the lobby is exactly full, then transitions to
 * COLOR_PICKING), and disconnect.
 *
 * Per Option C, every event sends a personalized state to the actor
 * (Target.SENDER) and a third-person announcement to the rest of the
 * lobby (Target.BROADCAST_EXCEPT_SENDER).
 */
public class LobbyWaitingForPlayersStrategy extends PhaseStrategy {

    @Override
    public List<Notification> handleConnect(ServerController c, ServerController.ClientData slot) {
        String nick = slot.getNickname();
        String error = LobbyWaitingForNicknameStrategy.validateSyntax(nick);
        if (error != null) {
            return rejectAction(c, slot, ActionLabel.NICKNAME, error);
        }
        // Reconnection?
        if (c.getClients().contains(slot)) {
            List<Notification> out = new ArrayList<>();
            out.add(new ActionResultNotification(
                    Target.SENDER,
                    new ActionResultDto(ActionLabel.NICKNAME, true, "Reconnected")));
            out.add(new GameStateNotification(
                    Target.SENDER,
                    c.buildGameState("Welcome back. Waiting for the lobby to fill.")));
            return out;
        }
        // Brand-new joiner: must not exceed expectedPlayers.
        if (c.getClients().size() >= c.getExpectedPlayers()) {
            return rejectAction(c, slot, ActionLabel.NICKNAME, "lobby is full");
        }
        c.getClients().add(slot);

        // Exactly full now → COLOR_PICKING.
        if (c.getClients().size() == c.getExpectedPlayers()) {
            // enterColorPicking() populates the colorPickQueue, sets
            // currentPlayerNickname to the head of the queue, and switches
            // strategy to ColorPickingStrategy.
            c.enterColorPicking();
            String firstPicker = c.getCurrentPlayerNickname();
            List<Notification> out = new ArrayList<>();
            // Sender (the joiner) gets a personal welcome.
            out.add(new ActionResultNotification(
                    Target.SENDER,
                    new ActionResultDto(ActionLabel.NICKNAME, true,
                            "Welcome, " + nick + ". Lobby is now full.")));
            out.add(new GameStateNotification(
                    Target.SENDER,
                    nick.equalsIgnoreCase(firstPicker)
                            ? c.buildGameState("Pick your color")
                            : c.buildGameState("Waiting for " + firstPicker + " to pick a color")));
            // Others get the announcement.
            out.add(new InfoNotification(
                    Target.BROADCAST_EXCEPT_SENDER,
                    nick + " joined; lobby is now full"));
            out.add(new GameStateNotification(
                    Target.BROADCAST_EXCEPT_SENDER,
                    c.buildGameState("Waiting for " + firstPicker + " to pick a color")));
            return out;
        }

        // Else: stay waiting. Personal welcome + announcement to others.
        int missing = c.getExpectedPlayers() - c.getClients().size();
        List<Notification> out = new ArrayList<>();
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.NICKNAME, true, "Welcome, " + nick)));
        out.add(new GameStateNotification(
                Target.SENDER,
                c.buildGameState("You joined. Waiting for " + missing + " more player(s).")));
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                nick + " joined; waiting for " + missing + " more"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState(nick + " joined. Waiting for " + missing + " more player(s).")));
        return out;
    }

    @Override
    public List<Notification> handleClientDisconnected(ServerController c,
                                                       ServerController.ClientData slot) {
        c.getClients().remove(slot);
        // Lobby empty: roll back.
        if (c.getClients().isEmpty()) {
            c.setFirstPlayerNickname(null);
            c.setExpectedPlayers(0);
            c.setCurrentPlayerNickname(null);
            c.setPhase(Phase.WAITING_FOR_NICKNAME, new LobbyWaitingForNicknameStrategy());
            return new ArrayList<>();
        }
        // First player left: revert to WAITING_FOR_PLAYER_COUNT.
        if (slot.getNickname() != null
                && slot.getNickname().equalsIgnoreCase(c.getFirstPlayerNickname())) {
            String newFirst = c.getClients().get(0).getNickname();
            c.setFirstPlayerNickname(newFirst);
            c.setCurrentPlayerNickname(newFirst);
            c.setPhase(Phase.WAITING_FOR_PLAYER_COUNT, new LobbyWaitingForPlayerCountStrategy());
            List<Notification> out = new ArrayList<>();
            out.add(new InfoNotification(
                    Target.BROADCAST_EXCEPT_SENDER,
                    slot.getNickname() + " (first player) left; new first player is "
                            + newFirst + ". Player count must be set again."));
            out.add(new GameStateNotification(
                    Target.BROADCAST_EXCEPT_SENDER,
                    c.buildGameState("New first player must set the participant count")));
            return out;
        }
        // Otherwise just announce.
        int missing = c.getExpectedPlayers() - c.getClients().size();
        List<Notification> out = new ArrayList<>();
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " left the lobby"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState("Waiting for " + missing + " more player(s) to join")));
        return out;
    }
}
