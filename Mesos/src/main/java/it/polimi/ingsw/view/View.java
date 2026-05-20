package it.polimi.ingsw.view;

import it.polimi.ingsw.controller.client.ClientController;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.List;
import java.util.Set;

/**
 * The interface the {@code ClientController} uses to talk to the user.
 * Two flavours of methods:
 *
 *   - display* / refresh: the controller tells the view "something
 *     happened, show it". Non-blocking.
 *
 *   - prompt*: the controller asks the user to pick something. These
 *     BLOCK until the user answers. Only called from the *Turn methods
 *     of ClientController when it's actually the local user's turn.
 *
 * Implementations: {@code TuiView} (text) and {@code GuiView} (JavaFX).
 */
public interface View {

    /** Called once the connection is open. */
    void displayConnected(String host, int port);

    /** Show a generic info message. */
    void displayInfo(String message);

    /** Show an error message. */
    void displayError(String message);

    /**
     * Re-renders the screen from the controller's current mini-state.
     * Called after each mini-state update.
     */
    void refresh(ClientController state);

    /** Initial nickname prompt. Blocks. */
    String promptNickname();

    /** Asks for the player count (2-5). Blocks. */
    int promptPlayerCount();

    /** Asks the user to pick a color. Blocks. */
    TotemColor promptColor(Set<TotemColor> available);

    /** Asks for an offering tile index (0..trackSize-1). Blocks. */
    int promptOfferingTile(int trackSize);

    /** Asks which character cards to take from the upper row. Blocks. */
    List<Integer> promptCharacterUpperIds(int budget);

    /** Asks which character cards to take from the lower row. Blocks. */
    List<Integer> promptCharacterLowerIds(int budget);

    /** Asks which buildings to buy from the upper row. Blocks. */
    List<Integer> promptBuildingUpperIds(int remaining);

    /** Asks which buildings to buy from the lower row. Blocks. */
    List<Integer> promptBuildingLowerIds(int remaining);

    /** Extra pick prompt: skip, one character card, or one building. Blocks. */
    ExtraPickChoice promptExtraCard();

    /**
     * Result of the extra-pick prompt. Three shapes: skip, character
     * with id, building with id. Instead of exposing more complex
     * types we just use a tiny record-like holder.
     */
    final class ExtraPickChoice {
        public final Integer cardId;
        public final boolean isCharacter;

        /** Skip: {@code cardId == null}. */
        public ExtraPickChoice() {
            this.cardId = null;
            this.isCharacter = true;
        }

        /** Take the card with the given id. */
        public ExtraPickChoice(Integer cardId, boolean isCharacter) {
            this.cardId = cardId;
            this.isCharacter = isCharacter;
        }
    }
}
