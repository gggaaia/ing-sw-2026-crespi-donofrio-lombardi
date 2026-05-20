package it.polimi.ingsw.controller.client;

import it.polimi.ingsw.common.VirtualServer;
import it.polimi.ingsw.common.dto.GameSnapshot;
import it.polimi.ingsw.common.dto.Phase;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.view.View;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Client-side controller. Keeps a local mini-state that mirrors what
 * the server told us, updated event by event.
 *
 * Threading model (kept simple):
 *   - every public method is synchronized
 *   - event methods (called from the RMI / Socket network thread)
 *     mutate the state and trigger view.refresh()
 *   - the *Turn methods, when it's our turn, BLOCK on view.promptXxx()
 *     and then push the action through virtualServer
 *   - the outgoing network call (virtualServer.xxx()) happens inside
 *     the synchronized method: the lock is held, but the call is
 *     fast both on RMI (microseconds for the dispatch) and on socket
 *     (writeObject into the OS buffer)
 *
 * "Block" on the prompt means that, while the user is thinking,
 * incoming events from the server just queue up in the socket buffer
 * / RMI thread pool. They get processed as soon as the user picks.
 */
public class ClientController {

    private final View view;
    private String myNickname;
    private VirtualServer virtualServer;

    // === local mini-state ===

    private Phase currentPhase;
    private String currentPlayer;
    private final List<String> joinedPlayers = new ArrayList<>();
    private final Map<String, TotemColor> colorsByPlayer = new LinkedHashMap<>();
    private final Map<String, Integer> prestige = new HashMap<>();
    private final Map<String, Integer> food = new HashMap<>();
    private final Map<String, List<CharacterCard>> handsByPlayer = new LinkedHashMap<>();
    private final Map<String, List<BuildingCard>> buildingsByPlayer = new LinkedHashMap<>();
    private Set<TotemColor> availableColors = new HashSet<>();
    private int currentUpperBudget;
    private int currentLowerBudget;
    private int currentUpperRemaining;
    private int currentLowerRemaining;
    private int currentRound;
    private Era currentEra;
    private Board currentBoard;
    private int expectedPlayers;
    private List<String> finalLeaderboard;
    private List<String> finalWinners;
    /**
     * Server-side error (e.g. "tile already occupied") waiting to be
     * shown. We just stash it in {@link #actionRejected} without
     * printing it right away: the next {@link #commit()} flushes it
     * AFTER the state dump and right before the prompt.
     */
    private String pendingError;

    /**
     * Game log lines accumulated by the event handlers (e.g.
     * "A picked RED", "B prestige -> 5", "Round 1 started"). They
     * get dumped in one batch by {@link #commit()} after the state
     * refresh, so the user first sees the up-to-date dump and then
     * the history of what happened.
     */
    private final List<String> pendingLogs = new ArrayList<>();

    /**
     * Prompt to run on the next {@link #commit()}, after refresh and
     * logs. Set by the {@code *Turn} handlers when it's our turn.
     * It just sits here until commit fires it: this way the block on
     * stdin happens AFTER we've finished rendering the state (and
     * not in the middle of a bundle).
     */
    private Runnable pendingPrompt;

    /**
     * Flag telling the next {@link #commit()} to also fire
     * {@link #endLatch} (game over). Set inside {@link #gameEnded}.
     * That way the main thread keeps blocking in
     * {@code awaitGameEnd} until the final dump has actually been
     * shown.
     */
    private boolean pendingGameEnded;

    /**
     * Set by {@link #nicknameRejected} so the network client (Rmi_Client
     * / Socket_Client) knows to skip the trailing {@code disconnect()}
     * call after the latch fires. Without this, a rejected duplicate
     * would still ship {@code disconnect(nick)} which on the server
     * would kick the legitimate holder of that nickname out of the
     * lobby/match.
     */
    private boolean connectionRejected;

