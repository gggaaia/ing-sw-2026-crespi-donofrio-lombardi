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
import java.util.concurrent.CancellationException;

/**
 *  Client-side controller. Keeps a local mini-state that mirrors what
 *  the server told us, updated event by event.
 *
 *  Threading model (kept simple):
 *  - every public method is synchronized
 *  - event methods (called from the RMI / Socket network thread)
 *  mutate the state and trigger view.refresh()
 *  - the *Turn methods, when it's our turn, BLOCK on view.promptXxx()
 *  and then push the action through virtualServer
 *  - the outgoing network call (virtualServer.xxx()) happens inside
 *  the synchronized method: the lock is held, but the call is
 *  fast both on RMI (microseconds for the dispatch) and on socket
 *  (writeObject into the OS buffer)
 *  *
 * "Block" on the prompt means that, while the user is thinking,
 *  incoming events from the server just queue up in the socket buffer
 *  / RMI thread pool. They get processed as soon as the user picks.
 *
 */
public class ClientController {

    private final View view;
    private String myNickname;
    private VirtualServer virtualServer;

    // === Local mini-state ===
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
    private Map<String, Integer> globalLeaderboard;

    /**
     * Server-side error (e.g. "tile already occupied") waiting to be
     * shown. We just stash it in {@link #actionRejected} without
     * printing it right away: the next {@link #commit()} flushes it
     * AFTER the state dump and right before the prompt.
     */
    private String pendingError;
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
    private final CountDownLatch endLatch = new CountDownLatch(1);

    /**
     * Constructs a new ClientController.
     *
     * @param view The view implementation (CLI or GUI) attached to this client.
     */
    public ClientController(View view) {
        this.view = view;
    }

    /**
     * Sets the nickname of the current player.
     * Called by the adapter (Rmi_Client / Socket_Client)
     * after the nickname prompt.
     * @param nick The chosen nickname.
     */
    public synchronized void setNickname(String nick) {
        this.myNickname = nick;
    }

    /**
     * Retrieves the nickname of the current player.
     *
     * @return The current player's nickname.
     */
    public synchronized String getMyNickname() {
        return myNickname;
    }

    /**
     * Sets the remote virtual server reference to allow outward communication.
     *
     * @param vs The virtual server stub.
     */
    public synchronized void setVirtualServer(VirtualServer vs) {
        this.virtualServer = vs;
    }

    /**
     * Install the "re-send connect" callback used by
     *      * {@link #nicknameRejected(String)} to retry with a fresh
     *      * nickname after a recoverable rejection
     */
    public synchronized void setRetryConnect(Runnable r) {
        this.retryConnect = r;
    }

