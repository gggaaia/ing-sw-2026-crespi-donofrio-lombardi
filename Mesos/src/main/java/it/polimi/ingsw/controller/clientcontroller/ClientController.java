package it.polimi.ingsw.controller.clientcontroller;

import it.polimi.ingsw.common.VirtualServer;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;
import it.polimi.ingsw.common.dto.PhaseVisitor;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.view.View;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client-side controller. Two responsibilities:
 *
 *   - Translate incoming server notifications into View calls (the
 *     four onXxx methods, called from the network thread).
 *
 *   - Drive the interactive input loop on the main thread:
 *     runInputLoop() blocks until a new game state arrives from the
 *     server, then asks the View for the right input based on the
 *     current phase and sends it via the VirtualServer stub.
 *
 * Phase-to-input dispatch is done with the visitor pattern (no
 * instanceof, no switch, no if/else for type discrimination): the
 * inner class InputPrompter implements PhaseVisitor and overrides
 * one method per phase to decide whether to prompt and what to send.
 */
public class ClientController {

    private final View view;
    private String nickname;
    private VirtualServer serverStub;

    /**
     * Most recent GameStateDto pushed by the server. Updated by
     * onGameState (network thread) and read by runInputLoop (main
     * thread) under the controller monitor. Null until the very
     * first state arrives.
     */
    private GameStateDto currentState;
    /**
     * Bumped on every onGameState. The input loop remembers the
     * version it last processed and waits until the version moves so
     * the same state is not handled twice in a row.
     */
    private long stateVersion;
    /**
     * Set true by the InputPrompter when it sees a GAME_ENDED phase,
     * so the input loop can return cleanly.
     */
    private boolean gameEnded;

    /**
     * Flipped to true by sendOrError() whenever the input loop
     * actually transmitted something to the server in the current
     * prompt cycle. The loop reads it after each visit() and, when
     * set, advances lastSeenVersion up to consumeUpToVersion so
     * that intermediate states that arrived DURING the prompt (with
     * the same phase + turn we just answered) are not re-processed
     * as if they still required input. Reset at the top of every
     * loop iteration.
     */
    private boolean inputSentSinceLastWait;

    /**
     * Snapshot of stateVersion taken inside sendOrError() right
     * BEFORE the synchronous call to the server. Everything up to
     * (and including) this version is, by construction, a state the
     * server produced before it received our input - those are
     * duplicate intermediate states queued during the prompt and we
     * are safe to skip them. States with a higher version arrived
     * after we sent (typically the server's response to the action,
     * possibly several broadcasts) and MUST still be processed,
     * otherwise we would silently drop the server's reply and the
     * loop would deadlock waiting for a state that never comes.
     */
    private long consumeUpToVersion;

    public ClientController(View view) {
        this.view = view;
    }

    /**
     * Wired by Rmi_Client / Socket_Client right after construction
     * so the input loop can call back into the server.
     */
    public void setServerStub(VirtualServer serverStub) {
        this.serverStub = serverStub;
    }