    /**
     * Callback installed by the network client (Rmi_Client /
     * Socket_Client) that re-sends a {@code connect} to the server
     * using the controller's current {@link #myNickname}. Used by
     * {@link #nicknameRejected(String)} when the rejection is
     * retryable (duplicate nickname, bad syntax) to re-prompt the
     * user instead of closing the session.
     */
    private Runnable retryConnect;

    /**
     * One-shot latch the client's main thread (Rmi_Client.run /
     * Socket_Client.run) uses to park itself until the
     * {@code onGameEnded} event arrives. The network thread calls
     * {@code countDown()} inside {@code gameEnded(...)} which wakes
     * the main thread up. See {@link #awaitGameEnd()}.
     */
    private final CountDownLatch endLatch = new CountDownLatch(1);

    public ClientController(View view) {
        this.view = view;
    }

    /** Called by the adapter (Rmi_Client / Socket_Client) after the nickname prompt. */
    public synchronized void setNickname(String nick) {
        this.myNickname = nick;
    }

    public synchronized String getMyNickname() {
        return myNickname;
    }

    /** Used by the adapter to hand the controller a reference to the server. */
    public synchronized void setVirtualServer(VirtualServer vs) {
        this.virtualServer = vs;
    }

    /**
     * Install the "re-send connect" callback used by
     * {@link #nicknameRejected(String)} to retry with a fresh
     * nickname after a recoverable rejection.
     */
    public synchronized void setRetryConnect(Runnable r) {
        this.retryConnect = r;
    }

    /** Initial nickname prompt; called by the adapter. */
    public String promptNickname() {
        return view.promptNickname();
    }

    /**
     * Blocks the caller until the {@code onGameEnded} event arrives.
     * Used by {@code Rmi_Client.run()} and {@code Socket_Client.run()}
     * to keep the main thread alive during the match without polling.
     * The network thread calls {@code endLatch.countDown()} inside
     * {@link #gameEnded} when the match is over.
     */
    public void awaitGameEnd() throws InterruptedException {
        endLatch.await();
    }

    // === getters for the view (used by refresh) ===

    public synchronized Phase getCurrentPhase()                       { return currentPhase; }
    public synchronized String getCurrentPlayer()                     { return currentPlayer; }
    public synchronized List<String> getJoinedPlayers()               { return new ArrayList<>(joinedPlayers); }
    public synchronized Map<String, TotemColor> getColorsByPlayer()   { return new LinkedHashMap<>(colorsByPlayer); }
    public synchronized Map<String, Integer> getPrestige()            { return new HashMap<>(prestige); }
    public synchronized Map<String, Integer> getFood()                { return new HashMap<>(food); }
    public synchronized Map<String, List<CharacterCard>> getHandsByPlayer()   { return new LinkedHashMap<>(handsByPlayer); }
    public synchronized Map<String, List<BuildingCard>> getBuildingsByPlayer() { return new LinkedHashMap<>(buildingsByPlayer); }
    public synchronized Set<TotemColor> getAvailableColors()          { return new HashSet<>(availableColors); }
    public synchronized int getCurrentUpperBudget()                   { return currentUpperBudget; }
    public synchronized int getCurrentLowerBudget()                   { return currentLowerBudget; }
    public synchronized int getCurrentUpperRemaining()                { return currentUpperRemaining; }
    public synchronized int getCurrentLowerRemaining()                { return currentLowerRemaining; }
    public synchronized int getCurrentRound()                         { return currentRound; }
    public synchronized Era getCurrentEra()                           { return currentEra; }
    public synchronized Board getCurrentBoard()                       { return currentBoard; }
    public synchronized int getExpectedPlayers()                      { return expectedPlayers; }
    public synchronized List<String> getFinalLeaderboard()            { return finalLeaderboard == null ? null : new ArrayList<>(finalLeaderboard); }
    public synchronized List<String> getFinalWinners()                { return finalWinners == null ? null : new ArrayList<>(finalWinners); }

    // ====================================================================
    // Event handlers (called by Rmi_Client / Socket_Client).
    //
    // SIMPLE RULE: every handler only updates the local mini-state and
    // queues any log/prompt. NO handler calls view.refresh /
    // view.displayInfo / view.promptXxx directly. The only render
    // path is {@link #commit()}, triggered by the server at the end
    // of a bundle via {@code v.onCommit()}.
    // ====================================================================

