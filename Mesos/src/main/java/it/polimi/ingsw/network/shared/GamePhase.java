package it.polimi.ingsw.network.shared;

/**
 * The phase the server's state machine is currently in. Every
 * {@code GameStateDto} carries one of these so the client knows what input
 * (if any) is expected of it.
 * <p>
 * The phases divide neatly into three groups:
 * <ul>
 *   <li>Lobby phases ({@link #WAITING_FOR_NICKNAME},
 *       {@link #WAITING_FOR_PLAYER_COUNT},
 *       {@link #WAITING_FOR_PLAYERS}, {@link #COLOR_PICKING}): the
 *       {@link it.polimi.ingsw.model.Match} has not been built yet, the
 *       controller is gathering players, nicknames and colors.</li>
 *   <li>Per-round phases ({@link #PICK_OFFERING_TILE},
 *       {@link #TURN_PICK_CARDS_CHARACTER},
 *       {@link #TURN_PICK_CARDS_BUILDING},
 *       {@link #RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING},
 *       {@link #RESOLVE_BUILDINGS_AND_EVENTS}): the match is in progress
 *       and these cycle round after round until {@link #GAME_ENDED}.</li>
 *   <li>Terminal phase ({@link #GAME_ENDED}): final scoring is done, the
 *       leaderboard and winners are in the DTO.</li>
 * </ul>
 */
public enum GamePhase {
    /** Initial phase: the lobby is empty, the very first connecting client
     *  must send its nickname. */
    WAITING_FOR_NICKNAME,

    /** First player is set; that player must send the desired total player
     *  count (2..5). */
    WAITING_FOR_PLAYER_COUNT,

    /** Player count is set, but not all expected clients have connected
     *  and registered a nickname yet. */
    WAITING_FOR_PLAYERS,

    /** Every expected player has joined; players take turns (in join
     *  order) picking their {@link it.polimi.ingsw.model.enums.TotemColor}.
     *  At the end of this phase the controller builds {@code Player}
     *  instances and the {@link it.polimi.ingsw.model.Match}. */
    COLOR_PICKING,

    /** Per-round phase 1: each player, in
     *  {@link it.polimi.ingsw.model.Board#getPlayersTurnOrder} order,
     *  places their totem on one slot of the offering track. */
    PICK_OFFERING_TILE,

    /** Per-round phase 2 (per-player): the current player draws character
     *  cards from the upper and lower rows, up to the budgets defined by
     *  the offering tile they picked. */
    TURN_PICK_CARDS_CHARACTER,

    /** Per-round phase 3 (per-player): the current player optionally buys
     *  buildings, with per-row budgets equal to the leftover from the
     *  character phase. */
    TURN_PICK_CARDS_BUILDING,

    /** Per-round phase 4 (only entered when some player owns a building
     *  with the special "extra pick" effect): that player picks one extra
     *  card (character or building) or skips. */
    RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING,

    /** Per-round phase 5: the controller applies all buildings' end-of-round
     *  effects, then all events on the lower row. If the round just
     *  resolved was {@link it.polimi.ingsw.model.Match#FINAL_ROUND}, the
     *  controller transitions to {@link #GAME_ENDED}; otherwise it advances
     *  the round and loops back to {@link #PICK_OFFERING_TILE}. */
    RESOLVE_BUILDINGS_AND_EVENTS,

    /**
     * Pass 2 phase: the match is paused because not enough players are
     * connected (typically only one). The controller remembers the phase
     * it was in before the pause and resumes that phase as soon as a
     * second client reconnects. While in this phase, no player input is
     * expected; a 2-minute lone-player timer is running, after which the
     * remaining player is declared the winner by default.
     */
    PAUSED_WAITING_RECONNECT,

    /** Terminal phase. The DTO carries the leaderboard and winners list. */
    GAME_ENDED
}
