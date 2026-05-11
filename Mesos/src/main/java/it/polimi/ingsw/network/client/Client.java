package it.polimi.ingsw.network.client;

import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.network.shared.GamePhase;
import it.polimi.ingsw.network.shared.GameStateDto;
import it.polimi.ingsw.network.shared.messages.client.ClientMessage;
import it.polimi.ingsw.network.shared.messages.client.PickBuildingCardsMessage;
import it.polimi.ingsw.network.shared.messages.client.PickCharacterCardsMessage;
import it.polimi.ingsw.network.shared.messages.client.PickColorMessage;
import it.polimi.ingsw.network.shared.messages.client.PickExtraCardMessage;
import it.polimi.ingsw.network.shared.messages.client.PickOfferingTileMessage;
import it.polimi.ingsw.network.shared.messages.client.SetNicknameMessage;
import it.polimi.ingsw.network.shared.messages.client.SetPlayerCountMessage;
import it.polimi.ingsw.network.shared.messages.server.ServerMessage;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Console client for Pass 1. The client is dumb on purpose: it prints the
 * latest {@link GameStateDto} on every update and asks the user for input
 * only when the server says it's their turn.
 * <p>
 * The class is deliberately written without lambdas or streams, with
 * extensive comments, so the per-phase prompting logic is easy to follow.
 */
public class Client {

    /** Default host (matches {@code Server.DEFAULT_PORT}'s pair). */
    private static final String DEFAULT_HOST = "localhost";

    /** Default port; must match the server's. */
    private static final int DEFAULT_PORT = 12345;

    // === Networking ====================================================

    /** Wire output stream for {@link ClientMessage}s. */
    private ObjectOutputStream out;

    /** Wire input stream for {@link ServerMessage}s. */
    private ObjectInputStream in;

    /*
    /** Reads the user's console input.
    private final Scanner scanner = new Scanner(System.in);
    */

    // === Local state ====================================================

    /** Our final nickname, or {@code null} until the server confirms it. */
    private String myNickname;

    /** Nickname we just sent and are waiting confirmation for. */
    private String pendingNickname;

    /** The latest game-state snapshot received from the server. */
    private GameStateDto currentGameState;

    // Each "waiting" flag is set when we send a request and cleared when
    // we receive the matching ActionResultMessage. They prevent us from
    // re-asking the same question twice while the server hasn't answered.
    private boolean waitingNicknameResponse;
    private boolean waitingPlayerCountResponse;
    private boolean waitingColorResponse;
    private boolean waitingOfferingTileResponse;
    private boolean waitingCharactersResponse;
    private boolean waitingBuildingsResponse;
    private boolean waitingExtraResponse;

    // ===================================================================
    // Phase → action dispatcher (State pattern, client-side)
    // ===================================================================

    /**
     * Strategy for "what should the client do when it receives a state
     * snapshot in this phase?". Each {@link GamePhase} value is mapped
     * (via {@link #PHASE_ACTIONS}) to one strategy instance.
     * <p>
     * Two callbacks:
     * <ul>
     *   <li>{@link #perform} — called from {@code decideNextAction}; the
     *       strategy should ask the user for input if it's their turn.</li>
     *   <li>{@link #printExtras} — called from {@code printGameState};
     *       the strategy can print phase-specific details (e.g. per-turn
     *       card budgets during the pick phases).</li>
     * </ul>
     * Both have empty implementations by default so phases that don't
     * need them just don't override.
     * <p>
     * Implemented as an abstract class (rather than an interface with
     * default methods) so the pattern is familiar to anyone who has
     * only seen "classes with empty methods you can override" — no Java
     * 8+ interface features required.
     */
    private static abstract class ClientPhaseAction {
        /** Default: do nothing. */
        public void perform(Client client, GameStateDto state) {
            // Nothing to do for this phase.
        }

        /** Default: print no extras. */
        public void printExtras(Client client, GameStateDto state) {
            // Nothing to print for this phase.
        }
    }

    /**
     * Phase-to-action registry. Populated once in the static block
     * below. {@link EnumMap} is just a {@link Map} optimized for enum
     * keys — we use {@code put} / {@code get} on it the same way as a
     * {@code HashMap}.
     */
    private static final Map<GamePhase, ClientPhaseAction> PHASE_ACTIONS =
            new EnumMap<GamePhase, ClientPhaseAction>(GamePhase.class);