    public synchronized void playerJoined(String nick) {
        if (!joinedPlayers.contains(nick)) {
            joinedPlayers.add(nick);
        }
        pendingLogs.add(nick + " joined the lobby");
    }

    public synchronized void awaitPlayerCount(String firstPlayerNick) {
        this.currentPlayer = firstPlayerNick;
        // The first player doesn't get an initial snapshot (see
        // ServerController.acceptFirstPlayerJoin); without a snapshot
        // they wouldn't know they're in the lobby. So add them
        // manually here.
        if (!joinedPlayers.contains(firstPlayerNick)) {
            joinedPlayers.add(firstPlayerNick);
        }
        if (firstPlayerNick.equals(myNickname)) {
            pendingLogs.add("You are the first player. Pick the number of participants.");
            pendingPrompt = new Runnable() {
                @Override public void run() {
                    final int count = view.promptPlayerCount();
                    sendOrError(new SafeSend() {
                        @Override public void send() throws RemoteException {
                            virtualServer.setPlayerCount(myNickname, count);
                        }
                    });
                }
            };
        } else {
            pendingLogs.add("Waiting for " + firstPlayerNick + " to set the participant count");
        }
    }

    public synchronized void playerCountSet(int count) {
        this.expectedPlayers = count;
        pendingLogs.add("Player count set to " + count);
    }

    public synchronized void awaitMorePlayers(int joined, int total) {
        pendingLogs.add("Waiting for more players (" + joined + "/" + total + ")");
    }

    public synchronized void colorTurn(String nick, Set<TotemColor> available) {
        this.currentPhase = Phase.COLOR_PICKING;
        this.currentPlayer = nick;
        this.availableColors = new HashSet<>(available);
        if (nick.equals(myNickname)) {
            final HashSet<TotemColor> avail = new HashSet<>(available);
            pendingPrompt = new Runnable() {
                @Override public void run() {
                    final TotemColor c = view.promptColor(avail);
                    sendOrError(new SafeSend() {
                        @Override public void send() throws RemoteException {
                            virtualServer.pickColor(myNickname, c);
                        }
                    });
                }
            };
        } else {
            pendingLogs.add("Waiting for " + nick + " to pick a color");
        }
    }

    public synchronized void colorPicked(String nick, TotemColor color) {
        colorsByPlayer.put(nick, color);
        availableColors.remove(color);
        pendingLogs.add(nick + " picked " + color);
    }

    public synchronized void matchStarted(List<String> playerOrder, Era era) {
        this.currentEra = era;
        pendingLogs.add("MATCH STARTED. Era: " + era + ". Order: " + playerOrder);
    }

    public synchronized void eraChanged(Era newEra) {
        this.currentEra = newEra;
        pendingLogs.add("Era changed to " + newEra);
    }

    public synchronized void roundStarted(int round) {
        this.currentRound = round;
        pendingLogs.add("Round " + round + " started");
    }

    public synchronized void offeringTileTurn(String nick, int trackSize) {
        this.currentPhase = Phase.PICK_OFFERING_TILE;
        this.currentPlayer = nick;
        if (nick.equals(myNickname)) {
            final int size = trackSize;
            pendingPrompt = new Runnable() {
                @Override public void run() {
                    final int idx = view.promptOfferingTile(size);
                    sendOrError(new SafeSend() {
                        @Override public void send() throws RemoteException {
                            virtualServer.pickOfferingTile(myNickname, idx);
                        }
                    });
                }
            };
        } else {
            pendingLogs.add("Waiting for " + nick + " to place a totem on the offering track");
        }
    }

    public synchronized void offeringTilePicked(String nick, int tileIndex) {
        pendingLogs.add(nick + " placed a totem on offering tile " + tileIndex);
    }

