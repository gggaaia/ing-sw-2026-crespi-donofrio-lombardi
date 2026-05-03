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

    /** Reads the user's console input. */
    private final Scanner scanner = new Scanner(System.in);

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
        // First: nickname is needed across the three lobby sub-phases.
        if (myNickname == null && !waitingNicknameResponse) {
            GamePhase p = state.getPhase();
            if (p == GamePhase.WAITING_FOR_NICKNAME
                    || p == GamePhase.WAITING_FOR_PLAYER_COUNT
                    || p == GamePhase.WAITING_FOR_PLAYERS) {
                askNickname();
                return;
            }
        }

        // Per-phase prompt only when it's our turn (or the phase has no
        // turn concept, like RESOLVE_BUILDINGS_AND_EVENTS).
        GamePhase phase = state.getPhase();

        if (phase == GamePhase.WAITING_FOR_PLAYER_COUNT
                && isMyTurn(state)
                && !waitingPlayerCountResponse) {
            askPlayerCount();
            return;
        }
        if (phase == GamePhase.COLOR_PICKING
                && isMyTurn(state)
                && !waitingColorResponse) {
            askColor();
            return;
        }
        if (phase == GamePhase.PICK_OFFERING_TILE
                && isMyTurn(state)
                && !waitingOfferingTileResponse) {
            askOfferingTile();
            return;
        }
        if (phase == GamePhase.TURN_PICK_CARDS_CHARACTER
                && isMyTurn(state)
                && !waitingCharactersResponse) {
            askCharacterCards();
            return;
        }
        if (phase == GamePhase.TURN_PICK_CARDS_BUILDING
                && isMyTurn(state)
                && !waitingBuildingsResponse) {
            askBuildingCards();
            return;
        }
        if (phase == GamePhase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING
                && isMyTurn(state)
                && !waitingExtraResponse) {
            askExtraPick();
            return;
        }

        if (phase == GamePhase.GAME_ENDED) {
            printEndOfGame(state);
        }
    }

    /** {@code true} iff the server's currentPlayerNickname matches ours. */
    private boolean isMyTurn(GameStateDto state) {
        return myNickname != null
                && myNickname.equals(state.getCurrentPlayerNickname());
    }

    // ===================================================================
    // Per-phase prompts
    // ===================================================================

    private void askNickname() {
        System.out.print("Choose nickname: ");
        String nickname = scanner.nextLine();
        pendingNickname = nickname;
        waitingNicknameResponse = true;
        send(new SetNicknameMessage(nickname));
    }

    private void askPlayerCount() {
        System.out.print("Choose number of players (2..5): ");
        try {
            int count = Integer.parseInt(scanner.nextLine().trim());
            waitingPlayerCountResponse = true;
            send(new SetPlayerCountMessage(count));
        } catch (NumberFormatException e) {
            System.out.println("Invalid number, try again.");
            askPlayerCount();
        }
    }

    private void askColor() {
        List<TotemColor> available = currentGameState.getAvailableColors();
        System.out.print("Available colors: ");
        for (int i = 0; i < available.size(); i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(available.get(i));
        }
        System.out.println();
        System.out.print("Choose color: ");
        String raw = scanner.nextLine().trim();
        TotemColor parsed;
        try {
            parsed = TotemColor.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.out.println("Unknown color: " + raw);
            askColor();
            return;
        }
        waitingColorResponse = true;
        send(new PickColorMessage(parsed));
    }

    private void askOfferingTile() {
        Board board = currentGameState.getBoard();
        if (board == null) {
            System.out.println("Board not yet available.");
            return;
        }
        List<OfferingTile> track = board.getOfferingTrack();
        System.out.println("Offering track:");
        for (int i = 0; i < track.size(); i++) {
            OfferingTile t = track.get(i);
            String occupied = t.isOccupied()
                    ? ("OCCUPIED by " + t.getCurrentTotemColor())
                    : "FREE";
            System.out.println("  [" + i + "] " + t.getLetter()
                    + " top=" + t.getTopCardsToGet()
                    + " bottom=" + t.getBottomCardsToGet()
                    + " minPlayers=" + t.getMinPlayers()
                    + " food+=" + t.getFoodGained()
                    + " (" + occupied + ")");
        }
        System.out.print("Pick a tile index: ");
        try {
            int idx = Integer.parseInt(scanner.nextLine().trim());
            waitingOfferingTileResponse = true;
            send(new PickOfferingTileMessage(idx));
        } catch (NumberFormatException e) {
            System.out.println("Invalid number, try again.");
            askOfferingTile();
        }
    }

    private void askCharacterCards() {
        Board board = currentGameState.getBoard();
        if (board == null) {
            System.out.println("Board not yet available.");
            return;
        }
        int upperBudget = currentGameState.getUpperRowCardsToDraw();
        int lowerBudget = currentGameState.getLowerRowCardsToDraw();

        System.out.println("CHARACTER PHASE: pick up to " + upperBudget
                + " upper-row character(s) and up to " + lowerBudget
                + " lower-row character(s).");
        printCharactersInRow("Upper", board.getUpperRow());
        printCharactersInRow("Lower", board.getLowerRow());

        List<Integer> upperIds = readCardIds(
                "Upper-row character IDs (space-separated, empty for none): ",
                upperBudget);
        List<Integer> lowerIds = readCardIds(
                "Lower-row character IDs (space-separated, empty for none): ",
                lowerBudget);
        if (upperIds == null || lowerIds == null) {
            // readCardIds already explained the issue; re-ask both.
            askCharacterCards();
            return;
        }

        waitingCharactersResponse = true;
        send(new PickCharacterCardsMessage(upperIds, lowerIds));
    }

    private void askBuildingCards() {
        Board board = currentGameState.getBoard();
        if (board == null) {
            System.out.println("Board not yet available.");
            return;
        }
        int upperBudget = currentGameState.getUpperRowCardsRemainingForBuilding();
        int lowerBudget = currentGameState.getLowerRowCardsRemainingForBuilding();

        System.out.println("BUILDING PHASE: you may buy up to " + upperBudget
                + " upper-row building(s) and up to " + lowerBudget
                + " lower-row building(s).");
        printBuildingsInRow("Upper", board.getUpperRow());
        printBuildingsInRow("Lower", board.getLowerRow());

        List<Integer> upperIds = readCardIds(
                "Upper-row building IDs (space-separated, empty for none): ",
                upperBudget);
        List<Integer> lowerIds = readCardIds(
                "Lower-row building IDs (space-separated, empty for none): ",
                lowerBudget);
        if (upperIds == null || lowerIds == null) {
            askBuildingCards();
            return;
        }

        waitingBuildingsResponse = true;
        send(new PickBuildingCardsMessage(upperIds, lowerIds));
    }

    private void askExtraPick() {
        Board board = currentGameState.getBoard();
        if (board == null) {
            System.out.println("Board not yet available.");
            return;
        }
        System.out.println("EXTRA PICK PHASE: you own the special building. Pick ONE extra card or skip.");
        System.out.println("Available characters:");
        printCharactersInRow("Upper", board.getUpperRow());
        printCharactersInRow("Lower", board.getLowerRow());
        System.out.println("Available buildings:");
        printBuildingsInRow("Upper", board.getUpperRow());
        printBuildingsInRow("Lower", board.getLowerRow());

        System.out.print("Type 'skip', 'character <id>' or 'building <id>': ");
        String line = scanner.nextLine().trim();
        if (line.equalsIgnoreCase("skip") || line.isEmpty()) {
            waitingExtraResponse = true;
            send(new PickExtraCardMessage(null, false));
            return;
        }
        String[] parts = line.split("\\s+");
        if (parts.length != 2) {
            System.out.println("Invalid input.");
            askExtraPick();
            return;
        }
        boolean isCharacter;
        if (parts[0].equalsIgnoreCase("character")) {
            isCharacter = true;
        } else if (parts[0].equalsIgnoreCase("building")) {
            isCharacter = false;
        } else {
            System.out.println("Invalid kind: " + parts[0]);
            askExtraPick();
            return;
        }
        try {
            int id = Integer.parseInt(parts[1]);
            waitingExtraResponse = true;
            send(new PickExtraCardMessage(Integer.valueOf(id), isCharacter));
        } catch (NumberFormatException e) {
            System.out.println("Invalid id: " + parts[1]);
            askExtraPick();
        }
    }

    // ===================================================================
    // Console-input helpers
    // ===================================================================

    /**
     * Reads a list of card IDs from one console line, validating size and
     * uniqueness. Returns {@code null} if the user typed something we
     * cannot recover from (caller will re-prompt the whole sub-phase).
     *
     * @param prompt       the prompt to display
     * @param maxCount     maximum number of IDs the server will accept
     * @return list of parsed IDs (possibly empty), or {@code null} on error
     */
    private List<Integer> readCardIds(String prompt, int maxCount) {
        System.out.print(prompt);
        String line = scanner.nextLine();
        if (line == null) {
            return new ArrayList<Integer>();
        }
        line = line.trim();
        if (line.isEmpty()) {
            return new ArrayList<Integer>();
        }
        String[] tokens = line.split("\\s+");
        if (tokens.length > maxCount) {
            System.out.println("Too many IDs: at most " + maxCount + " allowed.");
            return null;
        }
        List<Integer> ids = new ArrayList<Integer>();
        Set<Integer> seen = new HashSet<Integer>();
        for (int i = 0; i < tokens.length; i++) {
            int id;
            try {
                id = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid ID: " + tokens[i]);
                return null;
            }
            if (seen.contains(Integer.valueOf(id))) {
                System.out.println("Duplicate ID: " + id);
                return null;
            }
            seen.add(Integer.valueOf(id));
            ids.add(Integer.valueOf(id));
        }
        return ids;
    }

    private void printCharactersInRow(String label, CardRow row) {
        if (row == null) {
            System.out.println("  " + label + " row not available");
            return;
        }
        List<CharacterCard> chars = row.getCharacterCards();
        System.out.println("  " + label + " row characters (" + chars.size() + "):");
        for (int i = 0; i < chars.size(); i++) {
            CharacterCard c = chars.get(i);
            System.out.println("    ID " + c.getCardID() + " :: " + c.getClass().getSimpleName());
        }
    }

    private void printBuildingsInRow(String label, CardRow row) {
        if (row == null) {
            System.out.println("  " + label + " row not available");
            return;
        }
        List<BuildingCard> buildings = row.getBuildingCards();
        System.out.println("  " + label + " row buildings (" + buildings.size() + "):");
        for (int i = 0; i < buildings.size(); i++) {
            BuildingCard b = buildings.get(i);
            System.out.println("    ID " + b.getCardID()
                    + " cost=" + b.getFoodCost()
                    + " :: " + b.getClass().getSimpleName());
        }
    }

    // ===================================================================
    // Outgoing wire write
    // ===================================================================

    private void send(Object object) {
        try {
            out.reset();
            out.writeObject(object);
            out.flush();
        } catch (IOException e) {
            System.out.println("Could not send message to server: " + e.getMessage());
        }
    }

    // ===================================================================
    // Pretty-printing the game state
    // ===================================================================

    private void printGameState(GameStateDto state) {
        System.out.println();
        System.out.println("========== GAME STATE ==========");
        System.out.println("Phase:          " + state.getPhase());
        System.out.println("Round / Era:    " + state.getRound() + " / " + state.getEra());
        System.out.println("Players:        " + state.getPlayers());
        System.out.println("Player colors:  " + state.getPlayerColors());
        if (state.getColorPickTurnOrder() != null
                && !state.getColorPickTurnOrder().isEmpty()) {
            System.out.println("Color pick order: " + state.getColorPickTurnOrder());
        }
        System.out.println("Turn order:     " + state.getPlayersTurnOrderTile());
        System.out.println("Current player: " + state.getCurrentPlayerNickname());
        System.out.println("Available colors: " + state.getAvailableColors());

        // Per-turn budgets only meaningful in the picking phases.
        GamePhase p = state.getPhase();
        if (p == GamePhase.TURN_PICK_CARDS_CHARACTER
                || p == GamePhase.TURN_PICK_CARDS_BUILDING) {
            System.out.println("Char budget:    upper=" + state.getUpperRowCardsToDraw()
                    + " lower=" + state.getLowerRowCardsToDraw());
            System.out.println("Build leftover: upper=" + state.getUpperRowCardsRemainingForBuilding()
                    + " lower=" + state.getLowerRowCardsRemainingForBuilding());
        }

        // Resources: only useful once the match has started.
        if (state.getPlayerFood() != null && !state.getPlayerFood().isEmpty()) {
            System.out.println("Food:     " + state.getPlayerFood());
        }
        if (state.getPlayerPrestige() != null && !state.getPlayerPrestige().isEmpty()) {
            System.out.println("Prestige: " + state.getPlayerPrestige());
        }

        System.out.println("Message: " + state.getMessage());
        System.out.println("================================");
        System.out.println();
    }

    /** Prints the leaderboard + winner list when the game ends. */
    private void printEndOfGame(GameStateDto state) {
        System.out.println("=========== GAME OVER ===========");
        Map<String, Integer> food = state.getPlayerFood();
        Map<String, Integer> prestige = state.getPlayerPrestige();
        List<String> leaderboard = state.getLeaderboard();
        List<String> winners = state.getWinners();
        System.out.println("Leaderboard:");
        for (int i = 0; i < leaderboard.size(); i++) {
            String nick = leaderboard.get(i);
            Integer pp = (prestige == null) ? null : prestige.get(nick);
            Integer fd = (food == null) ? null : food.get(nick);
            System.out.println("  " + (i + 1) + ". " + nick
                    + "  PP=" + pp + "  Food=" + fd);
        }
        if (winners.size() == 1) {
            System.out.println("Winner: " + winners.get(0));
        } else {
            System.out.println("Shared victory between: " + winners);
        }
        System.out.println("=================================");
    }
}