    /** Reusable no-op action (lobby waits, RESOLVE, PAUSED). */
    private static final ClientPhaseAction NO_OP = new ClientPhaseAction() { };

    static {
        // Lobby phases that don't need any per-phase prompt beyond the
        // unconditional nickname prompt at the top of decideNextAction.
        PHASE_ACTIONS.put(GamePhase.WAITING_FOR_NICKNAME, NO_OP);
        PHASE_ACTIONS.put(GamePhase.WAITING_FOR_PLAYERS, NO_OP);

        PHASE_ACTIONS.put(GamePhase.WAITING_FOR_PLAYER_COUNT, new ClientPhaseAction() {
            @Override public void perform(Client c, GameStateDto state) {
                if (c.isMyTurn(state) && !c.waitingPlayerCountResponse) {
                    c.askPlayerCount();
                }
            }
        });
        PHASE_ACTIONS.put(GamePhase.COLOR_PICKING, new ClientPhaseAction() {
            @Override public void perform(Client c, GameStateDto state) {
                if (c.isMyTurn(state) && !c.waitingColorResponse) {
                    c.askColor();
                }
            }
        });
        PHASE_ACTIONS.put(GamePhase.PICK_OFFERING_TILE, new ClientPhaseAction() {
            @Override public void perform(Client c, GameStateDto state) {
                if (c.isMyTurn(state) && !c.waitingOfferingTileResponse) {
                    c.askOfferingTile();
                }
            }
        });
        // Helper: the printExtras body for the two TURN_PICK_* phases is
        // identical (both show the per-turn budgets). Defining it once
        // would need a shared anonymous-class instance; keeping the two
        // small overrides verbatim is clearer.
        PHASE_ACTIONS.put(GamePhase.TURN_PICK_CARDS_CHARACTER, new ClientPhaseAction() {
            @Override public void perform(Client c, GameStateDto state) {
                if (c.isMyTurn(state) && !c.waitingCharactersResponse) {
                    c.askCharacterCards();
                }
            }
            @Override public void printExtras(Client c, GameStateDto state) {
                c.printPickBudgets(state);
            }
        });
        PHASE_ACTIONS.put(GamePhase.TURN_PICK_CARDS_BUILDING, new ClientPhaseAction() {
            @Override public void perform(Client c, GameStateDto state) {
                if (c.isMyTurn(state) && !c.waitingBuildingsResponse) {
                    c.askBuildingCards();
                }
            }
            @Override public void printExtras(Client c, GameStateDto state) {
                c.printPickBudgets(state);
            }
        });
        PHASE_ACTIONS.put(GamePhase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING,
                new ClientPhaseAction() {
            @Override public void perform(Client c, GameStateDto state) {
                if (c.isMyTurn(state) && !c.waitingExtraResponse) {
                    c.askExtraPick();
                }
            }
        });

        // Phases without per-player input.
        PHASE_ACTIONS.put(GamePhase.RESOLVE_BUILDINGS_AND_EVENTS, NO_OP);
        PHASE_ACTIONS.put(GamePhase.PAUSED_WAITING_RECONNECT, NO_OP);

        PHASE_ACTIONS.put(GamePhase.GAME_ENDED, new ClientPhaseAction() {
            @Override public void perform(Client c, GameStateDto state) {
                c.printEndOfGame(state);
            }
        });
    }

