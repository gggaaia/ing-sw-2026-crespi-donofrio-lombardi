package it.polimi.ingsw.controller.servercontroller.notification;

import it.polimi.ingsw.controller.servercontroller.ServerController;

import java.rmi.RemoteException;

/**
 * Tells the dispatcher to push a free-text error message to the
 * recipients declared by the target. Delivery stores the string in
 * the recipient's lastError buffer and calls notifyErrorAvailable()
 * on its view.
 */
public class ErrorNotification extends Notification {

    private final String message;

    public ErrorNotification(Target target, String message) {
        super(target);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public void deliver(ServerController.ClientData recipient) throws RemoteException {
        recipient.setLastError(message);
        recipient.getView().notifyErrorAvailable();
    }
}
