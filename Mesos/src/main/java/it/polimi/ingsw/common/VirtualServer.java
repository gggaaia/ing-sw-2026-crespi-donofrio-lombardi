package it.polimi.ingsw.common;

import it.polimi.ingsw.model.enums.TotemColor;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Client-to-server call contract. One method per player action;
 * parameters are simple typed values (numbers, enums, lists of ids,
 * strings), no big DTOs flying around.
 *
 * Implemented by {@code Rmi_Server} and {@code Socket_VirtualServer}.
 * Both the client and server controllers always talk through this
 * interface, so they don't know (or care) which transport is below.
 *
 * The server identifies the caller via the nickname passed into
 * every method. There's no authentication: identity is a pure
 * trust-the-client model.
 */
public interface VirtualServer extends Remote {

    /**
     * The first call every client makes. Carries the requested
     * nickname and its own VirtualView (the server-to-client
     * back-channel).
     */
    void connect(String nickname, VirtualView client) throws RemoteException;

    /**
     * Explicit disconnect: the client tells the server it's leaving,
     * both sides drop the associated resources.
     */
    void disconnect(String nickname) throws RemoteException;

    /**
     * The first player picks how many participants the match will
     * have. Only accepted during {@code WAITING_FOR_PLAYER_COUNT}.
     */
    void setPlayerCount(String nickname, int count) throws RemoteException;

    /**
     * Pick the caller's totem colour. Only accepted during
     * {@code COLOR_PICKING}, from the player whose turn it is.
     */
    void pickColor(String nickname, TotemColor color) throws RemoteException;

    /**
     * Place the totem on the offering-track tile at the given index.
     * Only accepted during {@code PICK_OFFERING_TILE}, from the
     * current player.
     */
    void pickOfferingTile(String nickname, int tileIndex) throws RemoteException;

    /**
     * Pick character cards from the upper/lower rows by id, within
     * the budgets fixed by the chosen offering tile. Only accepted
     * during {@code TURN_PICK_CARDS_CHARACTER}. Either list can be
     * empty.
     */
    void pickCharacterCards(String nickname,
                            List<Integer> upperRowIds,
                            List<Integer> lowerRowIds) throws RemoteException;

    /**
     * Buy building cards from the upper/lower rows by id, paying with
     * the remaining budgets plus food. Only accepted during
     * {@code TURN_PICK_CARDS_BUILDING}.
     */
    void pickBuildingCards(String nickname,
                           List<Integer> upperRowIds,
                           List<Integer> lowerRowIds) throws RemoteException;

    /**
     * Optional extra pick granted by the special building.
     * {@code cardId == null} means "skip"; {@code isCharacter} tells
     * the server whether the picked card is a character or a
     * building. Only accepted during
     * {@code RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING}.
     */
    void pickExtraCard(String nickname,
                       Integer cardId,
                       boolean isCharacter) throws RemoteException;
}
