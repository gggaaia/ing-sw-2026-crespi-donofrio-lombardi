package it.polimi.ingsw.view;

import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;

/**
 * The interface the ClientController uses to talk to the user. Two
 * groups of methods:
 *
 *   - display methods (server-pushed updates the user just needs to
 *     see): displayConnected, displayInfo, displayError,
 *     displayActionResult, displayState.
 *
 *   - prompt methods (block waiting for user input): promptNickname.
 *     More prompt methods will be added in later steps when the
 *     controller starts asking for colors, offering tile choices,
 *     card picks, etc.
 *
 * Two implementations: TuiView (text-only, uses Scanner / System.out)
 * and GuiView (JavaFX; bodies in step 10).
 */
public interface View {

    /**
     * Called by Client.startXxxClient right after the connection has
     * been opened, before the controller takes over.
     */
    void displayConnected(String host, int port);

    /**
     * Blocks asking the user for a nickname. Called once per session,
     * at the very beginning of Rmi_Client.run / Socket_Client.run,
     * before the first connect call.
     */
    String promptNickname();

    /**
     * Displays a free-text info message ("X has joined", "match
     * resumed", ...).
     */
    void displayInfo(String message);

    /**
     * Displays a free-text error message.
     */
    void displayError(String message);

    /**
     * Displays the typed outcome of a player input: the action label,
     * the success flag, and the message.
     */
    void displayActionResult(ActionResultDto result);

    /**
     * Displays the current game state. The TuiView implementation
     * just prints a textual summary; GuiView re-renders the JavaFX
     * scene.
     */
    void displayState(GameStateDto state);

    /**
     * Blocks asking the user for an integer with the given prompt.
     * Re-prompts if the input is not a valid number. Used by the
     * ClientController's interactive input loop for player count,
     * tile index, etc.
     */
    int promptInt(String prompt);

    /**
     * Blocks asking the user for a free-text line with the given
     * prompt. Used by the ClientController for color names, card-id
     * lists, extra-pick choices.
     */
    String promptLine(String prompt);
}
