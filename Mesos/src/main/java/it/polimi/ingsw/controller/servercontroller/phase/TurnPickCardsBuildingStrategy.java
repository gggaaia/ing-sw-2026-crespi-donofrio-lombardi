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
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;

import java.util.ArrayList;
import java.util.List;

/**
 * TURN_PICK_CARDS_BUILDING phase. The same resolving player as in
 * TURN_PICK_CARDS_CHARACTER buys building cards from the upper or
 * lower row, within the residual upper/lower budget left after the
 * character pick and within the player's available food.
 *
 * Either list may be null or empty: the player who wants to skip
 * buying buildings just submits two empty lists.
 *
 * After the pick the controller advances currentResolutionIndex and
 * either transitions to TURN_PICK_CARDS_CHARACTER for the next
 * resolving player, or (when the list is exhausted) lands in the
 * round housekeeping (step 6c will fill that body; for now
 * advanceResolverUntilConnectedAndContinue stub-transitions to
 * RESOLVE_BUILDINGS_AND_EVENTS).
 */
public class TurnPickCardsBuildingStrategy extends PhaseStrategy {

    @Override
    public List<Notification> handleConnect(ServerController c,
                                            ServerController.ClientData slot) {
        return handleInMatchConnect(c, slot);
    }

    @Override
    public List<Notification> handlePickBuildingCards(ServerController c,
                                                      ServerController.ClientData slot,
                                                      List<Integer> upperRowIds,
                                                      List<Integer> lowerRowIds) {
        Player player = slot.getPlayer();
        if (player == null) {
            return rejectAction(c, slot, ActionLabel.PICK_BUILDINGS,
                    "no Player attached to your slot");
        }
        Player expected = c.getResolutionOrder().get(c.getCurrentResolutionIndex());
        if (player != expected) {
            return rejectAction(c, slot, ActionLabel.PICK_BUILDINGS, "not your turn");
        }
        List<Integer> upper = upperRowIds != null ? upperRowIds : new ArrayList<>();
        List<Integer> lower = lowerRowIds != null ? lowerRowIds : new ArrayList<>();
        if (upper.size() > c.getCurrentUpperRemainingForBuilding()) {
            return rejectAction(c, slot, ActionLabel.PICK_BUILDINGS,
                    "too many cards from the upper row (residual "
                            + c.getCurrentUpperRemainingForBuilding() + ")");
        }
        if (lower.size() > c.getCurrentLowerRemainingForBuilding()) {
            return rejectAction(c, slot, ActionLabel.PICK_BUILDINGS,
                    "too many cards from the lower row (residual "
                            + c.getCurrentLowerRemainingForBuilding() + ")");
        }
        if (TurnPickCardsCharacterStrategy.hasDuplicates(upper)) {
            return rejectAction(c, slot, ActionLabel.PICK_BUILDINGS,
                    "duplicate id in upper row selection");
        }
        if (TurnPickCardsCharacterStrategy.hasDuplicates(lower)) {
            return rejectAction(c, slot, ActionLabel.PICK_BUILDINGS,
                    "duplicate id in lower row selection");
        }
        // Read-only lookup first.
        Board board = c.getMatch().getBoard();
        List<BuildingCard> foundUpper = new ArrayList<>();
        for (Integer id : upper) {
            BuildingCard found = findBuildingById(board.getUpperRow(), id);
            if (found == null) {
                return rejectAction(c, slot, ActionLabel.PICK_BUILDINGS,
                        "upper-row building id " + id + " not available");
            }
            foundUpper.add(found);
        }
        List<BuildingCard> foundLower = new ArrayList<>();
        for (Integer id : lower) {
            BuildingCard found = findBuildingById(board.getLowerRow(), id);
            if (found == null) {
                return rejectAction(c, slot, ActionLabel.PICK_BUILDINGS,
                        "lower-row building id " + id + " not available");
            }
            foundLower.add(found);
        }
        // Cost calculation.
        int grossCost = 0;
        for (BuildingCard bc : foundUpper) {
            grossCost += bc.getFoodCost();
        }
        for (BuildingCard bc : foundLower) {
            grossCost += bc.getFoodCost();
        }
        int discount = player.getBuildingDiscountBuilder();
        int netCost = Math.max(0, grossCost - discount);
        if (!player.hasEnoughFood(netCost)) {
            return rejectAction(c, slot, ActionLabel.PICK_BUILDINGS,
                    "not enough food (need " + netCost + ")");
        }
        // Commit: remove from rows, set owner, add to hand.
        for (BuildingCard bc : foundUpper) {
            board.removeCardFromRows(bc);
            bc.setOwner(player);
            player.addBuildingToHand(bc);
        }
        for (BuildingCard bc : foundLower) {
            board.removeCardFromRows(bc);
            bc.setOwner(player);
            player.addBuildingToHand(bc);
        }
        player.changeFood(-netCost);
        // Advance to the next resolving player (or to housekeeping if
        // resolutionOrder is exhausted; the helper handles that).
        c.setCurrentResolutionIndex(c.getCurrentResolutionIndex() + 1);
        c.advanceResolverUntilConnectedAndContinue();
        return boughtNotifications(c, slot, foundUpper.size() + foundLower.size(), netCost);
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
        if (slot.getNickname() != null
                && slot.getNickname().equals(c.getCurrentPlayerNickname())) {
            c.setCurrentResolutionIndex(c.getCurrentResolutionIndex() + 1);
            c.advanceResolverUntilConnectedAndContinue();
        }
        List<Notification> out = new ArrayList<>();
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " disconnected; their turn is skipped"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState(c.getCurrentPlayerNickname() != null
                        ? "Waiting for " + c.getCurrentPlayerNickname()
                        : "Round resolution finishing")));
        return out;
    }

    @Override
    public void advanceCurrentPlayerIfDisconnected(ServerController c) {
        ServerController.ClientData cd = c.findByNickname(c.getCurrentPlayerNickname());
        if (cd == null || !cd.isConnected()) {
            c.setCurrentResolutionIndex(c.getCurrentResolutionIndex() + 1);
            c.advanceResolverUntilConnectedAndContinue();
        }
    }

    /**
     * Linear scan for a building card with the given id inside one
     * row. Returns the card or null.
     */
    static BuildingCard findBuildingById(CardRow row, int id) {
        for (BuildingCard bc : row.getBuildingCards()) {
            if (bc.getCardID() == id) {
                return bc;
            }
        }
        return null;
    }

    private List<Notification> boughtNotifications(ServerController c,
                                                   ServerController.ClientData slot,
                                                   int totalBought,
                                                   int netCost) {
        String next = c.getCurrentPlayerNickname();
        List<Notification> out = new ArrayList<>();
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.PICK_BUILDINGS, true,
                        "Bought " + totalBought + " building(s) for " + netCost + " food")));
        out.add(new GameStateNotification(
                Target.SENDER,
                c.buildGameState(next != null
                        ? "Waiting for " + next + " to draw cards"
                        : "Round resolution finishing")));
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " bought " + totalBought + " building(s) for "
                        + netCost + " food"));
        if (next != null) {
            out.add(new GameStateNotification(
                    Target.toNickname(next),
                    c.buildGameState("Choose character cards to draw "
                            + "(or empty lists to skip)")));
        }
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState(next != null
                        ? "Waiting for " + next + " to draw cards"
                        : "Round resolution finishing")));
        return out;
    }
}
