package it.polimi.ingsw.controller.servercontroller.notification;

import it.polimi.ingsw.controller.servercontroller.ServerController;

import java.rmi.RemoteException;

/**
 * Tells the dispatcher to push a free-text info message to the
 * recipients declared by the target. Delivery stores the string in
 * the recipient's lastInfo buffer and calls notifyInfoAvailable() on
 * its view.
 */
public class InfoNotification extends Notification {

    private final String message;

    public InfoNotification(Target target, String message) {
        super(target);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public void deliver(ServerController.ClientData recipient) throws RemoteException {
        recipient.setLastInfo(message);
        recipient.getView().notifyInfoAvailable();
    }
}
