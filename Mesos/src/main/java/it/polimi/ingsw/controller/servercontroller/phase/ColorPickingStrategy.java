package it.polimi.ingsw.controller.servercontroller.phase;

import it.polimi.ingsw.common.dto.ActionLabel;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.Phase;
import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.ActionResultNotification;
import it.polimi.ingsw.controller.servercontroller.notification.ErrorNotification;
import it.polimi.ingsw.controller.servercontroller.notification.GameStateNotification;
import it.polimi.ingsw.controller.servercontroller.notification.InfoNotification;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.controller.servercontroller.notification.Target;
import it.polimi.ingsw.exceptions.DeckLoadException;
import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.Match;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Color-picking phase. Each client takes a turn, in lobby-entry
 * order, picking a TotemColor that is still available. When the
 * color-pick queue empties, the controller starts the match (calls
 * GameManager.createMatch / Match.startMatch / etc.) and transitions
 * to PICK_OFFERING_TILE.
 *
 * Inputs accepted: pickColor (validated against the queue head),
 * disconnect (lobby-style: remove from clients and from the queue,
 * restore the picked color if any, advance the head if needed).
 */
public class ColorPickingStrategy extends PhaseStrategy {

    @Override
    public List<Notification> handlePickColor(ServerController c,
                                              ServerController.ClientData slot,
                                              TotemColor color) {
        Queue<ServerController.ClientData> queue = c.getColorPickQueue();
        ServerController.ClientData expected = queue.peek();
        if (expected == null) {
            return rejectAction(c, slot, ActionLabel.PICK_COLOR, "no color picker scheduled");
        }
        if (slot != expected) {
            return rejectAction(c, slot, ActionLabel.PICK_COLOR, "not your turn");
        }
        if (color == null) {
            return rejectAction(c, slot, ActionLabel.PICK_COLOR, "color is null");
        }
        if (!c.getAvailableColors().contains(color)) {
            return rejectAction(c, slot, ActionLabel.PICK_COLOR, "color not available");
        }
        // Validation passed: record the pick.
        slot.setPickedColor(color);
        slot.setPlayer(new Player(slot.getNickname(), color));
        c.getAvailableColors().remove(color);
        queue.poll();
        // Last picker? Start the match.
        if (queue.isEmpty()) {
            return tryStartMatch(c, slot, color);
        }
        // Otherwise advance to the next picker.
        String nextPicker = queue.peek().getNickname();
        c.setCurrentPlayerNickname(nextPicker);
        return colorPickedNotifications(c, slot, color, nextPicker);
    }

    /**
     * Attempts the match-start cascade described in GAME_FLOW.md
     * scenario 6.1 step 7c. On any DeckLoadException /
     * EmptyDeckException it falls back to resetMatchAndError, which
     * brings the controller back to a clean lobby state.
     */
    private List<Notification> tryStartMatch(ServerController c,
                                             ServerController.ClientData lastPicker,
                                             TotemColor lastColor) {
        try {
            int matchID = c.getGameManager().createMatch();
            Match m = c.getGameManager().retrieveMatch(matchID);
            for (ServerController.ClientData client : c.getClients()) {
                m.addPlayer(client.getPlayer());
            }
            m.startMatch();
            c.setMatch(m);

            List<Player> turnOrder = m.getBoard().getPlayersTurnOrder(m.getPlayersList());
            c.setTurnOrderForOfferingPick(turnOrder);
            c.setCurrentOfferingPickIndex(0);
            if (turnOrder.isEmpty()) {
                return resetMatchAndError(c, "no players for offering pick; match aborted");
            }

            String firstOfferingPicker = turnOrder.get(0).getNickname();
            c.setCurrentPlayerNickname(firstOfferingPicker);
            c.setPhase(Phase.PICK_OFFERING_TILE, new PickOfferingTileStrategy());

            return matchStartedNotifications(c, lastPicker, lastColor, firstOfferingPicker);
        } catch (DeckLoadException | EmptyDeckException e) {
            return resetMatchAndError(c, "match setup failed: " + e.getMessage());
        }
    }

