package it.polimi.ingsw.common.dto;

import java.io.Serializable;

/**
 * Label carried inside ActionResultDto. One concrete subclass per input
 * type listed in GAME_FLOW.md section 4 (excluding "connect" and
 * "disconnect", which never produce a player-facing action result).
 *
 * Discrimination is done via the visitor pattern (double dispatch),
 * exactly like Phase and Target. Callers do NOT compare
 * label == ActionLabel.X: they implement ActionLabelVisitor and call
 * label.accept(visitor); the right visitXxx method runs polymorphically.
 *
 * The seven labels are exposed as singleton public-static-final
 * instances purely for convenience when building an ActionResultDto on
 * the server side. Across the network, deserialization produces a fresh
 * subclass instance (not the singleton) - that's fine, because
 * accept(visitor) works on any instance.
 */
public abstract class ActionLabel implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final ActionLabel NICKNAME =
            new Nickname();
    public static final ActionLabel PLAYER_COUNT =
            new PlayerCount();
    public static final ActionLabel PICK_COLOR =
            new PickColor();
    public static final ActionLabel PICK_OFFERING_TILE =
            new PickOfferingTile();
    public static final ActionLabel PICK_CHARACTERS =
            new PickCharacters();
    public static final ActionLabel PICK_BUILDINGS =
            new PickBuildings();
    public static final ActionLabel PICK_EXTRA =
            new PickExtra();

    private final String name;

    // Private so only the nested subclasses in this file call super(name), each with its own
    // literal name (e.g. "PICK_COLOR"). Java permits that from types declared inside this class.
    // New label types can only be added in this file; other files cannot subclass ActionLabel.
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

    /**
     * The visitor entry point. Each subclass overrides it to call the
     * matching visitXxx on the visitor.
     */
    public abstract void accept(ActionLabelVisitor visitor);

    public static class Nickname extends ActionLabel {
        public Nickname() {
            super("NICKNAME");
        }

        @Override
        public void accept(ActionLabelVisitor visitor) {
            visitor.visitNickname();
        }
    }

    public static class PlayerCount extends ActionLabel {
        public PlayerCount() {
            super("PLAYER_COUNT");
        }

        @Override
        public void accept(ActionLabelVisitor visitor) {
            visitor.visitPlayerCount();
        }
    }

    public static class PickColor extends ActionLabel {
        public PickColor() {
            super("PICK_COLOR");
        }

        @Override
        public void accept(ActionLabelVisitor visitor) {
            visitor.visitPickColor();
        }
    }

    public static class PickOfferingTile extends ActionLabel {
        public PickOfferingTile() {
            super("PICK_OFFERING_TILE");
        }

        @Override
        public void accept(ActionLabelVisitor visitor) {
            visitor.visitPickOfferingTile();
        }
    }

    public static class PickCharacters extends ActionLabel {
        public PickCharacters() {
            super("PICK_CHARACTERS");
        }

        @Override
        public void accept(ActionLabelVisitor visitor) {
            visitor.visitPickCharacters();
        }
    }

    public static class PickBuildings extends ActionLabel {
        public PickBuildings() {
            super("PICK_BUILDINGS");
        }

        @Override
        public void accept(ActionLabelVisitor visitor) {
            visitor.visitPickBuildings();
        }
    }

    public static class PickExtra extends ActionLabel {
        public PickExtra() {
            super("PICK_EXTRA");
        }

        @Override
        public void accept(ActionLabelVisitor visitor) {
            visitor.visitPickExtra();
        }
    }
}
