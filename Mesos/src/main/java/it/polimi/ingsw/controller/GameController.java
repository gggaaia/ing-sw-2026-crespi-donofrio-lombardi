package it.polimi.ingsw.controller;

import it.polimi.ingsw.exceptions.DeckLoadException;
import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.GameManager;
import it.polimi.ingsw.model.Match;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.network.shared.ClientCallback;
import it.polimi.ingsw.network.shared.GamePhase;
import it.polimi.ingsw.network.shared.GameStateDto;
import it.polimi.ingsw.network.shared.messages.server.ActionResultMessage;
import it.polimi.ingsw.network.shared.messages.server.ErrorMessage;
import it.polimi.ingsw.network.shared.messages.server.GameStateMessage;
import it.polimi.ingsw.network.shared.messages.server.InfoMessage;
import it.polimi.ingsw.network.shared.messages.server.ServerMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Owns the game-side state machine. The {@code GameController} is the only
 * place that knows the rules; the {@code Server} / {@code ClientHandler}
 * pair only deals with sockets and message routing.
 * <p>
 * All public methods are {@code synchronized} on this controller instance
 * so that messages from different client threads can safely mutate the
 * shared state in any order.
 * <p>
 * The class purposely avoids advanced Java features (no streams, no
 * lambdas) so the rule-by-rule logic is easy to read and easy to debug.
 */
public class GameController {

    // ==================================================================
    // Constants
    // ==================================================================

    /** Minimum number of players the game accepts. */
    private static final int MIN_PLAYERS = 2;

    /** Maximum number of players the game accepts (also the number of
     *  totem colors available). */
    private static final int MAX_PLAYERS = 5;

    /** Maximum allowed length for a nickname. */
    private static final int MAX_NICKNAME_LENGTH = 16;

    /** How long to wait, when only one player is left in a match, before
     *  declaring them the lone-player winner by default (Pass 2, E4). */
    private static final long LONE_PLAYER_TIMEOUT_MINUTES = 2;

    // String constants used as the "action" field of ActionResultMessage,
    // shared with the client.
    private static final String ACTION_NICKNAME = "NICKNAME";
    private static final String ACTION_PLAYER_COUNT = "PLAYER_COUNT";
    private static final String ACTION_PICK_COLOR = "PICK_COLOR";
    private static final String ACTION_PICK_OFFERING_TILE = "PICK_OFFERING_TILE";
    private static final String ACTION_PICK_CHARACTERS = "PICK_CHARACTERS";
    private static final String ACTION_PICK_BUILDINGS = "PICK_BUILDINGS";
    private static final String ACTION_PICK_EXTRA = "PICK_EXTRA";

    // ==================================================================
    // Lobby / connection state
    // ==================================================================

    /** All currently connected clients, in the order they joined. */
    private final List<ClientCallback> clients = new ArrayList<>();

    /** Current phase of the FSM. */
    private GamePhase phase = GamePhase.WAITING_FOR_NICKNAME;

    /** The nickname of the very first player who set theirs (the only one
     *  allowed to choose the player count). */
    private String firstPlayerNickname;

    /** The number of players the first player asked for; {@code 0} until
     *  set, then {@code 2..5}. */
    private int expectedPlayers;

    /** Totem colors that nobody has picked yet. Initialised to all values
     *  of {@link TotemColor}; entries are removed as colors are chosen. */
    private final List<TotemColor> availableColors = new ArrayList<>();

    /** Queue of clients still waiting to pick a color, in join order. */
    private final Queue<ClientCallback> colorPickQueue = new LinkedList<>();

    // ==================================================================
    // Match-level state (null until COLOR_PICKING is over)
    // ==================================================================

    /** The active match. {@code null} until every color has been picked
     *  and {@link #startMatchAndEnterPickOfferingTile()} has run. */
    private Match match;

    /** Registry that tracks {@link #match} (and any future matches). */
    private final GameManager gameManager = new GameManager();

    // ==================================================================
    // Per-phase scratch state
    // ==================================================================

    /** Nickname of the client whose input the controller is currently
     *  expecting. {@code null} when no specific client is awaited (e.g.
     *  during {@link GamePhase#RESOLVE_BUILDINGS_AND_EVENTS}). */
    private String currentPlayerNickname;

    /** During {@link GamePhase#PICK_OFFERING_TILE}: players left to place
     *  a totem, in turn-order-tile slot order. We pop the head as each
     *  player places. */
    private List<Player> turnOrderForOfferingPick = new ArrayList<>();

    /** Index of the player currently picking, into
     *  {@link #turnOrderForOfferingPick}. */
    private int currentOfferingPickIndex;

    /** During {@link GamePhase#TURN_PICK_CARDS_CHARACTER} and
     *  {@link GamePhase#TURN_PICK_CARDS_BUILDING}: the players to be
     *  resolved, in offering-track order. */
    private List<Player> resolutionOrder = new ArrayList<>();

    /** Index into {@link #resolutionOrder}. */
    private int currentResolutionIndex;

    /** Top count of the offering tile of the player currently being
     *  resolved (= maximum upper-row character cards they may draw). */
    private int currentUpperBudget;

    /** Bottom count of the same tile (= maximum lower-row character cards
     *  they may draw). */
    private int currentLowerBudget;

    /** Upper-row budget left for the building phase, after the character
     *  phase consumed some of it. */
    private int currentUpperRemainingForBuilding;

    /** Lower-row budget left for the building phase. */
    private int currentLowerRemainingForBuilding;

    /** During {@link GamePhase#RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING}:
     *  the (single) player who owns the special "extra pick" building. */
    private Player extraPickPlayer;

    // ==================================================================
    // Pass 2 (resilience) state
    // ==================================================================

    /** When the controller is in {@link GamePhase#PAUSED_WAITING_RECONNECT},
     *  the phase it should resume once enough players reconnect.
     *  {@code null} when not paused. */
    private GamePhase phaseBeforePause;

    /** When the lone-player timer fires we override the normal
     *  end-of-game winners list with this one. {@code null} in normal
     *  scoring; non-null only during a lone-player default victory. */
    private List<String> forcedWinners;

    /** Single-thread daemon scheduler used to fire the lone-player
     *  timeout. Daemon so it doesn't keep the JVM alive after main exits. */
    private final ScheduledExecutorService scheduler;

    /** The currently scheduled lone-player timeout (or {@code null} if no
     *  timer is running). */
    private ScheduledFuture<?> lonePlayerTask;

    // ==================================================================
    // Construction
    // ==================================================================

