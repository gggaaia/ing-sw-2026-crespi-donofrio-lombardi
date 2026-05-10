package it.polimi.ingsw.controller.servercontroller.notification;

/**
 * Tells the dispatcher who should receive a Notification. Four kinds:
 *   - Sender:                the client who just made the request the
 *                            controller is currently handling.
 *   - Broadcast:             every registered client (the dispatcher
 *                            silently skips the disconnected ones).
 *   - BroadcastExceptSender: every registered client EXCEPT the sender.
 *                            Used to announce something to the rest of
 *                            the lobby/match without echoing it back to
 *                            the actor (who already gets a more
 *                            specific Sender-targeted notification).
 *   - ToNickname:            the client whose nickname matches the
 *                            stored value. If no such client exists,
 *                            the dispatcher silently drops the
 *                            notification.
 *
 * Discrimination among the four kinds is done via the visitor pattern
 * (also known as double dispatch): the dispatcher implements
 * TargetVisitor and calls target.accept(this); each subclass's accept
 * method calls back the matching visitXxx on the visitor. No instanceof,
 * no switch, no if/else.
 *
 * A Target value never travels over the network: it is server-internal
 * control flow.
 */
public abstract class Target {

    /** The single Sender instance, used as the target everywhere. */
    public static final Target SENDER = new Sender();

    /** The single Broadcast instance, used as the target everywhere. */
    public static final Target BROADCAST = new Broadcast();

    /**
     * The single BroadcastExceptSender instance. Use it when you want
     * "everybody else" to learn about an event whose actor is the
     * sender (the actor already gets a more specific Sender-targeted
     * notification, so they would otherwise receive two payloads).
     */
    public static final Target BROADCAST_EXCEPT_SENDER = new BroadcastExceptSender();

    /**
     * Builds a target that points to the unique client whose nickname is
     * the given one.
     */
    public static Target toNickname(String nickname) {
        return new ToNickname(nickname);
    }

    /**
     * The visitor entry point. Implemented by each subclass so the
     * dispatcher can discriminate among targets without instanceof.
     */
    public abstract void accept(TargetVisitor visitor);

    /**
     * "Send to the client who just made the request."
     */
    public static class Sender extends Target {
        @Override
        public void accept(TargetVisitor visitor) {
            visitor.visitSender();
        }
    }

    /**
     * "Send to every registered client."
     */
    public static class Broadcast extends Target {
        @Override
        public void accept(TargetVisitor visitor) {
            visitor.visitBroadcast();
        }
    }

    /**
     * "Send to every registered client EXCEPT the sender." Used by
     * the controller when the sender is the actor that just produced
     * an event and is already getting a Sender-targeted notification
     * with the personal version of the message.
     */
    public static class BroadcastExceptSender extends Target {
        @Override
        public void accept(TargetVisitor visitor) {
            visitor.visitBroadcastExceptSender();
        }
    }

    /**
     * "Send to the client whose nickname is the given one."
     */
    public static class ToNickname extends Target {
        private final String nickname;

        public ToNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getNickname() {
            return nickname;
        }

        @Override
        public void accept(TargetVisitor visitor) {
            visitor.visitToNickname(nickname);
        }
    }
}