    /**
     * Prompts the user for a nickname using the underlying View.
     * Called automatically by the network adapter upon connection start.
     *
     * @return The chosen nickname string.
     */
    public String promptNickname() {
        String nick = view.promptNickname();
        // Immediately save the nickname locally to avoid Race Conditions with RMI callbacks
        if (nick != null) {
            setNickname(nick);
        }
        return nick;
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

    // === Getters for the View (used during refresh) ===

    /** @return The current phase of the match. */
    public synchronized Phase getCurrentPhase()                       { return currentPhase; }
    /** @return The nickname of the player whose turn is currently active. */
    public synchronized String getCurrentPlayer()                     { return currentPlayer; }
    /** @return A copy of the list of currently joined players. */
    public synchronized List<String> getJoinedPlayers()               { return new ArrayList<>(joinedPlayers); }
    /** @return A copy of the color assignments for each player. */
    public synchronized Map<String, TotemColor> getColorsByPlayer()   { return new LinkedHashMap<>(colorsByPlayer); }
    /** @return A copy of the prestige points mapped by player nickname. */
    public synchronized Map<String, Integer> getPrestige()            { return new HashMap<>(prestige); }
    /** @return A copy of the food tokens mapped by player nickname. */
    public synchronized Map<String, Integer> getFood()                { return new HashMap<>(food); }
    /** @return A copy of the character cards owned by each player. */
    public synchronized Map<String, List<CharacterCard>> getHandsByPlayer()   { return new LinkedHashMap<>(handsByPlayer); }
    /** @return A copy of the building cards constructed by each player. */
    public synchronized Map<String, List<BuildingCard>> getBuildingsByPlayer() { return new LinkedHashMap<>(buildingsByPlayer); }
    /** @return A set containing the currently unassigned totem colors. */
    public synchronized Set<TotemColor> getAvailableColors()          { return new HashSet<>(availableColors); }
    /** @return The upper row budget for the active player. */
    public synchronized int getCurrentUpperBudget()                   { return currentUpperBudget; }
    /** @return The lower row budget for the active player. */
    public synchronized int getCurrentLowerBudget()                   { return currentLowerBudget; }
    /** @return The remaining upper row picks for the active player. */
    public synchronized int getCurrentUpperRemaining()                { return currentUpperRemaining; }
    /** @return The remaining lower row picks for the active player. */
    public synchronized int getCurrentLowerRemaining()                { return currentLowerRemaining; }
    /** @return The current round number. */
    public synchronized int getCurrentRound()                         { return currentRound; }
    /** @return The current era (Age). */
    public synchronized Era getCurrentEra()                           { return currentEra; }
    /** @return The most recent snapshot of the game board. */
    public synchronized Board getCurrentBoard()                       { return currentBoard; }
    /** @return The total expected player count set by the first player. */
    public synchronized int getExpectedPlayers()                      { return expectedPlayers; }
    /** @return A copy of the final leaderboard, or null if the game hasn't ended. */
    public synchronized List<String> getFinalLeaderboard()            { return finalLeaderboard == null ? null : new ArrayList<>(finalLeaderboard); }
    /** @return A copy of the final winners list, or null if the game hasn't ended. */
    public synchronized List<String> getFinalWinners()                { return finalWinners == null ? null : new ArrayList<>(finalWinners); }
    /**
     * Returns a copy of the global leaderboard.
     * Uses a {@link LinkedHashMap} to preserve the ranking order.
     *
     * @return an ordered copy of the leaderboard map.
     */
    public synchronized Map<String, Integer> getGlobalLeaderboard()        { return new LinkedHashMap<>(globalLeaderboard); }

    // ====================================================================
    // Event handlers (called by Rmi_Client / Socket_Client).
    //
    // SIMPLE RULE: every handler only updates the local mini-state and
    // queues any log/prompt. NO handler calls view.refresh /
    // view.displayInfo / view.promptXxx directly. The only render
    // path is {@link #commit()}, triggered by the server at the end
    // of a bundle via {@code v.onCommit()}.
    // ====================================================================
    /**
     * Handles a notification that a new player has joined the lobby.
     *
     * @param nick The nickname of the new player.
     */
    public synchronized void playerJoined(String nick) {
        if (!joinedPlayers.contains(nick)) {
            joinedPlayers.add(nick);
        }
        pendingLogs.add(nick + " joined the lobby");
    }

    /**
     * Prompts the first player to provide the total number of players for the match.
     *
     * @param firstPlayerNick The nickname of the first player to join.
     */
    public synchronized void awaitPlayerCount(String firstPlayerNick) {
        this.currentPhase = Phase.WAITING_FOR_PLAYER_COUNT;
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
                    try {
                        final int count = view.promptPlayerCount();
                        sendOrError(new SafeSend() {
                            @Override public void send() throws RemoteException {
                                virtualServer.setPlayerCount(myNickname, count);
                            }
                        });
                    } catch (CancellationException e) {
                        System.out.println("LOG: Player canceled game creation. Disconnecting...");
                        sendOrError(new SafeSend() {
                            @Override public void send() throws RemoteException {
                                virtualServer.disconnect(myNickname);
                            }
                        });
                        connectionRejected = true;
                        endLatch.countDown();
                    }
                }
            };
        } else {
            pendingLogs.add("Waiting for " + firstPlayerNick + " to set the participant count");
        }
    }

    /**
     * Notifies the client that the player count has been officially set.
     *
     * @param count The expected player count.
     */
    public synchronized void playerCountSet(int count) {
        this.expectedPlayers = count;
        pendingLogs.add("Player count set to " + count);
    }

    /**
     * Sets the state to waiting for remaining players to join the lobby.
     *
     * @param joined The number of currently connected players.
     * @param total The total expected number of players.
     */
    public synchronized void awaitMorePlayers(int joined, int total) {
        this.currentPhase = Phase.WAITING_FOR_PLAYERS;
        pendingLogs.add("Waiting for more players (" + joined + "/" + total + ")");
    }

    /**
     * Prompts the active player to choose their Totem color.
     *
     * @param nick The nickname of the active player.
     * @param available The set of colors currently available for selection.
     */
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

    /**
     * Notifies the client that a specific color was chosen by a player.
     *
     * @param nick The player who picked the color.
     * @param color The chosen color.
     */
    public synchronized void colorPicked(String nick, TotemColor color) {
        colorsByPlayer.put(nick, color);
        availableColors.remove(color);
        pendingLogs.add(nick + " picked " + color);
    }

    /**
     * Notifies the client that the match setup is complete and the game has started.
     *
     * @param playerOrder The initial turn order list.
     * @param era The starting era.
     */
    public synchronized void matchStarted(List<String> playerOrder, Era era) {
        this.currentEra = era;
        pendingLogs.add("MATCH STARTED. Era: " + era + ". Order: " + playerOrder);
    }

    /**
     * Updates the current Era of the game.
     *
     * @param newEra The new Era.
     */
    public synchronized void eraChanged(Era newEra) {
        this.currentEra = newEra;
        pendingLogs.add("Era changed to " + newEra);
    }

    /**
     * Signals the start of a new round.
     *
     * @param round The round number.
     */
    public synchronized void roundStarted(int round) {
        this.currentRound = round;
        pendingLogs.add("Round " + round + " started");
    }

    /**
     * Prompts the active player to place their totem on an offering tile.
     *
     * @param nick The nickname of the active player.
     * @param trackSize The maximum valid index on the offering track.
     */
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

    /**
     * Logs the placement of a totem by a player on an offering tile.
     *
     * @param nick The player's nickname.
     * @param tileIndex The chosen offering track index.
     */
    public synchronized void offeringTilePicked(String nick, int tileIndex) {
        pendingLogs.add(nick + " placed a totem on offering tile " + tileIndex);
    }

    /**
     * Prompts the active player to pick character cards within their designated budget.
     *
     * @param nick The active player.
     * @param upperBudget The maximum number of upper row character cards allowed.
     * @param lowerBudget The maximum number of lower row character cards allowed.
     */
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

    /**
     * Logs the character cards successfully drawn by a player.
     *
     * @param nick The player's nickname.
     * @param upper The list of upper row character cards picked.
     * @param lower The list of lower row character cards picked.
     */
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

    /**
     * Prompts the active player to purchase building cards.
     *
     * @param nick The active player.
     * @param upperRemaining Upper row allowance.
     * @param lowerRemaining Lower row allowance.
     */
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

    /**
     * Logs the building cards successfully constructed by a player.
     *
     * @param nick The player's nickname.
     * @param upper The list of upper row buildings constructed.
     * @param lower The list of lower row buildings constructed.
     */
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

    /**
     * Prompts the player to perform a bonus extra pick action.
     *
     * @param nick The active player.
     */
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

    /**
     * Logs the completion of an extra pick turn.
     *
     * @param nick The player who made the pick.
     * @param cardId The ID of the chosen card, or null if skipped.
     * @param isCharacter True if it's a character card, false if it's a building.
     */
    public synchronized void extraCardPicked(String nick, Integer cardId, boolean isCharacter) {
        if (cardId == null) {
            pendingLogs.add(nick + " skipped the extra pick");
        } else {
            pendingLogs.add(nick + " did extra pick: "
                    + (isCharacter ? "character" : "building") + " id=" + cardId);
        }
    }

    /**
     * Updates and logs a player's new prestige point total.
     *
     * @param nick The target player.
     * @param newPrestige The updated prestige value.
     */
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

    /**
     * Updates and logs a player's new food token total.
     *
     * @param nick The target player.
     * @param newFood The updated food value.
     */
    public synchronized void foodChanged(String nick, int newFood) {
        Integer prev = food.put(nick, newFood);
        if (prev != null && prev != newFood) {
            pendingLogs.add(nick + " food -> " + newFood);
        }
    }

    /** Logs the conclusion of the current game round. */
    public synchronized void roundEnded() {
        pendingLogs.add("Round ended");
    }

    /**
     * Notifies the client that the game has officially concluded.
     *
     * @param leaderboard The final ordered list of players.
     * @param winners The list of winners.
     */

    public synchronized void gameEnded(List<String> leaderboard, List<String> winners, Map<String, Integer> globalLeaderboard) {
        this.currentPhase = Phase.GAME_ENDED;
        this.finalLeaderboard = leaderboard;
        this.finalWinners = winners;
        this.globalLeaderboard = globalLeaderboard;
        pendingLogs.add("Game ended. Winners: " + winners);
        pendingGameEnded = true;
    }

    /**
     * Notifies the client that the match is currently paused due to network issues.
     *
     * @param reason The reason for pausing (e.g., player disconnected).
     */
    public synchronized void matchPaused(String reason) {
        this.currentPhase = Phase.PAUSED_WAITING_RECONNECT;
        pendingLogs.add("Match paused: " + reason);
    }

    /** Resumes a previously paused match. */
    public synchronized void matchResumed() {
        pendingLogs.add("Match resumed.");
    }

    /**
     * Notifies that another player has disconnected.
     *
     * @param nick The nickname of the disconnected player.
     */
    public synchronized void playerDisconnected(String nick) {
        joinedPlayers.remove(nick);
        pendingLogs.add(nick + " disconnected.");
    }

    /**
     * Notifies that a previously disconnected player has rejoined the match.
     *
     * @param nick The nickname of the reconnected player.
     */
    public synchronized void playerReconnected(String nick) {
        pendingLogs.add(nick + " reconnected.");
    }

    /**
     * Queues an error notification to be displayed to the user when their
     * last action was rejected by the server logic.
     *
     * @param reason The descriptive error reason.
     */
    public synchronized void actionRejected(String reason) {
        this.pendingError = reason;
    }

    /**
     * The client's connect attempt was rejected by the server.
     * We show the error right now (no commit follows). If the user can
     * fix it by picking another name we ask again and retry; otherwise
     * we close the session.
     */
    public synchronized void nicknameRejected(String reason) {
        view.displayError(reason);
        // Retryable cases (duplicate nickname, bad syntax): the server
        // is still accepting connects, just not THIS nickname. Ask for
        // a different one and re-send connect.
        if (isRetryableConnectError(reason) && retryConnect != null) {
            // Do the asking + re-sending on a NEW thread and return
            // from here right away.
            //
            // This method runs inside a callback from the server. On
            // RMI the server is waiting for this callback to finish. If
            // we asked the user for a new name and re-sent connect
            // here, that connect could cause another rejection callback
            // that needs this same lock we are still holding, so the
            // two sides would wait for each other forever (you type a
            // name and nothing happens). Returning first frees both
            // sides, so the next try works.
            final Runnable retry = retryConnect;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    view.displayInfo("Please choose a different nickname.");
                    String newNick = view.promptNickname();
                    setNickname(newNick);
                    try {
                        retry.run();
                    } catch (RuntimeException e) {
                        view.displayError("Reconnect failed: " + e.getMessage());
                        synchronized (ClientController.this) {
                            connectionRejected = true;
                        }
                        endLatch.countDown();
                    }
                }
            }, "nickname-retry");
            t.setDaemon(true);
            t.start();
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
     * True if the server refused our connect attempt (duplicate
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
     * Safely applies and propagates all accumulated state changes to the {@link View}.
     * <p>
     * This method acts as a synchronization point (barrier) between the network thread
     * (which receives messages from the server and silently updates the local state) and the user
     * interface thread (such as the JavaFX Application Thread or the CLI thread).
     * </p>
     * <p>
     * <b>Deadlock Prevention:</b><br>
     * The architecture of this method is specifically designed to avoid critical deadlocks.
     * The acquisition of pending data (errors, logs, prompts, and game-end state) occurs
     * within an extremely fast {@code synchronized} block.
     * Immediately after, calls that update the UI or require blocking user interaction
     * are executed <i>outside</i> the synchronization lock.
     * This ensures the Controller remains free and accessible even while the UI is waiting for user input.
     * </p>
     * The operations are executed in the following strict order:
     * <ol>
     * <li><b>Thread-Safe Acquisition and Reset:</b> Immediate reading and clearing of the log queues,
     * any pending error, the prompt to be executed, and the game-ended flag.</li>
     * <li><b>UI Update:</b> Calls {@code view.refresh(this)} to notify the View
     * to reload the screen based on the newly updated Controller state.</li>
     * <li><b>Visual Notifications:</b> Sequential printing of all accumulated logs and display
     * of any pending error popups.</li>
     * <li><b>Execution of Pending Prompts:</b> Starts the prompt's {@link Runnable} (e.g., choosing a Nickname
     * or a Totem). By running this outside the synchronized block, the user can take as much time
     * as needed to respond without freezing the client's network receiver thread.</li>
     * <li><b>Shutdown Resolution:</b> If the game-ended or connection-rejected flag was raised,
     * decrements the {@code endLatch}, unblocking the main thread for a clean application shutdown.</li>
     * </ol>
     */

    public void commit() {
        Runnable p;
        boolean ended;
        String err;
        List<String> logs;

        synchronized (this) {
            err = pendingError;
            pendingError = null;

            p = pendingPrompt;
            pendingPrompt = null;

            ended = pendingGameEnded;
            pendingGameEnded = false;

            logs = new ArrayList<>(pendingLogs);
            pendingLogs.clear();
        }

        view.refresh(this);
        for (String log : logs) {
            view.displayInfo(log);
        }
        if (err != null) {
            view.displayError(err);
        }

        if (p != null) {
            p.run();
        }
        if (ended) {
            endLatch.countDown();
        }
    }

    /**
     * Reconstructs the entire client controller state from a complete server snapshot.
     * Useful for recovering from disconnections.
     *
     * @param s The complete GameSnapshot object.
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
    }
    // ====================================================================
    // Internal helper: send with RemoteException catch
    // ====================================================================
    private interface SafeSend {
        void send() throws RemoteException;
    }

    /**
     * Wrapper logic to catch RMI exceptions when communicating with the Server
     * and forwarding them to the View's displayError.
     *
     * @param action The specific network transmission block to run.
     */
    private void sendOrError(SafeSend action) {
        try {
            action.send();
        } catch (RemoteException e) {
            view.displayError("Send failed: " + e.getMessage());
        }
    }

    /**
     * Disconnects the user from the current lobby or match without terminating the network thread.
     * <p>
     * This "soft" leave operation ensures that the underlying network worker remains active and parked,
     * ready to handle a new game session without requiring an application restart. It performs the following key actions:
     * <ul>
     * <li>Sends a formal disconnection request to the server.</li>
     * <li>Completely resets the local client memory (clearing player lists, expected counts, chosen colors,
     * and pending events) to prevent "ghost" data from bleeding into the next match.</li>
     * <li>Intentionally avoids triggering the end-of-game latch ({@code endLatch}), ensuring the client
     * application remains alive and fully responsive to new GUI commands.</li>
     * </ul>
     * </p>
     */
    public synchronized void leaveLobbySoft() {
        System.out.println("LOG: Player manually left the lobby. Disconnecting...");
        sendOrError(new SafeSend() {
            @Override public void send() throws RemoteException {
                virtualServer.disconnect(myNickname);
            }
        });

        this.joinedPlayers.clear();
        this.expectedPlayers = 0;
        this.currentPlayer = null;
        this.currentPhase = Phase.WAITING_FOR_NICKNAME;
        this.colorsByPlayer.clear();
        this.availableColors.clear();
        this.pendingLogs.clear();
        this.pendingError = null;
        this.pendingPrompt = null;
    }


    /**
     * Utilizes the network retry callback to simulate a new login attempt
     * without having to recreate the RMI or Socket connection from scratch.
     * * @param newNickname The updated nickname to try.
     */
    public synchronized void reconnectNewGame(String newNickname) {
        this.myNickname = newNickname;
        if (retryConnect != null) {
            System.out.println("LOG: Reconnecting to server with new nickname: " + newNickname);
            retryConnect.run();
        }
    }
}