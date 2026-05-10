package it.polimi.ingsw.controller.servercontroller.notification;

import it.polimi.ingsw.common.dto.GameStateDto;
import it.polimi.ingsw.controller.servercontroller.ServerController;

import java.rmi.RemoteException;

/**
 * Tells the dispatcher to push a GameStateDto to the recipients
 * declared by the target. Delivery stores the DTO in the recipient's
 * lastState buffer and calls notifyStateChanged() on its view.
 */
public class GameStateNotification extends Notification {

    private final GameStateDto state;

    public GameStateNotification(Target target, GameStateDto state) {
        super(target);
        this.state = state;
    }

    public GameStateDto getState() {
        return state;
    }

    @Override
    public void deliver(ServerController.ClientData recipient) throws RemoteException {
        recipient.setLastState(state);
        recipient.getView().notifyStateChanged();
    }
}