    /**
     * Asks the View for a nickname. Called once at start-up by the
     * network adapter (Rmi_Client.run / Socket_Client.run) before
     * issuing the very first connect call.
     */
    public String promptNickname() {
        return view.promptNickname();
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getNickname() {
        return nickname;
    }

    // --- onXxx: called from the network thread --------------------------

    public synchronized void onGameState(GameStateDto state) {
        this.currentState = state;
        this.stateVersion++;
        view.displayState(state);
        notifyAll();
    }

    public void onActionResult(ActionResultDto result) {
        view.displayActionResult(result);
    }

    public void onInfo(String message) {
        view.displayInfo(message);
    }

    public void onError(String message) {
        view.displayError(message);
    }

    // --- input loop: called from the main thread ------------------------

    /**
     * Blocks waiting for state updates and prompts the user for the
     * appropriate input each time. Returns when the visitor sees a
     * GAME_ENDED state. If the calling thread is interrupted, the
     * loop exits cleanly.
     */
    public void runInputLoop() {
        long lastSeenVersion = -1L;
        while (!gameEnded) {
            GameStateDto state;
            synchronized (this) {
                while (currentState == null || stateVersion == lastSeenVersion) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                state = currentState;
                lastSeenVersion = stateVersion;
                inputSentSinceLastWait = false;
            }
            // Visit the phase polymorphically; the visitor either prompts
            // and sends (if it is our turn and the phase wants input) or
            // does nothing (so we go back and wait for the next state).
            state.getPhase().accept(new InputPrompter(state));

            // If we actually sent something to the server, advance
            // lastSeenVersion to the snapshot taken inside sendOrError
            // right before the call. This drops the duplicate
            // intermediate states that piled up DURING the prompt
            // (so we do not re-prompt for an action already submitted)
            // WITHOUT swallowing the server's response, which arrives
            // at a strictly higher version. When we did not send (not
            // our turn, automatic phase, etc.) we leave lastSeenVersion
            // alone so the loop still reacts to whatever new state
            // showed up.
            if (inputSentSinceLastWait) {
                synchronized (this) {
                    if (consumeUpToVersion > lastSeenVersion) {
                        lastSeenVersion = consumeUpToVersion;
                    }
                }
            }
        }
    }

    // --- prompt helpers --------------------------------------------------

    private TotemColor promptColor(Set<TotemColor> available) {
        while (true) {
            String input = view.promptLine(
                    "Pick a color " + available + ": ").trim().toUpperCase();
            try {
                TotemColor color = TotemColor.valueOf(input);
                if (available.contains(color)) {
                    return color;
                }
                view.displayError("That color is not available, try again.");
            } catch (IllegalArgumentException e) {
                view.displayError("Unknown color name, try again.");
            }
        }
    }

    /**
     * Reads a comma-separated list of integers (or empty for "no
     * picks"). Skips tokens that don't parse instead of erroring out.
     */
    private List<Integer> promptIntList(String prompt) {
        String line = view.promptLine(prompt).trim();
        List<Integer> result = new ArrayList<>();
        if (line.isEmpty()) {
            return result;
        }
        String[] parts = line.split("\\s*,\\s*");
        for (String p : parts) {
            try {
                result.add(Integer.parseInt(p.trim()));
            } catch (NumberFormatException e) {
                view.displayError("Skipping invalid id: " + p);
            }
        }
        return result;
    }

    private void sendOrError(SafeSend action) {
        // Snapshot stateVersion BEFORE the synchronous call. This
        // captures every state the server pushed while the user was
        // blocked at the prompt - those are duplicates of the step
        // we just answered and runInputLoop() will discard them.
        // Anything that arrives at version > snapshot is a fresh
        // server reaction to our send (e.g. PICK_OFFERING_TILE
        // arriving after pickColor) and must reach the visitor.
        final long versionBeforeSend;
        synchronized (this) {
            versionBeforeSend = stateVersion;
        }
        try {
            action.send();
            synchronized (this) {
                consumeUpToVersion = versionBeforeSend;
                inputSentSinceLastWait = true;
            }
        } catch (RemoteException e) {
            view.displayError("Send failed: " + e.getMessage());
        }
    }

    /**
     * Tiny SAM-shape helper used by the prompter so each visit method
     * does not have to spell out its own try/catch. Implemented by
     * anonymous inner classes (no lambdas allowed in this project).
     */
    private interface SafeSend {
        void send() throws RemoteException;
    }

    // --- PhaseVisitor implementation ------------------------------------

    private class InputPrompter implements PhaseVisitor {

        private final GameStateDto state;

        InputPrompter(GameStateDto state) {
            this.state = state;
        }

        private boolean isOurTurn() {
            return nickname != null
                    && nickname.equals(state.getCurrentPlayerNickname());
        }

        @Override
        public void visitWaitingForNickname() {
            // Nickname has already been submitted via the connect call.
        }

        @Override
        public void visitWaitingForPlayerCount() {
            if (!isOurTurn()) {
                return;
            }
            int count;
            while (true) {
                count = view.promptInt("How many participants? (2..5): ");
                if (count >= 2 && count <= 5) {
                    break;
                }
                view.displayError("Must be between 2 and 5.");
            }
            final int finalCount = count;
            sendOrError(new SafeSend() {
                @Override
                public void send() throws RemoteException {
                    serverStub.setPlayerCount(nickname, finalCount);
                }
            });
        }

        @Override
        public void visitWaitingForPlayers() {
            // Nothing to do: just wait for more players to join.
        }

        @Override
        public void visitColorPicking() {
            if (!isOurTurn()) {
                return;
            }
            final TotemColor color = promptColor(state.getAvailableColors());
            sendOrError(new SafeSend() {
                @Override
                public void send() throws RemoteException {
                    serverStub.pickColor(nickname, color);
                }
            });
        }

        @Override
        public void visitPickOfferingTile() {
            if (!isOurTurn()) {
                return;
            }
            final int trackSize = state.getBoard() != null
                    ? state.getBoard().getOfferingTrack().size()
                    : 0;
            int idx;
            while (true) {
                idx = view.promptInt("Offering tile index (0.." + (trackSize - 1) + "): ");
                if (idx >= 0 && idx < trackSize) {
                    break;
                }
                view.displayError("Out of range.");
            }
            final int finalIdx = idx;
            sendOrError(new SafeSend() {
                @Override
                public void send() throws RemoteException {
                    serverStub.pickOfferingTile(nickname, finalIdx);
                }
            });
        }

        @Override
        public void visitTurnPickCardsCharacter() {
            if (!isOurTurn()) {
                return;
            }
            final List<Integer> upper = promptIntList(
                    "Upper-row character ids (comma-separated, blank to skip; budget "
                            + state.getCurrentUpperBudget() + "): ");
            final List<Integer> lower = promptIntList(
                    "Lower-row character ids (comma-separated, blank to skip; budget "
                            + state.getCurrentLowerBudget() + "): ");
            sendOrError(new SafeSend() {
                @Override
                public void send() throws RemoteException {
                    serverStub.pickCharacterCards(nickname, upper, lower);
                }
            });
        }

        @Override
        public void visitTurnPickCardsBuilding() {
            if (!isOurTurn()) {
                return;
            }
            final List<Integer> upper = promptIntList(
                    "Upper-row building ids (comma-separated, blank to skip; remaining "
                            + state.getCurrentUpperRemaining() + "): ");
            final List<Integer> lower = promptIntList(
                    "Lower-row building ids (comma-separated, blank to skip; remaining "
                            + state.getCurrentLowerRemaining() + "): ");
            sendOrError(new SafeSend() {
                @Override
                public void send() throws RemoteException {
                    serverStub.pickBuildingCards(nickname, upper, lower);
                }
            });
        }

        @Override
        public void visitResetTurnOrderAndPickExtraBuilding() {
            if (!isOurTurn()) {
                return;
            }
            String input = view.promptLine(
                    "Extra pick: 'skip', 'char <id>', or 'build <id>': ").trim().toLowerCase();
            final Integer cardId;
            final boolean isCharacter;
            if (input.startsWith("skip")) {
                cardId = null;
                isCharacter = true;
            } else {
                String[] parts = input.split("\\s+");
                if (parts.length != 2) {
                    view.displayError("Format: skip | char <id> | build <id>");
                    return;
                }
                isCharacter = parts[0].startsWith("char");
                try {
                    cardId = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    view.displayError("Invalid id");
                    return;
                }
            }
            final Integer finalCardId = cardId;
            final boolean finalIsCharacter = isCharacter;
            sendOrError(new SafeSend() {
                @Override
                public void send() throws RemoteException {
                    serverStub.pickExtraCard(nickname, finalCardId, finalIsCharacter);
                }
            });
        }

        @Override
        public void visitResolveBuildingsAndEvents() {
            // Automatic phase, no input expected.
        }

        @Override
        public void visitPausedWaitingReconnect() {
            // Match is paused; nothing to do until enough players come back.
        }

        @Override
        public void visitGameEnded() {
            view.displayInfo("Game ended.");
            gameEnded = true;
        }
    }
}
