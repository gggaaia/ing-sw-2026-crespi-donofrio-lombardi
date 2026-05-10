package it.polimi.ingsw.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Server-to-client notification contract. Every method is "notify-only"
 * with no payload: it tells the client "something changed", and the
 * client (on RMI) pulls the actual content via a getter on
 * VirtualServer.
 *
 * The four methods correspond one-to-one to the four payload kinds:
 * game state, action result, info, error. The corresponding getter on
 * VirtualServer (getState / getLastActionResult / getLastInfo /
 * getLastError) returns the latest value the server stored in the
 * client's per-slot buffer.
 *
 * Why no payloads: in Java RMI, method arguments are serialized and
 * copied across the wire. Carrying big DTOs (notably GameStateDto)
 * inside an RMI call ships the whole snapshot every time. Splitting
 * the call into a small notify + an explicit pull keeps RMI calls
 * lightweight and lets the client decide when to read.
 *
 * On the socket transport the payload is still carried on the wire by
 * the matching ServerToClientMessage subclass (push). The
 * Socket_VirtualClient implementation of these methods reads the
 * payload from its bound ClientData buffer and writes the wire
 * message; the client side receives the wire message with the payload
 * already inside.
 */
public interface VirtualView extends Remote {

    /**
     * Tells the client "the game state has changed; pull it via
     * VirtualServer.getState(nickname)".
     */
    void notifyStateChanged() throws RemoteException;

    /**
     * Tells the client "an action result is available; pull it via
     * VirtualServer.getLastActionResult(nickname)".
     */
    void notifyActionResultAvailable() throws RemoteException;

    /**
     * Tells the client "a free-text info message is available; pull
     * it via VirtualServer.getLastInfo(nickname)".
     */
    void notifyInfoAvailable() throws RemoteException;

    /**
     * Tells the client "a free-text error message is available; pull
     * it via VirtualServer.getLastError(nickname)".
     */
    void notifyErrorAvailable() throws RemoteException;

    /**
     * Liveness probe. Invoked periodically by the server's heartbeat
     * timer on every connected view. The body itself does nothing; it
     * is the SUCCESS or FAILURE of the call that matters: a thrown
     * RemoteException tells the server "this client is gone", and the
     * dispatcher then runs the same disconnect cascade as a failed
     * notification push.
     *
     * Without this method, RMI clients that disappear silently
     * (process exit) cannot be detected as long as no other push
     * happens to fail (e.g., the controller is just waiting for the
     * disconnected client's input). The 2-minute lone-player timeout
     * relies on detecting the disconnect promptly, so the heartbeat
     * is what lets it kick in at all on the RMI side.
     */
    void ping() throws RemoteException;
}
