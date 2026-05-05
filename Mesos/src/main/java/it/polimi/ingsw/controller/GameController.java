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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Owns the game-side state machine. The {@code GameController} is the only
 * place that knows the rules; the {@code Server} / {@code ClientHandler}
 * pair only deals with sockets and message routing.
 * <p>
 * <b>State pattern</b>: every {@link GamePhase} value is mapped (via
 * {@link #STRATEGIES}) to a {@link Phase} strategy that holds the
 * per-phase behavior. The public {@code handle*} methods are one-line
 * delegates: they call {@code phaseStrategy.onX(this, ...)} and let the
 * polymorphic dispatch pick the right code. Default-reject lives in the
 * {@link Phase} base class, so the historical "if (phase != X) reject"
 * guards have all moved into the type system.
 * <p>
 * All public methods are {@code synchronized} on this controller instance
 * so messages from different client threads can safely mutate state in
 * any order.
 * <p>
 * The class purposely avoids advanced Java features (no streams, no
 * lambdas) so the rule-by-rule logic is easy to read and easy to debug.
 */
public class GameController {

    // ==================================================================
    // Constants (package-private so Phase strategies can read them)
    // ==================================================================

    /** Minimum number of players the game accepts. */
    static final int MIN_PLAYERS = 2;

    /** Maximum number of players the game accepts (also the number of
     *  totem colors available). */
    static final int MAX_PLAYERS = 5;

    /** Maximum allowed length for a nickname. */
    private static final int MAX_NICKNAME_LENGTH = 16;

    /** How long to wait, when only one player is left in a match, before
     *  declaring them the lone-player winner by default. Used in the
     *  user-facing message ("waiting for reconnect (2 min)"). */
    private static final long LONE_PLAYER_TIMEOUT_MINUTES = 2;

    /** Same delay in milliseconds, for {@link Timer#schedule}. */
    private static final long LONE_PLAYER_TIMEOUT_MILLIS =
            LONE_PLAYER_TIMEOUT_MINUTES * 60L * 1000L;

    // ==================================================================
    // Phase → strategy registry. Populated once at class init time.
    // ==================================================================

    /**
     * Phase-to-strategy map. Each {@link GamePhase} value maps to a
     * stateless {@link Phase} singleton; the controller looks up the
     * strategy via {@link #setPhase(GamePhase)} and caches it in
     * {@link #phaseStrategy} for fast access.
     * <p>
     * Multiple enum values may share the same strategy instance (e.g.
     * {@code WAITING_FOR_NICKNAME} and {@code WAITING_FOR_PLAYERS} both
     * map to a plain {@link LobbyPhase}), since the strategy's behavior
     * doesn't differ between them.
     * <p>
     * {@link EnumMap} is just a {@link Map} optimized for enum keys —
     * we use {@code put} / {@code get} on it the same way as a
     * {@code HashMap}.
     */
    private static final Map<GamePhase, Phase> STRATEGIES =
            new EnumMap<GamePhase, Phase>(GamePhase.class);

    static {
        Phase plainLobby = new LobbyPhase();
        STRATEGIES.put(GamePhase.WAITING_FOR_NICKNAME, plainLobby);
        STRATEGIES.put(GamePhase.WAITING_FOR_PLAYER_COUNT, new LobbyPlayerCountPhase());
        STRATEGIES.put(GamePhase.WAITING_FOR_PLAYERS, plainLobby);
        STRATEGIES.put(GamePhase.COLOR_PICKING, new ColorPickingPhase());
        STRATEGIES.put(GamePhase.PICK_OFFERING_TILE, new PickOfferingTilePhase());
        STRATEGIES.put(GamePhase.TURN_PICK_CARDS_CHARACTER, new TurnPickCharactersPhase());
        STRATEGIES.put(GamePhase.TURN_PICK_CARDS_BUILDING, new TurnPickBuildingsPhase());
        STRATEGIES.put(GamePhase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING,
                new ResetTurnOrderExtraPickPhase());
        STRATEGIES.put(GamePhase.RESOLVE_BUILDINGS_AND_EVENTS, new ResolvePhase());
        STRATEGIES.put(GamePhase.PAUSED_WAITING_RECONNECT, new PausedPhase());
        STRATEGIES.put(GamePhase.GAME_ENDED, new GameEndedPhase());
    }

    // ==================================================================
    // Lobby / connection state
    // ==================================================================

    /** All currently connected clients, in the order they joined. */
    private final List<ClientCallback> clients = new ArrayList<ClientCallback>();

    /** Current phase of the FSM. Always kept in sync with
     *  {@link #phaseStrategy} via {@link #setPhase(GamePhase)}. The enum
     *  is what travels on the wire and lands in snapshots. */
    private GamePhase phase = GamePhase.WAITING_FOR_NICKNAME;

    /** Cached strategy for the current {@link #phase}. Always equal to
     *  {@code STRATEGIES.get(phase)}. Cached as a field rather than
     *  re-looked-up because it's read on every message dispatch. */
    private Phase phaseStrategy = STRATEGIES.get(phase);

    /** The nickname of the very first player who set theirs (the only
     *  one allowed to choose the player count). */
    private String firstPlayerNickname;

    /** The number of players the first player asked for; {@code 0}
     *  until set, then {@code 2..5}. */
    private int expectedPlayers;

    /** Totem colors that nobody has picked yet. Initialised to all
     *  values of {@link TotemColor}; entries are removed as colors are
     *  chosen. */
    private final List<TotemColor> availableColors = new ArrayList<TotemColor>();

    /** Queue of clients still waiting to pick a color, in join order. */
    private final Queue<ClientCallback> colorPickQueue = new LinkedList<ClientCallback>();

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
     *  expecting, or {@code null} when no specific client is awaited. */
    private String currentPlayerNickname;

    /** Players left to place a totem on the offering track this round,
     *  in turn-order-tile slot order. */
    private List<Player> turnOrderForOfferingPick = new ArrayList<Player>();

    /** Index of the player currently picking, into
     *  {@link #turnOrderForOfferingPick}. */
    private int currentOfferingPickIndex;

    /** Players to be resolved (character + building picks), in
     *  offering-track order. */
    private List<Player> resolutionOrder = new ArrayList<Player>();

    /** Index into {@link #resolutionOrder}. */
    private int currentResolutionIndex;

    /** Top count of the offering tile of the player currently being
     *  resolved (= maximum upper-row character cards they may draw). */
    private int currentUpperBudget;

    /** Bottom count of the same tile (= maximum lower-row character
     *  cards they may draw). */
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
     *  the phase it should resume to once enough players reconnect.
     *  {@code null} when not paused. */
    private GamePhase phaseBeforePause;

    /** When the lone-player timer fires we override the normal
     *  end-of-game winners list with this one. {@code null} in normal
     *  scoring; non-null only during a lone-player default victory. */
    private List<String> forcedWinners;

    /** Single-thread daemon timer used to fire the lone-player timeout.
     *  The {@code true} second argument makes its background thread a
     *  daemon, so it doesn't keep the JVM alive after main exits. */
    private final Timer scheduler = new Timer("lone-player-timeout", true);

    /** The currently scheduled lone-player timeout, or {@code null} if
     *  no timer is running. */
    private TimerTask lonePlayerTask;

    // ==================================================================
    // Construction
    // ==================================================================

    /** Creates a controller in {@link GamePhase#WAITING_FOR_NICKNAME}
     *  with all five totem colors available. The lone-player {@link Timer}
     *  is initialised at the field declaration above. */
    public GameController() {
        for (int i = 0; i < TotemColor.values().length; i++) {
            availableColors.add(TotemColor.values()[i]);
        }
    }

    // ==================================================================
    // Connection / disconnection hooks (called by ClientHandler)
    // ==================================================================

    /**
     * Called by the network layer right after a TCP connection has been
     * accepted and a {@link ClientCallback} has been built. Sends the
     * connecting client an initial state snapshot so its UI knows what
     * input is expected.
     */
    public synchronized void onClientConnected(ClientCallback client) {
        client.send(new InfoMessage("Connected to server"));
        sendGameStateTo(client, "Connected. Waiting for nickname if allowed.");
    }

    /**
     * Called by the network layer when a client disconnects. Routes the
     * disconnect through one of three paths based on the current phase
     * (queried polymorphically rather than by enum comparison):
     * <ul>
     *   <li>{@code GAME_ENDED} → just mark and log; no pause, no timer.</li>
     *   <li>Lobby phase → {@link #handleLobbyDisconnect}.</li>
     *   <li>In-match phase → mark + skip / pause + timer.</li>
     * </ul>
     */
    public synchronized void onClientDisconnected(ClientCallback client) {
        boolean hadNickname = client.getNickname() != null;
        if (!hadNickname) {
            clients.remove(client);
            colorPickQueue.remove(client);
            return;
        }

        String nickname = client.getNickname();

        // Game's already over: don't pause, don't start timers, don't
        // touch persistence (the snapshot is gone).
        if (phaseStrategy.isGameOver()) {
            client.markDisconnected();
            broadcastInfo(nickname + " disconnected (game already ended).");
            return;
        }

        if (!phaseStrategy.isInMatch()) {
            handleLobbyDisconnect(client, nickname);
            return;
        }

        // In-match: keep the slot, mark disconnected.
        client.markDisconnected();
        broadcastInfo(nickname + " disconnected. Their turns will be skipped.");

        int connected = countConnectedClients();

        if (connected == 0) {
            // Nobody online. Pause indefinitely; no lone-player timer.
            cancelLonePlayerTimeoutIfActive();
            // Only capture phaseBeforePause if we're not already paused.
            // Otherwise a second disconnect-while-paused would clobber
            // the original active phase with PAUSED, and resume would
            // land back in PAUSED.
            if (!phaseStrategy.isPaused()) {
                phaseBeforePause = phase;
            }
            setPhase(GamePhase.PAUSED_WAITING_RECONNECT);
            broadcastGameState("All players disconnected. Waiting for reconnect.");
            persistAfterTransition();
            return;
        }

        if (connected == 1) {
            // Lone-player condition: pause + start the 2-minute timer.
            if (!phaseStrategy.isPaused()) {
                phaseBeforePause = phase;
            }
            setPhase(GamePhase.PAUSED_WAITING_RECONNECT);
            startLonePlayerTimeout();
            broadcastGameState("Only one player left. Waiting for reconnect ("
                    + LONE_PLAYER_TIMEOUT_MINUTES + " min).");
            persistAfterTransition();
            return;
        }

        // 2+ still connected: skip this player's turn if it was theirs.
        advanceCurrentPlayerIfDisconnected();
        broadcastGameState("A player disconnected; their turns will be skipped.");
    }

    /**
     * Pure-lobby disconnect cleanup. The phase-specific bit (whether to
     * rewind to PLAYER_COUNT, refresh the color-pick queue head, etc.)
     * is delegated to {@link Phase#onLobbyDisconnect}.
     */
    private void handleLobbyDisconnect(ClientCallback client, String nickname) {
        clients.remove(client);
        colorPickQueue.remove(client);

        TotemColor color = client.getPickedColor();
        if (color != null && !availableColors.contains(color)) {
            availableColors.add(color);
        }

        // Empty lobby: reset everything.
        if (clients.isEmpty()) {
            resetToLobby();
            return;
        }

        boolean firstPlayerLeft = nickname.equalsIgnoreCase(firstPlayerNickname);
        // Polymorphic per-phase handling (LobbyPhase rewinds to
        // PLAYER_COUNT, ColorPickingPhase refreshes the queue head, etc.)
        phaseStrategy.onLobbyDisconnect(this, firstPlayerLeft);

        broadcastInfo(nickname + " disconnected.");
        broadcastGameState("A player disconnected.");
    }

    // ==================================================================
    // Public handlers (one per client message type)
    // ==================================================================
    //
    // Each handler is a one-line delegate to the current phase strategy.
    // The strategy's default behavior is "reject as wrong phase", so the
    // historical "if (phase != X)" guards no longer need to be written —
    // they're implicit in the polymorphic dispatch.

    /**
     * Handles a {@code SetNicknameMessage}. Three possible outcomes:
     * <ol>
     *   <li><b>Reconnect to an existing client slot</b>: the nickname
     *       matches a known disconnected client. We rebind that slot to
     *       the new callback. Allowed in any phase.</li>
     *   <li><b>Reconnect to a restored snapshot</b>: the nickname matches
     *       a {@link Player} restored from disk. We bind the new
     *       callback to the ghost. Allowed in any phase.</li>
     *   <li><b>New joiner</b>: dispatched to {@link Phase#onNewJoiner}
     *       which only the lobby phases accept; the rest reject.</li>
     * </ol>
     */
    public synchronized void handleNickname(ClientCallback client, String nickname) {
        // Idempotency: reject if this client already has a nickname.
        if (client.getNickname() != null) {
            sendActionResult(client, Phase.ACTION_NICKNAME, false,
                    "You already have a nickname");
            return;
        }

        String validationError = validateNicknameSyntax(nickname);
        if (validationError != null) {
            sendActionResult(client, Phase.ACTION_NICKNAME, false, validationError);
            sendGameStateTo(client, "Nickname invalid");
            return;
        }
        String trimmed = nickname.trim();

        // ---- Case 1: reconnect to an existing client slot. ----
        ClientCallback existing = findClientByNickname(trimmed);
        if (existing != null) {
            if (existing.isConnected()) {
                sendActionResult(client, Phase.ACTION_NICKNAME, false,
                        "Nickname already in use");
                return;
            }
            rebindClientSlot(existing, client);
            sendActionResult(client, Phase.ACTION_NICKNAME, true,
                    "Reconnected as " + trimmed);
            broadcastInfo(trimmed + " reconnected");
            broadcastGameState(trimmed + " reconnected");
            handleReconnectMaybeResume();
            return;
        }

        // ---- Case 2: reconnect to a restored snapshot. ----
        if (match != null) {
            Player ghost = findPlayerByNickname(trimmed);
            if (ghost != null) {
                client.setNickname(trimmed);
                client.setPickedColor(ghost.getPlayerColor());
                client.setPlayer(ghost);
                clients.add(client);
                sendActionResult(client, Phase.ACTION_NICKNAME, true,
                        "Reconnected (restored) as " + trimmed);
                broadcastInfo(trimmed + " reconnected");
                broadcastGameState(trimmed + " reconnected");
                handleReconnectMaybeResume();
                return;
            }
        }

        // ---- Case 3: new joiner — dispatched to the strategy. ----
        // Lobby phases override onNewJoiner to do the join; in-match
        // phases inherit the default-reject from the Phase base class.
        phaseStrategy.onNewJoiner(this, client, trimmed);
    }

    /** One-line delegate to the current phase strategy. */
    public synchronized void handlePlayerCount(ClientCallback client, int playerCount) {
        phaseStrategy.onPlayerCount(this, client, playerCount);
    }

    /** One-line delegate to the current phase strategy. */
    public synchronized void handlePickColor(ClientCallback client, TotemColor color) {
        phaseStrategy.onPickColor(this, client, color);
    }

    /** One-line delegate to the current phase strategy. */
    public synchronized void handlePickOfferingTile(ClientCallback client, int tileIndex) {
        phaseStrategy.onPickOfferingTile(this, client, tileIndex);
    }

    /** One-line delegate to the current phase strategy. */
    public synchronized void handlePickCharacterCards(
            ClientCallback client, List<Integer> upperRowIds, List<Integer> lowerRowIds) {
        phaseStrategy.onPickCharacterCards(this, client, upperRowIds, lowerRowIds);
    }

    /** One-line delegate to the current phase strategy. */
    public synchronized void handlePickBuildingCards(
            ClientCallback client, List<Integer> upperRowIds, List<Integer> lowerRowIds) {
        phaseStrategy.onPickBuildingCards(this, client, upperRowIds, lowerRowIds);
    }

    /** One-line delegate to the current phase strategy. */
    public synchronized void handlePickExtraCard(
            ClientCallback client, Integer cardId, boolean isCharacter) {
        phaseStrategy.onPickExtraCard(this, client, cardId, isCharacter);
    }

    // ==================================================================
    // Phase transitions (called by Phase strategies and by internal
    // transition helpers below)
    // ==================================================================

    /**
     * Updates {@link #phase} AND {@link #phaseStrategy} together. All
     * transitions must go through this setter so the two fields never
     * diverge.
     *
     * @param newPhase the target phase
     */
    void setPhase(GamePhase newPhase) {
        this.phase = newPhase;
        this.phaseStrategy = STRATEGIES.get(newPhase);
    }

    /**
     * Transition into {@link GamePhase#COLOR_PICKING}: rebuild the
     * queue, point {@code currentPlayerNickname} at its head, broadcast.
     */
    void startColorPickingPhase() {
        setPhase(GamePhase.COLOR_PICKING);
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
    void startMatchAndEnterPickOfferingTile() {
        int matchID = gameManager.createMatch();
        match = gameManager.retrieveMatch(matchID);

        for (int i = 0; i < clients.size(); i++) {
            ClientCallback c = clients.get(i);
            match.addPlayer(c.getPlayer());
        }

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

        turnOrderForOfferingPick =
                match.getBoard().getPlayersTurnOrder(match.getPlayersList());
        currentOfferingPickIndex = 0;

        if (turnOrderForOfferingPick.isEmpty()) {
            broadcastError("No players on the turn order tile after setup.");
            resetToLobby();
            broadcastGameState("Setup inconsistent; back to lobby.");
            return;
        }

        setPhase(GamePhase.PICK_OFFERING_TILE);
        advanceOfferingPickerUntilConnectedAndBroadcast();
        persistAfterTransition();
    }

    /**
     * Called once every player has placed a totem. Builds the resolution
     * order from the offering track and starts with the first connected
     * resolver (or jumps to {@link #afterAllResolutionsDone} if no one
     * placed a totem).
     */
    void enterCharacterPickForFirstResolutionPlayer() {
        resolutionOrder = match.getBoard()
                .getOfferingTrackPlayersOrder(match.getPlayersList());
        currentResolutionIndex = 0;

        if (resolutionOrder.isEmpty()) {
            afterAllResolutionsDone();
            return;
        }
        advanceResolverUntilConnectedAndContinue();
    }

    /**
     * Set up per-turn budgets for the current resolving player, credit
     * them with the offering tile's food bonus, and broadcast the new
     * {@link GamePhase#TURN_PICK_CARDS_CHARACTER} state.
     */
    void enterCharacterPickForCurrentResolutionPlayer() {
        Player player = resolutionOrder.get(currentResolutionIndex);
        Board board = match.getBoard();

        int tileIndex = board.getOfferingTrackIndex(player);
        OfferingTile tile = board.getOfferingTrack().get(tileIndex);

        currentUpperBudget = tile.getTopCardsToGet();
        currentLowerBudget = tile.getBottomCardsToGet();
        currentUpperRemainingForBuilding = currentUpperBudget;
        currentLowerRemainingForBuilding = currentLowerBudget;

        player.changeFood(tile.getFoodGained());

        setPhase(GamePhase.TURN_PICK_CARDS_CHARACTER);
        currentPlayerNickname = player.getNickname();
        broadcastGameState("Pick character cards (or none) within budget");
        persistAfterTransition();
    }

    /**
     * Called once the building phase has finished for the last resolving
     * player. Returns totems to the turn-order tile (E1 semantics: full
     * clear, rebuild from the offering track for participants, then
     * append players who didn't pick), applies turn-order-tile bonuses,
     * then either jumps to the special-building extra-pick phase or
     * directly to {@link #enterResolveBuildingsAndEvents}.
     */
    void afterAllResolutionsDone() {
        Board board = match.getBoard();

        clearTurnOrderTile();
        board.moveOfferingTrackToTurnOrder(match.getPlayersList());
        appendMissingPlayersToTurnOrderTile();

        match.resolveTurnOrderTile();

        extraPickPlayer = findSpecialBuildingOwner();

        if (extraPickPlayer == null) {
            enterResolveBuildingsAndEvents();
            return;
        }

        ClientCallback ownerCb = findCallbackByPlayer(extraPickPlayer);
        if (ownerCb == null || !ownerCb.isConnected()) {
            broadcastInfo("Special-building owner is disconnected; skipping extra pick.");
            enterResolveBuildingsAndEvents();
            return;
        }

        setPhase(GamePhase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING);
        currentPlayerNickname = extraPickPlayer.getNickname();
        broadcastGameState(
                extraPickPlayer.getNickname()
                        + " owns the special building and may pick one extra card (or skip)");
        persistAfterTransition();
    }

    /**
     * Apply end-of-round buildings and events; then either end the game
     * (final round) or move on to the next round and broadcast the new
     * {@link GamePhase#PICK_OFFERING_TILE}.
     */
    void enterResolveBuildingsAndEvents() {
        setPhase(GamePhase.RESOLVE_BUILDINGS_AND_EVENTS);
        currentPlayerNickname = null;
        broadcastGameState("Resolving buildings and events...");
        persistAfterTransition();

        match.resolveBuildings();
        match.resolveEvents();

        if (match.getCurrentRound() == Match.FINAL_ROUND) {
            endGame();
            return;
        }

        try {
            match.moveToNextRound();
        } catch (EmptyDeckException e) {
            broadcastError("Deck ran out while preparing the next round: "
                    + e.getMessage() + ". Ending the match early.");
            endGame();
            return;
        }

        turnOrderForOfferingPick =
                match.getBoard().getPlayersTurnOrder(match.getPlayersList());
        currentOfferingPickIndex = 0;

        setPhase(GamePhase.PICK_OFFERING_TILE);
        advanceOfferingPickerUntilConnectedAndBroadcast();
        persistAfterTransition();
    }

    /**
     * Final-round end-of-game sequence: shift up→low one last time,
     * resolve any final-event cards that just shifted, apply final
     * scoring, build the leaderboard, transition to
     * {@link GamePhase#GAME_ENDED}, then close the match.
     */
    private void endGame() {
        Board board = match.getBoard();
        board.shiftUpToLow();
        match.resolveEvents();
        match.endMatch();

        setPhase(GamePhase.GAME_ENDED);
        phaseBeforePause = null;
        currentPlayerNickname = null;
        broadcastGameState("Game ended.");

        gameManager.closeMatch(match.getMatchID());
        PersistenceManager.delete();
    }

    // ==================================================================
    // Append-missing / turn-order helpers
    // ==================================================================

    /**
     * Walks {@link Match#getPlayersList()} and, for each player whose
     * totem is NOT currently on the turn-order tile, places it on the
     * next free slot.
     * <p>
     * The check is on totem position rather than connection state so a
     * player who disconnected mid-pick and reconnected before
     * end-of-round is still placed (otherwise their totem would silently
     * vanish, and they would be excluded from every subsequent round's
     * pick rotation).
     */
    private void appendMissingPlayersToTurnOrderTile() {
        List<Player> players = match.getPlayersList();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (isPlayerOnTurnOrderTile(p)) {
                continue;
            }
            int freeSlot = findNextFreeTurnOrderSlot();
            if (freeSlot < 0) {
                break;
            }
            match.getBoard().getTurnOrderTile().placeTotem(p, freeSlot);
        }
    }

    /**
     * @return {@code true} if the given player's totem occupies any slot
     *         of the turn-order tile, {@code false} otherwise
     */
    private boolean isPlayerOnTurnOrderTile(Player p) {
        it.polimi.ingsw.model.TurnOrderSlot[] slots =
                match.getBoard().getTurnOrderTile().getTurnOrderSlots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].isOccupied()
                    && slots[i].getTotemColor() == p.getPlayerColor()) {
                return true;
            }
        }
        return false;
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
     * Removes every totem from the turn-order tile so
     * {@link Board#moveOfferingTrackToTurnOrder(List)} can rebuild it
     * cleanly without colliding with leftover totems.
     */
    private void clearTurnOrderTile() {
        List<Player> players = match.getPlayersList();
        for (int i = 0; i < players.size(); i++) {
            match.getBoard().getTurnOrderTile().clearSlot(players.get(i));
        }
    }

    // ==================================================================
    // Validation / lookup helpers
    // ==================================================================

    /**
     * Validates the syntactic shape of a candidate nickname (non-null,
     * non-empty after trim, length cap). Uniqueness against connected
     * clients is checked separately by {@link #handleNickname}.
     *
     * @return {@code null} if acceptable, or a short error message
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

    /** Linear, case-insensitive search through {@link #clients}. */
    private ClientCallback findClientByNickname(String nickname) {
        for (int i = 0; i < clients.size(); i++) {
            String existing = clients.get(i).getNickname();
            if (existing != null && existing.equalsIgnoreCase(nickname)) {
                return clients.get(i);
            }
        }
        return null;
    }

    /** Linear, case-insensitive search through the match's player list. */
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

    /** Finds the live (or last-known) callback bound to the given player. */
    ClientCallback findCallbackByPlayer(Player player) {
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).getPlayer() == player) {
                return clients.get(i);
            }
        }
        return null;
    }

    /** Counts how many entries in {@link #clients} are still connected. */
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
     */
    private void rebindClientSlot(ClientCallback oldSlot, ClientCallback newSlot) {
        newSlot.setNickname(oldSlot.getNickname());
        newSlot.setPickedColor(oldSlot.getPickedColor());
        newSlot.setPlayer(oldSlot.getPlayer());
        int idx = clients.indexOf(oldSlot);
        if (idx >= 0) {
            clients.set(idx, newSlot);
        } else {
            clients.add(newSlot);
        }
    }

    /**
     * Called after a successful reconnect. If the controller is paused
     * waiting for someone to come back AND we now have at least two
     * connected players, cancel the lone-player timer and resume.
     */
    private void handleReconnectMaybeResume() {
        if (!phaseStrategy.isPaused()) {
            return;
        }
        if (countConnectedClients() < 2) {
            return;
        }
        cancelLonePlayerTimeoutIfActive();
        resumeFromPause();
    }

    /** @return {@code true} if {@code list} contains at least two equal ints */
    boolean hasDuplicates(List<Integer> list) {
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(i).intValue() == list.get(j).intValue()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Linear search for a {@link CharacterCard} by id in a row. */
    CharacterCard findCharacterById(CardRow row, int cardID) {
        List<CharacterCard> list = row.getCharacterCards();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getCardID() == cardID) {
                return list.get(i);
            }
        }
        return null;
    }

    /** Linear search for a {@link BuildingCard} by id in a row. */
    BuildingCard findBuildingById(CardRow row, int cardID) {
        List<BuildingCard> list = row.getBuildingCards();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getCardID() == cardID) {
                return list.get(i);
            }
        }
        return null;
    }

    /** Searches both rows of the board for a character card by id. */
    CharacterCard findCharacterByIdAnywhere(Board board, int cardID) {
        CharacterCard inUpper = findCharacterById(board.getUpperRow(), cardID);
        if (inUpper != null) {
            return inUpper;
        }
        return findCharacterById(board.getLowerRow(), cardID);
    }

    /** Searches both rows of the board for a building card by id. */
    BuildingCard findBuildingByIdAnywhere(Board board, int cardID) {
        BuildingCard inUpper = findBuildingById(board.getUpperRow(), cardID);
        if (inUpper != null) {
            return inUpper;
        }
        return findBuildingById(board.getLowerRow(), cardID);
    }

    /**
     * Walks every player's owned buildings looking for one whose
     * {@code hasUpperRowBuilding(player)} returns {@code true}. Returns
     * the owner (the only player allowed to take the extra pick) or
     * {@code null} if no such building exists.
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

    // ==================================================================
    // Skip-disconnected / pause / resume helpers (Pass 2)
    // ==================================================================

    /**
     * If the current player is disconnected, advance to the next
     * connected one. The actual per-phase strategy is encoded in
     * {@link Phase#advanceCurrentPlayerIfDisconnected} — this method
     * just delegates polymorphically.
     */
    private void advanceCurrentPlayerIfDisconnected() {
        if (currentPlayerNickname == null) {
            return;
        }
        ClientCallback currentCb = findClientByNickname(currentPlayerNickname);
        if (currentCb != null && currentCb.isConnected()) {
            return;
        }
        phaseStrategy.advanceCurrentPlayerIfDisconnected(this);
    }

    /**
     * Walks {@link #turnOrderForOfferingPick} starting at
     * {@link #currentOfferingPickIndex} until either a connected player
     * is found (broadcast) or the list is exhausted (jump to resolution
     * phase).
     */
    void advanceOfferingPickerUntilConnectedAndBroadcast() {
        while (currentOfferingPickIndex < turnOrderForOfferingPick.size()) {
            Player p = turnOrderForOfferingPick.get(currentOfferingPickIndex);
            ClientCallback cb = findCallbackByPlayer(p);
            if (cb != null && cb.isConnected()) {
                currentPlayerNickname = p.getNickname();
                broadcastGameState("Next player must pick an offering tile");
                return;
            }
            currentOfferingPickIndex++;
        }
        enterCharacterPickForFirstResolutionPlayer();
    }

    /**
     * Walks {@link #resolutionOrder} starting at
     * {@link #currentResolutionIndex} until either a connected player is
     * found (re-enter character pick) or the list is exhausted (run the
     * post-resolution housekeeping).
     */
    void advanceResolverUntilConnectedAndContinue() {
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

    /** Schedules the lone-player timeout. Cancels any previous one. */
    private void startLonePlayerTimeout() {
        cancelLonePlayerTimeoutIfActive();
        // Anonymous TimerTask subclass instead of a lambda so the intent
        // is obvious. TimerTask is just a Runnable with a cancel() method.
        lonePlayerTask = new TimerTask() {
            @Override
            public void run() {
                onLonePlayerTimeout();
            }
        };
        scheduler.schedule(lonePlayerTask, LONE_PLAYER_TIMEOUT_MILLIS);
    }

    /** Cancels the lone-player timeout if one is currently scheduled. */
    private void cancelLonePlayerTimeoutIfActive() {
        if (lonePlayerTask != null) {
            lonePlayerTask.cancel();
            lonePlayerTask = null;
        }
    }

    /** Lone-player timeout body. Runs on the scheduler thread; gets the
     *  controller lock before touching any shared state. */
    private synchronized void onLonePlayerTimeout() {
        // Defensive checks: situation may have changed while we waited.
        if (!phaseStrategy.isPaused()) {
            return;
        }
        if (countConnectedClients() != 1) {
            return;
        }
        for (int i = 0; i < clients.size(); i++) {
            ClientCallback cb = clients.get(i);
            if (cb.isConnected() && cb.getPlayer() != null) {
                forceLonePlayerVictory(cb.getPlayer());
                return;
            }
        }
    }

    /** Ends the game declaring {@code lone} the only winner. */
    private void forceLonePlayerVictory(Player lone) {
        broadcastInfo("Reconnect timeout reached. " + lone.getNickname()
                + " wins by default.");
        forcedWinners = new ArrayList<String>();
        forcedWinners.add(lone.getNickname());
        setPhase(GamePhase.GAME_ENDED);
        phaseBeforePause = null;
        currentPlayerNickname = null;
        broadcastGameState("Game over: lone-player victory for "
                + lone.getNickname());
        if (match != null) {
            gameManager.closeMatch(match.getMatchID());
        }
        PersistenceManager.delete();
    }

    /**
     * Comes out of {@link GamePhase#PAUSED_WAITING_RECONNECT}, restoring
     * the phase that was active before the pause.
     */
    private void resumeFromPause() {
        if (phaseBeforePause == null) {
            return;
        }
        setPhase(phaseBeforePause);
        phaseBeforePause = null;
        broadcastInfo("Match resumed.");
        advanceCurrentPlayerIfDisconnected();
        broadcastGameState("Resumed");
        persistAfterTransition();
    }

    // ==================================================================
    // Persistence (Pass 2)
    // ==================================================================

    /**
     * Builds a snapshot and writes it to disk. Called at the end of
     * every phase transition that lands on a persistable phase. On I/O
     * failure: log + broadcast an error + carry on (the match keeps
     * running, just no longer crash-safe).
     */
    private void persist() {
        if (match == null || !phaseStrategy.shouldPersist()) {
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

    /** Convenience wrapper for callers that just performed a transition. */
    void persistAfterTransition() {
        persist();
    }

    /**
     * Replaces the controller's state with the contents of the given
     * snapshot, then forces the controller into
     * {@link GamePhase#PAUSED_WAITING_RECONNECT}. Real clients will then
     * reconnect by nickname and the controller will resume once at least
     * two of them are back.
     */
    public synchronized void restoreFrom(GameSnapshot snapshot) {
        this.match = snapshot.match;

        // Always come back online paused. The phase to resume to depends
        // on whether the snapshot itself was paused:
        //   - already paused → keep the original pre-pause phase.
        //   - was active     → pause now and remember the active phase.
        setPhase(GamePhase.PAUSED_WAITING_RECONNECT);
        if (STRATEGIES.get(snapshot.phase).isPaused()) {
            this.phaseBeforePause = snapshot.phaseBeforePause;
        } else {
            this.phaseBeforePause = snapshot.phase;
        }
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

        if (!match.getPlayersList().isEmpty()) {
            this.firstPlayerNickname = match.getPlayersList().get(0).getNickname();
        }

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
     * Resets every controller field to the lobby's initial state. Used
     * on full lobby disconnects (no clients left).
     */
    private void resetToLobby() {
        setPhase(GamePhase.WAITING_FOR_NICKNAME);
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

        if (match != null) {
            gameManager.closeMatch(match.getMatchID());
            match = null;
        }

        turnOrderForOfferingPick = new ArrayList<Player>();
        currentOfferingPickIndex = 0;
        resolutionOrder = new ArrayList<Player>();
        currentResolutionIndex = 0;
        currentUpperBudget = 0;
        currentLowerBudget = 0;
        currentUpperRemainingForBuilding = 0;
        currentLowerRemainingForBuilding = 0;
        extraPickPlayer = null;

        for (int i = 0; i < clients.size(); i++) {
            ClientCallback c = clients.get(i);
            c.setPickedColor(null);
            c.setPlayer(null);
        }

        if (!clients.isEmpty()) {
            firstPlayerNickname = clients.get(0).getNickname();
            currentPlayerNickname = firstPlayerNickname;
            setPhase(GamePhase.WAITING_FOR_PLAYER_COUNT);
        }
    }

    // ==================================================================
    // Broadcast / send helpers (package-private so Phase strategies can
    // call them)
    // ==================================================================

    /** Sends a single ServerMessage to every connected client. */
    private void broadcastMessage(ServerMessage message) {
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).send(message);
        }
    }

    /** Builds a fresh {@link GameStateDto} and broadcasts it. */
    void broadcastGameState(String message) {
        GameStateDto dto = createGameStateDto(message);
        broadcastMessage(new GameStateMessage(dto));
    }

    /** Sends the current game state to one specific client. */
    void sendGameStateTo(ClientCallback client, String message) {
        client.send(new GameStateMessage(createGameStateDto(message)));
    }

    /** Broadcasts a free-form info message. */
    void broadcastInfo(String message) {
        broadcastMessage(new InfoMessage(message));
    }

    /** Broadcasts a free-form error message. */
    void broadcastError(String message) {
        broadcastMessage(new ErrorMessage(message));
    }

    /** Sends an action-result message to one specific client. */
    void sendActionResult(
            ClientCallback client, String action, boolean success, String text) {
        client.send(new ActionResultMessage(action, success, text));
    }

    /**
     * Builds an immutable snapshot of the current state for
     * broadcasting. Defensive copies of every collection.
     */
    private GameStateDto createGameStateDto(String message) {
        List<String> playersList = new ArrayList<String>();
        Map<String, TotemColor> playerColors = new HashMap<String, TotemColor>();
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

        List<String> colorPickOrder = new ArrayList<String>();
        for (ClientCallback c : colorPickQueue) {
            if (c.getNickname() != null) {
                colorPickOrder.add(c.getNickname());
            }
        }

        List<String> turnOrderTileNicks = new ArrayList<String>();
        if (match != null && match.getBoard() != null
                && match.getBoard().getTurnOrderTile() != null) {
            List<Player> ordered = match.getBoard()
                    .getPlayersTurnOrder(match.getPlayersList());
            for (int i = 0; i < ordered.size(); i++) {
                turnOrderTileNicks.add(ordered.get(i).getNickname());
            }
        }

        List<TotemColor> available = new ArrayList<TotemColor>(availableColors);

        Board boardRef = (match == null) ? null : match.getBoard();
        Map<String, Integer> playerFood = new HashMap<String, Integer>();
        Map<String, Integer> playerPP = new HashMap<String, Integer>();
        if (match != null) {
            List<Player> players = match.getPlayersList();
            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);
                playerFood.put(p.getNickname(), p.getFood());
                playerPP.put(p.getNickname(), p.getPrestigePoints());
            }
        }

        // End-of-game fields are only populated in GAME_ENDED.
        List<String> leaderboard = new ArrayList<String>();
        List<String> winners = new ArrayList<String>();
        if (phaseStrategy.isGameOver() && match != null) {
            List<Player> sorted = match.createLeaderboard();
            for (int i = 0; i < sorted.size(); i++) {
                leaderboard.add(sorted.get(i).getNickname());
            }
            // If the lone-player timer fired, the forced winners override
            // the natural score-based winners.
            if (forcedWinners != null) {
                winners = new ArrayList<String>(forcedWinners);
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
     * Returns the nicknames of every player tied at the top of the
     * leaderboard. Tied = same prestige points AND same food.
     */
    private List<String> computeWinners(List<Player> sorted) {
        List<String> result = new ArrayList<String>();
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

    // ==================================================================
    // Package-private accessors used by Phase strategies
    // ==================================================================
    //
    // The strategies live in the `controller` package and need to read
    // and mutate the controller's state. We expose narrow getters/setters
    // with package-private visibility so callers from other packages
    // cannot reach in.

    Match getMatch() { return match; }
    List<ClientCallback> getClients() { return clients; }
    Queue<ClientCallback> getColorPickQueue() { return colorPickQueue; }
    List<TotemColor> getAvailableColors() { return availableColors; }

    String getFirstPlayerNickname() { return firstPlayerNickname; }
    void setFirstPlayerNickname(String nick) { this.firstPlayerNickname = nick; }

    int getExpectedPlayers() { return expectedPlayers; }
    void setExpectedPlayers(int n) { this.expectedPlayers = n; }

    String getCurrentPlayerNickname() { return currentPlayerNickname; }
    void setCurrentPlayerNickname(String nick) { this.currentPlayerNickname = nick; }

    List<Player> getTurnOrderForOfferingPick() { return turnOrderForOfferingPick; }

    int getCurrentOfferingPickIndex() { return currentOfferingPickIndex; }
    void setCurrentOfferingPickIndex(int idx) { this.currentOfferingPickIndex = idx; }

    List<Player> getResolutionOrder() { return resolutionOrder; }

    int getCurrentResolutionIndex() { return currentResolutionIndex; }
    void setCurrentResolutionIndex(int idx) { this.currentResolutionIndex = idx; }

    int getCurrentUpperBudget() { return currentUpperBudget; }
    int getCurrentLowerBudget() { return currentLowerBudget; }

    int getCurrentUpperRemainingForBuilding() { return currentUpperRemainingForBuilding; }
    void setCurrentUpperRemainingForBuilding(int n) { this.currentUpperRemainingForBuilding = n; }

    int getCurrentLowerRemainingForBuilding() { return currentLowerRemainingForBuilding; }
    void setCurrentLowerRemainingForBuilding(int n) { this.currentLowerRemainingForBuilding = n; }

    Player getExtraPickPlayer() { return extraPickPlayer; }
}
