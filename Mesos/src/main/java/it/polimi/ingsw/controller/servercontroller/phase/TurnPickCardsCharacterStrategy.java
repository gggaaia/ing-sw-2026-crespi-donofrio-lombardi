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
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.CharacterCard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TURN_PICK_CARDS_CHARACTER phase. The current resolving player
 * draws character cards from the upper row, the lower row, or both,
 * within the budgets fixed by the offering tile they chose. After
 * the pick the controller transitions to TURN_PICK_CARDS_BUILDING
 * for the SAME player; the residual budgets are reused as the cap
 * on the building purchase.
 *
 * Either list may be null or empty (interpreted as empty), so a
 * player who wants to skip the character pick simply submits two
 * empty lists.
 */
public class TurnPickCardsCharacterStrategy extends PhaseStrategy {

    @Override
    public List<Notification> handleConnect(ServerController c,
                                            ServerController.ClientData slot) {
        return handleInMatchConnect(c, slot);
    }

    @Override
    public List<Notification> handlePickCharacterCards(ServerController c,
                                                       ServerController.ClientData slot,
                                                       List<Integer> upperRowIds,
                                                       List<Integer> lowerRowIds) {
        Player player = slot.getPlayer();
        if (player == null) {
            return rejectAction(c, slot, ActionLabel.PICK_CHARACTERS,
                    "no Player attached to your slot");
        }
        Player expected = c.getResolutionOrder().get(c.getCurrentResolutionIndex());
        if (player != expected) {
            return rejectAction(c, slot, ActionLabel.PICK_CHARACTERS, "not your turn");
        }
        // Normalize null to empty list. Permitted by GAME_FLOW.md section 4.
        List<Integer> upper = upperRowIds != null ? upperRowIds : new ArrayList<>();
        List<Integer> lower = lowerRowIds != null ? lowerRowIds : new ArrayList<>();
        if (upper.size() > c.getCurrentUpperBudget()) {
            return rejectAction(c, slot, ActionLabel.PICK_CHARACTERS,
                    "too many cards from the upper row (budget "
                            + c.getCurrentUpperBudget() + ")");
        }
        if (lower.size() > c.getCurrentLowerBudget()) {
            return rejectAction(c, slot, ActionLabel.PICK_CHARACTERS,
                    "too many cards from the lower row (budget "
                            + c.getCurrentLowerBudget() + ")");
        }
        if (hasDuplicates(upper)) {
            return rejectAction(c, slot, ActionLabel.PICK_CHARACTERS,
                    "duplicate id in upper row selection");
        }
        if (hasDuplicates(lower)) {
            return rejectAction(c, slot, ActionLabel.PICK_CHARACTERS,
                    "duplicate id in lower row selection");
        }
        // Read-only lookup first; only commit (remove + drawEffect + addToHand)
        // once every id resolves. Avoids partial picks if any id is wrong.
        Board board = c.getMatch().getBoard();
        List<CharacterCard> foundUpper = new ArrayList<>();
        for (Integer id : upper) {
            CharacterCard found = findCharacterById(board.getUpperRow(), id);
            if (found == null) {
                return rejectAction(c, slot, ActionLabel.PICK_CHARACTERS,
                        "upper-row character id " + id + " not available");
            }
            foundUpper.add(found);
        }
        List<CharacterCard> foundLower = new ArrayList<>();
        for (Integer id : lower) {
            CharacterCard found = findCharacterById(board.getLowerRow(), id);
            if (found == null) {
                return rejectAction(c, slot, ActionLabel.PICK_CHARACTERS,
                        "lower-row character id " + id + " not available");
            }
            foundLower.add(found);
        }
        // Commit phase: actually remove and apply draw effects.
        for (CharacterCard cc : foundUpper) {
            board.removeCardFromRows(cc);
            cc.drawEffect(player);
            player.addCardToHand(cc);
        }
        for (CharacterCard cc : foundLower) {
            board.removeCardFromRows(cc);
            cc.drawEffect(player);
            player.addCardToHand(cc);
        }
        // Update residual budgets for the upcoming building purchase.
        c.setCurrentUpperRemainingForBuilding(c.getCurrentUpperBudget() - upper.size());
        c.setCurrentLowerRemainingForBuilding(c.getCurrentLowerBudget() - lower.size());
        // Transition to TURN_PICK_CARDS_BUILDING for the same player.
        c.setPhase(Phase.TURN_PICK_CARDS_BUILDING, new TurnPickCardsBuildingStrategy());
        // currentPlayerNickname stays the same (same resolving player).
        return drewNotifications(c, slot, upper.size() + lower.size());
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
        // 2+ connected: skip the turn if it was the disconnected slot's.
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
                        ? "Waiting for " + c.getCurrentPlayerNickname() + " to draw cards"
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
     * Linear scan for a character card with the given id inside one
     * row. Returns the card or null.
     */
    static CharacterCard findCharacterById(CardRow row, int id) {
        for (CharacterCard cc : row.getCharacterCards()) {
            if (cc.getCardID() == id) {
                return cc;
            }
        }
        return null;
    }

    /**
     * True if the list contains the same Integer twice. Uses a HashSet
     * so it's O(n).
     */
    static boolean hasDuplicates(List<Integer> ids) {
        Set<Integer> seen = new HashSet<>();
        for (Integer id : ids) {
            if (!seen.add(id)) {
                return true;
            }
        }
        return false;
    }

    private List<Notification> drewNotifications(ServerController c,
                                                 ServerController.ClientData slot,
                                                 int totalDrawn) {
        List<Notification> out = new ArrayList<>();
        out.add(new ActionResultNotification(
                Target.SENDER,
                new ActionResultDto(ActionLabel.PICK_CHARACTERS, true,
                        "Drew " + totalDrawn + " character card(s). Now choose buildings to buy.")));
        out.add(new GameStateNotification(
                Target.SENDER,
                c.buildGameState("Choose buildings to buy from upper or lower row "
                        + "(or empty lists to skip). Residual budget: upper="
                        + c.getCurrentUpperRemainingForBuilding()
                        + ", lower=" + c.getCurrentLowerRemainingForBuilding())));
        out.add(new InfoNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                slot.getNickname() + " drew " + totalDrawn + " character card(s)"));
        out.add(new GameStateNotification(
                Target.BROADCAST_EXCEPT_SENDER,
                c.buildGameState("Waiting for " + slot.getNickname() + " to buy buildings")));
        return out;
    }
}
