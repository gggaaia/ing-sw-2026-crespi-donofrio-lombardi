package it.polimi.ingsw.controller.servercontroller.notification;

/**
 * Half of the visitor (double-dispatch) pair used to discriminate among
 * the three kinds of Target without using instanceof, switch or if/else.
 *
 * The dispatcher inside Server.java implements this interface. For every
 * notification the dispatcher calls
 *     notification.getTarget().accept(this)
 * which polymorphically calls back the right visitXxx method on the
 * visitor. Inside that method the visitor knows exactly which kind of
 * Target it is dealing with and uses its own state (the client registry
 * and the current sender) to enumerate the recipients and deliver the
 * notification.
 *
 * The visit methods are void: the visitor accumulates results inside its
 * own fields, not as return values.
 */
public interface TargetVisitor {

    /** Called when the target is Target.SENDER. */
    void visitSender();

    /** Called when the target is Target.BROADCAST. */
    void visitBroadcast();

    /**
     * Called when the target is Target.BROADCAST_EXCEPT_SENDER. The
     * visitor iterates the registry and skips the entry that matches
     * the current sender it is processing.
     */
    void visitBroadcastExceptSender();

    /**
     * Called when the target is Target.toNickname(name). The visitor
     * receives the requested nickname so it can look it up in the
     * registry.
     */
    void visitToNickname(String nickname);
}
