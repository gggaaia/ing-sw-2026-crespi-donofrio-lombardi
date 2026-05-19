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
 * Controller client-side. Tiene un mini-stato locale che riflette
 * quello che il server gli ha detto, aggiornato evento-per-evento.
 *
 * Threading model (semplice):
 *   - tutti i metodi pubblici sono synchronized
 *   - i metodi di evento (chiamati dal thread di rete RMI o Socket)
 *     aggiornano lo stato e fanno view.refresh()
 *   - i metodi *Turn, se il turno \u00e8 nostro, BLOCCANO sul prompt
 *     view.promptXxx() e poi mandano l'azione via virtualServer
 *   - la chiamata di rete uscente (virtualServer.xxx()) avviene
 *     dentro il metodo synchronized: il lock \u00e8 tenuto, ma la
 *     chiamata \u00e8 veloce su RMI (microsecondi al massimo per il
 *     dispatch) e su socket (writeObject in buffer OS)
 *
 * Il "block" durante il prompt significa che, mentre l'utente pensa,
 * gli eventi entranti dal server si accodano nel buffer del socket /
 * thread pool RMI. Vengono processati appena l'utente sceglie.
 */
public class ClientController {

    private final View view;
    private String myNickname;
    private VirtualServer virtualServer;

    // === mini-stato locale ===

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
     * Errore lato server (es. "tile already occupied") in attesa di
     * essere mostrato. Lo settiamo in {@link #actionRejected} senza
     * stamparlo subito: il successivo {@link #commit()} lo flusha
     * DOPO il dump dello stato e prima del prompt.
     */
    private String pendingError;

    /**
     * Log di gioco accumulati dagli handler degli eventi (es.
     * "A picked RED", "B prestige -> 5", "Round 1 started"). Vengono
     * stampati in blocco da {@link #commit()} dopo il refresh dello
     * stato, cos\u00ec l'utente vede prima il dump aggiornato e poi
     * la cronologia di cosa \u00e8 successo.
     */
    private final List<String> pendingLogs = new ArrayList<>();

    /**
     * Prompt da eseguire al prossimo {@link #commit()}, dopo refresh
     * e log. Settato dagli handler dei {@code *Turn} quando il turno
     * \u00e8 nostro. Qui rimane parcheggiato finch\u00e9 commit non
     * lo invoca: cos\u00ec il blocco su stdin avviene DOPO che
     * abbiamo finito di renderizzare lo stato (e non a met\u00e0
     * di un bundle).
     */
    private Runnable pendingPrompt;

    /**
     * Marker che indica che il prossimo {@link #commit()} deve anche
     * trigghiare {@link #endLatch} (fine partita). Settato in
     * {@link #gameEnded}. Cos\u00ec il main thread blocca in
     * {@code awaitGameEnd} finch\u00e9 il dump finale \u00e8 stato
     * mostrato.
     */
    private boolean pendingGameEnded;

    /**
     * Latch one-shot che il main thread del client (Rmi_Client.run /
     * Socket_Client.run) usa per addormentarsi finch\u00e9 non arriva
     * l'evento {@code onGameEnded}. Il network thread chiama
     * {@code countDown()} dentro {@code gameEnded(...)} svegliando il
     * main thread. Vedi {@link #awaitGameEnd()}.
     */
    private final CountDownLatch endLatch = new CountDownLatch(1);

    public ClientController(View view) {
        this.view = view;
    }

    /** Chiamato dall'adapter (Rmi_Client / Socket_Client) dopo il prompt nickname. */
    public synchronized void setNickname(String nick) {
        this.myNickname = nick;
    }

    public synchronized String getMyNickname() {
        return myNickname;
    }

    /** Chiamato dall'adapter per dare al controller il puntatore al server. */
    public synchronized void setVirtualServer(VirtualServer vs) {
        this.virtualServer = vs;
    }

    /** Prompt iniziale del nickname; chiamato dall'adapter. */
    public String promptNickname() {
        return view.promptNickname();
    }

    /**
     * Blocca il chiamante finch\u00e9 non arriva l'evento
     * {@code onGameEnded}. Usato da {@code Rmi_Client.run()} e
     * {@code Socket_Client.run()} per tenere vivo il main thread
     * durante la partita, senza fare polling. Il network thread
     * chiama {@code endLatch.countDown()} dentro {@link #gameEnded}
     * quando la partita termina.
     */
    public void awaitGameEnd() throws InterruptedException {
        endLatch.await();
    }

    // === getter per la view (per refresh) ===

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
    // Event handlers (chiamati da Rmi_Client / Socket_Client).
    //
    // REGOLA SEMPLICE: ogni handler aggiorna SOLO il mini-stato locale
    // e accoda eventuali log/prompt. NESSUN handler chiama
    // view.refresh / view.displayInfo / view.promptXxx direttamente.
    // L'unico render path \u00e8 {@link #commit()}, invocato dal server
    // a fine bundle via {@code v.onCommit()}.
    // ====================================================================

    public synchronized void playerJoined(String nick) {
        if (!joinedPlayers.contains(nick)) {
            joinedPlayers.add(nick);
        }
        pendingLogs.add(nick + " joined the lobby");
    }

    public synchronized void awaitPlayerCount(String firstPlayerNick) {
        this.currentPlayer = firstPlayerNick;
        // Il primo player non riceve uno snapshot iniziale (vedi
        // ServerController.acceptFirstPlayerJoin); senza snapshot non
        // saprebbe di essere in lobby. Lo aggiungiamo manualmente qui.
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
        // Mostriamo il log solo se il valore \u00e8 EFFETTIVAMENTE
        // cambiato rispetto a uno noto. Saltiamo:
        //   - prev == null   : primo broadcast (es. il bundle iniziale
        //                       di startMatch con prestige=0/food=0).
        //                       L'utente vedr\u00e0 i valori nel dump,
        //                       non serve un log "X prestige -> 0".
        //   - prev == new    : il server emette pi\u00f9 fasi di scoring
        //                       e capita che la seconda non cambi nulla.
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
        // NB: il countDown del latch \u00e8 spostato in commit(), in modo
        // che il main thread non esca dall'awaitGameEnd prima che il
        // dump finale venga mostrato.
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
        // Solo store: l'errore viene stampato dal prossimo {@link #commit()}
        // (sezione "displayError"), dopo il dump dello stato e prima
        // del prompt.
        this.pendingError = reason;
    }

    /**
     * Il connect del client \u00e8 stato rifiutato dal server. Caso
     * speciale: lo stampiamo SUBITO (senza passare da commit) e
     * trigghiamo il latch, perch\u00e9 questo \u00e8 un terminale di
     * sessione e nessun commit seguir\u00e0.
     */
    public synchronized void nicknameRejected(String reason) {
        view.displayError(reason);
        view.displayInfo("Closing connection.");
        endLatch.countDown();
    }

    /**
     * Aggiorna in modo silenzioso il mini-stato della board (offering
     * track, upper/lower row, turn order tile) con quello arrivato
     * dal server. NON fa view.refresh(): il dump completo lo fa il
     * successivo evento {@code *Turn} che segue il broadcast della
     * board nel server controller. Cos\u00ec il render del game
     * state arriva con la fase corretta e la board fresca.
     */
    public synchronized void boardSnapshot(Board board) {
        this.currentBoard = board;
    }

    /**
     * Marker di fine bundle dal server. Esegue, in ordine:
     *   1. {@code view.refresh(this)} \u2014 unico punto in cui il
     *      client stampa il GAME STATE dump.
     *   2. flush dei {@link #pendingLogs} accumulati dagli handler
     *      del bundle (uno per riga, via {@code displayInfo}).
     *   3. eventuale {@link #pendingError} via {@code displayError}.
     *   4. eventuale {@link #pendingPrompt}: blocca su stdin
     *      tenendo ancora il monitor del controller (semantica
     *      identica al vecchio prompt-dentro-handler).
     *
     * Tutti gli handler {@code onXxx} si limitano ad aggiornare lo
     * stato e ad accodare log/prompt; commit \u00e8 l'unico render
     * path. Vedi {@code VirtualView#onCommit}.
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
     * Ricostruisce completamente il mini-stato dallo snapshot ricevuto.
     * Usato sia all'arrivo iniziale del client che alla riconnessione.
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
        // NB: il dump arriva dal {@link #commit()} che il server
        // appende dopo l'onGameSnapshot. Qui ci limitiamo a sostituire
        // lo stato.
    }

    // ====================================================================
    // Helper interno: send con catch di RemoteException
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
