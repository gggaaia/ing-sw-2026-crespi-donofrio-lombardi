package it.polimi.ingsw.controller.server;

import it.polimi.ingsw.Server;
import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.dto.FinalStats;
import it.polimi.ingsw.common.dto.GameSnapshot;
import it.polimi.ingsw.database.DatabaseManager;
import it.polimi.ingsw.exceptions.DeckLoadException;
import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.GameManager;
import it.polimi.ingsw.model.Match;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.TurnOrderSlot;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;

import java.rmi.RemoteException;
import java.util.*;

/**
 * Server-side controller. Holds the game state and handles phase
 * transitions through {@link PhaseLogic}.
 *
 * Layout:
 *   - public methods match {@link it.polimi.ingsw.common.VirtualServer}
 *     one-to-one and are all synchronized. They delegate to the
 *     current {@code phase} via polymorphic dispatch.
 *   - {@code PhaseLogic} does the dispatch: if the action is allowed
 *     in the current phase it calls an {@code accept*} method on this
 *     controller; otherwise it calls {@code rejectAction}.
 *   - the {@code accept*} methods do the fine-grained validation,
 *     mutate the model, and broadcast events to the clients'
 *     VirtualViews.
 *   - broadcasts go through {@link Server#send}, which uses a
 *     per-client RMI executor (remote calls can be slow) and an
 *     inline write for socket clients.
 *
 * Locking: everything under a single monitor (this). Dispatch through
 * {@code server.send} is fast (executor submit or writeObject into
 * an OS buffer), so keeping it inside the synchronized block doesn't
 * block other threads for any noticeable time.
 */
public class ServerController {

    // === fixed state ===

    private final Server server;
    private final GameManager gameManager = new GameManager();

    // === phase state ===

    private PhaseLogic phase = new PhaseLogic.WaitingForNickname();

    // === lobby state ===

    private String firstPlayerNickname;
    private int expectedPlayers;
    private final List<String> joinedNicknames = new ArrayList<>();

    // === match state ===

    private Match match;
    /**
     * True once the roster is locked and the game has begun (from color
     * picking onward); false while still in the lobby. We can't just
     * use {@code match != null} for this: the {@link Match} object is
     * only built after colors are picked, but color picking is already
     * part of the game (players can drop and must be able to reconnect).
     */
    private boolean gameStarted;
    private String currentPlayerNickname;
    private final Set<TotemColor> availableColors = EnumSet.allOf(TotemColor.class);

    /** nickname -> Player. Populated from color picking onwards. */
    private final Map<String, Player> playerByNickname = new HashMap<>();
    /** nickname -> picked colour. */
    private final Map<String, TotemColor> colorByNickname = new HashMap<>();

    /** Queue of players still waiting to pick a colour. */
    private final Queue<String> colorPickQueue = new LinkedList<>();

    /** Order for placing totems on the offering track in the current round. */
    private List<Player> turnOrderForOfferingPick = new ArrayList<>();
    private int currentOfferingPickIndex;

    /** Order for resolution (draw + buy) in the current round. */
    private List<Player> resolutionOrder = new ArrayList<>();
    private int currentResolutionIndex;

    /** Budget for the current draw. */
    private int currentUpperBudget;
    private int currentLowerBudget;
    private int currentUpperRemainingForBuilding;
    private int currentLowerRemainingForBuilding;

    /** Owner of the special building (extra pick). */
    private Player extraPickPlayer;

    // === pause / end state ===

    private PhaseLogic phaseBeforePause;
    private Timer lonePlayerTimer;
    public static final long LONE_PLAYER_TIMEOUT_MS = 60_000L;

    private List<String> finalLeaderboard;
    private List<String> finalWinners;
    private List<String> forcedWinners = new ArrayList<>();

    public ServerController(Server server) {
        this.server = server;
    }

    // ====================================================================
    // 1. Public API (called by Rmi_Server and Socket_ClientHandler)
    // ====================================================================

    public synchronized void connect(String nick, VirtualView view) {
        phase.onConnect(this, nick, view);
    }

    /**
     * True iff the current phase has a meaningful {@code onConnect}
     * (lobby join or paused-reconnect). The network adapters check
     * this BEFORE the nickname-registry duplicate check, so that
     * during phases that reject everyone (e.g. WAITING_FOR_PLAYER_COUNT
     * or a running match) a duplicate-nickname connect gets the
     * proper "no game" error and, crucially, never gets registered —
     * which in turn prevents its later disconnect from kicking out
     * the legitimate holder of that nickname.
     */
    public synchronized boolean isAcceptingNewConnections() {
        return phase.acceptsNewConnections();
    }

    /**
     * Returns true if this nickname is a player who is already in the
     * game but is offline right now, so they are allowed to join back.
     *
     * Need this because when 2 or more players are still online the
     * game does not pause. Without this check the player who left would
     * get "no game currently accepting players" and could never return.
     *
     * It also checks if the player is not connected at the moment, so
     * nobody can take a nickname that someone else is still using.
     */
    public synchronized boolean canReconnect(String nick) {
        return isActiveMatchMember(nick) && !server.isConnected(nick);
    }

    /**
     * Returns true if there is a game going on (not finished yet) and
     * this nickname is one of the players in it.
     *
     * Used to know if a new connection is a player coming back
     * (let them reconnect) or just a stranger trying to join a game
     * that already started (we say no to them).
     */
    boolean isActiveMatchMember(String nick) {
        return gameStarted && !phase.isFinished() && joinedNicknames.contains(nick);
    }

    public synchronized void disconnect(String nick) {
        phase.onDisconnect(this, nick);
    }

    public synchronized void setPlayerCount(String nick, int count) {
        phase.onSetPlayerCount(this, nick, count);
    }

    public synchronized void pickColor(String nick, TotemColor color) {
        phase.onPickColor(this, nick, color);
    }

    public synchronized void pickOfferingTile(String nick, int tileIndex) {
        phase.onPickOfferingTile(this, nick, tileIndex);
    }

    public synchronized void pickCharacterCards(String nick,
                                                List<Integer> upper,
                                                List<Integer> lower) {
        phase.onPickCharacterCards(this, nick, upper, lower);
    }

    public synchronized void pickBuildingCards(String nick,
                                               List<Integer> upper,
                                               List<Integer> lower) {
        phase.onPickBuildingCards(this, nick, upper, lower);
    }

    public synchronized void pickExtraCard(String nick,
                                           Integer cardId,
                                           boolean isCharacter) {
        phase.onPickExtraCard(this, nick, cardId, isCharacter);
    }

    /**
     * Called by Server (from the heartbeat or after a send failure)
     * when a client is considered disconnected. Same effect as an
     * explicit {@link #disconnect(String)}.
     */
    public synchronized void handleDeliveryFailure(String nick) {
        phase.onDisconnect(this, nick);
    }

    // ====================================================================
    // 2. Dispatch helpers (broadcast / single-target)
    // ====================================================================

    /**
     * Internal SAM-shape used by {@link #broadcast},
     * {@link #broadcastExcept} and {@link #sendToNickname} to pass
     * "which view method to call" as an argument.
     */
    interface ViewCall {
        void apply(VirtualView v) throws RemoteException;
    }

