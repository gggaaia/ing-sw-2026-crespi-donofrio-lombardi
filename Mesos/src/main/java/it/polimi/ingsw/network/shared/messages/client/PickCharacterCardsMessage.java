package it.polimi.ingsw.network.shared.messages.client;

import it.polimi.ingsw.controller.GameController;
import it.polimi.ingsw.network.shared.ClientCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent by the current player during {@code TURN_PICK_CARDS_CHARACTER}.
 * <p>
 * The two lists carry the {@code cardID}s the client wants to take,
 * separated by row. Each list may be empty (the player can choose to take
 * fewer than the budget allows). Cards are referred to by id rather than
 * by full {@link it.polimi.ingsw.model.card.Card} object so the wire
 * payload stays small and the server can validate against its own copy
 * of the row.
 */
public class PickCharacterCardsMessage extends ClientMessage {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** IDs of upper-row character cards to take (size 0..top). */
    private final List<Integer> upperRowCardIds;

    /** IDs of lower-row character cards to take (size 0..bottom). */
    private final List<Integer> lowerRowCardIds;

    /**
     * @param upperRowCardIds upper-row character card ids (may be empty)
     * @param lowerRowCardIds lower-row character card ids (may be empty)
     */
    public PickCharacterCardsMessage(
            List<Integer> upperRowCardIds,
            List<Integer> lowerRowCardIds) {
        // Defensive copies so the server's view doesn't depend on what
        // the client (or a buggy serializer) does with the list later.
        this.upperRowCardIds = (upperRowCardIds == null)
                ? new ArrayList<Integer>()
                : new ArrayList<Integer>(upperRowCardIds);
        this.lowerRowCardIds = (lowerRowCardIds == null)
                ? new ArrayList<Integer>()
                : new ArrayList<Integer>(lowerRowCardIds);
    }

    @Override
    public void handle(GameController controller, ClientCallback client) {
        controller.handlePickCharacterCards(client, upperRowCardIds, lowerRowCardIds);
    }
}