    /**
     * Creates a controller in the {@link GamePhase#WAITING_FOR_NICKNAME}
     * phase, with all five totem colors available.
     */
    public GameController() {
        // Start with every totem color marked as available; entries are
        // removed as players pick.
        for (int i = 0; i < TotemColor.values().length; i++) {
            availableColors.add(TotemColor.values()[i]);
        }

        // Build a daemon-thread executor for the lone-player timer. We
        // use an explicit ThreadFactory inner class (no lambda) so the
        // intent ("make the thread a daemon") is obvious.
        ThreadFactory daemonFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "lone-player-timeout");
                t.setDaemon(true);
                return t;
            }
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory);
    }

    // ==================================================================
    // Connection / disconnection hooks (called by ClientHandler)
    // ==================================================================

    /**
     * Called by the network layer right after a TCP connection has been
     * accepted and a {@link ClientCallback} has been built. Sends the
     * connecting client an initial state snapshot so its UI knows what
     * input is expected.
     *
     * @param client the just-connected client
     */
    public synchronized void onClientConnected(ClientCallback client) {
        // Send a friendly hello and the current state. The client cannot
        // yet do anything but pick a nickname.
        client.send(new InfoMessage("Connected to server"));
        sendGameStateTo(client, "Connected. Waiting for nickname if allowed.");
    }

    /**
     * Called by the network layer when a client disconnects (either
     * cleanly or because of a network/peer failure).
     * <p>
     * Pass 2 behaviour:
     * <ul>
     *   <li>Lobby phases (WAITING_FOR_*, COLOR_PICKING): drop the client
     *       entirely, free their color (if any), promote a new "first
     *       player" if needed.</li>
     *   <li>In-match phases (PICK_OFFERING_TILE..GAME_ENDED): keep the
     *       client in the list but mark them disconnected. If they were
     *       the current player, advance to the next connected one. If we
     *       drop to one connected player, pause and start the lone-player
     *       timer.</li>
     * </ul>
     *
     * @param client the disconnecting client
     */
    public synchronized void onClientDisconnected(ClientCallback client) {
        // If this client never registered a nickname there is nothing to
        // clean up beyond the entry in the clients list.
        boolean hadNickname = client.getNickname() != null;
        if (!hadNickname) {
            clients.remove(client);
            colorPickQueue.remove(client);
            return;
        }

        String nickname = client.getNickname();
        boolean wasInMatch = phaseIsInMatch();

        if (!wasInMatch) {
            // Pass 1-style cleanup: drop the client, recycle color, etc.
            handleLobbyDisconnect(client, nickname);
            return;
        }

        // In-match: keep the slot, mark disconnected.
        client.markDisconnected();
        broadcastInfo(nickname + " disconnected. Their turns will be skipped.");

        int connected = countConnectedClients();

        if (connected == 0) {
            // Nobody is online any more. Pause indefinitely (no timer
            // because nobody could be the lone winner). When somebody
            // reconnects, handleReconnectMaybeResume will resume.
            // We deliberately keep currentPlayerNickname unchanged so
            // resumeFromPause can pick up from the same player; clients
            // are expected to look at phase==PAUSED first and treat
            // currentPlayerNickname as a hint only.
            cancelLonePlayerTimeoutIfActive();
            phaseBeforePause = phase;
            phase = GamePhase.PAUSED_WAITING_RECONNECT;
            broadcastGameState("All players disconnected. Waiting for reconnect.");
            persistAfterTransition();
            return;
        }

        if (connected == 1) {
            // Lone-player condition: pause + start the 2-minute timer.
            phaseBeforePause = phase;
            phase = GamePhase.PAUSED_WAITING_RECONNECT;
            startLonePlayerTimeout();
            broadcastGameState("Only one player left. Waiting for reconnect ("
                    + LONE_PLAYER_TIMEOUT_MINUTES + " min).");
            persistAfterTransition();
            return;
        }

        // 2+ still connected: just skip this player's turn if it was theirs.
        advanceCurrentPlayerIfDisconnected();
        broadcastGameState("A player disconnected; their turns will be skipped.");
    }

    /**
     * Pure-lobby disconnect cleanup (Pass 1 semantics, kept for the lobby
     * phases where there is nothing to "skip" or "reconnect to").
     */
    private void handleLobbyDisconnect(ClientCallback client, String nickname) {
        clients.remove(client);
        colorPickQueue.remove(client);

        TotemColor color = client.getPickedColor();
        if (color != null && !availableColors.contains(color)) {
            availableColors.add(color);
        }

        if (nickname.equals(firstPlayerNickname)) {
            if (clients.isEmpty()) {
                resetToLobby();
                return;
            }
            ClientCallback newFirst = clients.get(0);
            firstPlayerNickname = newFirst.getNickname();
            currentPlayerNickname = firstPlayerNickname;
            phase = GamePhase.WAITING_FOR_PLAYER_COUNT;
        }

        broadcastInfo(nickname + " disconnected.");
        broadcastGameState("A player disconnected.");
    }

    /**
     * @return {@code true} if the current {@link #phase} is one of the
     *         in-match phases (i.e. the {@link Match} object is built and
     *         the game is in progress)
     */
    private boolean phaseIsInMatch() {
        return phase == GamePhase.PICK_OFFERING_TILE
                || phase == GamePhase.TURN_PICK_CARDS_CHARACTER
                || phase == GamePhase.TURN_PICK_CARDS_BUILDING
                || phase == GamePhase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING
                || phase == GamePhase.RESOLVE_BUILDINGS_AND_EVENTS
                || phase == GamePhase.PAUSED_WAITING_RECONNECT
                || phase == GamePhase.GAME_ENDED;
    }

    // ==================================================================
    // Public handlers (one per client message type)
    // ==================================================================

    /**
     * Handles a {@code SetNicknameMessage}. There are three possible
     * outcomes:
     * <ol>
     *   <li><b>Reconnect to an existing client slot</b>: the nickname
     *       matches a client that the controller already knows about and
     *       that is currently disconnected. We rebind that slot to the
     *       newly connected callback.</li>
     *   <li><b>Reconnect to a restored snapshot</b>: the nickname matches
     *       a {@link Player} restored from disk for which no callback
     *       exists yet. We add a new client entry and bind it to the
     *       restored player.</li>
     *   <li><b>New joiner</b>: the nickname does not match any existing
     *       slot or player. Only valid in lobby phases (Pass 1 logic).</li>
     * </ol>
     */
    public synchronized void handleNickname(ClientCallback client, String nickname) {
        // Reject if this client already has a nickname (means the message
        // was sent twice).
        if (client.getNickname() != null) {
            sendActionResult(client, ACTION_NICKNAME, false,
                    "You already have a nickname");
            return;
        }

        // Validate the raw nickname (length, non-empty, ...). Note: the
        // "already used by a connected client" check happens later because
        // here we want to allow reconnection (same nickname as a known
        // disconnected client).
        String validationError = validateNicknameSyntax(nickname);
        if (validationError != null) {
            sendActionResult(client, ACTION_NICKNAME, false, validationError);
            sendGameStateTo(client, "Nickname invalid");
            return;
        }
        String trimmed = nickname.trim();

        // ---- Case 1: reconnect to an existing client slot. ----
        ClientCallback existing = findClientByNickname(trimmed);
        if (existing != null) {
            if (existing.isConnected()) {
                // Same nickname is currently held by a live client.
                sendActionResult(client, ACTION_NICKNAME, false,
                        "Nickname already in use");
                return;
            }
            // Rebind: copy over nickname, color and Player from the dead
            // slot to the new live callback, then replace in the list.
            rebindClientSlot(existing, client);
            sendActionResult(client, ACTION_NICKNAME, true,
                    "Reconnected as " + trimmed);
            broadcastInfo(trimmed + " reconnected");
            sendGameStateTo(client, "Welcome back");
            broadcastGameState("A player reconnected");
            handleReconnectMaybeResume();
            return;
        }

        // ---- Case 2: reconnect to a restored snapshot. ----
        if (match != null) {
            Player ghost = findPlayerByNickname(trimmed);
            if (ghost != null) {
                // First time this restored player is seen by a client.
                client.setNickname(trimmed);
                client.setPickedColor(ghost.getPlayerColor());
                client.setPlayer(ghost);
                clients.add(client);
                sendActionResult(client, ACTION_NICKNAME, true,
                        "Reconnected (restored) as " + trimmed);
                broadcastInfo(trimmed + " reconnected");
                sendGameStateTo(client, "Welcome back");
                broadcastGameState("A player reconnected");
                handleReconnectMaybeResume();
                return;
            }
        }

        // ---- Case 3: new joiner (lobby only). ----
        if (phase != GamePhase.WAITING_FOR_NICKNAME
                && phase != GamePhase.WAITING_FOR_PLAYER_COUNT
                && phase != GamePhase.WAITING_FOR_PLAYERS) {
            sendActionResult(client, ACTION_NICKNAME, false,
                    "You cannot join the game now");
            sendGameStateTo(client, "Nickname rejected");
            return;
        }
        if (expectedPlayers > 0 && clients.size() >= expectedPlayers) {
            sendActionResult(client, ACTION_NICKNAME, false,
                    "The game is already full");
            sendGameStateTo(client, "Game full");
            return;
        }

        // Accept.
        client.setNickname(trimmed);
        clients.add(client);
        sendActionResult(client, ACTION_NICKNAME, true,
                "Nickname accepted: " + trimmed);

        // First joiner: promote to "first player" so they can pick the
        // total count.
        if (clients.size() == 1) {
            firstPlayerNickname = trimmed;
            currentPlayerNickname = trimmed;
            phase = GamePhase.WAITING_FOR_PLAYER_COUNT;
            broadcastInfo(trimmed + " joined as first player");
            broadcastGameState("First player must choose player count");
            return;
        }

        broadcastInfo(trimmed + " joined the game");

        if (expectedPlayers == 0) {
            phase = GamePhase.WAITING_FOR_PLAYER_COUNT;
            currentPlayerNickname = firstPlayerNickname;
            broadcastGameState("First player must choose player count");
        } else if (clients.size() == expectedPlayers) {
            startColorPickingPhase();
        } else {
            phase = GamePhase.WAITING_FOR_PLAYERS;
            currentPlayerNickname = null;
            broadcastGameState("Waiting for more players");
        }
    }

    /**
     * Handles a {@code SetPlayerCountMessage} from the first player.
     */
    public synchronized void handlePlayerCount(ClientCallback client, int playerCount) {
        if (phase != GamePhase.WAITING_FOR_PLAYER_COUNT) {
            sendActionResult(client, ACTION_PLAYER_COUNT, false,
                    "You cannot set player count now");
            sendGameStateTo(client, "Wrong phase");
            return;
        }
        if (client.getNickname() == null
                || !client.getNickname().equals(firstPlayerNickname)) {
            sendActionResult(client, ACTION_PLAYER_COUNT, false,
                    "Only the first player can choose player count");
            return;
        }
        if (playerCount < MIN_PLAYERS) {
            sendActionResult(client, ACTION_PLAYER_COUNT, false,
                    "Player count must be at least " + MIN_PLAYERS);
            return;
        }
        if (playerCount > MAX_PLAYERS) {
            sendActionResult(client, ACTION_PLAYER_COUNT, false,
                    "Player count cannot be greater than " + MAX_PLAYERS);
            return;
        }
        if (playerCount < clients.size()) {
            sendActionResult(client, ACTION_PLAYER_COUNT, false,
                    "Player count cannot be lower than already connected players");
            return;
        }

        expectedPlayers = playerCount;
        sendActionResult(client, ACTION_PLAYER_COUNT, true,
                "Player count set to " + expectedPlayers);

        if (clients.size() == expectedPlayers) {
            startColorPickingPhase();
        } else {
            phase = GamePhase.WAITING_FOR_PLAYERS;
            currentPlayerNickname = null;
            broadcastGameState("Waiting for " + expectedPlayers + " players");
        }
    }

    /**
     * Handles a {@code PickColorMessage}. On the last successful pick, the
     * controller builds the {@link Match} and transitions to
     * {@link GamePhase#PICK_OFFERING_TILE}.
     */
    public synchronized void handlePickColor(ClientCallback client, TotemColor color) {
        if (phase != GamePhase.COLOR_PICKING) {
            sendActionResult(client, ACTION_PICK_COLOR, false,
                    "You cannot pick a color now");
            sendGameStateTo(client, "Wrong phase");
            return;
        }
        ClientCallback expected = colorPickQueue.peek();
        if (expected == null) {
            sendActionResult(client, ACTION_PICK_COLOR, false,
                    "No current color picker");
            return;
        }
        if (client != expected) {
            sendActionResult(client, ACTION_PICK_COLOR, false,
                    "It is not your turn to pick a color");
            sendGameStateTo(client, "Not your turn");
            return;
        }
        if (color == null) {
            sendActionResult(client, ACTION_PICK_COLOR, false,
                    "Color cannot be null");
            return;
        }
        if (!availableColors.contains(color)) {
            sendActionResult(client, ACTION_PICK_COLOR, false,
                    "Color " + color + " is not available");
            sendGameStateTo(client, "Choose another color");
            return;
        }

        // Apply: remember the choice on the callback, build the Player
        // object (per Q4 = "construct at color pick"), bind both ways.
        client.setPickedColor(color);
        Player player = new Player(client.getNickname(), color);
        client.setPlayer(player);
        availableColors.remove(color);
        colorPickQueue.poll();

        sendActionResult(client, ACTION_PICK_COLOR, true,
                "Color picked: " + color);
        broadcastInfo(client.getNickname() + " picked " + color);

        if (colorPickQueue.isEmpty()) {
            // Everyone has a color: build and start the match.
            startMatchAndEnterPickOfferingTile();
        } else {
            // Hand the turn to the next color picker.
            currentPlayerNickname = colorPickQueue.peek().getNickname();
            broadcastGameState("Next player must pick a color");
        }
    }

    /**
     * Handles a {@code PickOfferingTileMessage}: the current player wants
     * to place their totem on offering tile {@code tileIndex}.
     */
    public synchronized void handlePickOfferingTile(ClientCallback client, int tileIndex) {
        if (phase != GamePhase.PICK_OFFERING_TILE) {
            sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "You cannot pick an offering tile now");
            sendGameStateTo(client, "Wrong phase");
            return;
        }
        Player player = client.getPlayer();
        if (player == null) {
            sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "You have no Player bound");
            return;
        }
        Player expectedPlayer = turnOrderForOfferingPick.get(currentOfferingPickIndex);
        if (player != expectedPlayer) {
            sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "It is not your turn to pick an offering tile");
            sendGameStateTo(client, "Not your turn");
            return;
        }

        Board board = match.getBoard();
        List<OfferingTile> track = board.getOfferingTrack();
        if (tileIndex < 0 || tileIndex >= track.size()) {
            sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "Tile index out of range");
            return;
        }
        OfferingTile tile = track.get(tileIndex);
        if (tile.isOccupied()) {
            sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "Tile " + tileIndex + " is already occupied");
            sendGameStateTo(client, "Pick a free tile");
            return;
        }
        if (tile.getMinPlayers() > match.getPlayersList().size()) {
            sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "Tile " + tile.getLetter() + " requires more players");
            sendGameStateTo(client, "Pick a tile your group can use");
            return;
        }

        // Apply.
        boolean ok = board.moveTotem(tileIndex, player);
        if (!ok) {
            // Should not happen because we just validated, but defensive.
            sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "Internal error: moveTotem failed");
            return;
        }
        sendActionResult(client, ACTION_PICK_OFFERING_TILE, true,
                "Totem placed on tile " + tile.getLetter());
        broadcastInfo(player.getNickname() + " placed totem on tile " + tile.getLetter());

        // Next picker, or transition to the next phase if everyone placed.
        currentOfferingPickIndex++;
        // Skip past any disconnected players that may sit between the
        // last placer and the next still-live one.
        advanceOfferingPickerUntilConnectedAndBroadcast();
    }

    /**
     * Handles a {@code PickCharacterCardsMessage}: the current player
     * picks 0..top character cards from the upper row and 0..bottom from
     * the lower row.
     */
    public synchronized void handlePickCharacterCards(
            ClientCallback client,
            List<Integer> upperRowIds,
            List<Integer> lowerRowIds
    ) {
        if (phase != GamePhase.TURN_PICK_CARDS_CHARACTER) {
            sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "You cannot pick character cards now");
            sendGameStateTo(client, "Wrong phase");
            return;
        }
        Player player = client.getPlayer();
        Player expectedPlayer = resolutionOrder.get(currentResolutionIndex);
        if (player == null || player != expectedPlayer) {
            sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "It is not your turn to pick characters");
            sendGameStateTo(client, "Not your turn");
            return;
        }
        if (upperRowIds == null) upperRowIds = new ArrayList<>();
        if (lowerRowIds == null) lowerRowIds = new ArrayList<>();

        // Per-row size budgets.
        if (upperRowIds.size() > currentUpperBudget) {
            sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "You can take at most " + currentUpperBudget
                            + " upper-row character card(s)");
            return;
        }
        if (lowerRowIds.size() > currentLowerBudget) {
            sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "You can take at most " + currentLowerBudget
                            + " lower-row character card(s)");
            return;
        }

        // No duplicate IDs within a single list.
        if (hasDuplicates(upperRowIds)) {
            sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "Duplicate upper-row card ID");
            return;
        }
        if (hasDuplicates(lowerRowIds)) {
            sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "Duplicate lower-row card ID");
            return;
        }

        // Look up every requested card; abort the whole batch on the first
        // miss so we never apply a half-batch.
        Board board = match.getBoard();
        List<CharacterCard> foundUpper = new ArrayList<>();
        for (int i = 0; i < upperRowIds.size(); i++) {
            int id = upperRowIds.get(i);
            CharacterCard found = findCharacterById(board.getUpperRow(), id);
            if (found == null) {
                sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                        "Upper-row character ID " + id + " not available");
                sendGameStateTo(client, "Pick again");
                return;
            }
            foundUpper.add(found);
        }
        List<CharacterCard> foundLower = new ArrayList<>();
        for (int i = 0; i < lowerRowIds.size(); i++) {
            int id = lowerRowIds.get(i);
            CharacterCard found = findCharacterById(board.getLowerRow(), id);
            if (found == null) {
                sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                        "Lower-row character ID " + id + " not available");
                sendGameStateTo(client, "Pick again");
                return;
            }
            foundLower.add(found);
        }

        // Apply: remove each card from the board (returning the same
        // instance), trigger its draw effect, add to player's hand.
        for (int i = 0; i < foundUpper.size(); i++) {
            CharacterCard card = foundUpper.get(i);
            Card removed = board.removeCardFromRows(card);
            // Defensive: should equal `card` because we just looked it up.
            CharacterCard cc = (CharacterCard) removed;
            cc.drawEffect(player);
            player.addCardToHand(cc);
        }
        for (int i = 0; i < foundLower.size(); i++) {
            CharacterCard card = foundLower.get(i);
            Card removed = board.removeCardFromRows(card);
            CharacterCard cc = (CharacterCard) removed;
            cc.drawEffect(player);
            player.addCardToHand(cc);
        }

        // Compute per-row leftover for the building phase. Per the spec
        // (Q1/Q2), the leftover is per-row, not total.
        currentUpperRemainingForBuilding = currentUpperBudget - upperRowIds.size();
        currentLowerRemainingForBuilding = currentLowerBudget - lowerRowIds.size();

        sendActionResult(client, ACTION_PICK_CHARACTERS, true,
                "Drew " + upperRowIds.size() + " upper + "
                        + lowerRowIds.size() + " lower character card(s)");
        broadcastInfo(player.getNickname() + " drew character cards");

        // Same player goes into the building sub-phase.
        phase = GamePhase.TURN_PICK_CARDS_BUILDING;
        broadcastGameState("Pick buildings (or none) within remaining budget");
        persistAfterTransition();
    }

    /**
     * Handles a {@code PickBuildingCardsMessage}: the current player
     * spends some of their leftover row budgets to buy buildings,
     * deducting their food cost (after the builder discount).
     */
    public synchronized void handlePickBuildingCards(
            ClientCallback client,
            List<Integer> upperRowIds,
            List<Integer> lowerRowIds
    ) {
        if (phase != GamePhase.TURN_PICK_CARDS_BUILDING) {
            sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "You cannot buy buildings now");
            sendGameStateTo(client, "Wrong phase");
            return;
        }
        Player player = client.getPlayer();
        Player expectedPlayer = resolutionOrder.get(currentResolutionIndex);
        if (player == null || player != expectedPlayer) {
            sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "It is not your turn to buy buildings");
            sendGameStateTo(client, "Not your turn");
            return;
        }
        if (upperRowIds == null) upperRowIds = new ArrayList<>();
        if (lowerRowIds == null) lowerRowIds = new ArrayList<>();

        // Per-row remaining budgets (Q1/Q2).
        if (upperRowIds.size() > currentUpperRemainingForBuilding) {
            sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "You can take at most "
                            + currentUpperRemainingForBuilding
                            + " upper-row building(s)");
            return;
        }
        if (lowerRowIds.size() > currentLowerRemainingForBuilding) {
            sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "You can take at most "
                            + currentLowerRemainingForBuilding
                            + " lower-row building(s)");
            return;
        }

        if (hasDuplicates(upperRowIds)) {
            sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "Duplicate upper-row building ID");
            return;
        }
        if (hasDuplicates(lowerRowIds)) {
            sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "Duplicate lower-row building ID");
            return;
        }

        // Look up each requested building.
        Board board = match.getBoard();
        List<BuildingCard> foundUpper = new ArrayList<>();
        for (int i = 0; i < upperRowIds.size(); i++) {
            int id = upperRowIds.get(i);
            BuildingCard found = findBuildingById(board.getUpperRow(), id);
            if (found == null) {
                sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                        "Upper-row building ID " + id + " not available");
                sendGameStateTo(client, "Pick again");
                return;
            }
            foundUpper.add(found);
        }
        List<BuildingCard> foundLower = new ArrayList<>();
        for (int i = 0; i < lowerRowIds.size(); i++) {
            int id = lowerRowIds.get(i);
            BuildingCard found = findBuildingById(board.getLowerRow(), id);
            if (found == null) {
                sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                        "Lower-row building ID " + id + " not available");
                sendGameStateTo(client, "Pick again");
                return;
            }
            foundLower.add(found);
        }

        // Compute the total food cost (with the builder discount applied
        // to the gross sum, never going below zero).
        int grossCost = 0;
        for (int i = 0; i < foundUpper.size(); i++) {
            grossCost += foundUpper.get(i).getFoodCost();
        }
        for (int i = 0; i < foundLower.size(); i++) {
            grossCost += foundLower.get(i).getFoodCost();
        }
        int discount = player.getBuildingDiscountBuilder();
        int netCost = grossCost - discount;
        if (netCost < 0) {
            netCost = 0;
        }
        if (!player.hasEnoughFood(netCost)) {
            sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "You need " + netCost + " food but have only "
                            + player.getFood());
            return;
        }

        // Apply: remove each from the board, set ownership, add to hand,
        // pay the food.
        for (int i = 0; i < foundUpper.size(); i++) {
            BuildingCard card = foundUpper.get(i);
            Card removed = board.removeCardFromRows(card);
            BuildingCard bc = (BuildingCard) removed;
            bc.setOwner(player);
            player.addBuildingToHand(bc);
        }
        for (int i = 0; i < foundLower.size(); i++) {
            BuildingCard card = foundLower.get(i);
            Card removed = board.removeCardFromRows(card);
            BuildingCard bc = (BuildingCard) removed;
            bc.setOwner(player);
            player.addBuildingToHand(bc);
        }
        player.changeFood(-netCost);

        sendActionResult(client, ACTION_PICK_BUILDINGS, true,
                "Bought " + (upperRowIds.size() + lowerRowIds.size())
                        + " building(s) for " + netCost + " food");
        broadcastInfo(player.getNickname() + " bought building(s)");

        // Advance to the next resolving player, skipping any disconnected
        // ones; if the list is exhausted, run the post-resolution
        // housekeeping.
        currentResolutionIndex++;
        advanceResolverUntilConnectedAndContinue();
    }

    /**
     * Handles a {@code PickExtraCardMessage}: the player who owns a
     * special "extra pick" building takes one bonus card. Sending
     * {@code cardId == null} means "skip".
     */
    public synchronized void handlePickExtraCard(
            ClientCallback client,
            Integer cardId,
            boolean isCharacter
    ) {
        if (phase != GamePhase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING) {
            sendActionResult(client, ACTION_PICK_EXTRA, false,
                    "You cannot make an extra pick now");
            sendGameStateTo(client, "Wrong phase");
            return;
        }
        Player player = client.getPlayer();
        if (player == null || player != extraPickPlayer) {
            sendActionResult(client, ACTION_PICK_EXTRA, false,
                    "Only the special-building owner can make this pick");
            sendGameStateTo(client, "Not your pick");
            return;
        }

        // Skip: nothing to do, jump straight to the resolve phase.
        if (cardId == null) {
            sendActionResult(client, ACTION_PICK_EXTRA, true, "Extra pick skipped");
            broadcastInfo(player.getNickname() + " skipped the extra pick");
            enterResolveBuildingsAndEvents();
            return;
        }

        Board board = match.getBoard();
        if (isCharacter) {
            CharacterCard card = findCharacterByIdAnywhere(board, cardId);
            if (card == null) {
                sendActionResult(client, ACTION_PICK_EXTRA, false,
                        "Character ID " + cardId + " not found on the board");
                sendGameStateTo(client, "Pick again");
                return;
            }
            Card removed = board.removeCardFromRows(card);
            CharacterCard cc = (CharacterCard) removed;
            cc.drawEffect(player);
            player.addCardToHand(cc);
            sendActionResult(client, ACTION_PICK_EXTRA, true,
                    "Took extra character card " + cardId);
            broadcastInfo(player.getNickname() + " took an extra character card");
            enterResolveBuildingsAndEvents();
            return;
        }

        // Building: same flow as the normal building phase but for one card.
        BuildingCard card = findBuildingByIdAnywhere(board, cardId);
        if (card == null) {
            sendActionResult(client, ACTION_PICK_EXTRA, false,
                    "Building ID " + cardId + " not found on the board");
            sendGameStateTo(client, "Pick again");
            return;
        }
        int discount = player.getBuildingDiscountBuilder();
        int netCost = card.getFoodCost() - discount;
        if (netCost < 0) {
            netCost = 0;
        }
        if (!player.hasEnoughFood(netCost)) {
            sendActionResult(client, ACTION_PICK_EXTRA, false,
                    "You need " + netCost + " food but have only "
                            + player.getFood());
            return;
        }
        Card removed = board.removeCardFromRows(card);
        BuildingCard bc = (BuildingCard) removed;
        bc.setOwner(player);
        player.addBuildingToHand(bc);
        player.changeFood(-netCost);

        sendActionResult(client, ACTION_PICK_EXTRA, true,
                "Bought extra building " + cardId + " for " + netCost + " food");
        broadcastInfo(player.getNickname() + " bought an extra building");
        enterResolveBuildingsAndEvents();
    }

    // ==================================================================
    // Phase transitions (private)
    // ==================================================================

    /**
     * Transition into {@link GamePhase#COLOR_PICKING}: rebuild the queue,
     * point {@code currentPlayerNickname} at its head, broadcast.
     */
    private void startColorPickingPhase() {
        phase = GamePhase.COLOR_PICKING;
        colorPickQueue.clear();
        for (int i = 0; i < clients.size(); i++) {
            colorPickQueue.add(clients.get(i));
        }
        if (!colorPickQueue.isEmpty()) {
            currentPlayerNickname = colorPickQueue.peek().getNickname();
        }
        broadcastGameState("Color picking phase started");
    }

    /**
     * Transition out of color picking: build the {@link Match}, register
     * every player, start the match, then enter
     * {@link GamePhase#PICK_OFFERING_TILE} for the first totem placer.
     */
    private void startMatchAndEnterPickOfferingTile() {
        // Create + register the match.
        int matchID = gameManager.createMatch();
        match = gameManager.retrieveMatch(matchID);

        // Register every player (in connection order).
        for (int i = 0; i < clients.size(); i++) {
            ClientCallback c = clients.get(i);
            match.addPlayer(c.getPlayer());
        }

        // Run the model's start sequence (populates decks, sets up board,
        // places initial totems on the turn order tile).
        try {
            match.startMatch();
        } catch (DeckLoadException e) {
            broadcastError("Could not load decks: " + e.getMessage()
                    + ". Aborting match.");
            resetToLobby();
            broadcastGameState("Setup failed; back to lobby.");
            return;
        } catch (EmptyDeckException e) {
            broadcastError("Deck ran out during setup: " + e.getMessage()
                    + ". Aborting match.");
            resetToLobby();
            broadcastGameState("Setup failed; back to lobby.");
            return;
        }

        // Build the per-round picker order using the new initial totem
        // positions on the turn order tile.
        turnOrderForOfferingPick =
                match.getBoard().getPlayersTurnOrder(match.getPlayersList());
        currentOfferingPickIndex = 0;

        if (turnOrderForOfferingPick.isEmpty()) {
            // Should not happen because placeInitTotems just ran, but be
            // defensive against future model changes.
            broadcastError("No players on the turn order tile after setup.");
            resetToLobby();
            broadcastGameState("Setup inconsistent; back to lobby.");
            return;
        }

        phase = GamePhase.PICK_OFFERING_TILE;
        // Position the index on the first connected picker (the rest of
        // the FSM relies on currentPlayerNickname being a connected one).
        advanceOfferingPickerUntilConnectedAndBroadcast();
        // The advance helper sends its own broadcast; this persist
        // captures the fresh start-of-match state on disk.
        persistAfterTransition();
    }

    /**
     * Called once every player has placed a totem. Compute the resolution
     * order from the offering track and start with the first connected
     * one (skipping any disconnected players up front).
     */
    private void enterCharacterPickForFirstResolutionPlayer() {
        resolutionOrder = match.getBoard()
                .getOfferingTrackPlayersOrder(match.getPlayersList());
        currentResolutionIndex = 0;

        if (resolutionOrder.isEmpty()) {
            // No totem on any tile (e.g. degenerate case): jump straight
            // to the post-resolution housekeeping.
            afterAllResolutionsDone();
            return;
        }
        advanceResolverUntilConnectedAndContinue();
    }

    /**
     * Set up the per-turn budgets for the current resolving player and
     * broadcast that we are now in {@link GamePhase#TURN_PICK_CARDS_CHARACTER}.
     * <p>
     * Also credits the player with the food the chosen offering tile gives
     * them (this is the only place that food gain happens during a round).
     */
    private void enterCharacterPickForCurrentResolutionPlayer() {
        Player player = resolutionOrder.get(currentResolutionIndex);
        Board board = match.getBoard();

        int tileIndex = board.getOfferingTrackIndex(player);
        OfferingTile tile = board.getOfferingTrack().get(tileIndex);

        currentUpperBudget = tile.getTopCardsToGet();
        currentLowerBudget = tile.getBottomCardsToGet();
        // Default the building remaining to the budget; if the player
        // takes characters, handlePickCharacterCards will overwrite these.
        currentUpperRemainingForBuilding = currentUpperBudget;
        currentLowerRemainingForBuilding = currentLowerBudget;

        // Credit the food granted by the tile right at the start of the
        // resolution for this player.
        player.changeFood(tile.getFoodGained());

        phase = GamePhase.TURN_PICK_CARDS_CHARACTER;
        currentPlayerNickname = player.getNickname();
        broadcastGameState("Pick character cards (or none) within budget");
        persistAfterTransition();
    }

    /**
     * Called once the building phase has finished for the last resolving
     * player. Returns totems to the turn-order tile (E1 semantics: full
     * clear, rebuild from the offering track for connected players, then
     * append every disconnected player at the end so they too take the
     * last-slot PP malus), applies turn-order-tile bonuses, then either
     * jumps to the special-building extra-pick phase or directly to
     * {@link #enterResolveBuildingsAndEvents()}.
     */
    private void afterAllResolutionsDone() {
        Board board = match.getBoard();

        // E1 step 1: clear all totems from the turn-order tile.
        clearTurnOrderTile();

        // E1 step 2: rebuild from the offering track. Only players who
        // actually placed a totem this round get moved across; the model
        // method walks the offering track in order and assigns slots
        // 0, 1, 2, ... to the totems it finds.
        board.moveOfferingTrackToTurnOrder(match.getPlayersList());

        // E1 step 3: append the disconnected players at the end of the
        // turn-order tile, in playersList order. We pick the next free
        // slot index for each disconnected player so they end up at the
        // tail (where the PP malus lives).
        appendDisconnectedPlayersToTurnOrderTile();

        // Apply slot food/PP to every totem currently on the tile (this
        // is where the disconnected players take the last-slot malus).
        match.resolveTurnOrderTile();

        // Look for a player who owns the special "extra pick" building.
        extraPickPlayer = findSpecialBuildingOwner();

        if (extraPickPlayer == null) {
            enterResolveBuildingsAndEvents();
            return;
        }

        // If the special-building owner happens to be disconnected, skip
        // the extra-pick phase entirely.
        ClientCallback ownerCb = findCallbackByPlayer(extraPickPlayer);
        if (ownerCb == null || !ownerCb.isConnected()) {
            broadcastInfo("Special-building owner is disconnected; skipping extra pick.");
            enterResolveBuildingsAndEvents();
            return;
        }

        phase = GamePhase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING;
        currentPlayerNickname = extraPickPlayer.getNickname();
        broadcastGameState(
                extraPickPlayer.getNickname()
                        + " owns the special building and may pick one extra card (or skip)");
        persistAfterTransition();
    }

    /**
     * Walks {@link Match#getPlayersList()} and, for each player whose
     * callback is currently disconnected, places their totem on the next
     * free slot of the turn-order tile. Used by
     * {@link #afterAllResolutionsDone()} to implement E1's "append
     * disconnected" rule.
     */
    private void appendDisconnectedPlayersToTurnOrderTile() {
        // Walk slots 0..N-1; the first unoccupied one is the "next free".
        // We re-scan after each placement to find the new next free slot.
        List<Player> players = match.getPlayersList();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            ClientCallback cb = findCallbackByPlayer(p);
            if (cb != null && cb.isConnected()) {
                continue;
            }
            // Disconnected: find the next free slot and place there.
            int freeSlot = findNextFreeTurnOrderSlot();
            if (freeSlot < 0) {
                // No free slot left (shouldn't happen, but defensive).
                break;
            }
            match.getBoard().getTurnOrderTile().placeTotem(p, freeSlot);
        }
    }

    /**
     * @return the index of the first unoccupied slot on the turn-order
     *         tile, or {@code -1} if every slot is occupied
     */
    private int findNextFreeTurnOrderSlot() {
        it.polimi.ingsw.model.TurnOrderSlot[] slots =
                match.getBoard().getTurnOrderTile().getTurnOrderSlots();
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].isOccupied()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Apply end-of-round buildings and events; then either end the game
     * (final round) or move on to the next round and broadcast the new
     * {@link GamePhase#PICK_OFFERING_TILE}.
     */
    private void enterResolveBuildingsAndEvents() {
        // Per Q9: broadcast on entry (so clients see we are about to
        // resolve), then apply, then broadcast with results.
        phase = GamePhase.RESOLVE_BUILDINGS_AND_EVENTS;
        currentPlayerNickname = null;
        broadcastGameState("Resolving buildings and events...");
        persistAfterTransition();

        // Per Q8 ordering: buildings first, then events.
        match.resolveBuildings();
        match.resolveEvents();

        if (match.getCurrentRound() == Match.FINAL_ROUND) {
            endGame();
            return;
        }

        // Not the final round: refill the board for the next round.
        try {
            match.moveToNextRound();
        } catch (EmptyDeckException e) {
            broadcastError("Deck ran out while preparing the next round: "
                    + e.getMessage() + ". Ending the match early.");
            endGame();
            return;
        }

        // Set up the next round's offering-tile picker order from the
        // turn-order tile that moveOfferingTrackToTurnOrder rebuilt.
        turnOrderForOfferingPick =
                match.getBoard().getPlayersTurnOrder(match.getPlayersList());
        currentOfferingPickIndex = 0;

        phase = GamePhase.PICK_OFFERING_TILE;
        // Skip past any disconnected players right at the start of the
        // new round.
        advanceOfferingPickerUntilConnectedAndBroadcast();
        persistAfterTransition();
    }

    /**
     * Final-round end-of-game sequence per the spec (Q8): shift up→low
     * one last time, resolve any final-event cards that just shifted,
     * apply final scoring, build the leaderboard, transition to
     * {@link GamePhase#GAME_ENDED}, then close the match in the registry.
     */
    private void endGame() {
        Board board = match.getBoard();
        board.shiftUpToLow();
        match.resolveEvents();

        // Apply end-of-game scoring (this is what match.endMatch() does
        // internally, so we call it via the public hook for clarity).
        match.endMatch();

        phase = GamePhase.GAME_ENDED;
        phaseBeforePause = null;
        currentPlayerNickname = null;
        broadcastGameState("Game ended.");

        // Remove the match from the registry and the saved snapshot from
        // disk (Pass 2 persistence cleanup).
        gameManager.closeMatch(match.getMatchID());
        PersistenceManager.delete();
    }

    // ==================================================================
    // Helpers (private)
    // ==================================================================

    /**
     * Validates the syntactic shape of a candidate nickname (non-null,
     * non-empty after trim, length cap). Uniqueness against connected
     * clients is checked separately by {@link #handleNickname} so the
     * reconnection path can match the same nickname.
     *
     * @param nickname raw nickname from the client
     * @return {@code null} if the nickname's shape is acceptable, or a
     *         short error message explaining the rejection
     */
    private String validateNicknameSyntax(String nickname) {
        if (nickname == null) {
            return "Nickname cannot be null";
        }
        String trimmed = nickname.trim();
        if (trimmed.isEmpty()) {
            return "Nickname cannot be empty";
        }
        if (trimmed.length() > MAX_NICKNAME_LENGTH) {
            return "Nickname cannot be longer than " + MAX_NICKNAME_LENGTH + " characters";
        }
        return null;
    }

    /**
     * Linear search through {@link #clients} for a callback whose nickname
     * matches (case-insensitive). Returns the first hit (there should be
     * at most one) or {@code null} if no match is found.
     */
    private ClientCallback findClientByNickname(String nickname) {
        for (int i = 0; i < clients.size(); i++) {
            String existing = clients.get(i).getNickname();
            if (existing != null && existing.equalsIgnoreCase(nickname)) {
                return clients.get(i);
            }
        }
        return null;
    }

    /**
     * Linear search through {@link Match#getPlayersList()} for a player
     * with the given nickname. Used by the reconnect-from-snapshot path,
     * where the controller has restored the match but no callbacks yet.
     */
    private Player findPlayerByNickname(String nickname) {
        if (match == null) {
            return null;
        }
        List<Player> players = match.getPlayersList();
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getNickname().equalsIgnoreCase(nickname)) {
                return players.get(i);
            }
        }
        return null;
    }

    /**
     * Finds the live (or last-known) callback that is bound to the given
     * {@link Player}. Returns {@code null} if no callback is currently
     * associated with the player (e.g. right after a snapshot restore).
     */
    private ClientCallback findCallbackByPlayer(Player player) {
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).getPlayer() == player) {
                return clients.get(i);
            }
        }
        return null;
    }

    /**
     * Counts how many entries in {@link #clients} are still connected.
     * Used to detect the lone-player condition.
     */
    private int countConnectedClients() {
        int count = 0;
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).isConnected()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Copies the {@code nickname}, {@code pickedColor} and {@code Player}
     * binding from {@code oldSlot} to {@code newSlot}, then replaces
     * {@code oldSlot} with {@code newSlot} in {@link #clients}.
     * <p>
     * Used both by the live-reconnect path (case 1 in
     * {@link #handleNickname}) and indirectly by the restore-from-snapshot
     * path (which adds the new slot directly because there's no old one).
     */
    private void rebindClientSlot(ClientCallback oldSlot, ClientCallback newSlot) {
        newSlot.setNickname(oldSlot.getNickname());
        newSlot.setPickedColor(oldSlot.getPickedColor());
        newSlot.setPlayer(oldSlot.getPlayer());
        int idx = clients.indexOf(oldSlot);
        if (idx >= 0) {
            clients.set(idx, newSlot);
        } else {
            // Defensive: if the old slot wasn't in the list (shouldn't
            // happen) just append the new one.
            clients.add(newSlot);
        }
    }

    /**
     * Called after a successful reconnect. If the controller was paused
     * waiting for someone to come back and we now have at least two
     * connected players, cancel the lone-player timer and resume.
     */
    private void handleReconnectMaybeResume() {
        if (phase != GamePhase.PAUSED_WAITING_RECONNECT) {
            return;
        }
        if (countConnectedClients() < 2) {
            return;
        }
        cancelLonePlayerTimeoutIfActive();
        resumeFromPause();
    }

    /**
     * @return {@code true} if {@code list} contains at least two equal
     *         integers
     */
    private boolean hasDuplicates(List<Integer> list) {
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(i).intValue() == list.get(j).intValue()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Linear search for a {@link CharacterCard} with the given id inside
     * a single {@link CardRow}'s character list.
     *
     * @return the card, or {@code null} if no character with that id is in
     *         the row
     */
    private CharacterCard findCharacterById(CardRow row, int cardID) {
        List<CharacterCard> list = row.getCharacterCards();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getCardID() == cardID) {
                return list.get(i);
            }
        }
        return null;
    }

    /**
     * Linear search for a {@link BuildingCard} with the given id inside a
     * single {@link CardRow}'s building list.
     */
    private BuildingCard findBuildingById(CardRow row, int cardID) {
        List<BuildingCard> list = row.getBuildingCards();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getCardID() == cardID) {
                return list.get(i);
            }
        }
        return null;
    }

    /**
     * Searches both rows of the given board for a character card with the
     * given id. Used by the extra-pick phase, where the player can take
     * the card from either row.
     */
    private CharacterCard findCharacterByIdAnywhere(Board board, int cardID) {
        CharacterCard inUpper = findCharacterById(board.getUpperRow(), cardID);
        if (inUpper != null) {
            return inUpper;
        }
        return findCharacterById(board.getLowerRow(), cardID);
    }

    /**
     * Searches both rows of the given board for a building card with the
     * given id.
     */
    private BuildingCard findBuildingByIdAnywhere(Board board, int cardID) {
        BuildingCard inUpper = findBuildingById(board.getUpperRow(), cardID);
        if (inUpper != null) {
            return inUpper;
        }
        return findBuildingById(board.getLowerRow(), cardID);
    }

    /**
     * Walks every player's owned buildings looking for one whose
     * {@code hasUpperRowBuilding} returns {@code true}. Returns the owner
     * (the only player who is allowed to take the extra pick) or
     * {@code null} if no such building exists in the match.
     */
    private Player findSpecialBuildingOwner() {
        List<Player> players = match.getPlayersList();
        for (int pi = 0; pi < players.size(); pi++) {
            Player p = players.get(pi);
            List<BuildingCard> bs = p.getPlayerBuildings();
            for (int bi = 0; bi < bs.size(); bi++) {
                if (bs.get(bi).hasUpperRowBuilding(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * Removes every totem from the turn-order tile so
     * {@link Board#moveOfferingTrackToTurnOrder(List)} can rebuild it
     * cleanly without colliding with leftover totems.
     */
    private void clearTurnOrderTile() {
        List<Player> players = match.getPlayersList();
        for (int i = 0; i < players.size(); i++) {
            // clearSlot returns false if the player isn't on the tile;
            // either outcome is fine.
            match.getBoard().getTurnOrderTile().clearSlot(players.get(i));
        }
    }

    // ==================================================================
    // Skip-disconnected / pause / resume helpers (Pass 2)
    // ==================================================================

    /**
     * If the current player is disconnected, advance to the next connected
     * one. The exact semantics depend on the phase.
     */
    private void advanceCurrentPlayerIfDisconnected() {
        // Nothing to skip if no current player is awaited.
        if (currentPlayerNickname == null) {
            return;
        }
        ClientCallback currentCb = findClientByNickname(currentPlayerNickname);
        if (currentCb != null && currentCb.isConnected()) {
            return; // Current player is still here.
        }

        if (phase == GamePhase.PICK_OFFERING_TILE) {
            currentOfferingPickIndex++;
            advanceOfferingPickerUntilConnectedAndBroadcast();
        } else if (phase == GamePhase.TURN_PICK_CARDS_CHARACTER
                || phase == GamePhase.TURN_PICK_CARDS_BUILDING) {
            currentResolutionIndex++;
            advanceResolverUntilConnectedAndContinue();
        } else if (phase == GamePhase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING) {
            broadcastInfo(
                    "Special-building owner disconnected; skipping the extra pick.");
            enterResolveBuildingsAndEvents();
        }
    }

    /**
     * Walks {@link #turnOrderForOfferingPick} starting at
     * {@link #currentOfferingPickIndex} until either a connected player is
     * found (in which case {@link #currentPlayerNickname} is updated and
     * a state broadcast is sent) or the list is exhausted (in which case
     * we advance to the resolution phase).
     */
    private void advanceOfferingPickerUntilConnectedAndBroadcast() {
        while (currentOfferingPickIndex < turnOrderForOfferingPick.size()) {
            Player p = turnOrderForOfferingPick.get(currentOfferingPickIndex);
            ClientCallback cb = findCallbackByPlayer(p);
            if (cb != null && cb.isConnected()) {
                currentPlayerNickname = p.getNickname();
                broadcastGameState("Next player must pick an offering tile");
                return;
            }
            // Skip this disconnected player.
            currentOfferingPickIndex++;
        }
        // Everyone left was disconnected: jump to the next phase.
        enterCharacterPickForFirstResolutionPlayer();
    }

    /**
     * Walks {@link #resolutionOrder} starting at
     * {@link #currentResolutionIndex} until either a connected player is
     * found (then we re-enter the character-pick phase for them) or the
     * list is exhausted (then we run the post-resolution housekeeping).
     */
    private void advanceResolverUntilConnectedAndContinue() {
        while (currentResolutionIndex < resolutionOrder.size()) {
            Player p = resolutionOrder.get(currentResolutionIndex);
            ClientCallback cb = findCallbackByPlayer(p);
            if (cb != null && cb.isConnected()) {
                enterCharacterPickForCurrentResolutionPlayer();
                return;
            }
            currentResolutionIndex++;
        }
        afterAllResolutionsDone();
    }

    /**
     * Schedules the lone-player timeout. After
     * {@value #LONE_PLAYER_TIMEOUT_MINUTES} minutes,
     * {@link #onLonePlayerTimeout()} fires (on the scheduler's daemon
     * thread). If a previous timer was running it is cancelled first.
     */
    private void startLonePlayerTimeout() {
        cancelLonePlayerTimeoutIfActive();
        // Anonymous Runnable instead of a lambda for explicitness.
        Runnable task = new Runnable() {
            @Override
            public void run() {
                onLonePlayerTimeout();
            }
        };
        lonePlayerTask = scheduler.schedule(task,
                LONE_PLAYER_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Cancels the lone-player timeout if one is currently scheduled.
     */
    private void cancelLonePlayerTimeoutIfActive() {
        if (lonePlayerTask != null) {
            lonePlayerTask.cancel(false);
            lonePlayerTask = null;
        }
    }

    /**
     * Lone-player timeout body. Synchronizes on the controller because it
     * runs on the scheduler thread but mutates shared state.
     */
    private synchronized void onLonePlayerTimeout() {
        // Defensive checks: the situation may have changed while we
        // waited (someone reconnected, the game ended, etc.).
        if (phase != GamePhase.PAUSED_WAITING_RECONNECT) {
            return;
        }
        if (countConnectedClients() != 1) {
            return;
        }
        // Find the lone connected client and force its player to win.
        for (int i = 0; i < clients.size(); i++) {
            ClientCallback cb = clients.get(i);
            if (cb.isConnected() && cb.getPlayer() != null) {
                forceLonePlayerVictory(cb.getPlayer());
                return;
            }
        }
    }

    /**
     * Ends the game declaring {@code lone} the only winner. Used when the
     * lone-player timer expires.
     */
    private void forceLonePlayerVictory(Player lone) {
        broadcastInfo("Reconnect timeout reached. " + lone.getNickname()
                + " wins by default.");
        forcedWinners = new ArrayList<String>();
        forcedWinners.add(lone.getNickname());
        phase = GamePhase.GAME_ENDED;
        phaseBeforePause = null;
        currentPlayerNickname = null;
        broadcastGameState("Game over: lone-player victory for "
                + lone.getNickname());
        if (match != null) {
            gameManager.closeMatch(match.getMatchID());
        }
        // No more state worth keeping on disk: remove the snapshot file.
        PersistenceManager.delete();
    }

    /**
     * Comes out of {@link GamePhase#PAUSED_WAITING_RECONNECT}, restoring
     * the phase that was active before the pause. If the player whose
     * input was awaited before the pause is still disconnected, the
     * skip-disconnected logic moves on to the next connected one.
     */
    private void resumeFromPause() {
        if (phaseBeforePause == null) {
            // Nothing to resume to. Should not happen, but be safe.
            return;
        }
        phase = phaseBeforePause;
        phaseBeforePause = null;
        broadcastInfo("Match resumed.");
        // Make sure the current player isn't still a disconnected one.
        advanceCurrentPlayerIfDisconnected();
        broadcastGameState("Resumed");
        persistAfterTransition();
    }

    // ==================================================================
    // Persistence (Pass 2)
    // ==================================================================

    /**
     * Builds a snapshot of the current state and writes it to disk via
     * {@link PersistenceManager#save(GameSnapshot)}. Called at the end of
     * every phase transition that lands on an in-match phase. On I/O
     * failure we follow Q3=c: log + broadcast an error message + carry
     * on. The match keeps running (it is just no longer crash-safe).
     */
    private void persist() {
        // Only persist meaningful in-match state. Lobby snapshots are
        // useless (players have not committed yet) and a GAME_ENDED save
        // would just be deleted moments later anyway.
        if (match == null
                || phase == GamePhase.GAME_ENDED
                || !phaseIsInMatch()) {
            return;
        }
        GameSnapshot snapshot = new GameSnapshot(
                match,
                phase,
                phaseBeforePause,
                currentPlayerNickname,
                turnOrderForOfferingPick,
                currentOfferingPickIndex,
                resolutionOrder,
                currentResolutionIndex,
                currentUpperBudget,
                currentLowerBudget,
                currentUpperRemainingForBuilding,
                currentLowerRemainingForBuilding,
                extraPickPlayer
        );
        try {
            PersistenceManager.save(snapshot);
        } catch (IOException e) {
            String msg = "Could not persist snapshot: " + e.getMessage()
                    + " (the match is no longer crash-safe).";
            System.out.println(msg);
            broadcastError(msg);
        }
    }

    /**
     * Convenience wrapper for callers that just performed a phase
     * transition. Same as calling {@link #persist()} directly; the
     * separate name makes the intent ("we just changed phase") explicit
     * at the call sites.
     */
    private void persistAfterTransition() {
        persist();
    }

    /**
     * Replaces the controller's state with the contents of the given
     * snapshot, then forces the controller into
     * {@link GamePhase#PAUSED_WAITING_RECONNECT} (with
     * {@link #phaseBeforePause} pointing to the snapshot's original
     * phase). Real clients will then reconnect by nickname and the
     * controller will resume once at least two of them are back.
     * <p>
     * Called by the {@code Server} once on startup, before the accept
     * loop begins.
     *
     * @param snapshot the snapshot to restore from
     */
    public synchronized void restoreFrom(GameSnapshot snapshot) {
        this.match = snapshot.match;
        // Make the loaded match findable through the GameManager too, so
        // gameManager.closeMatch(matchID) can locate it at end of game.
        // (createMatch always builds a NEW match; we instead inject the
        // restored one through the only public mutator we have, which is
        // also createMatch — but that returns a new id. So we just leave
        // the registry empty for the restored match; closeMatch will
        // simply return false at the end, which is harmless.)

        this.phase = GamePhase.PAUSED_WAITING_RECONNECT;
        this.phaseBeforePause = snapshot.phase;
        this.currentPlayerNickname = snapshot.currentPlayerNickname;
        this.turnOrderForOfferingPick =
                new ArrayList<Player>(snapshot.turnOrderForOfferingPick);
        this.currentOfferingPickIndex = snapshot.currentOfferingPickIndex;
        this.resolutionOrder = new ArrayList<Player>(snapshot.resolutionOrder);
        this.currentResolutionIndex = snapshot.currentResolutionIndex;
        this.currentUpperBudget = snapshot.currentUpperBudget;
        this.currentLowerBudget = snapshot.currentLowerBudget;
        this.currentUpperRemainingForBuilding =
                snapshot.currentUpperRemainingForBuilding;
        this.currentLowerRemainingForBuilding =
                snapshot.currentLowerRemainingForBuilding;
        this.extraPickPlayer = snapshot.extraPickPlayer;

        // The lobby fields don't really apply post-restore: derive a
        // sensible "first player" so legacy lobby code doesn't trip if
        // someone disconnects right back to lobby.
        if (!match.getPlayersList().isEmpty()) {
            this.firstPlayerNickname = match.getPlayersList().get(0).getNickname();
        }

        // Available colors: every TotemColor minus the ones already used.
        availableColors.clear();
        for (int i = 0; i < TotemColor.values().length; i++) {
            availableColors.add(TotemColor.values()[i]);
        }
        for (int i = 0; i < match.getPlayersList().size(); i++) {
            Player p = match.getPlayersList().get(i);
            availableColors.remove(p.getPlayerColor());
        }
    }

    /**
     * Resets every controller field to the lobby's initial state. Used on
     * full lobby disconnects (no clients left).
     */
    private void resetToLobby() {
        phase = GamePhase.WAITING_FOR_NICKNAME;
        phaseBeforePause = null;
        firstPlayerNickname = null;
        expectedPlayers = 0;
        currentPlayerNickname = null;
        forcedWinners = null;
        cancelLonePlayerTimeoutIfActive();

        availableColors.clear();
        for (int i = 0; i < TotemColor.values().length; i++) {
            availableColors.add(TotemColor.values()[i]);
        }

        colorPickQueue.clear();

        // Drop the match reference; the GameManager registry stays as-is
        // for the lifetime of the controller.
        if (match != null) {
            gameManager.closeMatch(match.getMatchID());
            match = null;
        }

        turnOrderForOfferingPick = new ArrayList<>();
        currentOfferingPickIndex = 0;
        resolutionOrder = new ArrayList<>();
        currentResolutionIndex = 0;
        currentUpperBudget = 0;
        currentLowerBudget = 0;
        currentUpperRemainingForBuilding = 0;
        currentLowerRemainingForBuilding = 0;
        extraPickPlayer = null;

        // Forget every still-connected client's color/Player binding so a
        // fresh lobby can re-pick colors. Their nicknames stay so they
        // don't have to re-type them.
        for (int i = 0; i < clients.size(); i++) {
            ClientCallback c = clients.get(i);
            c.setPickedColor(null);
            c.setPlayer(null);
        }

        // Demote the first player back to "lobby"; if there's still
        // somebody connected, the first one in the list takes over.
        if (!clients.isEmpty()) {
            firstPlayerNickname = clients.get(0).getNickname();
            currentPlayerNickname = firstPlayerNickname;
            phase = GamePhase.WAITING_FOR_PLAYER_COUNT;
        }
    }

    // ==================================================================
    // Broadcast / send helpers (private)
    // ==================================================================

    /** Sends a single ServerMessage to every connected client. */
    private void broadcastMessage(ServerMessage message) {
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).send(message);
        }
    }

    /** Builds a fresh {@link GameStateDto} and broadcasts it. */
    private void broadcastGameState(String message) {
        GameStateDto dto = createGameStateDto(message);
        broadcastMessage(new GameStateMessage(dto));
    }

    /** Sends the current game state to one specific client. */
    private void sendGameStateTo(ClientCallback client, String message) {
        client.send(new GameStateMessage(createGameStateDto(message)));
    }

    /** Broadcasts a free-form info message. */
    private void broadcastInfo(String message) {
        broadcastMessage(new InfoMessage(message));
    }

    /** Broadcasts a free-form error message. */
    private void broadcastError(String message) {
        broadcastMessage(new ErrorMessage(message));
    }

    /** Sends an action-result message to one specific client. */
    private void sendActionResult(
            ClientCallback client, String action, boolean success, String text) {
        client.send(new ActionResultMessage(action, success, text));
    }

    /**
     * Builds a snapshot of the current state. Defensive copies are used
     * for every collection so the receiver can't mutate the controller's
     * internal lists/maps.
     */
    private GameStateDto createGameStateDto(String message) {
        // Lobby fields.
        List<String> playersList = new ArrayList<>();
        Map<String, TotemColor> playerColors = new HashMap<>();
        for (int i = 0; i < clients.size(); i++) {
            ClientCallback c = clients.get(i);
            String nick = c.getNickname();
            if (nick != null) {
                playersList.add(nick);
            }
            if (nick != null && c.getPickedColor() != null) {
                playerColors.put(nick, c.getPickedColor());
            }
        }

        List<String> colorPickOrder = new ArrayList<>();
        for (ClientCallback c : colorPickQueue) {
            if (c.getNickname() != null) {
                colorPickOrder.add(c.getNickname());
            }
        }

        List<String> turnOrderTileNicks = new ArrayList<>();
        if (match != null && match.getBoard() != null
                && match.getBoard().getTurnOrderTile() != null) {
            // Read the order straight from the model so it stays in sync
            // with whatever the controller has done since last broadcast.
            List<Player> ordered = match.getBoard()
                    .getPlayersTurnOrder(match.getPlayersList());
            for (int i = 0; i < ordered.size(); i++) {
                turnOrderTileNicks.add(ordered.get(i).getNickname());
            }
        }

        List<TotemColor> available = new ArrayList<>(availableColors);

        // Match-level fields (board, era, round) and per-player resources
        // are populated only after the match has been built.
        Board boardRef = (match == null) ? null : match.getBoard();
        Map<String, Integer> playerFood = new HashMap<>();
        Map<String, Integer> playerPP = new HashMap<>();
        if (match != null) {
            List<Player> players = match.getPlayersList();
            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);
                playerFood.put(p.getNickname(), p.getFood());
                playerPP.put(p.getNickname(), p.getPrestigePoints());
            }
        }

        // End-of-game fields are only populated in GAME_ENDED.
        List<String> leaderboard = new ArrayList<>();
        List<String> winners = new ArrayList<>();
        if (phase == GamePhase.GAME_ENDED && match != null) {
            List<Player> sorted = match.createLeaderboard();
            for (int i = 0; i < sorted.size(); i++) {
                leaderboard.add(sorted.get(i).getNickname());
            }
            // Pass 2: if the lone-player timer fired, the forcedWinners
            // list overrides the natural score-based winners.
            if (forcedWinners != null) {
                winners = new ArrayList<>(forcedWinners);
            } else {
                winners = computeWinners(sorted);
            }
        }

        return new GameStateDto(
                phase,
                playersList,
                playerColors,
                colorPickOrder,
                turnOrderTileNicks,
                currentPlayerNickname,
                available,
                boardRef,
                match == null ? null : match.getCurrentEra(),
                match == null ? 0 : match.getCurrentRound(),
                currentUpperBudget,
                currentLowerBudget,
                currentUpperRemainingForBuilding,
                currentLowerRemainingForBuilding,
                playerFood,
                playerPP,
                leaderboard,
                winners,
                message
        );
    }

    /**
     * Returns the nicknames of every player who is tied at the top of the
     * sorted leaderboard. Tied = same prestige points AND same food
     * (matching {@link Match#createLeaderboard()}'s comparator).
     *
     * @param sorted the leaderboard returned by
     *               {@link Match#createLeaderboard()}
     * @return a list with at least one entry; longer than one means a
     *         shared victory
     */
    private List<String> computeWinners(List<Player> sorted) {
        List<String> result = new ArrayList<>();
        if (sorted.isEmpty()) {
            return result;
        }
        Player best = sorted.get(0);
        result.add(best.getNickname());
        for (int i = 1; i < sorted.size(); i++) {
            Player p = sorted.get(i);
            if (p.getPrestigePoints() == best.getPrestigePoints()
                    && p.getFood() == best.getFood()) {
                result.add(p.getNickname());
            } else {
                break;
            }
        }
        return result;
    }
}
