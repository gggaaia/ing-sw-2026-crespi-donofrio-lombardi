package it.polimi.ingsw.common;

import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;
import it.polimi.ingsw.model.enums.TotemColor;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Every operation a client may ask the server to perform. Two kinds:
 *
 *   - The 7 "do something" methods (connect, setPlayerCount, pickColor,
 *     pickOfferingTile, pickCharacterCards, pickBuildingCards,
 *     pickExtraCard). Their parameters are small primitives, ids,
 *     enum values or short lists; they never carry a big DTO.
 *
 *   - The 4 "pull" methods (getState, getLastActionResult,
 *     getLastInfo, getLastError) used by the RMI client side after
 *     receiving a notifyXxx callback: the server returns the value
 *     it last stored in this nickname's buffer. Socket clients never
 *     call these; the socket transport carries the payload on the
 *     wire (push) instead.
 *
 * Trust assumption: the client never lies about its nickname.
 * Acceptable for a class project; flag in the report.
 *
 * Only connect carries the VirtualView client argument; the server
 * stores it once and uses it from then on. Subsequent methods identify
 * the caller by nickname only.
 */
public interface VirtualServer extends Remote {

    /**
     * First call any client makes. Carries the nickname the client
     * wants AND the client's VirtualView so the server can keep the
     * stub for outgoing notifyXxx calls.
     */
    void connect(String nickname, VirtualView client) throws RemoteException;

    /**
     * The first player chooses how many participants the match will
     * have. Only accepted while the controller is in
     * WAITING_FOR_PLAYER_COUNT.
     */
    void setPlayerCount(String nickname, int count) throws RemoteException;

    /**
     * Picks the calling player's totem color. Only accepted in
     * COLOR_PICKING, only from the player at the head of the pick
     * queue, only with a still-available color.
     */
    void pickColor(String nickname, TotemColor color) throws RemoteException;

    /**
     * Places the calling player's totem on the offering tile at the
     * given index. Only accepted in PICK_OFFERING_TILE, from the
     * player whose turn it currently is.
     */
    void pickOfferingTile(String nickname, int tileIndex) throws RemoteException;

    /**
     * Picks character cards from the upper and/or lower row, by id,
     * within the budgets fixed by the offering tile. Only accepted in
     * TURN_PICK_CARDS_CHARACTER from the resolving player. Either
     * list may be empty or null (interpreted as empty).
     */
    void pickCharacterCards(String nickname,
                            List<Integer> upperRowIds,
                            List<Integer> lowerRowIds) throws RemoteException;

    /**
     * Buys building cards from the upper and/or lower row, by id,
     * within the leftover budgets and food. Only accepted in
     * TURN_PICK_CARDS_BUILDING from the resolving player. Either
     * list may be empty or null.
     */
    void pickBuildingCards(String nickname,
                           List<Integer> upperRowIds,
                           List<Integer> lowerRowIds) throws RemoteException;

    /**
     * Performs the optional extra pick granted by the special
     * building. Only accepted in
     * RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING from the
     * special-building owner. cardId == null means "skip";
     * isCharacter tells the server whether the requested card is a
     * character or a building.
     */
    void pickExtraCard(String nickname,
                       Integer cardId,
                       boolean isCharacter) throws RemoteException;

    /**
     * Pull-side of the "state changed" notification. Returns whatever
     * GameStateDto the server last stored in this nickname's buffer
     * before the most recent notifyStateChanged callback.
     */
    GameStateDto getState(String nickname) throws RemoteException;

    /**
     * Pull-side of the "action result available" notification.
     */
    ActionResultDto getLastActionResult(String nickname) throws RemoteException;

    /**
     * Pull-side of the "info available" notification.
     */
    String getLastInfo(String nickname) throws RemoteException;

    /**
     * Pull-side of the "error available" notification.
     */
    String getLastError(String nickname) throws RemoteException;
}
