package it.polimi.ingsw.common.dto;

import java.io.Serializable;

/**
 * Phase of the server controller as seen by clients (carried inside
 * GameStateDto). One concrete subclass per state listed in GAME_FLOW.md
 * section 3.
 *
 * Discrimination is done via the visitor pattern (also known as double
 * dispatch), exactly like Target. Callers do NOT compare
 * phase == Phase.X: they implement PhaseVisitor and call
 * phase.accept(visitor); the right visitXxx method runs polymorphically.
 *
 * The eleven phases are exposed as singleton public-static-final
 * instances purely for convenience when building a GameStateDto on the
 * server side. Across the network, deserialization produces a fresh
 * subclass instance (not the singleton) - that's fine, because
 * accept(visitor) works on any instance.
 */
public abstract class Phase implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final Phase WAITING_FOR_NICKNAME =
            new WaitingForNickname();
    public static final Phase WAITING_FOR_PLAYER_COUNT =
            new WaitingForPlayerCount();
    public static final Phase WAITING_FOR_PLAYERS =
            new WaitingForPlayers();
    public static final Phase COLOR_PICKING =
            new ColorPicking();
    public static final Phase PICK_OFFERING_TILE =
            new PickOfferingTile();
    public static final Phase TURN_PICK_CARDS_CHARACTER =
            new TurnPickCardsCharacter();
    public static final Phase TURN_PICK_CARDS_BUILDING =
            new TurnPickCardsBuilding();
    public static final Phase RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING =
            new ResetTurnOrderAndPickExtraBuilding();
    public static final Phase RESOLVE_BUILDINGS_AND_EVENTS =
            new ResolveBuildingsAndEvents();
    public static final Phase PAUSED_WAITING_RECONNECT =
            new PausedWaitingReconnect();
    public static final Phase GAME_ENDED =
            new GameEnded();

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

    /**
     * The visitor entry point. Each subclass overrides it to call the
     * matching visitXxx on the visitor.
     */
    public abstract void accept(PhaseVisitor visitor);

    public static class WaitingForNickname extends Phase {
        public WaitingForNickname() {
            super("WAITING_FOR_NICKNAME");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitWaitingForNickname();
        }
    }

    public static class WaitingForPlayerCount extends Phase {
        public WaitingForPlayerCount() {
            super("WAITING_FOR_PLAYER_COUNT");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitWaitingForPlayerCount();
        }
    }

    public static class WaitingForPlayers extends Phase {
        public WaitingForPlayers() {
            super("WAITING_FOR_PLAYERS");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitWaitingForPlayers();
        }
    }

    public static class ColorPicking extends Phase {
        public ColorPicking() {
            super("COLOR_PICKING");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitColorPicking();
        }
    }

    public static class PickOfferingTile extends Phase {
        public PickOfferingTile() {
            super("PICK_OFFERING_TILE");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitPickOfferingTile();
        }
    }

    public static class TurnPickCardsCharacter extends Phase {
        public TurnPickCardsCharacter() {
            super("TURN_PICK_CARDS_CHARACTER");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitTurnPickCardsCharacter();
        }
    }

    public static class TurnPickCardsBuilding extends Phase {
        public TurnPickCardsBuilding() {
            super("TURN_PICK_CARDS_BUILDING");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitTurnPickCardsBuilding();
        }
    }

    public static class ResetTurnOrderAndPickExtraBuilding extends Phase {
        public ResetTurnOrderAndPickExtraBuilding() {
            super("RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitResetTurnOrderAndPickExtraBuilding();
        }
    }

    public static class ResolveBuildingsAndEvents extends Phase {
        public ResolveBuildingsAndEvents() {
            super("RESOLVE_BUILDINGS_AND_EVENTS");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitResolveBuildingsAndEvents();
        }
    }

    public static class PausedWaitingReconnect extends Phase {
        public PausedWaitingReconnect() {
            super("PAUSED_WAITING_RECONNECT");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitPausedWaitingReconnect();
        }
    }

    public static class GameEnded extends Phase {
        public GameEnded() {
            super("GAME_ENDED");
        }

        @Override
        public void accept(PhaseVisitor visitor) {
            visitor.visitGameEnded();
        }
    }
}
