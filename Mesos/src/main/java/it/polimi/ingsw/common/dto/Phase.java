package it.polimi.ingsw.common.dto;

import java.io.Serializable;

/**
 * Phase identifier for the server controller, shipped over the wire
 * inside {@code GameSnapshot} and kept as a single field in the
 * client's mini-state.
 *
 * Each of the 11 values is a named nested subclass, exposed as a
 * {@code public static final} singleton.
 *
 * On the client this is purely informational (something to show
 * the user): events on {@code VirtualView} are already
 * phase-specific (e.g. {@code onColorTurn} only fires during
 * {@code COLOR_PICKING}), so we never need to dispatch on the
 * {@code Phase} value.
 */
public abstract class Phase implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final Phase WAITING_FOR_NICKNAME                  = new WaitingForNickname();
    public static final Phase WAITING_FOR_PLAYER_COUNT              = new WaitingForPlayerCount();
    public static final Phase WAITING_FOR_PLAYERS                   = new WaitingForPlayers();
    public static final Phase COLOR_PICKING                         = new ColorPicking();
    public static final Phase PICK_OFFERING_TILE                    = new PickOfferingTile();
    public static final Phase TURN_PICK_CARDS_CHARACTER             = new TurnPickCardsCharacter();
    public static final Phase TURN_PICK_CARDS_BUILDING              = new TurnPickCardsBuilding();
    public static final Phase RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING = new ResetTurnOrderAndPickExtraBuilding();
    public static final Phase RESOLVE_BUILDINGS_AND_EVENTS          = new ResolveBuildingsAndEvents();
    public static final Phase PAUSED_WAITING_RECONNECT              = new PausedWaitingReconnect();
    public static final Phase GAME_ENDED                            = new GameEnded();

    private final String name;

    protected Phase(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static class WaitingForNickname extends Phase {
        public WaitingForNickname() { super("WAITING_FOR_NICKNAME"); }
    }

    public static class WaitingForPlayerCount extends Phase {
        public WaitingForPlayerCount() { super("WAITING_FOR_PLAYER_COUNT"); }
    }

    public static class WaitingForPlayers extends Phase {
        public WaitingForPlayers() { super("WAITING_FOR_PLAYERS"); }
    }

    public static class ColorPicking extends Phase {
        public ColorPicking() { super("COLOR_PICKING"); }
    }

    public static class PickOfferingTile extends Phase {
        public PickOfferingTile() { super("PICK_OFFERING_TILE"); }
    }

    public static class TurnPickCardsCharacter extends Phase {
        public TurnPickCardsCharacter() { super("TURN_PICK_CARDS_CHARACTER"); }
    }

    public static class TurnPickCardsBuilding extends Phase {
        public TurnPickCardsBuilding() { super("TURN_PICK_CARDS_BUILDING"); }
    }

    public static class ResetTurnOrderAndPickExtraBuilding extends Phase {
        public ResetTurnOrderAndPickExtraBuilding() { super("RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING"); }
    }

    public static class ResolveBuildingsAndEvents extends Phase {
        public ResolveBuildingsAndEvents() { super("RESOLVE_BUILDINGS_AND_EVENTS"); }
    }

    public static class PausedWaitingReconnect extends Phase {
        public PausedWaitingReconnect() { super("PAUSED_WAITING_RECONNECT"); }
    }

    public static class GameEnded extends Phase {
        public GameEnded() { super("GAME_ENDED"); }
    }
}