    /**
     * Cleans up after a failed match start and tells everyone what
     * happened. The controller goes back to WAITING_FOR_NICKNAME with
     * an empty lobby (clients stay registered but their lobby state
     * is wiped); the Server's registry of clients is unchanged.
     */
    private List<Notification> resetMatchAndError(ServerController c, String reason) {
        c.resetToLobby();
        List<Notification> out = new ArrayList<>();
        out.add(new ErrorNotification(Target.BROADCAST, reason));
        out.add(new GameStateNotification(Target.BROADCAST,
                c.buildGameState("Match could not start. Lobby reset.")));
        return out;
    }

    private List<Notification> colorPickedNotifications(ServerController c,
                                                        ServerController.ClientData slot,
                                                        TotemColor color,
                                                        String nextPicker) {
        List<Notification> out = new ArrayList<>();
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.PICK_COLOR, true, "Picked " + color)));
        out.add(new GameStateNotification(
                Target.SENDER,
                c.buildGameState("Waiting for " + nextPicker + " to pick a color")));
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " picked " + color));
        // For everyone else, the description depends on whether they are the next picker.
        out.add(new GameStateNotification(
                Target.toNickname(nextPicker),
                c.buildGameState("Pick your color")));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState(slot.getNickname() + " picked " + color
                        + ". Waiting for " + nextPicker + " to pick.")));
        return out;
    }

    private List<Notification> matchStartedNotifications(ServerController c,
                                                         ServerController.ClientData lastPicker,
                                                         TotemColor lastColor,
                                                         String firstOfferingPicker) {
        List<Notification> out = new ArrayList<>();
        // The last picker just picked their color and now the match is
        // running. Their personal state shows whether THEY are the first
        // offering picker or not.
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.PICK_COLOR, true,
                        "Picked " + lastColor + ". All colors picked, match starting.")));
        out.add(new GameStateNotification(
                Target.SENDER,
                lastPicker.getNickname().equalsIgnoreCase(firstOfferingPicker)
                        ? c.buildGameState("Match started. Place your totem on an offering tile.")
                        : c.buildGameState("Match started. Waiting for " + firstOfferingPicker
                                + " to place their totem.")));
        // Everyone else gets the announcement; the next-actor's personal
        // state is delivered to them via ToNickname.
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                lastPicker.getNickname() + " picked " + lastColor + ". Match started."));
        out.add(new GameStateNotification(
                Target.toNickname(firstOfferingPicker),
                c.buildGameState("Match started. Place your totem on an offering tile.")));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState("Match started. Waiting for " + firstOfferingPicker
                        + " to place their totem.")));
        return out;
    }

    @Override
    public List<Notification> handleClientDisconnected(ServerController c,
                                                       ServerController.ClientData slot) {
        boolean wasHead = !c.getColorPickQueue().isEmpty()
                && c.getColorPickQueue().peek() == slot;
        // Remove from the registry the controller knows about.
        c.getClients().remove(slot);
        c.getColorPickQueue().remove(slot);
        // If the slot had already chosen a color, return it to the available pool.
        if (slot.getPickedColor() != null) {
            c.getAvailableColors().add(slot.getPickedColor());
            slot.setPickedColor(null);
            slot.setPlayer(null);
        }
        // Lobby empty: full reset.
        if (c.getClients().isEmpty()) {
            c.resetToLobby();
            return new ArrayList<>();
        }
        // Advance the head of the queue if the disconnected client was at
        // the head; otherwise leave currentPlayerNickname unchanged.
        if (wasHead) {
            ServerController.ClientData newHead = c.getColorPickQueue().peek();
            c.setCurrentPlayerNickname(newHead != null ? newHead.getNickname() : null);
        }
        // First player left: promote next; semantics inside COLOR_PICKING
        // are simpler than in lobby (we already tracked the queue), so we
        // just update firstPlayerNickname.
        if (slot.getNickname() != null
                && slot.getNickname().equalsIgnoreCase(c.getFirstPlayerNickname())
                && !c.getClients().isEmpty()) {
            c.setFirstPlayerNickname(c.getClients().get(0).getNickname());
        }
        // Announce.
        List<Notification> out = new ArrayList<>();
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " left during color picking"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState(c.getCurrentPlayerNickname() != null
                        ? "Waiting for " + c.getCurrentPlayerNickname() + " to pick a color"
                        : "All players have picked")));
        return out;
    }
}