    // ===================================================================
    // Entry point
    // ===================================================================

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        new Client().start(host, port);
    }

    public void start(String host, int port) throws Exception {
        Socket socket = new Socket(host, port);
        try {
            // Same ordering trick as on the server: out before in.
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            System.out.println("Connected to server " + host + ":" + port);
            listenToServer();
        } finally {
            // Best-effort cleanup. The listenToServer loop only returns
            // on EOF or error, so by the time we get here the connection
            // is already done; we just close the socket explicitly.
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing useful to do here.
            }
        }
    }

    /**
     * Read loop: every server message dispatches to one of the {@code on*}
     * callbacks below.
     */
    private void listenToServer() {
        try {
            while (true) {
                Object object = in.readObject();
                if (!(object instanceof ServerMessage)) {
                    System.out.println("[ERROR] Received an invalid server message");
                    continue;
                }
                ServerMessage message = (ServerMessage) object;
                message.handle(this);
            }
        } catch (EOFException e) {
            System.out.println("Server closed the connection");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Disconnected from server: " + e.getMessage());
        }
    }

    // ===================================================================
    // ServerMessage callbacks (called by ServerMessage.handle(Client))
    // ===================================================================

    /** Called by the server's {@code GameStateMessage}. */
    public void onGameState(GameStateDto gameState) {
        this.currentGameState = gameState;
        printGameState(gameState);
        decideNextAction(gameState);
    }

    /** Called by the server's {@code ActionResultMessage}. */
    public void onActionResult(String action, boolean success, String message) {
        System.out.println("[ACTION RESULT] " + action + " | success=" + success);
        System.out.println(message);

        // Clear the matching "waiting" flag.
        if ("NICKNAME".equals(action)) {
            waitingNicknameResponse = false;
            if (success) {
                myNickname = pendingNickname == null ? null : pendingNickname.trim();
                pendingNickname = null;
            } else {
                pendingNickname = null;
                askNickname();
            }
        } else if ("PLAYER_COUNT".equals(action)) {
            waitingPlayerCountResponse = false;
            if (!success) {
                askPlayerCount();
            }
        } else if ("PICK_COLOR".equals(action)) {
            waitingColorResponse = false;
            if (!success) {
                askColor();
            }
        } else if ("PICK_OFFERING_TILE".equals(action)) {
            waitingOfferingTileResponse = false;
            if (!success) {
                askOfferingTile();
            }
        } else if ("PICK_CHARACTERS".equals(action)) {
            waitingCharactersResponse = false;
            if (!success) {
                askCharacterCards();
            }
        } else if ("PICK_BUILDINGS".equals(action)) {
            waitingBuildingsResponse = false;
            if (!success) {
                askBuildingCards();
            }
        } else if ("PICK_EXTRA".equals(action)) {
            waitingExtraResponse = false;
            if (!success) {
                askExtraPick();
            }
        }
    }

    /** Called by the server's {@code ErrorMessage}. */
    public void onError(String message) {
        System.out.println("[ERROR] " + message);
    }

    /** Called by the server's {@code InfoMessage}. */
    public void onInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    // ===================================================================
    // Phase-driven dispatcher
    // ===================================================================

    /**
     * Looks at the current phase and asks for input if the server is
     * waiting for ours. We avoid re-prompting if we've already prompted
     * for this same phase (and haven't received a result yet).
     */
    private void decideNextAction(GameStateDto state) {
        // Whenever we don't yet have a nickname, ask for one. We do NOT
        // gate this on the phase: the server is the only authority on
        // whether a given nickname is acceptable right now, and there are
        // legitimate reasons to send one in any phase:
        //   - lobby phases  → register a new joiner
        //   - COLOR_PICKING → still part of the lobby flow on the server
        //   - mid-match     → reconnect by nickname (3+ player games stay
        //                     in their active phase even while one player
        //                     is gone, so we must still prompt)
        //   - PAUSED        → reconnect by nickname (1- or 0-player paused)
        //   - GAME_ENDED    → server will reject; the rejection re-prompts,
        //                     but eventually the user will type Ctrl-C.
        // If the server rejects, onActionResult re-prompts automatically.
        if (myNickname == null && !waitingNicknameResponse) {
            askNickname();
            return;
        }

        // Polymorphic dispatch: each phase has a strategy that knows
        // what (if anything) to ask the user. Default for phases without
        // input is the NO_OP strategy. No if-else chain needed.
        ClientPhaseAction action = PHASE_ACTIONS.get(state.getPhase());
        if (action != null) {
            action.perform(this, state);
        }
    }

    /**
     * {@code true} iff the server's {@code currentPlayerNickname} refers
     * to us. The comparison is case-insensitive on purpose: the server's
     * nickname lookup uses {@code equalsIgnoreCase} (so "Carlo" and
     * "carlo" are treated as the same player on reconnect), but the
     * broadcast always carries the canonical (original) form. If the
     * user reconnected with a different case, our local {@code myNickname}
     * field would not equal the broadcast string, and we'd never realize
     * it's our turn.
     */
    private boolean isMyTurn(GameStateDto state) {
        return myNickname != null
                && state.getCurrentPlayerNickname() != null
                && myNickname.equalsIgnoreCase(state.getCurrentPlayerNickname());
    }

    private void send(Object object) {
        try {
            out.reset();
            out.writeObject(object);
            out.flush();
        } catch (IOException e) {
            System.out.println("Could not send message to server: " + e.getMessage());
        }
    }
}
