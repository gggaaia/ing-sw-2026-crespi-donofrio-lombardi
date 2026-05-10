package it.polimi.ingsw.controller.servercontroller.phase;

import it.polimi.ingsw.common.dto.ActionLabel;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.ActionResultNotification;
import it.polimi.ingsw.controller.servercontroller.notification.GameStateNotification;
import it.polimi.ingsw.controller.servercontroller.notification.InfoNotification;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.controller.servercontroller.notification.Target;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * PICK_OFFERING_TILE phase. Players, in turn-order order, place their
 * totem on a free offering tile. After every connected player has
 * placed (or been skipped on disconnect), the controller transitions
 * to TURN_PICK_CARDS_CHARACTER for the first resolving player.
 */
public class PickOfferingTileStrategy extends PhaseStrategy {

    @Override
    public List<Notification> handleConnect(ServerController c,
                                            ServerController.ClientData slot) {
        return handleInMatchConnect(c, slot);
    }

    @Override
    public List<Notification> handlePickOfferingTile(ServerController c,
                                                     ServerController.ClientData slot,
                                                     int tileIndex) {
        Player player = slot.getPlayer();
        if (player == null) {
            return rejectAction(c, slot, ActionLabel.PICK_OFFERING_TILE,
                    "no Player attached to your slot");
        }
        // Must be the player whose turn it is.
        Player expected = c.getTurnOrderForOfferingPick().get(c.getCurrentOfferingPickIndex());
        if (player != expected) {
            return rejectAction(c, slot, ActionLabel.PICK_OFFERING_TILE, "not your turn");
        }
        List<OfferingTile> track = c.getMatch().getBoard().getOfferingTrack();
        if (tileIndex < 0 || tileIndex >= track.size()) {
            return rejectAction(c, slot, ActionLabel.PICK_OFFERING_TILE,
                    "tile index out of range");
        }
        OfferingTile tile = track.get(tileIndex);
        if (tile.isOccupied()) {
            return rejectAction(c, slot, ActionLabel.PICK_OFFERING_TILE, "tile already occupied");
        }
        if (tile.getMinPlayers() > c.getMatch().getPlayersList().size()) {
            return rejectAction(c, slot, ActionLabel.PICK_OFFERING_TILE,
                    "tile requires at least " + tile.getMinPlayers() + " players");
        }
        boolean ok = c.getMatch().getBoard().moveTotem(tileIndex, player);
        if (!ok) {
            return rejectAction(c, slot, ActionLabel.PICK_OFFERING_TILE,
                    "internal error: moveTotem failed");
        }
        // Successful placement.
        String tileLabel = tile.getLetter().toString();
        c.setCurrentOfferingPickIndex(c.getCurrentOfferingPickIndex() + 1);
        // advanceOfferingPickerUntilConnected may transition phase on its
        // own when there is no connected player left to place a totem.
        c.advanceOfferingPickerUntilConnected();
        // Notifications: actor knows what they did; the rest of the lobby
        // hears the announcement and gets the new phase / current-player
        // information from the broadcast game state.
        return placedNotifications(c, slot, tileLabel);
    }

    @Override
    public List<Notification> handleClientDisconnected(ServerController c,
                                                       ServerController.ClientData slot) {
        // Match-level disconnects DO NOT remove the slot from clients (per
        // GAME_FLOW.md section 7): the Player stays in playersList so the
        // disconnected client can later reconnect with the same nickname.
        // We only mark the slot disconnected.
        slot.markDisconnected();
        int connected = c.countConnectedClients();
        // 0 or 1 connected: pause the match per GAME_FLOW.md section 7.
        if (connected <= 1) {
            c.enterPause();
            List<Notification> out = new ArrayList<>();
            out.add(new InfoNotification(
                    Target.BROADCAST_EXCEPT_SENDER,
                    slot.getNickname() + " disconnected; match paused"));
            out.add(new GameStateNotification(
                    Target.BROADCAST_EXCEPT_SENDER,
                    c.buildGameState(connected == 0
                            ? "All clients disconnected; match paused"
                            : "Only one client left; 2-minute reconnection timeout started")));
            return out;
        }
        // 2+ connected: skip the turn if the disconnected slot was the
        // current offering picker.
        if (slot.getNickname() != null
                && slot.getNickname().equals(c.getCurrentPlayerNickname())) {
            c.setCurrentOfferingPickIndex(c.getCurrentOfferingPickIndex() + 1);
            c.advanceOfferingPickerUntilConnected();
        }
        List<Notification> out = new ArrayList<>();
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " disconnected; their turn is skipped"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState(c.getCurrentPlayerNickname() != null
                        ? "Waiting for " + c.getCurrentPlayerNickname() + " to place a totem"
                        : "Round resolution starting")));
        return out;
    }

    @Override
    public void advanceCurrentPlayerIfDisconnected(ServerController c) {
        ServerController.ClientData cd = c.findByNickname(c.getCurrentPlayerNickname());
        if (cd == null || !cd.isConnected()) {
            c.setCurrentOfferingPickIndex(c.getCurrentOfferingPickIndex() + 1);
            c.advanceOfferingPickerUntilConnected();
        }
    }

    private List<Notification> placedNotifications(ServerController c,
                                                   ServerController.ClientData slot,
                                                   String tileLabel) {
        String next = c.getCurrentPlayerNickname();
        List<Notification> out = new ArrayList<>();
        // Sender confirmation.
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.PICK_OFFERING_TILE, true,
                        "Placed totem on tile " + tileLabel)));
        out.add(new GameStateNotification(
                Target.SENDER,
                c.buildGameState(next != null
                        ? "Waiting for " + next + " to place a totem"
                        : "Round resolution starting")));
        // Announcement to others.
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " placed a totem on tile " + tileLabel));
        // The next picker (if any) gets the personal "your turn" view.
        if (next != null) {
            out.add(new GameStateNotification(
                    Target.toNickname(next),
                    c.buildGameState("Place your totem on an offering tile")));
        }
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState(next != null
                        ? "Waiting for " + next + " to place a totem"
                        : "Round resolution starting")));
        return out;
    }
}
