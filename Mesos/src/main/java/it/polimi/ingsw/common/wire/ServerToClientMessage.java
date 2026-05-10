package it.polimi.ingsw.common.wire;

import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;

import java.io.Serializable;

/**
 * Wire-message abstract base for every notification going from the
 * server to a socket client. Mirrors VirtualView one-to-one: one
 * concrete inner subclass per VirtualView method.
 *
 * Discrimination on the receiving side (Socket_Client in step 9) is
 * done via the visitor pattern: no instanceof, no switch, no if/else.
 * Each subclass overrides accept(Visitor) to call the matching
 * visitXxx method.
 */
public abstract class ServerToClientMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Receives the polymorphic dispatch back from the visitor. Each
     * concrete subclass implements this with one line that calls the
     * matching visitXxx on the visitor.
     */
    public abstract void accept(Visitor visitor);

    /**
     * Client-side dispatcher contract. Implemented by Socket_Client in
     * step 9.
     */
    public interface Visitor {
        void visitGameState(GameStateDto state);

        void visitActionResult(ActionResultDto result);

        void visitInfo(String message);

        void visitError(String message);
    }

    public static class GameState extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final GameStateDto state;

        public GameState(GameStateDto state) {
            this.state = state;
        }

        public GameStateDto getState() {
            return state;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitGameState(state);
        }
    }

    public static class ActionResult extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final ActionResultDto result;

        public ActionResult(ActionResultDto result) {
            this.result = result;
        }

        public ActionResultDto getResult() {
            return result;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitActionResult(result);
        }
    }

    public static class Info extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String message;

        public Info(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitInfo(message);
        }
    }

    public static class Error extends ServerToClientMessage {
        private static final long serialVersionUID = 1L;
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitError(message);
        }
    }
}
