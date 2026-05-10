package it.polimi.ingsw.controller.servercontroller.phase;

import it.polimi.ingsw.common.dto.ActionLabel;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.controller.servercontroller.ServerController;
import it.polimi.ingsw.controller.servercontroller.notification.ActionResultNotification;
import it.polimi.ingsw.controller.servercontroller.notification.GameStateNotification;
import it.polimi.ingsw.controller.servercontroller.notification.InfoNotification;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.controller.servercontroller.notification.Target;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;

import java.util.ArrayList;
import java.util.List;

/**
 * RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING phase. The owner of the
 * special building (an upper-row building) chooses an optional extra
 * pick: a character card from anywhere on the board, a building card
 * from anywhere on the board (paid as usual), or skip.
 *
 * Reaching this phase means
 * ServerController.afterAllResolutionsDone() has already done the
 * end-of-round housekeeping (clear the turn-order tile, copy the
 * offering track to it, append missing players, resolve slot
 * bonuses) and identified an extraPickPlayer that is currently
 * connected. The single accepted input is pickExtraCard; when it
 * succeeds (or the special-building owner skips), the controller
 * runs enterResolveBuildingsAndEvents() to close the round.
 */
public class ResetTurnOrderAndPickExtraBuildingStrategy extends PhaseStrategy {

    @Override
    public List<Notification> handleConnect(ServerController c,
                                            ServerController.ClientData slot) {
        return handleInMatchConnect(c, slot);
    }

    @Override
    public List<Notification> handlePickExtraCard(ServerController c,
                                                  ServerController.ClientData slot,
                                                  Integer cardId,
                                                  boolean isCharacter) {
        Player player = slot.getPlayer();
        if (player == null) {
            return rejectAction(c, slot, ActionLabel.PICK_EXTRA,
                    "no Player attached to your slot");
        }
        if (player != c.getExtraPickPlayer()) {
            return rejectAction(c, slot, ActionLabel.PICK_EXTRA,
                    "extra pick belongs to another player");
        }

        // null cardId means "skip the extra pick"; just resolve the round.
        if (cardId == null) {
            c.enterResolveBuildingsAndEvents();
            return skippedNotifications(c, slot);
        }

        Board board = c.getMatch().getBoard();
        if (isCharacter) {
            CharacterCard card = findCharacterByIdAnywhere(board, cardId);
            if (card == null) {
                return rejectAction(c, slot, ActionLabel.PICK_EXTRA,
                        "character id " + cardId + " not found on the board");
            }
            // Commit.
            board.removeCardFromRows(card);
            card.drawEffect(player);
            player.addCardToHand(card);
            c.enterResolveBuildingsAndEvents();
            return characterTakenNotifications(c, slot);
        }

        // Building branch.
        BuildingCard card = findBuildingByIdAnywhere(board, cardId);
        if (card == null) {
            return rejectAction(c, slot, ActionLabel.PICK_EXTRA,
                    "building id " + cardId + " not found on the board");
        }
        int discount = player.getBuildingDiscountBuilder();
        int netCost = Math.max(0, card.getFoodCost() - discount);
        if (!player.hasEnoughFood(netCost)) {
            return rejectAction(c, slot, ActionLabel.PICK_EXTRA,
                    "not enough food (need " + netCost + ")");
        }
        // Commit.
        board.removeCardFromRows(card);
        card.setOwner(player);
        player.addBuildingToHand(card);
        player.changeFood(-netCost);
        c.enterResolveBuildingsAndEvents();
        return buildingBoughtNotifications(c, slot, netCost);
    }

    @Override
    public List<Notification> handleClientDisconnected(ServerController c,
                                                       ServerController.ClientData slot) {
        slot.markDisconnected();
        int connected = c.countConnectedClients();
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
        // 2+ connected: if the disconnected slot owns the extra pick,
        // skip it; otherwise nothing to do.
        if (slot.getPlayer() == c.getExtraPickPlayer()) {
            c.enterResolveBuildingsAndEvents();
        }
        List<Notification> out = new ArrayList<>();
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " disconnected; their extra pick is skipped"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState("Round resolution finishing")));
        return out;
    }

    @Override
    public void advanceCurrentPlayerIfDisconnected(ServerController c) {
        // If the special-building owner is no longer connected, skip
        // the extra pick by going straight to resolveBuildingsAndEvents.
        if (c.getExtraPickPlayer() != null) {
            ServerController.ClientData cd = c.findByPlayer(c.getExtraPickPlayer());
            if (cd == null || !cd.isConnected()) {
                c.enterResolveBuildingsAndEvents();
            }
        }
    }

    private List<Notification> skippedNotifications(ServerController c,
                                                    ServerController.ClientData slot) {
        return finalizeNotifications(c, slot, "skipped the extra pick");
    }

    private List<Notification> characterTakenNotifications(ServerController c,
                                                           ServerController.ClientData slot) {
        return finalizeNotifications(c, slot, "took an extra character card");
    }

    private List<Notification> buildingBoughtNotifications(ServerController c,
                                                           ServerController.ClientData slot,
                                                           int netCost) {
        return finalizeNotifications(c, slot,
                "bought an extra building for " + netCost + " food");
    }

    /**
     * Builds the notifications for the actor + the rest. The
     * controller has already advanced to either PICK_OFFERING_TILE
     * (next round) or GAME_ENDED, so the broadcast game state shows
     * the final post-round state.
     */
    private List<Notification> finalizeNotifications(ServerController c,
                                                     ServerController.ClientData slot,
                                                     String summary) {
        List<Notification> out = new ArrayList<>();
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.PICK_EXTRA, true,
                        slot.getNickname() + " " + summary)));
        out.add(new GameStateNotification(
                Target.SENDER,
                c.buildGameState("Round resolved.")));
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " " + summary));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState("Round resolved.")));
        return out;
    }

    private static CharacterCard findCharacterByIdAnywhere(Board board, int cardId) {
        CharacterCard found = TurnPickCardsCharacterStrategy.findCharacterById(
                board.getUpperRow(), cardId);
        if (found != null) {
            return found;
        }
        return TurnPickCardsCharacterStrategy.findCharacterById(
                board.getLowerRow(), cardId);
    }

    private static BuildingCard findBuildingByIdAnywhere(Board board, int cardId) {
        BuildingCard found = TurnPickCardsBuildingStrategy.findBuildingById(
                board.getUpperRow(), cardId);
        if (found != null) {
            return found;
        }
        return TurnPickCardsBuildingStrategy.findBuildingById(
                board.getLowerRow(), cardId);
    }
}
