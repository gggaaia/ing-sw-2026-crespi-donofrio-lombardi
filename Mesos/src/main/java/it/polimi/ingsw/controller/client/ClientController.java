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
 * Client-side controller.
 * <p>
 * This class acts as the central hub for the client application. It maintains a local
 * "mini-state" that mirrors the server's game state, updated incrementally via incoming events.
 * </p>
 * <p>
 * <b>Threading Model:</b>
 * <ul>
 * <li>Every public method is synchronized to guarantee thread safety between network events and UI updates.</li>
 * <li>Event methods (invoked by the RMI or Socket network thread) mutate the internal state and trigger a view refresh.</li>
 * <li>The {@code *Turn} methods (when it is this client's turn) BLOCK on {@code view.promptXxx()} to wait for human input,
 * then push the chosen action through the {@link VirtualServer}.</li>
 * <i>Note on Blocking:</i> While the client blocks on a UI prompt waiting for the user to think, incoming server
 * events queue up securely in the network buffer and will be processed sequentially once the user completes their action.
 * </p>
 */
public class ClientController {

    private final View view;
    private String myNickname;
    private VirtualServer virtualServer;

    // --- Local Mini-State ---
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

    /** Keeps track of players who have disconnected but are still officially part of the match. */
    private final Set<String> disconnectedPlayers = new HashSet<>();

    /**
     * Stores a server-side error (e.g., "tile already occupied") waiting to be displayed.
     * Stashed here to avoid interrupting the UI flow; the next {@link #commit()} flushes it
     * exactly when the screen is ready to prompt the user again.
     */
    private String pendingError;
    private final List<String> pendingLogs = new ArrayList<>();

    /**
     * The specific prompt action to run on the next {@link #commit()}, after the view refreshes.
     * Prevents blocking the thread mid-update and ensures prompts are rendered cleanly.
     */
    private Runnable pendingPrompt;

    /** Flag indicating that the next {@link #commit()} should release the game-over latch. */
    private boolean pendingGameEnded;

    /**
     * Prevents the network client from sending a trailing {@code disconnect()} command
     * if the initial connection was rejected (e.g., duplicate nickname).
     */
    private boolean connectionRejected;

    /**
     * Callback provided by the network layer to retry a connection attempt
     * if a recoverable error (like a taken nickname) occurs.
     */
    private Runnable retryConnect;

    private final CountDownLatch endLatch = new CountDownLatch(1);

    /**
     * Constructs a new ClientController.
     *
     * @param view The graphical or textual view interface attached to this client.
     */
    public ClientController(View view) {
        this.view = view;
    }

    /**
     * Assigns the nickname for the local player.
     *
     * @param nick The chosen nickname.
     */
    public synchronized void setNickname(String nick) {
        this.myNickname = nick;
    }

    /**
     * Retrieves the nickname of the local player.
     *
     * @return The local player's nickname.
     */
    public synchronized String getMyNickname() {
        return myNickname;
    }

    /**
     * Links the remote server stub allowing outward communication.
     *
     * @param vs The virtual server interface.
     */
    public synchronized void setVirtualServer(VirtualServer vs) {
        this.virtualServer = vs;
    }

    /**
     * Installs the callback used to retry a connection to the server.
     * * @param r The runnable logic handling the reconnection.
     */
    public synchronized void setRetryConnect(Runnable r) {
        this.retryConnect = r;
    }

    /**
     * Uses the View to prompt the user for their desired nickname upon startup.
     *
     * @return The chosen nickname string.
     */
    public String promptNickname() {
        String nick = view.promptNickname();
        if (nick != null) {
            setNickname(nick);
        }
        return nick;
    }

    /**
     * Blocks the main execution thread until the match officially ends.
     * Used by network runners to keep the application alive without aggressive polling.
     *
     * @throws InterruptedException if the waiting thread is interrupted.
     */
    public void awaitGameEnd() throws InterruptedException {
        endLatch.await();
    }

    // ====================================================================
    // GETTERS FOR VIEW REFRESH RENDERING
    // ====================================================================

    /** @return The current phase of the match. */
    public synchronized Phase getCurrentPhase()                       { return currentPhase; }
    /** @return The nickname of the player whose turn is active. */
    public synchronized String getCurrentPlayer()                     { return currentPlayer; }
    /** @return A copy of the list containing all joined players. */
    public synchronized List<String> getJoinedPlayers()               { return new ArrayList<>(joinedPlayers); }
    /** @return A copy of the color assignments mapped by player nickname. */
    public synchronized Map<String, TotemColor> getColorsByPlayer()   { return new LinkedHashMap<>(colorsByPlayer); }
    /** @return A copy of the prestige points mapped by player nickname. */
    public synchronized Map<String, Integer> getPrestige()            { return new HashMap<>(prestige); }
    /** @return A copy of the food tokens mapped by player nickname. */
    public synchronized Map<String, Integer> getFood()                { return new HashMap<>(food); }
    /** @return A copy of character cards owned, mapped by player nickname. */
    public synchronized Map<String, List<CharacterCard>> getHandsByPlayer()   { return new LinkedHashMap<>(handsByPlayer); }
    /** @return A copy of building cards constructed, mapped by player nickname. */
    public synchronized Map<String, List<BuildingCard>> getBuildingsByPlayer() { return new LinkedHashMap<>(buildingsByPlayer); }
    /** @return A set of totem colors currently unassigned. */
    public synchronized Set<TotemColor> getAvailableColors()          { return new HashSet<>(availableColors); }
    /** @return The current allowed character card draws from the upper row. */
    public synchronized int getCurrentUpperBudget()                   { return currentUpperBudget; }
    /** @return The current allowed character card draws from the lower row. */
    public synchronized int getCurrentLowerBudget()                   { return currentLowerBudget; }
    /** @return The remaining upper row building purchases allowed. */
    public synchronized int getCurrentUpperRemaining()                { return currentUpperRemaining; }
    /** @return The remaining lower row building purchases allowed. */
    public synchronized int getCurrentLowerRemaining()                { return currentLowerRemaining; }
    /** @return The current numeric round. */
    public synchronized int getCurrentRound()                         { return currentRound; }
    /** @return The active gameplay Era. */
    public synchronized Era getCurrentEra()                           { return currentEra; }
    /** @return The latest data snapshot of the game board. */
    public synchronized Board getCurrentBoard()                       { return currentBoard; }
    /** @return The total participant count required to start the match. */
    public synchronized int getExpectedPlayers()                      { return expectedPlayers; }
    /** @return A copy of the final endgame leaderboard. */
    public synchronized List<String> getFinalLeaderboard()            { return finalLeaderboard == null ? null : new ArrayList<>(finalLeaderboard); }
    /** @return A copy of the winning players list. */
    public synchronized List<String> getFinalWinners()                { return finalWinners == null ? null : new ArrayList<>(finalWinners); }

    /**
     * Retrieves an ordered copy of the global historical leaderboard.
     * Uses a LinkedHashMap to ensure ranking consistency.
     *
     * @return The global database leaderboard map.
     */
    public synchronized Map<String, Integer> getGlobalLeaderboard() {
        if (globalLeaderboard == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(globalLeaderboard);
    }

    /**
     * Retrieves a set of players who are temporarily disconnected.
     * * @return A set of disconnected player nicknames.
     */
    public synchronized Set<String> getDisconnectedPlayers() {
        return new HashSet<>(disconnectedPlayers);
    }

    // ====================================================================
    // INCOMING NETWORK EVENT HANDLERS
    // ====================================================================

    /**
     * Registers a newly joined player into the lobby.
     *
     * @param nick The joining player's nickname.
     */
    public synchronized void playerJoined(String nick) {
        if (!joinedPlayers.contains(nick)) {
            joinedPlayers.add(nick);
        }
        pendingLogs.add(nick + " joined the lobby");
    }

    /**
     * Triggers the UI to request the desired lobby size from the very first connected player.
     *
     * @param firstPlayerNick The first player's nickname.
     */
    public synchronized void awaitPlayerCount(String firstPlayerNick) {
        this.currentPhase = Phase.WAITING_FOR_PLAYER_COUNT;
        this.currentPlayer = firstPlayerNick;

        if (!joinedPlayers.contains(firstPlayerNick)) {
            joinedPlayers.add(firstPlayerNick);
        }

        if (firstPlayerNick.equals(myNickname)) {
            pendingLogs.add("You are the first player. Pick the number of participants.");
            pendingPrompt = () -> {
                try {
                    final int count = view.promptPlayerCount();
                    sendOrError(() -> virtualServer.setPlayerCount(myNickname, count));
                } catch (CancellationException e) {
                    System.out.println("LOG: Player canceled game creation. Disconnecting...");
                    sendOrError(() -> virtualServer.disconnect(myNickname));
                    connectionRejected = true;
                    endLatch.countDown();
                }
            };
        } else {
            pendingLogs.add("Waiting for " + firstPlayerNick + " to set the participant count");
        }
    }

    /**
     * Locks in the required participant count.
     *
     * @param count The required total players.
     */
    public synchronized void playerCountSet(int count) {
        this.expectedPlayers = count;
        pendingLogs.add("Player count set to " + count);
    }

    /**
     * Places the game in a waiting state until the lobby is full.
     *
     * @param joined Current number of connected players.
     * @param total Maximum capacity of the lobby.
     */
    public synchronized void awaitMorePlayers(int joined, int total) {
        this.currentPhase = Phase.WAITING_FOR_PLAYERS;
        pendingLogs.add("Waiting for more players (" + joined + "/" + total + ")");
    }

    /**
     * Triggers the totem color selection prompt for the active player.
     *
     * @param nick The active player.
     * @param available The colors remaining to be picked.
     */
    public synchronized void colorTurn(String nick, Set<TotemColor> available) {
        this.currentPhase = Phase.COLOR_PICKING;
        this.currentPlayer = nick;
        this.availableColors = new HashSet<>(available);
        if (nick.equals(myNickname)) {
            final HashSet<TotemColor> avail = new HashSet<>(available);
            pendingPrompt = () -> {
                final TotemColor c = view.promptColor(avail);
                sendOrError(() -> virtualServer.pickColor(myNickname, c));
            };
        } else {
            pendingLogs.add("Waiting for " + nick + " to pick a color");
        }
    }

    /**
     * Stores the selected totem color for a specific player.
     *
     * @param nick The player's nickname.
     * @param color The chosen color.
     */
    public synchronized void colorPicked(String nick, TotemColor color) {
        colorsByPlayer.put(nick, color);
        availableColors.remove(color);
        pendingLogs.add(nick + " picked " + color);
    }

    /**
     * Initializes the match state upon starting.
     *
     * @param playerOrder The randomized starting turn order.
     * @param era The starting era.
     */
    public synchronized void matchStarted(List<String> playerOrder, Era era) {
        this.currentEra = era;
        pendingLogs.add("MATCH STARTED. Era: " + era + ". Order: " + playerOrder);
    }

    /**
     * Progresses the game state to the specified Era.
     *
     * @param newEra The newly reached Era.
     */
    public synchronized void eraChanged(Era newEra) {
        this.currentEra = newEra;
        pendingLogs.add("Era changed to " + newEra);
    }

    /**
     * Updates the internal round counter.
     *
     * @param round The current round sequence number.
     */
    public synchronized void roundStarted(int round) {
        this.currentRound = round;
        pendingLogs.add("Round " + round + " started");
    }

    /**
     * Triggers the UI prompt to place a totem on the offering track.
     *
     * @param nick The active player.
     * @param trackSize The maximum valid boundary of the track.
     */
    public synchronized void offeringTileTurn(String nick, int trackSize) {
        this.currentPhase = Phase.PICK_OFFERING_TILE;
        this.currentPlayer = nick;
        if (nick.equals(myNickname)) {
            final int size = trackSize;
            pendingPrompt = () -> {
                final int idx = view.promptOfferingTile(size);
                sendOrError(() -> virtualServer.pickOfferingTile(myNickname, idx));
            };
        } else {
            pendingLogs.add("Waiting for " + nick + " to place a totem on the offering track");
        }
    }

    /**
     * Logs the successful placement of an offering track totem.
     *
     * @param nick The active player.
     * @param tileIndex The chosen track tile.
     */
    public synchronized void offeringTilePicked(String nick, int tileIndex) {
        pendingLogs.add(nick + " placed a totem on offering tile " + tileIndex);
    }

    /**
     * Triggers the character drawing UI prompt.
     *
     * @param nick The active player.
     * @param upperBudget The maximum character picks allowed from the upper row.
     * @param lowerBudget The maximum character picks allowed from the lower row.
     */
    public synchronized void characterPickTurn(String nick, int upperBudget, int lowerBudget) {
        this.currentPhase = Phase.TURN_PICK_CARDS_CHARACTER;
        this.currentPlayer = nick;
        this.currentUpperBudget = upperBudget;
        this.currentLowerBudget = lowerBudget;
        if (nick.equals(myNickname)) {
            final int upper = upperBudget;
            final int lower = lowerBudget;
            pendingPrompt = () -> {
                final List<Integer> upperIds = view.promptCharacterUpperIds(upper);
                final List<Integer> lowerIds = view.promptCharacterLowerIds(lower);
                sendOrError(() -> virtualServer.pickCharacterCards(myNickname, upperIds, lowerIds));
            };
        } else {
            pendingLogs.add("Waiting for " + nick + " to pick character cards (upper="
                    + upperBudget + ", lower=" + lowerBudget + ")");
        }
    }

    /**
     * Stores the drawn character cards into the player's local hand state.
     *
     * @param nick The active player.
     * @param upper The list of drawn upper row characters.
     * @param lower The list of drawn lower row characters.
     */
    public synchronized void characterCardsPicked(String nick,
                                                  List<CharacterCard> upper,
                                                  List<CharacterCard> lower) {
        List<CharacterCard> hand = handsByPlayer.computeIfAbsent(nick, k -> new ArrayList<>());
        hand.addAll(upper);
        hand.addAll(lower);
        int total = upper.size() + lower.size();
        pendingLogs.add(nick + " drew " + total + " character card(s)");
    }

    /**
     * Triggers the building construction UI prompt.
     *
     * @param nick The active player.
     * @param upperRemaining The remaining buildings permitted to buy from the upper row.
     * @param lowerRemaining The remaining buildings permitted to buy from the lower row.
     */
    public synchronized void buildingPickTurn(String nick, int upperRemaining, int lowerRemaining) {
        this.currentPhase = Phase.TURN_PICK_CARDS_BUILDING;
        this.currentPlayer = nick;
        this.currentUpperRemaining = upperRemaining;
        this.currentLowerRemaining = lowerRemaining;
        if (nick.equals(myNickname)) {
            final int uRem = upperRemaining;
            final int lRem = lowerRemaining;
            pendingPrompt = () -> {
                final List<Integer> upperIds = view.promptBuildingUpperIds(uRem);
                final List<Integer> lowerIds = view.promptBuildingLowerIds(lRem);
                sendOrError(() -> virtualServer.pickBuildingCards(myNickname, upperIds, lowerIds));
            };
        } else {
            pendingLogs.add("Waiting for " + nick + " to buy buildings (residual upper="
                    + upperRemaining + ", lower=" + lowerRemaining + ")");
        }
    }

    /**
     * Stores newly constructed buildings into the player's local state.
     *
     * @param nick The active player.
     * @param upper The buildings bought from the upper row.
     * @param lower The buildings bought from the lower row.
     */
    public synchronized void buildingCardsBought(String nick,
                                                 List<BuildingCard> upper,
                                                 List<BuildingCard> lower) {
        List<BuildingCard> owned = buildingsByPlayer.computeIfAbsent(nick, k -> new ArrayList<>());
        owned.addAll(upper);
        owned.addAll(lower);
        int total = upper.size() + lower.size();
        pendingLogs.add(nick + " bought " + total + " building(s)");
    }

    /**
     * Triggers the special bonus extra pick prompt.
     *
     * @param nick The active player.
     */
    public synchronized void extraPickTurn(String nick) {
        this.currentPhase = Phase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING;
        this.currentPlayer = nick;
        if (nick.equals(myNickname)) {
            pendingPrompt = () -> {
                View.ExtraPickChoice choice = view.promptExtraCard();
                final Integer id = choice.cardId;
                final boolean isChar = choice.isCharacter;
                sendOrError(() -> virtualServer.pickExtraCard(myNickname, id, isChar));
            };
        } else {
            pendingLogs.add(nick + " has the extra pick (special building bonus)");
        }
    }

    /**
     * Logs the outcome of an extra pick action.
     *
     * @param nick The acting player.
     * @param cardId The ID of the drawn card, or null if skipped.
     * @param isCharacter True if it was a character, false if a building.
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
     * Refreshes the prestige score mapping for a player.
     *
     * @param nick The player to update.
     * @param newPrestige The newly calculated score.
     */
    public synchronized void prestigeChanged(String nick, int newPrestige) {
        Integer prev = prestige.put(nick, newPrestige);
        if (prev != null && prev != newPrestige) {
            pendingLogs.add(nick + " prestige -> " + newPrestige);
        }
    }

    /**
     * Refreshes the food token count mapping for a player.
     *
     * @param nick The player to update.
     * @param newFood The newly calculated food count.
     */
    public synchronized void foodChanged(String nick, int newFood) {
        Integer prev = food.put(nick, newFood);
        if (prev != null && prev != newFood) {
            pendingLogs.add(nick + " food -> " + newFood);
        }
    }

    /** Logs the conclusion of an entire game round. */
    public synchronized void roundEnded() {
        pendingLogs.add("Round ended");
    }

    /**
     * Signals that the match has officially concluded and final statistics are ready.
     *
     * @param leaderboard The final ordered ranking of the players.
     * @param winners The list containing the match winner(s).
     * @param globalLeaderboard The historical global database records.
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
     * Alters the game state to pause pending the resolution of a disconnection.
     *
     * @param reason The explanatory pause reason.
     */
    public synchronized void matchPaused(String reason) {
        this.currentPhase = Phase.PAUSED_WAITING_RECONNECT;
        pendingLogs.add("Match paused: " + reason);
    }

    /** Logs the resumption of the match. */
    public synchronized void matchResumed() {
        pendingLogs.add("Match resumed.");
    }

    /**
     * Flags a specific player as disconnected, ensuring the UI can grey out their panel.
     *
     * @param nick The nickname of the disconnected player.
     */
    public synchronized void playerDisconnected(String nick) {
        disconnectedPlayers.add(nick);
        pendingLogs.add("ATTENTION: " + nick + " has disconnected.");
    }

    /**
     * Restores a previously disconnected player back into active status.
     *
     * @param nick The nickname of the reconnected player.
     */
    public synchronized void playerReconnected(String nick) {
        disconnectedPlayers.remove(nick);
        pendingLogs.add("GOOD NEWS: " + nick + " has reconnected.");
    }

    /**
     * Enqueues an error string indicating that the last action was rejected by the server logic.
     *
     * @param reason The descriptive error reason.
     */
    public synchronized void actionRejected(String reason) {
        this.pendingError = reason;
    }

    /**
     * Handles scenarios where the server refuses a connection attempt.
     * Will automatically re-prompt the user if the error is recoverable (e.g., duplicated nickname).
     *
     * @param reason The specific reason for rejection.
     */
    public synchronized void nicknameRejected(String reason) {
        view.displayError(reason);
        if (isRetryableConnectError(reason) && retryConnect != null) {
            final Runnable retry = retryConnect;
            Thread t = new Thread(() -> {
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
     * Determines whether a connection rejection warrants a retry attempt by the user.
     *
     * @param reason The error string.
     * @return True if it is a nickname validation error, false if it's a structural barrier.
     */
    private static boolean isRetryableConnectError(String reason) {
        return reason != null && reason.startsWith("nickname");
    }

    /**
     * Confirms whether a connection sequence was permanently aborted.
     * Used by network clients to skip manual disconnect commands on application tear-down.
     *
     * @return True if the server rejected the connection and closed the session.
     */
    public synchronized boolean wasConnectionRejected() {
        return connectionRejected;
    }

    /**
     * Updates the internal board layout data safely behind the scenes.
     * Will be fully rendered on the next UI {@code commit()}.
     *
     * @param board The complete layout of the central game board.
     */
    public synchronized void boardSnapshot(Board board) {
        this.currentBoard = board;
    }

    /**
     * Executes accumulated updates and resolves asynchronous UI actions safely.
     * <p>
     * Fetches all pending states (logs, errors, prompts) inside a synchronized
     * block, and then processes them sequentially in the external thread. This architecture ensures
     * that network listeners are never frozen while waiting for the GUI to render or users to interact.
     * </p>
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
     * Radically overwrites and reconstructs the client's local state using a massive server snapshot.
     * Essential for synchronizing players who drop and successfully reconnect mid-game.
     *
     * @param s The comprehensive GameSnapshot payload.
     */
    public synchronized void gameSnapshot(GameSnapshot s) {
        // Purge the offline memory list.
        this.disconnectedPlayers.clear();

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
                ? new HashSet<>(s.getAvailableColors()) : new HashSet<>();
        this.expectedPlayers = s.getExpectedPlayers();
        this.finalLeaderboard = s.getLeaderboard();
        this.finalWinners = s.getWinners();
    }

    /** Functional interface simplifying RemoteException handling during transmissions. */
    private interface SafeSend {
        void send() throws RemoteException;
    }

    /**
     * Executes network routines catching any RMI remote connection faults.
     *
     * @param action The specific logic to execute over the network boundary.
     */
    private void sendOrError(SafeSend action) {
        try {
            action.send();
        } catch (RemoteException e) {
            view.displayError("Send failed: " + e.getMessage());
        }
    }

    /**
     * Silently extracts the user from the current lobby without tearing down the underlying thread.
     * Clears all internal memory vectors so the client can join a brand new game efficiently.
     */
    public synchronized void leaveLobbySoft() {
        System.out.println("LOG: Player manually left the lobby. Disconnecting...");
        sendOrError(() -> virtualServer.disconnect(myNickname));

        this.joinedPlayers.clear();
        this.expectedPlayers = 0;
        this.currentPlayer = null;
        this.currentPhase = Phase.WAITING_FOR_NICKNAME;
        this.colorsByPlayer.clear();
        this.availableColors.clear();
        this.pendingLogs.clear();
        this.pendingError = null;
        this.pendingPrompt = null;

        // Reset the memory array of disconnected players to prevent GUI bugs leaking into the next match.
        this.disconnectedPlayers.clear();
    }

    /**
     * Initiates a soft reconnection simulating a fresh start with a new identity.
     *
     * @param newNickname The newly selected player moniker.
     */
    public synchronized void reconnectNewGame(String newNickname) {
        this.myNickname = newNickname;
        if (retryConnect != null) {
            System.out.println("LOG: Reconnecting to server with new nickname: " + newNickname);
            retryConnect.run();
        }
    }
}