    public synchronized void characterPickTurn(String nick, int upperBudget, int lowerBudget) {
        this.currentPhase = Phase.TURN_PICK_CARDS_CHARACTER;
        this.currentPlayer = nick;
        this.currentUpperBudget = upperBudget;
        this.currentLowerBudget = lowerBudget;
        if (nick.equals(myNickname)) {
            final int upper = upperBudget;
            final int lower = lowerBudget;
            pendingPrompt = new Runnable() {
                @Override public void run() {
                    final List<Integer> upperIds = view.promptCharacterUpperIds(upper);
                    final List<Integer> lowerIds = view.promptCharacterLowerIds(lower);
                    sendOrError(new SafeSend() {
                        @Override public void send() throws RemoteException {
                            virtualServer.pickCharacterCards(myNickname, upperIds, lowerIds);
                        }
                    });
                }
            };
        } else {
            pendingLogs.add("Waiting for " + nick + " to pick character cards (upper="
                    + upperBudget + ", lower=" + lowerBudget + ")");
        }
    }

    public synchronized void characterCardsPicked(String nick,
                                                  List<CharacterCard> upper,
                                                  List<CharacterCard> lower) {
        List<CharacterCard> hand = handsByPlayer.get(nick);
        if (hand == null) {
            hand = new ArrayList<>();
            handsByPlayer.put(nick, hand);
        }
        hand.addAll(upper);
        hand.addAll(lower);
        int total = upper.size() + lower.size();
        pendingLogs.add(nick + " drew " + total + " character card(s)");
    }

    public synchronized void buildingPickTurn(String nick, int upperRemaining, int lowerRemaining) {
        this.currentPhase = Phase.TURN_PICK_CARDS_BUILDING;
        this.currentPlayer = nick;
        this.currentUpperRemaining = upperRemaining;
        this.currentLowerRemaining = lowerRemaining;
        if (nick.equals(myNickname)) {
            final int uRem = upperRemaining;
            final int lRem = lowerRemaining;
            pendingPrompt = new Runnable() {
                @Override public void run() {
                    final List<Integer> upperIds = view.promptBuildingUpperIds(uRem);
                    final List<Integer> lowerIds = view.promptBuildingLowerIds(lRem);
                    sendOrError(new SafeSend() {
                        @Override public void send() throws RemoteException {
                            virtualServer.pickBuildingCards(myNickname, upperIds, lowerIds);
                        }
                    });
                }
            };
        } else {
            pendingLogs.add("Waiting for " + nick + " to buy buildings (residual upper="
                    + upperRemaining + ", lower=" + lowerRemaining + ")");
        }
    }

    public synchronized void buildingCardsBought(String nick,
                                                 List<BuildingCard> upper,
                                                 List<BuildingCard> lower) {
        List<BuildingCard> owned = buildingsByPlayer.get(nick);
        if (owned == null) {
            owned = new ArrayList<>();
            buildingsByPlayer.put(nick, owned);
        }
        owned.addAll(upper);
        owned.addAll(lower);
        int total = upper.size() + lower.size();
        pendingLogs.add(nick + " bought " + total + " building(s)");
    }

    public synchronized void extraPickTurn(String nick) {
        this.currentPhase = Phase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING;
        this.currentPlayer = nick;
        if (nick.equals(myNickname)) {
            pendingPrompt = new Runnable() {
                @Override public void run() {
                    View.ExtraPickChoice choice = view.promptExtraCard();
                    final Integer id = choice.cardId;
                    final boolean isChar = choice.isCharacter;
                    sendOrError(new SafeSend() {
                        @Override public void send() throws RemoteException {
                            virtualServer.pickExtraCard(myNickname, id, isChar);
                        }
                    });
                }
            };
        } else {
            pendingLogs.add(nick + " has the extra pick (special building bonus)");
        }
    }

    public synchronized void extraCardPicked(String nick, Integer cardId, boolean isCharacter) {
        if (cardId == null) {
            pendingLogs.add(nick + " skipped the extra pick");
        } else {
            pendingLogs.add(nick + " did extra pick: "
                    + (isCharacter ? "character" : "building") + " id=" + cardId);
        }
    }

