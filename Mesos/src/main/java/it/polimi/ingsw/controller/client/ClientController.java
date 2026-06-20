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
 * The {@code ClientController} receives updates from the server, updates its local,
 * simplified model of the game state, and notifies the {@link View} to prompt the user
 * or display information when needed.
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

    private String pendingError;
    private final List<String> pendingLogs = new ArrayList<>();
    private Runnable pendingPrompt;
    private boolean pendingGameEnded;
    private boolean connectionRejected;
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
     *
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
     * Sets the retry connection hook to allow reconnecting under a different nickname.
     *
     * @param r The runnable logic to retry connection.
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
        // Immediately save the nickname locally to avoid Race Conditions with RMI callbacks!
        if (nick != null) {
            setNickname(nick);
        }
        return nick;
    }

    /**
     * Blocks the current thread until the game has officially ended or the connection drops.
     *
     * @throws InterruptedException If the thread is interrupted while waiting.
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
    public synchronized void gameEnded(List<String> leaderboard, List<String> winners) {
        this.currentPhase = Phase.GAME_ENDED;
        this.finalLeaderboard = leaderboard;
        this.finalWinners = winners;
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
     * Handles a server response refusing the initially requested nickname.
     * Displays an error and prompts for a new nickname if the error is recoverable.
     *
     * @param reason The specific reason the nickname was invalid (e.g., taken).
     */
    public synchronized void nicknameRejected(String reason) {
        view.displayError(reason);
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

    private static boolean isRetryableConnectError(String reason) {
        return reason != null && reason.startsWith("nickname");
    }

    /**
     * Checks if the connection request was definitively rejected by the server.
     *
     * @return True if rejected, false otherwise.
     */
    public synchronized boolean wasConnectionRejected() {
        return connectionRejected;
    }

    /**
     * Receives and stores a fresh board state from the server.
     *
     * @param board The updated game board entity.
     */
    public synchronized void boardSnapshot(Board board) {
        this.currentBoard = board;
    }

    /**
     * Acts as a barrier that applies all queued state changes, logs,
     * and prompts collectively to the View to avoid partial or tearing updates.
     */
    /**
     * Applica tutti i cambiamenti di stato alla View.
     */
    public void commit() {
        Runnable p;
        boolean ended;
        String err;
        List<String> logs;

        // 1. Leggiamo i dati e svuotiamo le code in modo sicuro e rapidissimo
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

        // 2. FUORI DAL LUCCHETTO: Aggiorniamo la grafica senza bloccare il Controller
        view.refresh(this);
        for (String log : logs) {
            view.displayInfo(log);
        }
        if (err != null) {
            view.displayError(err);
        }

        // 3. FUORI DAL LUCCHETTO: Il Thread di rete ora può mettersi in pausa
        // aspettando l'input dell'utente senza bloccare il Thread Grafico!
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
     * Disconnette l'utente dalla lobby senza chiudere il thread di rete.
     * Mantiene l'operaio di rete "parcheggiato" e in ascolto per una nuova partita.
     */
    public synchronized void leaveLobbySoft() {
        System.out.println("LOG: Player manually left the lobby. Disconnecting...");
        sendOrError(new SafeSend() {
            @Override public void send() throws RemoteException {
                virtualServer.disconnect(myNickname);
            }
        });

        // ==========================================
        // FIX: PULIZIA DELLA MEMORIA LOCALE (AMNESIA)
        // ==========================================
        this.joinedPlayers.clear();
        this.expectedPlayers = 0;
        this.currentPlayer = null;
        this.currentPhase = Phase.WAITING_FOR_NICKNAME;
        this.colorsByPlayer.clear();
        this.availableColors.clear();
        this.pendingLogs.clear();
        this.pendingError = null;
        this.pendingPrompt = null;
        // ==========================================

        // IMPORTANTE: NON chiamiamo endLatch.countDown();
        // In questo modo la rete non muore e aspetta ordini dalla GUI.
    }

    /**
     * Sfrutta il callback di retry della rete per simulare un nuovo accesso
     * senza dover ricreare la connessione RMI o Socket da zero.
     *  @param newNickname The updated nickname to try.
     */
    public synchronized void reconnectNewGame(String newNickname) {
        this.myNickname = newNickname;
        if (retryConnect != null) {
            System.out.println("LOG: Reconnecting to server with new nickname: " + newNickname);
            retryConnect.run();
        }
    }
}