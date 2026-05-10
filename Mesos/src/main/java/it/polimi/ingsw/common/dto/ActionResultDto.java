package it.polimi.ingsw.common.dto;

import java.io.Serializable;

/**
 * Outcome of a player input. Sent only to the client that issued the
 * input. Three fields:
 *   - label:   which input this result refers to;
 *   - success: true if the input was accepted, false if rejected;
 *   - message: short text (a confirmation if accepted, a reason if
 *              rejected).
 */
public class ActionResultDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ActionLabel label;
    private final boolean success;
    private final String message;

    public ActionResultDto(ActionLabel label, boolean success, String message) {
        this.label = label;
        this.success = success;
        this.message = message;
    }

    public ActionLabel getLabel() {
        return label;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