    public synchronized void prestigeChanged(String nick, int newPrestige) {
        Integer prev = prestige.put(nick, newPrestige);
        // Only log the change if the value ACTUALLY changed from a
        // known previous one. We skip:
        //   - prev == null   : first broadcast (e.g. the startMatch
        //                      initial bundle with prestige=0/food=0).
        //                      The user sees the value in the dump
        //                      anyway, no need for an "X prestige -> 0".
        //   - prev == new    : the server emits several scoring passes
        //                      and sometimes the second one doesn't
        //                      change anything.
        if (prev != null && prev != newPrestige) {
            pendingLogs.add(nick + " prestige -> " + newPrestige);
        }
    }

    public synchronized void foodChanged(String nick, int newFood) {
        Integer prev = food.put(nick, newFood);
        if (prev != null && prev != newFood) {
            pendingLogs.add(nick + " food -> " + newFood);
        }
    }

    public synchronized void roundEnded() {
        pendingLogs.add("Round ended");
    }

    public synchronized void gameEnded(List<String> leaderboard, List<String> winners) {
        this.currentPhase = Phase.GAME_ENDED;
        this.finalLeaderboard = leaderboard;
        this.finalWinners = winners;
        pendingLogs.add("Game ended. Winners: " + winners);
        pendingGameEnded = true;
        // NB: the latch countDown is moved to commit(), so the main
        // thread doesn't exit awaitGameEnd before the final dump has
        // actually been printed.
    }

    public synchronized void matchPaused(String reason) {
        this.currentPhase = Phase.PAUSED_WAITING_RECONNECT;
        pendingLogs.add("Match paused: " + reason);
    }

    public synchronized void matchResumed() {
        pendingLogs.add("Match resumed.");
    }

    public synchronized void playerDisconnected(String nick) {
        pendingLogs.add(nick + " disconnected.");
    }

    public synchronized void playerReconnected(String nick) {
        pendingLogs.add(nick + " reconnected.");
    }

    public synchronized void actionRejected(String reason) {
        // Just stash it: the error gets printed by the next
        // {@link #commit()} (in the "displayError" step), after the
        // state dump and before the prompt.
        this.pendingError = reason;
    }

    /**
     * The client's connect attempt was rejected by the server.
     * Special case: print it RIGHT NOW (no commit) and trigger the
     * latch, because this is a session terminator and no commit will
     * follow.
     */
    public synchronized void nicknameRejected(String reason) {
        view.displayError(reason);
        // Retryable cases (duplicate nickname, bad syntax): the server
        // is still accepting connects, just not THIS nickname. Prompt
        // for a different one and re-send connect via the adapter's
        // installed callback. We stay synchronized while prompting:
        // no other event can usefully race in (server is waiting on
        // the next connect from us), and any incoming callback after
        // the retry just queues on the monitor.
        if (isRetryableConnectError(reason) && retryConnect != null) {
            view.displayInfo("Please choose a different nickname.");
            String newNick = view.promptNickname();
            this.myNickname = newNick;
            try {
                retryConnect.run();
            } catch (RuntimeException e) {
                view.displayError("Reconnect failed: " + e.getMessage());
                this.connectionRejected = true;
                endLatch.countDown();
            }
            return;
        }
        view.displayInfo("Closing connection.");
        this.connectionRejected = true;
        endLatch.countDown();
    }

    /**
     * True for connect rejections the user can fix by trying again
     * with a different nickname (e.g. "nickname already in use",
     * "nickname too long (max 16)"). False for phase-level rejections
     * such as "no game currently accepting players" or "cannot join
     * right now", which mean the session has to close.
     */
    private static boolean isRetryableConnectError(String reason) {
        return reason != null && reason.startsWith("nickname");
    }

    /**
     * True iff the server refused our connect attempt (duplicate
     * nickname, no game accepting, etc.). Network clients check this
     * after {@link #awaitGameEnd()} to skip the trailing
     * {@code disconnect()} call: shipping a disconnect for a nickname
     * we never officially held would kick the legitimate holder out.
     */
    public synchronized boolean wasConnectionRejected() {
        return connectionRejected;
    }

