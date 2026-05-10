package it.polimi.ingsw.controller.servercontroller.notification;

import it.polimi.ingsw.controller.servercontroller.ServerController;

import java.rmi.RemoteException;

/**
 * One instruction from the ServerController to the dispatcher in
 * Server.java: "push this payload to these recipients". A Notification
 * is server-internal: it never crosses the network. Only the typed
 * payload the subclasses carry (a GameStateDto, an ActionResultDto or
 * a String) is delivered to clients.
 *
 * Each concrete subclass overrides deliver(recipient) to do two
 * things, in this order:
 *
 *   1. Store the payload in the recipient's matching buffer field
 *      (lastState / lastActionResult / lastInfo / lastError). RMI
 *      clients will read this buffer when they call the matching
 *      VirtualServer.getXxx(nickname) getter from inside their
 *      notifyXxx callback.
 *
 *   2. Call the matching no-payload notifyXxx method on the
 *      recipient's view. For RMI views this is a remote signal that
 *      triggers the client's pull. For socket views
 *      (Socket_VirtualClient) the implementation re-reads the buffer
 *      and writes the payload onto the socket as a
 *      ServerToClientMessage.
 *
 * The dispatcher only needs to iterate the recipients and call
 * notification.deliver(recipient) on each one, with no need for
 * instanceof or type checks.
 */
public abstract class Notification {

    private final Target target;

    public Notification(Target target) {
        this.target = target;
    }

    public Target getTarget() {
        return target;
    }

    /**
     * Stores the payload in the recipient's buffer and then calls
     * the matching notifyXxx on its view.
     */
    public abstract void deliver(ServerController.ClientData recipient) throws RemoteException;
}
