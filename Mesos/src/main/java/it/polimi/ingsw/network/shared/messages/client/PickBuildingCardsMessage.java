package it.polimi.ingsw.network.shared.messages.client;

import it.polimi.ingsw.controller.GameController;
import it.polimi.ingsw.network.shared.ClientCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent by the current player during {@code TURN_PICK_CARDS_BUILDING}.
 * <p>
 * Two lists like {@link PickCharacterCardsMessage} because (per Q2)
 * buildings can sit in either row during eras II / III (the previous
 * era's leftovers shifted down).
 */
public class PickBuildingCardsMessage extends ClientMessage {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** IDs of upper-row buildings to buy (size 0..upper-leftover). */
    private final List<Integer> upperRowCardIds;

    /** IDs of lower-row buildings to buy (size 0..lower-leftover). */
    private final List<Integer> lowerRowCardIds;

    /**
     * @param upperRowCardIds upper-row building card ids (may be empty)
     * @param lowerRowCardIds lower-row building card ids (may be empty)
     */
    public PickBuildingCardsMessage(
            List<Integer> upperRowCardIds,
            List<Integer> lowerRowCardIds) {
        this.upperRowCardIds = (upperRowCardIds == null)
                ? new ArrayList<Integer>()
                : new ArrayList<Integer>(upperRowCardIds);
        this.lowerRowCardIds = (lowerRowCardIds == null)
                ? new ArrayList<Integer>()
                : new ArrayList<Integer>(lowerRowCardIds);
    }

    @Override
    public void handle(GameController controller, ClientCallback client) {
        controller.handlePickBuildingCards(client, upperRowCardIds, lowerRowCardIds);
    }
}
