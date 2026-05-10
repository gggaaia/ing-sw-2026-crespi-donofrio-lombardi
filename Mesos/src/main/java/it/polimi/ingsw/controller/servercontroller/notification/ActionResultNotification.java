package it.polimi.ingsw.controller.servercontroller.notification;

import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.controller.servercontroller.ServerController;

import java.rmi.RemoteException;

/**
 * Tells the dispatcher to push an ActionResultDto to the recipients
 * declared by the target. Delivery stores the DTO in the recipient's
 * lastActionResult buffer and calls notifyActionResultAvailable() on
 * its view.
 */
public class ActionResultNotification extends Notification {

    private final ActionResultDto result;

    public ActionResultNotification(Target target, ActionResultDto result) {
        super(target);
        this.result = result;
    }

    public ActionResultDto getResult() {
        return result;
    }

    @Override
    public void deliver(ServerController.ClientData recipient) throws RemoteException {
        recipient.setLastActionResult(result);
        recipient.getView().notifyActionResultAvailable();
    }
}