    /** Sends a view call to every connected client. */
    private void broadcast(final ViewCall call) {
        Map<String, VirtualView> snapshot = server.snapshotViews();
        for (final Map.Entry<String, VirtualView> e : snapshot.entrySet()) {
            sendTo(e.getKey(), e.getValue(), call);
        }
    }

    /** Sends a view call to every client EXCEPT the sender. */
    private void broadcastExcept(final String exceptNick, final ViewCall call) {
        Map<String, VirtualView> snapshot = server.snapshotViews();
        for (final Map.Entry<String, VirtualView> e : snapshot.entrySet()) {
            if (e.getKey().equals(exceptNick)) {
                continue;
            }
            sendTo(e.getKey(), e.getValue(), call);
        }
    }

    /** Sends a view call to a single client by nickname. */
    private void sendToNickname(final String nick, final ViewCall call) {
        VirtualView v = server.getView(nick);
        if (v == null) {
            return;
        }
        sendTo(nick, v, call);
    }

    // -- *AndCommit versions: after applying the ViewCall, they also
    //    send a v.onCommit() to the client which closes the bundle
    //    and triggers {@code ClientController.commit()} (refresh +
    //    flush log + run prompt). Use these by default for EVERYTHING
    //    that produces a user-visible change. The "bare"
    //    {@code broadcast} / {@code sendToNickname} versions are
    //    reserved for cases where a later send will close the bundle
    //    with its own commit (e.g. {@link #rejectAction}).