    /**
     * Quietly updates the board mini-state (offering track,
     * upper/lower row, turn order tile) with what came from the
     * server. Does NOT call view.refresh(): the full dump is done
     * by the following {@code *Turn} event that comes right after
     * the board broadcast in the server controller. This way the
     * game-state render shows up with the right phase AND a fresh
     * board.
     */
    public synchronized void boardSnapshot(Board board) {
        this.currentBoard = board;
    }

    /**
     * End-of-bundle marker from the server. Runs, in order:
     *   1. {@code view.refresh(this)} -- the one and only place
     *      where the client prints the GAME STATE dump.
     *   2. flush of {@link #pendingLogs} accumulated by the bundle's
     *      handlers (one per line, via {@code displayInfo}).
     *   3. any {@link #pendingError} via {@code displayError}.
     *   4. any {@link #pendingPrompt}: blocks on stdin while still
     *      holding the controller monitor (same semantics as the
     *      old prompt-inside-handler approach).
     *
     * All the {@code onXxx} handlers just update state and queue
     * logs/prompts; commit is the only render path. See
     * {@code VirtualView#onCommit}.
     */
    public synchronized void commit() {
        view.refresh(this);
        for (String log : pendingLogs) {
            view.displayInfo(log);
        }
        pendingLogs.clear();
        String err = pendingError;
        pendingError = null;
        if (err != null) {
            view.displayError(err);
        }
        Runnable p = pendingPrompt;
        pendingPrompt = null;
        if (p != null) {
            p.run();
        }
        if (pendingGameEnded) {
            pendingGameEnded = false;
            endLatch.countDown();
        }
    }

    /**
     * Fully rebuilds the mini-state from the received snapshot. Used
     * both on initial client arrival and on reconnect.
     */
    public synchronized void gameSnapshot(GameSnapshot s) {
        this.currentPhase = s.getCurrentPhase();
        this.currentPlayer = s.getCurrentPlayer();
        this.joinedPlayers.clear();
        if (s.getPlayerOrder() != null) {
            this.joinedPlayers.addAll(s.getPlayerOrder());
        }
        this.colorsByPlayer.clear();
        if (s.getColors() != null) {
            this.colorsByPlayer.putAll(s.getColors());
        }
        this.prestige.clear();
        if (s.getPrestige() != null) {
            this.prestige.putAll(s.getPrestige());
        }
        this.food.clear();
        if (s.getFood() != null) {
            this.food.putAll(s.getFood());
        }
        this.handsByPlayer.clear();
        if (s.getHands() != null) {
            this.handsByPlayer.putAll(s.getHands());
        }
        this.buildingsByPlayer.clear();
        if (s.getBuildings() != null) {
            this.buildingsByPlayer.putAll(s.getBuildings());
        }
        this.currentBoard = s.getBoard();
        this.currentEra = s.getEra();
        this.currentRound = s.getRound();
        this.currentUpperBudget = s.getUpperBudget();
        this.currentLowerBudget = s.getLowerBudget();
        this.currentUpperRemaining = s.getUpperRemaining();
        this.currentLowerRemaining = s.getLowerRemaining();
        this.availableColors = s.getAvailableColors() != null
                ? new HashSet<>(s.getAvailableColors()) : new HashSet<TotemColor>();
        this.expectedPlayers = s.getExpectedPlayers();
        this.finalLeaderboard = s.getLeaderboard();
        this.finalWinners = s.getWinners();
        // NB: the dump comes from the {@link #commit()} that the
        // server appends after onGameSnapshot. Here we just swap the
        // state.
    }

    // ====================================================================
    // Internal helper: send with RemoteException catch
    // ====================================================================

    private interface SafeSend {
        void send() throws RemoteException;
    }

    private void sendOrError(SafeSend action) {
        try {
            action.send();
        } catch (RemoteException e) {
            view.displayError("Send failed: " + e.getMessage());
        }
    }
}
