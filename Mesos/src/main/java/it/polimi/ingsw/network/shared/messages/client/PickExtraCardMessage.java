package it.polimi.ingsw.network.shared.messages.client;

import it.polimi.ingsw.controller.GameController;
import it.polimi.ingsw.network.shared.ClientCallback;

/**
 * Sent during {@code RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING} by the
 * (single) player who owns the special "extra pick" building.
 * <p>
 * The player picks exactly one card (character or building) from either
 * row of the board, or skips:
 * <ul>
 *   <li>{@code cardId == null}     → skip the extra pick.</li>
 *   <li>{@code isCharacter == true}  → take the character card with id
 *       {@code cardId}.</li>
 *   <li>{@code isCharacter == false} → buy the building with id
 *       {@code cardId} (food cost is checked).</li>
 * </ul>
 */
public class PickExtraCardMessage extends ClientMessage {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** ID of the chosen card, or {@code null} to skip. */
    private final Integer cardId;

    /** True if the chosen id refers to a character card; false for a
     *  building. Ignored when {@code cardId == null}. */
    private final boolean isCharacter;

    /**
     * @param cardId      the chosen card id, or {@code null} to skip
     * @param isCharacter {@code true} for a character card,
     *                    {@code false} for a building (ignored when skipping)
     */
    public PickExtraCardMessage(Integer cardId, boolean isCharacter) {
        this.cardId = cardId;
        this.isCharacter = isCharacter;
    }

    @Override
    public void handle(GameController controller, ClientCallback client) {
        controller.handlePickExtraCard(client, cardId, isCharacter);
    }
}
