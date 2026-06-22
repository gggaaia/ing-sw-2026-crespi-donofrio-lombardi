package it.polimi.ingsw.common;

import it.polimi.ingsw.common.dto.GameSnapshot;
import it.polimi.ingsw.common.dto.Phase;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-to-client call contract. One method per game event;
 * parameters carry the typed data the client needs to merge into
 * its local mini-state.
 *
 * Implemented by {@code Rmi_Client} (on the RMI client side) and by
 * {@code Socket_VirtualClient} (on the server side, for socket
 * clients). The {@code ServerController} calls methods on instances
 * of this interface without knowing which transport is behind it.
 *
 * Two flavours of methods:
 *   - broadcast (most of them): sent to every connected client
 *   - single-player (3): {@code onActionRejected}, {@code onGameSnapshot},
 *     {@code ping} -- targeted at a single client
 *
 * On each event the client updates its mini-state and, if the event
 * targets its own nickname and asks for input, kicks off the
 * user prompt.
 */
public interface VirtualView extends Remote {

    // === LOBBY EVENTS (broadcast) ===

    /** A new player joined the lobby. */
    void onPlayerJoined(String nickname) throws RemoteException;

    /** First player (with the given nickname) needs to set the participant count. */
    void onAwaitPlayerCount(String firstPlayerNickname) throws RemoteException;

    /** Participant count has been fixed. */
    void onPlayerCountSet(int count) throws RemoteException;

    /** Lobby still waiting for more players. */
    void onAwaitMorePlayers(int joined, int total) throws RemoteException;

    // === COLOR PICKING EVENTS (broadcast) ===

    /** It's this player's turn to pick a colour. */
    void onColorTurn(String nickname, Set<TotemColor> available) throws RemoteException;

    /** The player picked a colour. */
    void onColorPicked(String nickname, TotemColor color) throws RemoteException;

    // === MATCH START / PROGRESS EVENTS (broadcast) ===

    /** The match started. */
    void onMatchStarted(List<String> playerOrder, Era startingEra) throws RemoteException;

    /** Era changed. */
    void onEraChanged(Era newEra) throws RemoteException;

    /** A new round started. */
    void onRoundStarted(int round) throws RemoteException;

    // === TURN FLOW EVENTS (broadcast) ===

    /** The player's turn to place their totem on the offering tile. */
    void onOfferingTileTurn(String nickname, int trackSize) throws RemoteException;

    /** The player placed their totem on the tile. */
    void onOfferingTilePicked(String nickname, int tileIndex) throws RemoteException;

    /** The player's turn to pick character cards. */
    void onCharacterPickTurn(String nickname, int upperBudget, int lowerBudget) throws RemoteException;

    /** The player picked character cards. */
    void onCharacterCardsPicked(String nickname,
                                List<CharacterCard> upper,
                                List<CharacterCard> lower) throws RemoteException;

    /** The player's turn to buy buildings. */
    void onBuildingPickTurn(String nickname, int upperRemaining, int lowerRemaining) throws RemoteException;

    /** The player bought buildings. */
    void onBuildingCardsBought(String nickname,
                               List<BuildingCard> upper,
                               List<BuildingCard> lower) throws RemoteException;

    /** The player's turn to make the extra pick (special building). */
    void onExtraPickTurn(String nickname) throws RemoteException;

    /** The player took the extra pick (or skipped it if cardId=null). */
    void onExtraCardPicked(String nickname, Integer cardId, boolean isCharacter) throws RemoteException;

    // === RESOURCE EVENTS (broadcast) ===

    /** The player's prestige changed. */
    void onPrestigeChanged(String nickname, int newPrestige) throws RemoteException;

    /** The player's food changed. */
    void onFoodChanged(String nickname, int newFood) throws RemoteException;

    // === ROUND/GAME END EVENTS (broadcast) ===

    /** Current round is over. */
    void onRoundEnded() throws RemoteException;

    /** Match is over. */
    void onGameEnded(List<String> leaderboard, List<String> winners, Map<String, Integer> globalLeaderboard) throws RemoteException;

    // === PAUSE / RECONNECT EVENTS (broadcast) ===

    /** Match paused (e.g. only one player still connected). */
    void onMatchPaused(String reason) throws RemoteException;

    /** Match resumed. */
    void onMatchResumed() throws RemoteException;

    /** A player disconnected. */
    void onPlayerDisconnected(String nickname) throws RemoteException;

    /** A player reconnected. */
    void onPlayerReconnected(String nickname) throws RemoteException;

    // === SINGLE-PLAYER EVENTS ===

    /**
     * The client's connect attempt was refused (e.g. no match is
     * accepting players, lobby is full, a match is already in
     * progress). The client shows the error and tears down its own
     * connection.
     */
    void onNicknameRejected(String reason) throws RemoteException;

    /** An in-game action from the caller was refused by the server. */
    void onActionRejected(String reason) throws RemoteException;

    /** Full snapshot of the game state (for lobby join or reconnect). */
    void onGameSnapshot(GameSnapshot snapshot) throws RemoteException;

    /**
     * Lightweight snapshot of just the {@link Board}, sent by the
     * server right before a {@code *Turn} event that needs the
     * player to pick on a fresh board state (offering track,
     * upper/lower row, turn order tile). Unlike
     * {@link #onGameSnapshot}, this does NOT trigger a view refresh:
     * it silently updates the local board mini-state, so the
     * following {@code *Turn} already has the right board to print.
     */
    void onBoardSnapshot(Board board) throws RemoteException;

    /**
     * Heartbeat. Empty body: only used to check the client is still
     * reachable. A {@code RemoteException} tells the server "this
     * client is dead".
     */
    void ping() throws RemoteException;

    /**
     * End-of-bundle marker for a logical batch of events (e.g. "the
     * player did a pick", "round ended", "match started"), telling
     * the client it can render now. This is the ONLY place where the
     * client calls {@code view.refresh}; every preceding {@code onXxx}
     * in the bundle just updates the mini-state and queues a log
     * line.
     *
     * Server-side rule: every broadcast/sendToNickname that produces
     * a user-visible change must finish with {@code v.onCommit()}
     * (see the {@code broadcastAndCommit} / {@code sendToNicknameAndCommit}
     * helpers in {@code ServerController}). Without commit the
     * client receives the deltas but never repaints the screen.
     */
    void onCommit() throws RemoteException;
}
