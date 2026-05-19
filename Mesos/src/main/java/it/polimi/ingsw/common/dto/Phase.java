package it.polimi.ingsw.common.dto;

import java.io.Serializable;

/**
 * Identificatore di fase del controller server, trasportato sul
 * filo dentro {@code GameSnapshot} e come singolo campo nel
 * mini-stato del client.
 *
 * Ognuno degli 11 valori \u00e8 una sottoclasse nested con un nome,
 * esposta come singleton {@code public static final}.
 *
 * Sul client questo campo serve solo come informazione mostrata
 * all'utente: gli eventi su {@code VirtualView} sono ognuno
 * specifico della propria fase (es. {@code onColorTurn} arriva
 * solo durante {@code COLOR_PICKING}), quindi non c'\u00e8 bisogno
 * di dispatchare sul valore di {@code Phase}.
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