    /**
     * {@link #broadcast} + onCommit to everyone. Acts as an atomic
     * bundle: the events in {@code call} arrive in order on the
     * client's thread, followed by the commit that triggers refresh.
     */
    private void broadcastAndCommit(final ViewCall call) {
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                call.apply(v);
                v.onCommit();
            }
        });
    }

    /** {@link #broadcastExcept} + onCommit to everyone except the sender. */
    private void broadcastExceptAndCommit(final String exceptNick, final ViewCall call) {
        broadcastExcept(exceptNick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                call.apply(v);
                v.onCommit();
            }
        });
    }

    /** {@link #sendToNickname} + onCommit to a single client. */
    private void sendToNicknameAndCommit(final String nick, final ViewCall call) {
        sendToNickname(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                call.apply(v);
                v.onCommit();
            }
        });
    }

    /** Low-level dispatcher: goes through Server.send (RMI executor / inline socket). */
    private void sendTo(final String nick, final VirtualView v, final ViewCall call) {
        server.send(nick, new Runnable() {
            @Override
            public void run() {
                try {
                    call.apply(v);
                } catch (RemoteException ex) {
                    // Client is dead: kick off the disconnect cascade.
                    handleDeliveryFailure(nick);
                }
            }
        });
    }

    // ====================================================================
    // 3. Reject / snapshot helpers (exposed to PhaseLogic)
    // ====================================================================

    /**
     * Sends an {@code onActionRejected} to the caller with the given
     * reason and RIGHT AFTER re-emits the current {@code *Turn} so
     * the client doesn't sit there waiting forever with no prompt.
     * The re-emit logic is polymorphic on {@link PhaseLogic}.
     *
     * Used to refuse in-game ACTIONS (e.g. a pickColor in the wrong
     * phase). To refuse a connect attempt use {@link #rejectNickname}
     * instead.
     */
    void rejectAction(String nick, final String reason) {
        // The rejection is sent WITHOUT a commit on purpose: right
        // after, {@code phase.emitCurrentTurnTo} sends the current
        // *Turn with its own commit (see sendXxxTurnTo). The two
        // ViewCalls arrive in order on the client (always on socket,
        // and on RMI thanks to the per-client executor), and the
        // single commit at the end of the bundle renders ONE single
        // screen with state + error + prompt.
        sendToNickname(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onActionRejected(reason);
            }
        });
        phase.emitCurrentTurnTo(this, nick);
    }

    /**
     * Sends an {@code onNicknameRejected} to {@code targetView} with
     * the given reason. Triggers a nickname re-prompt on the client
     * so the user can try again (same or different nickname).
     *
     * Important: we send to {@code targetView} directly instead of
     * looking it up in the registry by nickname. On a duplicate
     * connect the new view is NOT in the registry (the server
     * refuses to overwrite the original), so a registry lookup
     * would either find the legitimate holder's view (and reject
     * the wrong client) or nothing at all. We also swallow any
     * {@link RemoteException} silently: the duplicate isn't tracked
     * anywhere, so there is no disconnect cascade to trigger.
     *
     * Also unregisters the nickname from the server registry if it
     * happens to be bound to {@code targetView}. This covers the
     * case where the network adapter registered the view BEFORE the
     * controller reached this rejection (e.g. a phase-level reject
     * during WAITING_FOR_PLAYER_COUNT): leaving the entry behind
     * would block future legitimate uses of the nickname AND keep
     * the heartbeat pinging a doomed connection.
     */
    void rejectNickname(String nick, final VirtualView targetView, final String reason) {
        if (targetView == null) {
            return;
        }
        if (nick != null && server.getView(nick) == targetView) {
            server.unregister(nick);
        }
        // server.send falls back to inline execution when nick isn't
        // an RMI client (which is now always the case here, since
        // we just unregistered it), so the rejection still goes out.
        // The client handles this callback quickly and does any
        // re-prompt on its own thread, so it's safe to send it here.
        server.send(nick, new Runnable() {
            @Override
            public void run() {
                try {
                    targetView.onNicknameRejected(reason);
                } catch (RemoteException ignored) {
                    // Duplicate's view is gone; we don't cascade
                    // because this view was never registered.
                }
            }
        });
    }

    /** Builds the current GameSnapshot for joins / reconnects. */
    GameSnapshot buildSnapshot() {
        Map<String, TotemColor> colors = new LinkedHashMap<>(colorByNickname);
        Map<String, Integer> prestige = new LinkedHashMap<>();
        Map<String, Integer> food = new LinkedHashMap<>();
        Map<String, List<CharacterCard>> hands = new LinkedHashMap<>();
        Map<String, List<BuildingCard>> buildings = new LinkedHashMap<>();
        for (Map.Entry<String, Player> e : playerByNickname.entrySet()) {
            String nick = e.getKey();
            Player p = e.getValue();
            prestige.put(nick, p.getPrestigePoints());
            food.put(nick, p.getFood());
            hands.put(nick, p.getPlayerHand());
            buildings.put(nick, p.getPlayerBuildings());
        }
        return new GameSnapshot(
                phase.getPhaseDto(),
                currentPlayerNickname,
                new ArrayList<>(joinedNicknames),
                colors,
                prestige,
                food,
                hands,
                buildings,
                match != null ? match.getBoard() : null,
                match != null ? match.getCurrentEra() : null,
                match != null ? match.getCurrentRound() : 0,
                currentUpperBudget,
                currentLowerBudget,
                currentUpperRemainingForBuilding,
                currentLowerRemainingForBuilding,
                new HashSet<>(availableColors),
                expectedPlayers,
                joinedNicknames.size(),
                finalLeaderboard,
                finalWinners
        );
    }

    // ====================================================================
    // 4. ACCEPT methods (called by PhaseLogic after the per-phase
    //    dispatch). The bulk of the game logic lives here.
    // ====================================================================

    // --- lobby ----------------------------------------------------------

    /** Accepted in {@link PhaseLogic.WaitingForNickname}: first player. */
    void acceptFirstPlayerJoin(final String nick, VirtualView view) {
        String error = validateNicknameSyntax(nick);
        if (error != null) {
            rejectNickname(nick, view, error);
            return;
        }
        joinedNicknames.add(nick);
        firstPlayerNickname = nick;
        currentPlayerNickname = nick;
        phase = new PhaseLogic.WaitingForPlayerCount();

        // No snapshot for the first player (see GameSnapshot javadoc):
        // just tell them "your turn to set the count". The client
        // adds itself to joinedPlayers inside awaitPlayerCount.
        broadcastAndCommit(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onAwaitPlayerCount(nick);
            }
        });
    }

    /** Accepted in {@link PhaseLogic.WaitingForPlayerCount}. */
    void acceptSetPlayerCount(final String nick, final int count) {
        if (!nick.equals(firstPlayerNickname)) {
            rejectAction(nick, "only the first player can set the count");
            return;
        }
        if (count < 2 || count > 5) {
            rejectAction(nick, "player count must be between 2 and 5");
            return;
        }
        if (count < joinedNicknames.size()) {
            rejectAction(nick, "count cannot be less than the number already in the lobby");
            return;
        }
        expectedPlayers = count;
        if (joinedNicknames.size() == count) {
            // Bundle: count set + transition to color picking in the
            // same commit, so the client renders only once with the
            // new phase.
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onPlayerCountSet(count);
                }
            });
            enterColorPicking();
        } else {
            phase = new PhaseLogic.WaitingForPlayers();
            currentPlayerNickname = null;
            final int joined = joinedNicknames.size();
            // Bundle: count set + await more players in a single commit.
            broadcastAndCommit(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onPlayerCountSet(count);
                    v.onAwaitMorePlayers(joined, count);
                }
            });
        }
    }

    /** Accepted in {@link PhaseLogic.WaitingForPlayers}. */
    void acceptAdditionalPlayerJoin(final String nick, VirtualView view) {
        String error = validateNicknameSyntax(nick);
        if (error != null) {
            rejectNickname(nick, view, error);
            return;
        }
        if (joinedNicknames.contains(nick)) {
            rejectNickname(nick, view, "nickname already in use");
            return;
        }
        joinedNicknames.add(nick);
        // Announce to everyone else (the joiner doesn't get its own
        // onPlayerJoined: it learns about itself from the snapshot
        // below). No commit here: if the lobby fills up right after,
        // {@code enterColorPicking} broadcasts onColorTurn with its
        // own commit, which is atomic for the other clients.
        broadcastExcept(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onPlayerJoined(nick);
            }
        });
        if (joinedNicknames.size() == expectedPlayers) {
            // Lobby full: FIRST transition to COLOR_PICKING (broadcast
            // onColorTurn + commit to everyone), THEN send the joiner
            // their own snapshot (with its own dedicated commit).
            enterColorPicking();
            final GameSnapshot snapAfter = buildSnapshot();
            sendToNicknameAndCommit(nick, new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onGameSnapshot(snapAfter);
                }
            });
        } else {
            // Lobby still incomplete: the WAITING_FOR_PLAYERS snapshot
            // is correct. Bundle: snap + count notification to whoever
            // is already in, in the same commit cycle.
            final GameSnapshot snap = buildSnapshot();
            sendToNicknameAndCommit(nick, new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onGameSnapshot(snap);
                }
            });
            final int joined = joinedNicknames.size();
            final int total = expectedPlayers;
            broadcastExceptAndCommit(nick, new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onAwaitMorePlayers(joined, total);
                }
            });
        }
    }

    private void enterColorPicking() {
        // From here on the roster is locked: the game is "in progress",
        // so disconnects keep the player (for reconnect) instead of
        // dropping them from the lobby.
        gameStarted = true;
        colorPickQueue.clear();
        colorPickQueue.addAll(joinedNicknames);
        currentPlayerNickname = colorPickQueue.peek();
        phase = new PhaseLogic.ColorPicking();
        final String first = currentPlayerNickname;
        final Set<TotemColor> avail = new HashSet<>(availableColors);
        broadcastAndCommit(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onColorTurn(first, avail);
            }
        });
    }

    // --- color picking --------------------------------------------------

    /** Accepted in {@link PhaseLogic.ColorPicking}. */
    void acceptColorPick(final String nick, final TotemColor color) {
        String expected = colorPickQueue.peek();
        if (expected == null) {
            rejectAction(nick, "no color picker scheduled");
            return;
        }
        if (!nick.equals(expected)) {
            rejectAction(nick, "not your turn");
            return;
        }
        if (color == null || !availableColors.contains(color)) {
            rejectAction(nick, "color not available");
            return;
        }
        colorByNickname.put(nick, color);
        playerByNickname.put(nick, new Player(nick, color));
        availableColors.remove(color);
        colorPickQueue.poll();

        // Announce the pick now (no commit); the advance below sends the
        // next color turn (or starts the match) together with the commit
        // so the user sees one single screen.
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onColorPicked(nick, color);
            }
        });
        advanceColorPickingUntilConnected();
    }

    /**
     * Moves color picking on to the next player who is actually
     * connected. If the next player has dropped, we don't wait for them:
     * we give them the first free color so they can still be part of the
     * match (they keep it when they reconnect) and skip ahead, exactly
     * like the other phases skip disconnected players instead of waiting
     * forever. When nobody is left to pick, the match starts.
     */
    void advanceColorPickingUntilConnected() {
        while (!colorPickQueue.isEmpty()) {
            final String next = colorPickQueue.peek();
            if (server.isConnected(next)) {
                currentPlayerNickname = next;
                final Set<TotemColor> avail = new HashSet<>(availableColors);
                broadcastAndCommit(new ViewCall() {
                    @Override public void apply(VirtualView v) throws RemoteException {
                        v.onColorTurn(next, avail);
                    }
                });
                return;
            }
            // Disconnected at their turn: give them the first free color
            // so the match can still include them, then move on.
            final TotemColor assigned = availableColors.iterator().next();
            colorByNickname.put(next, assigned);
            playerByNickname.put(next, new Player(next, assigned));
            availableColors.remove(assigned);
            colorPickQueue.poll();
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onColorPicked(next, assigned);
                }
            });
        }
        startMatch();
    }

    private void startMatch() {
        try {
            int matchID = gameManager.createMatch();
            Match m = gameManager.retrieveMatch(matchID);
            for (String nick : joinedNicknames) {
                m.addPlayer(playerByNickname.get(nick));
            }
            m.startMatch();
            this.match = m;
            this.turnOrderForOfferingPick = m.getBoard().getPlayersTurnOrder(m.getPlayersList());
            this.currentOfferingPickIndex = 0;
        } catch (DeckLoadException | EmptyDeckException e) {
            // Setup failed: reset to lobby with an error.
            final String reason = "match setup failed: " + e.getMessage();
            broadcastAndCommit(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onActionRejected(reason);
                }
            });
            resetToLobby();
            return;
        }
        final List<String> order = new ArrayList<>();
        for (Player p : turnOrderForOfferingPick) {
            order.add(p.getNickname());
        }
        final Era era = match.getCurrentEra();
        final int round = match.getCurrentRound();
        // "match start" bundle: matchStarted + each player's initial
        // prestige/food + roundStarted, all in ONE commit. That way
        // the first dump after color picking shows up fully populated
        // (no "PP: ?" / "Food: ?") and all the lobby + match-start
        // logs are on one screen.
        broadcastAndCommit(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onMatchStarted(order, era);
                for (Player p : match.getPlayersList()) {
                    v.onPrestigeChanged(p.getNickname(), p.getPrestigePoints());
                    v.onFoodChanged(p.getNickname(), p.getFood());
                }
                v.onRoundStarted(round);
            }
        });
        phase = new PhaseLogic.PickOfferingTile();
        advanceOfferingPickerUntilConnected();
    }

    // --- offering tile --------------------------------------------------

    /** Accepted in {@link PhaseLogic.PickOfferingTile}. */
    void acceptOfferingTilePick(final String nick, final int tileIndex) {
        Player player = playerByNickname.get(nick);
        if (player == null) {
            rejectAction(nick, "no player attached to your slot");
            return;
        }
        if (currentOfferingPickIndex >= turnOrderForOfferingPick.size()) {
            rejectAction(nick, "no more pickers scheduled");
            return;
        }
        Player expected = turnOrderForOfferingPick.get(currentOfferingPickIndex);
        if (player != expected) {
            rejectAction(nick, "not your turn");
            return;
        }
        List<OfferingTile> track = match.getBoard().getOfferingTrack();
        if (tileIndex < 0 || tileIndex >= track.size()) {
            rejectAction(nick, "tile index out of range");
            return;
        }
        OfferingTile tile = track.get(tileIndex);
        if (tile.isOccupied()) {
            rejectAction(nick, "tile already occupied");
            return;
        }
        if (tile.getMinPlayers() > match.getPlayersList().size()) {
            rejectAction(nick, "tile requires at least " + tile.getMinPlayers() + " players");
            return;
        }
        boolean ok = match.getBoard().moveTotem(tileIndex, player);
        if (!ok) {
            rejectAction(nick, "internal error: moveTotem failed");
            return;
        }
        // "pick done" bundle: updated board + onOfferingTilePicked,
        // still NO commit. The commit comes with the next bundle
        // (advanceOfferingPickerUntilConnected sends the *Turn to
        // the next picker, or enters resolution): so the client sees
        // ONE single screen with the updated track, the "X placed
        // totem" log, and the new picker state.
        //
        // Note: still safe if for some reason the next bundle is
        // missing, because advanceOfferingPickerUntilConnected
        // always either emits a *Turn(...AndCommit) or enters
        // enterCharacterPickForFirstResolutionPlayer (which commits).
        final Board board = match.getBoard();
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onBoardSnapshot(board);
                v.onOfferingTilePicked(nick, tileIndex);
            }
        });
        currentOfferingPickIndex++;
        advanceOfferingPickerUntilConnected();
    }

    private void advanceOfferingPickerUntilConnected() {
        while (currentOfferingPickIndex < turnOrderForOfferingPick.size()) {
            Player p = turnOrderForOfferingPick.get(currentOfferingPickIndex);
            String nick = p.getNickname();
            if (server.isConnected(nick)) {
                currentPlayerNickname = nick;
                final String fNick = nick;
                final int trackSize = match.getBoard().getOfferingTrack().size();
                final Board board = match.getBoard();
                broadcastAndCommit(new ViewCall() {
                    @Override public void apply(VirtualView v) throws RemoteException {
                        v.onBoardSnapshot(board);
                        v.onOfferingTileTurn(fNick, trackSize);
                    }
                });
                return;
            }
            currentOfferingPickIndex++;
        }
        // No more pickers left: move on to resolution.
        enterCharacterPickForFirstResolutionPlayer();
    }

    // --- character / building picking -----------------------------------

    private void enterCharacterPickForFirstResolutionPlayer() {
        resolutionOrder = match.getBoard().getOfferingTrackPlayersOrder(match.getPlayersList());
        currentResolutionIndex = 0;
        if (resolutionOrder.isEmpty()) {
            phase = new PhaseLogic.ResolveBuildingsAndEvents();
            currentPlayerNickname = null;
            afterAllResolutionsDone();
            return;
        }
        advanceResolverUntilConnectedAndContinue();
    }

    private void advanceResolverUntilConnectedAndContinue() {
        while (currentResolutionIndex < resolutionOrder.size()) {
            Player p = resolutionOrder.get(currentResolutionIndex);
            String nick = p.getNickname();
            if (server.isConnected(nick)) {
                enterCharacterPickForCurrentResolutionPlayer();
                return;
            }
            currentResolutionIndex++;
        }
        afterAllResolutionsDone();
    }

    private void enterCharacterPickForCurrentResolutionPlayer() {
        Player p = resolutionOrder.get(currentResolutionIndex);
        int tileIndex = match.getBoard().getOfferingTrackIndex(p);
        OfferingTile tile = match.getBoard().getOfferingTrack().get(tileIndex);
        currentUpperBudget = tile.getTopCardsToGet();
        currentLowerBudget = tile.getBottomCardsToGet();
        currentUpperRemainingForBuilding = currentUpperBudget;
        currentLowerRemainingForBuilding = currentLowerBudget;
        p.changeFood(tile.getFoodGained());
        currentPlayerNickname = p.getNickname();
        phase = new PhaseLogic.TurnPickCardsCharacter();
        final String nick = currentPlayerNickname;
        final int upper = currentUpperBudget;
        final int lower = currentLowerBudget;
        final int newFood = p.getFood();
        final Board board = match.getBoard();
        broadcastAndCommit(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onFoodChanged(nick, newFood);
                v.onBoardSnapshot(board);
                v.onCharacterPickTurn(nick, upper, lower);
            }
        });
    }

    /** Accepted in {@link PhaseLogic.TurnPickCardsCharacter}. */
    void acceptCharacterCardsPick(final String nick, List<Integer> upperIds, List<Integer> lowerIds) {
        Player player = playerByNickname.get(nick);
        if (player == null) {
            rejectAction(nick, "no player attached to your slot");
            return;
        }
        if (currentResolutionIndex >= resolutionOrder.size()
                || player != resolutionOrder.get(currentResolutionIndex)) {
            rejectAction(nick, "not your turn");
            return;
        }
        List<Integer> upper = upperIds != null ? upperIds : new ArrayList<>();
        List<Integer> lower = lowerIds != null ? lowerIds : new ArrayList<>();
        if (upper.size() > currentUpperBudget) {
            rejectAction(nick, "too many cards from upper row (budget " + currentUpperBudget + ")");
            return;
        }
        if (lower.size() > currentLowerBudget) {
            rejectAction(nick, "too many cards from lower row (budget " + currentLowerBudget + ")");
            return;
        }
        if (hasDuplicates(upper) || hasDuplicates(lower)) {
            rejectAction(nick, "duplicate id in selection");
            return;
        }
        Board board = match.getBoard();
        List<CharacterCard> foundUpper = new ArrayList<>();
        for (Integer id : upper) {
            CharacterCard c = findCharacterById(board.getUpperRow(), id);
            if (c == null) { rejectAction(nick, "upper character id " + id + " not found"); return; }
            foundUpper.add(c);
        }
        List<CharacterCard> foundLower = new ArrayList<>();
        for (Integer id : lower) {
            CharacterCard c = findCharacterById(board.getLowerRow(), id);
            if (c == null) { rejectAction(nick, "lower character id " + id + " not found"); return; }
            foundLower.add(c);
        }
        // Commit
        for (CharacterCard c : foundUpper) {
            board.removeCardFromRows(c);
            c.drawEffect(player);
            player.addCardToHand(c);
        }
        for (CharacterCard c : foundLower) {
            board.removeCardFromRows(c);
            c.drawEffect(player);
            player.addCardToHand(c);
        }
        currentUpperRemainingForBuilding = currentUpperBudget - upper.size();
        currentLowerRemainingForBuilding = currentLowerBudget - lower.size();

        final List<CharacterCard> finalUpper = foundUpper;
        final List<CharacterCard> finalLower = foundLower;
        phase = new PhaseLogic.TurnPickCardsBuilding();
        final int uRem = currentUpperRemainingForBuilding;
        final int lRem = currentLowerRemainingForBuilding;
        final Board boardAfterCharPick = match.getBoard();
        broadcastAndCommit(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onBoardSnapshot(boardAfterCharPick);
                v.onCharacterCardsPicked(nick, finalUpper, finalLower);
                v.onBuildingPickTurn(nick, uRem, lRem);
            }
        });
    }

    /** Accepted in {@link PhaseLogic.TurnPickCardsBuilding}. */
    void acceptBuildingCardsPick(final String nick, List<Integer> upperIds, List<Integer> lowerIds) {
        Player player = playerByNickname.get(nick);
        if (player == null) {
            rejectAction(nick, "no player attached to your slot");
            return;
        }
        if (currentResolutionIndex >= resolutionOrder.size()
                || player != resolutionOrder.get(currentResolutionIndex)) {
            rejectAction(nick, "not your turn");
            return;
        }
        List<Integer> upper = upperIds != null ? upperIds : new ArrayList<>();
        List<Integer> lower = lowerIds != null ? lowerIds : new ArrayList<>();
        if (upper.size() > currentUpperRemainingForBuilding) {
            rejectAction(nick, "too many upper buildings (residual " + currentUpperRemainingForBuilding + ")");
            return;
        }
        if (lower.size() > currentLowerRemainingForBuilding) {
            rejectAction(nick, "too many lower buildings (residual " + currentLowerRemainingForBuilding + ")");
            return;
        }
        if (hasDuplicates(upper) || hasDuplicates(lower)) {
            rejectAction(nick, "duplicate id in selection");
            return;
        }
        Board board = match.getBoard();
        List<BuildingCard> foundUpper = new ArrayList<>();
        for (Integer id : upper) {
            BuildingCard b = findBuildingById(board.getUpperRow(), id);
            if (b == null) { rejectAction(nick, "upper building id " + id + " not found"); return; }
            foundUpper.add(b);
        }
        List<BuildingCard> foundLower = new ArrayList<>();
        for (Integer id : lower) {
            BuildingCard b = findBuildingById(board.getLowerRow(), id);
            if (b == null) { rejectAction(nick, "lower building id " + id + " not found"); return; }
            foundLower.add(b);
        }
        int grossCost = 0;
        for (BuildingCard b : foundUpper) grossCost += b.getFoodCost();
        for (BuildingCard b : foundLower) grossCost += b.getFoodCost();
        int discount = player.getBuildingDiscountBuilder();
        int netCost = Math.max(0, grossCost - discount);
        if (!player.hasEnoughFood(netCost)) {
            rejectAction(nick, "not enough food (need " + netCost + ")");
            return;
        }
        // Commit
        for (BuildingCard b : foundUpper) {
            board.removeCardFromRows(b);
            b.setOwner(player);
            player.addBuildingToHand(b);
        }
        for (BuildingCard b : foundLower) {
            board.removeCardFromRows(b);
            b.setOwner(player);
            player.addBuildingToHand(b);
        }
        player.changeFood(-netCost);

        final List<BuildingCard> finalUpper = foundUpper;
        final List<BuildingCard> finalLower = foundLower;
        final Board boardAfterBuild = match.getBoard();
        final int newFood = player.getFood();
        // NO commit: the next step (advanceResolver -> next
        // character pick turn, or afterAllResolutionsDone -> extra
        // pick / round end) closes the bundle with its own commit.
        // This way we get ONE single screen showing the bought
        // buildings + the new turn.
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onBoardSnapshot(boardAfterBuild);
                v.onBuildingCardsBought(nick, finalUpper, finalLower);
                v.onFoodChanged(nick, newFood);
            }
        });
        currentResolutionIndex++;
        advanceResolverUntilConnectedAndContinue();
    }

    private void afterAllResolutionsDone() {
        // End-of-round housekeeping:
        //   clear turn order tile, move offering->turn-order tile,
        //   append missing players, apply slot bonuses, find the
        //   special building's owner.
        for (Player p : match.getPlayersList()) {
            match.getBoard().getTurnOrderTile().clearSlot(p);
        }
        match.getBoard().moveOfferingTrackToTurnOrder(match.getPlayersList());
        appendMissingPlayersToTurnOrderTile();
        match.resolveTurnOrderTile();
        // "bonus tile applied" bundle: every player's updated
        // prestige/food in ONE ViewCall, NO commit. The commit will
        // come either from the next extra-pick turn below, or (if
        // there's no extra-pick) from enterResolveBuildingsAndEvents.
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                for (Player p : match.getPlayersList()) {
                    v.onPrestigeChanged(p.getNickname(), p.getPrestigePoints());
                    v.onFoodChanged(p.getNickname(), p.getFood());
                }
            }
        });
        extraPickPlayer = findSpecialBuildingOwner();
        if (extraPickPlayer == null || !server.isConnected(extraPickPlayer.getNickname())) {
            extraPickPlayer = null;
            enterResolveBuildingsAndEvents();
            return;
        }
        currentPlayerNickname = extraPickPlayer.getNickname();
        phase = new PhaseLogic.ResetTurnOrderAndPickExtraBuilding();
        final String nick = currentPlayerNickname;
        final Board board = match.getBoard();
        broadcastAndCommit(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onBoardSnapshot(board);
                v.onExtraPickTurn(nick);
            }
        });
    }

    private void appendMissingPlayersToTurnOrderTile() {
        TurnOrderTile turnOrderTile = match.getBoard().getTurnOrderTile();
        for (Player p : match.getPlayersList()) {
            TurnOrderSlot[] slots = turnOrderTile.getTurnOrderSlots();
            boolean alreadyOnTile = false;
            for (TurnOrderSlot slot : slots) {
                if (slot.isOccupied() && slot.getTotemColor() == p.getPlayerColor()) {
                    alreadyOnTile = true;
                    break;
                }
            }
            if (alreadyOnTile) continue;
            for (int i = 0; i < slots.length; i++) {
                if (!slots[i].isOccupied()) {
                    turnOrderTile.placeTotem(p, i);
                    break;
                }
            }
        }
    }

    private Player findSpecialBuildingOwner() {
        for (Player p : match.getPlayersList()) {
            for (BuildingCard b : p.getPlayerBuildings()) {
                if (b.hasUpperRowBuilding(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Accepted in {@link PhaseLogic.ResetTurnOrderAndPickExtraBuilding}. */
    void acceptExtraCardPick(final String nick, final Integer cardId, final boolean isCharacter) {
        Player player = playerByNickname.get(nick);
        if (player == null || player != extraPickPlayer) {
            rejectAction(nick, "extra pick belongs to another player");
            return;
        }
        if (cardId == null) {
            // Skip: no commit here, the next
            // enterResolveBuildingsAndEvents closes the bundle.
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onExtraCardPicked(nick, null, isCharacter);
                }
            });
            enterResolveBuildingsAndEvents();
            return;
        }
        Board board = match.getBoard();
        final boolean[] foodWillChange = { false };
        final int[] newFoodHolder = { 0 };
        if (isCharacter) {
            CharacterCard c = findCharacterAnywhere(board, cardId);
            if (c == null) {
                rejectAction(nick, "character id " + cardId + " not found");
                return;
            }
            board.removeCardFromRows(c);
            c.drawEffect(player);
            player.addCardToHand(c);
        } else {
            BuildingCard b = findBuildingAnywhere(board, cardId);
            if (b == null) {
                rejectAction(nick, "building id " + cardId + " not found");
                return;
            }
            int discount = player.getBuildingDiscountBuilder();
            int netCost = Math.max(0, b.getFoodCost() - discount);
            if (!player.hasEnoughFood(netCost)) {
                rejectAction(nick, "not enough food (need " + netCost + ")");
                return;
            }
            board.removeCardFromRows(b);
            b.setOwner(player);
            player.addBuildingToHand(b);
            player.changeFood(-netCost);
            foodWillChange[0] = true;
            newFoodHolder[0] = player.getFood();
        }
        final Board boardAfterExtra = match.getBoard();
        // "extra pick result" bundle: board + (food changed if
        // building) + onExtraCardPicked, NO commit; the following
        // enterResolveBuildingsAndEvents closes it.
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onBoardSnapshot(boardAfterExtra);
                if (foodWillChange[0]) {
                    v.onFoodChanged(nick, newFoodHolder[0]);
                }
                v.onExtraCardPicked(nick, cardId, isCharacter);
            }
        });
        enterResolveBuildingsAndEvents();
    }

    private void enterResolveBuildingsAndEvents() {
        extraPickPlayer = null;
        currentPlayerNickname = null;
        phase = new PhaseLogic.ResolveBuildingsAndEvents();

        match.resolveBuildings();
        match.resolveEvents();
        // "end of round + scoring" bundle: all updated prestige/food
        // + onRoundEnded + (era change if any) + onRoundStarted, in
        // ONE broadcast with NO commit. The commit is added by
        // advanceOfferingPickerUntilConnected (with the first *Turn
        // of the next round) or by endGame.
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                for (Player p : match.getPlayersList()) {
                    v.onPrestigeChanged(p.getNickname(), p.getPrestigePoints());
                    v.onFoodChanged(p.getNickname(), p.getFood());
                }
                v.onRoundEnded();
            }
        });
        if (match.getCurrentRound() == Match.FINAL_ROUND) {
            endGame();
            return;
        }
        try {
            Era prevEra = match.getCurrentEra();
            match.moveToNextRound();
            final Era newEra = match.getCurrentEra();
            final boolean eraChanged = (newEra != prevEra);
            final int round = match.getCurrentRound();
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    if (eraChanged) v.onEraChanged(newEra);
                    v.onRoundStarted(round);
                }
            });
        } catch (EmptyDeckException e) {
            endGame();
            return;
        }
        turnOrderForOfferingPick = match.getBoard().getPlayersTurnOrder(match.getPlayersList());
        currentOfferingPickIndex = 0;
        currentUpperBudget = 0;
        currentLowerBudget = 0;
        currentUpperRemainingForBuilding = 0;
        currentLowerRemainingForBuilding = 0;
        phase = new PhaseLogic.PickOfferingTile();
        advanceOfferingPickerUntilConnected();
    }

    // --- end of match ---------------------------------------------------


    /**
     * Handles the end-of-game routine by resolving final board states, events, and scores.
     * <p>
     * This method finalizes the board row transitions, triggers end-game events and point
     * calculations, determines the official winners (handling forced victories if applicable),
     * updates the server's phase to game-over, and broadcasts the final leaderboard
     * and winners list to all connected clients.
     */
    private void endGame() {
        Map<String, Integer> finalGlobalLeaderboard = new LinkedHashMap<>();
        if (match != null) {
            match.getBoard().shiftUpToLow();
            match.resolveEvents();
            match.endMatch();
            List<Player> sorted = match.createLeaderboard();
            finalLeaderboard = new ArrayList<>();
            for (Player p : sorted) {
                finalLeaderboard.add(p.getNickname());
            }
            finalWinners = (forcedWinners != null && !forcedWinners.isEmpty())
                    ? new ArrayList<>(forcedWinners)
                    : computeWinners(sorted);

            finalGlobalLeaderboard = processDatabasePersistence();
        }
        currentPlayerNickname = null;
        phase = new PhaseLogic.GameEnded();

        final List<String> leaderboard = finalLeaderboard != null
                ? new ArrayList<>(finalLeaderboard) : new ArrayList<String>();
        final List<String> winners = finalWinners != null
                ? new ArrayList<>(finalWinners) : new ArrayList<String>();
        final Map<String, Integer> globalLeaderboard = finalGlobalLeaderboard != null
                ? new LinkedHashMap<>(finalGlobalLeaderboard) : new LinkedHashMap<>();

        broadcastAndCommit(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onGameEnded(leaderboard, winners, globalLeaderboard);
            }
        });
    }

    /**
     * Creates a {@link FinalStats} DTO populated with the current match data and player scores.
     * Used for DB communications.
     *
     * @return a new instance containing the final match statistics
     */
    private FinalStats makeFinalStats() {
        int matchId=match.getMatchID();
        Date date=match.getDate();
        int numPlayer=(match.getPlayersList().size());
        Map<String, Integer> mapPoints = new HashMap<>();
        for (Player p : match.getPlayersList()) {
            mapPoints.put(p.getNickname(), p.getPrestigePoints());
        }
        return new FinalStats( matchId, date, numPlayer, mapPoints );
    }

    /**
     * Synchronously persists the final match statistics to the database and
     * retrieves the updated global leaderboard for the current player count category.
     *
     * @return the updated global leaderboard, or an empty map if a database error occurs
     */
    private Map<String, Integer> processDatabasePersistence() {
        try {
            FinalStats stats = makeFinalStats();
            DatabaseManager.getInstance().saveMatch(stats);
            return DatabaseManager.getInstance().getLeaderboard(stats.getNumPlayers());

        } catch (Exception e) {
            System.err.println("[SERVER DB ERROR] Critical failure during persistence routine: " + e.getMessage());

            // on error return an empty map to ensure the broadcast doesn't fail
            return new LinkedHashMap<>();
        }
    }

    /**
     * Computes the list of winners from a pre-sorted list of players.
     * <p>
     * Since the input list is already sorted by the main game criteria, this method
     * identifies the top player and includes any subsequent players who share the exact
     * same maximum prestige points and food score (handling ties).
     * </p>
     *
     * @param sorted the list of players, pre-sorted in descending order of performance
     * @return a list of nicknames of the winning player(s)
     */
    private List<String> computeWinners(List<Player> sorted) {
        List<String> winners = new ArrayList<>();
        if (sorted.isEmpty()) return winners;
        Player top = sorted.get(0);
        int topPP = top.getPrestigePoints();
        int topFood = top.getFood();
        for (Player p : sorted) {
            if (p.getPrestigePoints() == topPP && p.getFood() == topFood) {
                winners.add(p.getNickname());
            } else {
                break;
            }
        }
        return winners;
    }

    // ====================================================================
    // 5. Disconnect + reconnect
    // ====================================================================

    /** Accepted in any phase (handles the cascade). */
    void acceptDisconnect(final String nick) {
        // Idempotency: if the client was already removed by another
        // thread (e.g. two parallel sends to the same client both
        // failed), don't run the broadcast cascade twice.
        if (!server.unregisterIfPresent(nick)) {
            return;
        }

        boolean wasJoined;
        if (!gameStarted) {
            // Still in the lobby: a player who leaves is simply dropped.
            wasJoined = joinedNicknames.remove(nick);
            if (nick.equals(firstPlayerNickname)) {
                firstPlayerNickname = joinedNicknames.isEmpty() ? null : joinedNicknames.get(0);
            }
            if (joinedNicknames.isEmpty()) {
                resetToLobby();
                return;
            }
        } else {
            // Game in progress (color picking onward): keep the player
            // in the roster so they can reconnect.
            wasJoined = joinedNicknames.contains(nick);
        }

        // If the client was never officially in the lobby/match
        // (e.g. it was rejected at connect), there's nobody to tell
        // about the disconnect.
        if (!wasJoined) {
            return;
        }

        // "disconnect + maybe pause" bundle: one commit in either
        // case. The client sees a single screen.
        if (gameStarted && !phase.isFinished()) {
            int connected = countConnectedInMatch();
            if (connected <= 1) {
                enterPause();
                final String reason = connected == 0
                        ? "all clients disconnected"
                        : "only one client left";
                broadcastAndCommit(new ViewCall() {
                    @Override public void apply(VirtualView v) throws RemoteException {
                        v.onPlayerDisconnected(nick);
                        v.onMatchPaused(reason);
                    }
                });
                return;
            }
            // 2+: announce + skip the turn if it was theirs.
            // advanceCurrent will emit the *Turn (and therefore the
            // commit) for the next player. No commit here.
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onPlayerDisconnected(nick);
                }
            });
            if (nick.equals(currentPlayerNickname)) {
                phase.advanceCurrent(this);
            } else {
                // Wasn't their turn: still need a commit so the
                // announcement actually renders.
                broadcastAndCommit(new ViewCall() {
                    @Override public void apply(VirtualView v) throws RemoteException { }
                });
            }
            return;
        }
        // No active match (e.g. lobby): standalone announcement with commit.
        broadcastAndCommit(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onPlayerDisconnected(nick);
            }
        });
    }

    /**
     * Lets a player who left join back into the game. This is used both
     * when the game is paused (everyone else left too) and when the
     * game is still going on with the other players.
     */
    void acceptReconnect(final String nick, VirtualView view) {
        // Only let them in if this nickname is really one of the players.
        // If the name is wrong send a "nickname" error, not an action
        // error. A nickname error makes the client show the message and
        // ask for the name again, so the user can just try once more. An
        // action error here would leave the client stuck with no prompt
        // (during a pause there is no turn to send back), which is why
        // the client looked frozen before.
        if (!joinedNicknames.contains(nick)) {
            rejectNickname(nick, view, "nickname not in this game");
            return;
        }
        // Remember if the game was paused. If it was NOT paused we must
        // not tell everyone "match resumed", because for them the game
        // never stopped.
        final boolean wasPaused = phase.isPaused();
        // Send the whole game state to the player who came back, so
        // they can see everything again.
        final GameSnapshot snap = buildSnapshot();
        sendToNicknameAndCommit(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onGameSnapshot(snap);
            }
        });
        // Tell the other players that this player came back.
        broadcastExceptAndCommit(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onPlayerReconnected(nick);
            }
        });
        // If the game was paused and now there are at least 2 players
        // online again, start the game again. If it was never paused we
        // do nothing here: the state we just sent is enough, and we do
        // not want to disturb the player whose turn it is.
        if (wasPaused && countConnectedInMatch() >= 2) {
            resumeFromPause();
            broadcastAndCommit(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onMatchResumed();
                }
            });
            // Tell the player whose turn it is to play again.
            if (currentPlayerNickname != null) {
                phase.emitCurrentTurnTo(this, currentPlayerNickname);
            }
        }
    }

    private int countConnectedInMatch() {
        int n = 0;
        for (String nick : joinedNicknames) {
            if (server.isConnected(nick)) n++;
        }
        return n;
    }

    private void enterPause() {
        if (!phase.isPaused()) {
            phaseBeforePause = phase;
            phase = new PhaseLogic.PausedWaitingReconnect();
        }
        int connected = countConnectedInMatch();
        if (connected == 1) {
            startLonePlayerTimeout();
        } else {
            cancelLonePlayerTimeout();
        }
    }

    private void resumeFromPause() {
        if (!phase.isPaused()) return;
        cancelLonePlayerTimeout();
        PhaseLogic restored = phaseBeforePause;
        phaseBeforePause = null;
        phase = restored;
        // If currentPlayerNickname is still disconnected, skip.
        if (currentPlayerNickname != null && !server.isConnected(currentPlayerNickname)) {
            phase.advanceCurrent(this);
        }
    }

    /** Called by {@link PhaseLogic.PickOfferingTile#advanceCurrent}. */
    void advanceOfferingPicker() {
        currentOfferingPickIndex++;
        advanceOfferingPickerUntilConnected();
    }

    /** Called by {@link PhaseLogic.TurnPickCardsCharacter#advanceCurrent}
     *  and {@link PhaseLogic.TurnPickCardsBuilding#advanceCurrent}. */
    void advanceResolver() {
        currentResolutionIndex++;
        advanceResolverUntilConnectedAndContinue();
    }

    /** Called by {@link PhaseLogic.ResetTurnOrderAndPickExtraBuilding#advanceCurrent}. */
    void skipExtraPick() {
        extraPickPlayer = null;
        enterResolveBuildingsAndEvents();
    }

    // === re-send the current *Turn (for rejection / reconnect) =========

    /** Re-sends the current {@code onColorTurn} to {@code nick}. */
    void sendColorTurnTo(String nick) {
        final String current = currentPlayerNickname;
        final Set<TotemColor> avail = new HashSet<>(availableColors);
        if (current == null) return;
        sendToNicknameAndCommit(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onColorTurn(current, avail);
            }
        });
    }

    /** Re-sends the current {@code onOfferingTileTurn} to {@code nick}. */
    void sendOfferingTileTurnTo(String nick) {
        if (currentPlayerNickname == null || match == null) return;
        final String current = currentPlayerNickname;
        final int trackSize = match.getBoard().getOfferingTrack().size();
        final Board board = match.getBoard();
        sendToNicknameAndCommit(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onBoardSnapshot(board);
                v.onOfferingTileTurn(current, trackSize);
            }
        });
    }

    /** Re-sends the current {@code onCharacterPickTurn} to {@code nick}. */
    void sendCharacterPickTurnTo(String nick) {
        if (currentPlayerNickname == null) return;
        final String current = currentPlayerNickname;
        final int upper = currentUpperBudget;
        final int lower = currentLowerBudget;
        final Board board = match != null ? match.getBoard() : null;
        sendToNicknameAndCommit(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                if (board != null) v.onBoardSnapshot(board);
                v.onCharacterPickTurn(current, upper, lower);
            }
        });
    }

    /** Re-sends the current {@code onBuildingPickTurn} to {@code nick}. */
    void sendBuildingPickTurnTo(String nick) {
        if (currentPlayerNickname == null) return;
        final String current = currentPlayerNickname;
        final int upperRem = currentUpperRemainingForBuilding;
        final int lowerRem = currentLowerRemainingForBuilding;
        final Board board = match != null ? match.getBoard() : null;
        sendToNicknameAndCommit(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                if (board != null) v.onBoardSnapshot(board);
                v.onBuildingPickTurn(current, upperRem, lowerRem);
            }
        });
    }

    /** Re-sends the current {@code onExtraPickTurn} to {@code nick}. */
    void sendExtraPickTurnTo(String nick) {
        if (currentPlayerNickname == null) return;
        final String current = currentPlayerNickname;
        final Board board = match != null ? match.getBoard() : null;
        sendToNicknameAndCommit(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                if (board != null) v.onBoardSnapshot(board);
                v.onExtraPickTurn(current);
            }
        });
    }

    private void startLonePlayerTimeout() {
        cancelLonePlayerTimeout();
        lonePlayerTimer = new Timer("lone-player-timeout", true);
        lonePlayerTimer.schedule(new TimerTask() {
            @Override public void run() {
                handleLonePlayerTimeout();
            }
        }, LONE_PLAYER_TIMEOUT_MS);
    }

    private void cancelLonePlayerTimeout() {
        if (lonePlayerTimer != null) {
            lonePlayerTimer.cancel();
            lonePlayerTimer = null;
        }
    }

    /**
     * Handles the premature end of the game when a timeout expires and only one player remains.
     * <p>
     * Verifies the pause state and connection counts, declares the lone remaining player
     * as the forced winner, finalizes the match state within the game manager, and broadcasts
     * the final match results along with the global database leaderboard to the clients.
     */
    private void handleLonePlayerTimeout() {
        Map<String, Integer> finalGlobalLeaderboard = new LinkedHashMap<>();
        String winnerNick;
        synchronized (this) {
            if (!phase.isPaused()) return;
            if (countConnectedInMatch() != 1) return;
            winnerNick = findLoneConnectedNickname();
            if (winnerNick == null) return;
            forcedWinners = new ArrayList<>();
            forcedWinners.add(winnerNick);
            if (match != null) {
                List<Player> sorted = match.createLeaderboard();
                finalLeaderboard = new ArrayList<>();
                for (Player p : sorted) {
                    finalLeaderboard.add(p.getNickname());
                }
                gameManager.closeMatch(match.getMatchID());
            } else {
                finalLeaderboard = new ArrayList<>();
            }
            finalWinners = new ArrayList<>(forcedWinners);
            phaseBeforePause = null;
            currentPlayerNickname = null;
            phase = new PhaseLogic.GameEnded();
        }

        finalGlobalLeaderboard = processDatabasePersistence();
        final Map<String, Integer> globalLeaderboard = finalGlobalLeaderboard != null
                ? new LinkedHashMap<>(finalGlobalLeaderboard) : new LinkedHashMap<>();

        final List<String> lb = new ArrayList<>(finalLeaderboard);
        final List<String> wn = new ArrayList<>(finalWinners);
        broadcastAndCommit(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onGameEnded(lb, wn, globalLeaderboard);
            }
        });
    }

    private String findLoneConnectedNickname() {
        String lone = null;
        for (String nick : joinedNicknames) {
            if (server.isConnected(nick)) {
                if (lone != null) return null;
                lone = nick;
            }
        }
        return lone;
    }

    // ====================================================================
    // 6. Reset-to-lobby and static utilities
    // ====================================================================

    private void resetToLobby() {
        gameStarted = false;
        if (match != null) {
            gameManager.closeMatch(match.getMatchID());
            match = null;
        }
        turnOrderForOfferingPick = new ArrayList<>();
        currentOfferingPickIndex = 0;
        resolutionOrder = new ArrayList<>();
        currentResolutionIndex = 0;
        colorPickQueue.clear();
        availableColors.clear();
        availableColors.addAll(EnumSet.allOf(TotemColor.class));
        playerByNickname.clear();
        colorByNickname.clear();
        firstPlayerNickname = null;
        expectedPlayers = 0;
        currentPlayerNickname = null;
        joinedNicknames.clear();
        phase = new PhaseLogic.WaitingForNickname();
    }

    private static String validateNicknameSyntax(String nick) {
        if (nick == null) return "nickname is null";
        String trimmed = nick.trim();
        if (trimmed.isEmpty()) return "nickname is empty";
        if (trimmed.length() > 16) return "nickname too long (max 16)";
        return null;
    }

    private static boolean hasDuplicates(List<Integer> ids) {
        Set<Integer> seen = new HashSet<>();
        for (Integer id : ids) {
            if (!seen.add(id)) return true;
        }
        return false;
    }

    private static CharacterCard findCharacterById(CardRow row, int id) {
        for (CharacterCard c : row.getCharacterCards()) {
            if (c.getCardID() == id) return c;
        }
        return null;
    }

    private static BuildingCard findBuildingById(CardRow row, int id) {
        for (BuildingCard b : row.getBuildingCards()) {
            if (b.getCardID() == id) return b;
        }
        return null;
    }

    private static CharacterCard findCharacterAnywhere(Board board, int id) {
        CharacterCard c = findCharacterById(board.getUpperRow(), id);
        if (c != null) return c;
        return findCharacterById(board.getLowerRow(), id);
    }

    private static BuildingCard findBuildingAnywhere(Board board, int id) {
        BuildingCard b = findBuildingById(board.getUpperRow(), id);
        if (b != null) return b;
        return findBuildingById(board.getLowerRow(), id);
    }
}
