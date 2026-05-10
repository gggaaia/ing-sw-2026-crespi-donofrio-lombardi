package it.polimi.ingsw.common.dto;

import java.io.Serializable;

/**
 * Label carried inside ActionResultDto. One named constant per input
 * type listed in GAME_FLOW.md section 4 (excluding "connect" and
 * "disconnect", which never produce a player-facing action result).
 *
 * Pure tag data: nothing in the codebase dispatches on the value.
 * The only consumer is TuiView.displayActionResult which formats the
 * label as a string via toString(). For that reason this class is a
 * plain immutable holder with seven public-static-final constants
 * instead of an abstract hierarchy with a visitor; the constants are
 * the only instances that ever get constructed (the constructor is
 * private). This keeps ActionLabel.NICKNAME, ActionLabel.PICK_COLOR,
 * etc. accessible by the same dotted names as before.
 *
 * Across-the-network identity is intentionally not preserved: when
 * an ActionLabel is serialized and deserialized on the other side,
 * the receiving JVM holds a fresh instance, not the singleton. This
 * is fine because no code uses == or instanceof on ActionLabel; the
 * label only flows from server to client and is rendered as text.
 */
public class ActionLabel implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final ActionLabel NICKNAME           = new ActionLabel("NICKNAME");
    public static final ActionLabel PLAYER_COUNT       = new ActionLabel("PLAYER_COUNT");
    public static final ActionLabel PICK_COLOR         = new ActionLabel("PICK_COLOR");
    public static final ActionLabel PICK_OFFERING_TILE = new ActionLabel("PICK_OFFERING_TILE");
    public static final ActionLabel PICK_CHARACTERS    = new ActionLabel("PICK_CHARACTERS");
    public static final ActionLabel PICK_BUILDINGS     = new ActionLabel("PICK_BUILDINGS");
    public static final ActionLabel PICK_EXTRA         = new ActionLabel("PICK_EXTRA");

    private final String name;

    private ActionLabel(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
