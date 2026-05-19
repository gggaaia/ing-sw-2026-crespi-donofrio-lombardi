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
     * stamparlo subito: il successivo evento *Turn far\u00e0
     * {@code view.refresh(this)} che si occuper\u00e0 di stamparlo
     * DOPO il dump dello stato (vedi {@code TuiView.refresh}).
     * Cos\u00ec l'errore appare in fondo alla schermata, vicino al
     * prompt, invece di essere "sopra" il game state aggiornato.
     */
    private String pendingError;

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
    // Event handlers (chiamati da Rmi_Client / Socket_Client)
    //
    // Politica di stampa per evitare di duplicare il game-state dump:
    //   - aggiorniamo SEMPRE il mini-stato per ogni evento
    //   - view.refresh(this) viene chiamato SOLO quando \u00e8 il MIO turno
    //     (subito prima del prompt) o per le transizioni importanti
    //     (matchStarted, roundStarted, gameEnded, gameSnapshot)
    //   - per gli altri eventi usiamo view.displayInfo("...") che stampa
    //     una sola riga sintetica
    // ====================================================================

    public synchronized void playerJoined(String nick) {
        if (!joinedPlayers.contains(nick)) {
            joinedPlayers.add(nick);
        }
        view.displayInfo(nick + " joined the lobby");
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
            view.displayInfo("You are the first player. Pick the number of participants.");
            int count = view.promptPlayerCount();
            sendOrError(new SafeSend() {
                @Override public void send() throws RemoteException {
                    virtualServer.setPlayerCount(myNickname, count);
                }
            });
        } else {
            view.displayInfo("Waiting for " + firstPlayerNick + " to set the participant count");
        }
    }

    public synchronized void playerCountSet(int count) {
        this.expectedPlayers = count;
        view.displayInfo("Player count set to " + count);
    }

    public synchronized void awaitMorePlayers(int joined, int total) {
        view.displayInfo("Waiting for more players (" + joined + "/" + total + ")");
    }

    public synchronized void colorTurn(String nick, Set<TotemColor> available) {
        this.currentPhase = Phase.COLOR_PICKING;
        this.currentPlayer = nick;
        this.availableColors = new HashSet<>(available);
        if (nick.equals(myNickname)) {
            view.refresh(this);
            TotemColor color = view.promptColor(new HashSet<>(available));
            final TotemColor c = color;
            sendOrError(new SafeSend() {
                @Override public void send() throws RemoteException {
                    virtualServer.pickColor(myNickname, c);
                }
            });
        } else {
            view.displayInfo("Waiting for " + nick + " to pick a color");
        }
    }

    public synchronized void colorPicked(String nick, TotemColor color) {
        colorsByPlayer.put(nick, color);
        availableColors.remove(color);
        view.displayInfo(nick + " picked " + color);
    }

    public synchronized void matchStarted(List<String> playerOrder, Era era) {
        this.currentEra = era;
        // Niente view.refresh(this) qui: il *Turn event che segue
        // (es. onOfferingTileTurn) far\u00e0 lui il dump dello stato
        // con la fase corretta (PICK_OFFERING_TILE) e la board che
        // arriva via onBoardSnapshot. Cos\u00ec evitiamo un dump
        // intermedio con phase ancora COLOR_PICKING.
        view.displayInfo("MATCH STARTED. Era: " + era + ". Order: " + playerOrder);
    }

    public synchronized void eraChanged(Era newEra) {
        this.currentEra = newEra;
        view.displayInfo("Era changed to " + newEra);
    }

    public synchronized void roundStarted(int round) {
        this.currentRound = round;
        view.displayInfo("Round " + round + " started");
    }

    public synchronized void offeringTileTurn(String nick, int trackSize) {
        this.currentPhase = Phase.PICK_OFFERING_TILE;
        this.currentPlayer = nick;
        if (nick.equals(myNickname)) {
            view.refresh(this);
            int idx = view.promptOfferingTile(trackSize);
            final int finalIdx = idx;
            sendOrError(new SafeSend() {
                @Override public void send() throws RemoteException {
                    virtualServer.pickOfferingTile(myNickname, finalIdx);
                }
            });
        } else {
            view.displayInfo("Waiting for " + nick + " to place a totem on the offering track");
        }
    }

    public synchronized void offeringTilePicked(String nick, int tileIndex) {
        view.displayInfo(nick + " placed a totem on offering tile " + tileIndex);
        // Refresh: il server ha appena mandato un onBoardSnapshot
        // bundle-ato con questo evento, quindi currentBoard riflette
        // gi\u00e0 la track con il totem appena piazzato. Un dump qui
        // permette a chi non \u00e8 il prossimo picker di vedere
        // l'aggiornamento (chi \u00e8 il prossimo picker lo vedr\u00e0
        // comunque nel suo onOfferingTileTurn).
        view.refresh(this);
    }

    public synchronized void characterPickTurn(String nick, int upperBudget, int lowerBudget) {
        this.currentPhase = Phase.TURN_PICK_CARDS_CHARACTER;
        this.currentPlayer = nick;
        this.currentUpperBudget = upperBudget;
        this.currentLowerBudget = lowerBudget;
        if (nick.equals(myNickname)) {
            view.refresh(this);
            final List<Integer> upper = view.promptCharacterUpperIds(upperBudget);
            final List<Integer> lower = view.promptCharacterLowerIds(lowerBudget);
            sendOrError(new SafeSend() {
                @Override public void send() throws RemoteException {
                    virtualServer.pickCharacterCards(myNickname, upper, lower);
                }
            });
        } else {
            view.displayInfo("Waiting for " + nick + " to pick character cards (upper="
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
        view.displayInfo(nick + " drew " + total + " character card(s)");
        // Refresh solo se NON sono io il picker: il server ha bundle-ato
        // questo evento con il successivo onBuildingPickTurn(me) che
        // far\u00e0 lui il refresh con la fase aggiornata
        // (TURN_PICK_CARDS_BUILDING) e la mano gi\u00e0 piena. Se
        // refreshassi anche qui vedrei un dump intermedio con la fase
        // ancora TURN_PICK_CARDS_CHARACTER, immediatamente sostituito
        // dal dump corretto del *Turn.
        if (!nick.equals(myNickname)) {
            view.refresh(this);
        }
    }

    public synchronized void buildingPickTurn(String nick, int upperRemaining, int lowerRemaining) {
        this.currentPhase = Phase.TURN_PICK_CARDS_BUILDING;
        this.currentPlayer = nick;
        this.currentUpperRemaining = upperRemaining;
        this.currentLowerRemaining = lowerRemaining;
        if (nick.equals(myNickname)) {
            view.refresh(this);
            final List<Integer> upper = view.promptBuildingUpperIds(upperRemaining);
            final List<Integer> lower = view.promptBuildingLowerIds(lowerRemaining);
            sendOrError(new SafeSend() {
                @Override public void send() throws RemoteException {
                    virtualServer.pickBuildingCards(myNickname, upper, lower);
                }
            });
        } else {
            view.displayInfo("Waiting for " + nick + " to buy buildings (residual upper="
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
        view.displayInfo(nick + " bought " + total + " building(s)");
        view.refresh(this);
    }

    public synchronized void extraPickTurn(String nick) {
        this.currentPhase = Phase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING;
        this.currentPlayer = nick;
        if (nick.equals(myNickname)) {
            view.refresh(this);
            View.ExtraPickChoice choice = view.promptExtraCard();
            final Integer id = choice.cardId;
            final boolean isChar = choice.isCharacter;
            sendOrError(new SafeSend() {
                @Override public void send() throws RemoteException {
                    virtualServer.pickExtraCard(myNickname, id, isChar);
                }
            });
        } else {
            view.displayInfo(nick + " has the extra pick (special building bonus)");
        }
    }

    public synchronized void extraCardPicked(String nick, Integer cardId, boolean isCharacter) {
        if (cardId == null) {
            view.displayInfo(nick + " skipped the extra pick");
        } else {
            view.displayInfo(nick + " did extra pick: "
                    + (isCharacter ? "character" : "building") + " id=" + cardId);
        }
        view.refresh(this);
    }

    public synchronized void prestigeChanged(String nick, int newPrestige) {
        prestige.put(nick, newPrestige);
        view.displayInfo(nick + " prestige -> " + newPrestige);
    }

    public synchronized void foodChanged(String nick, int newFood) {
        food.put(nick, newFood);
        view.displayInfo(nick + " food -> " + newFood);
    }

    public synchronized void roundEnded() {
        view.displayInfo("Round ended");
    }

    public synchronized void gameEnded(List<String> leaderboard, List<String> winners) {
        this.currentPhase = Phase.GAME_ENDED;
        this.finalLeaderboard = leaderboard;
        this.finalWinners = winners;
        view.refresh(this);
        view.displayInfo("Game ended. Winners: " + winners);
        // Sveglia il main thread bloccato in awaitGameEnd() perch\u00e9
        // possa fare la sequenza di cleanup e uscire dal JVM.
        endLatch.countDown();
    }

    public synchronized void matchPaused(String reason) {
        this.currentPhase = Phase.PAUSED_WAITING_RECONNECT;
        view.displayInfo("Match paused: " + reason);
    }

    public synchronized void matchResumed() {
        view.displayInfo("Match resumed.");
    }

    public synchronized void playerDisconnected(String nick) {
        view.displayInfo(nick + " disconnected.");
    }

    public synchronized void playerReconnected(String nick) {
        view.displayInfo(nick + " reconnected.");
    }

    public synchronized void actionRejected(String reason) {
        // Solo store: l'errore viene stampato dal prossimo refresh,
        // dopo il dump dello stato e prima del prompt. Vedi
        // {@link #consumePendingError()} e TuiView.refresh.
        this.pendingError = reason;
    }

    /**
     * Letto e azzerato dalla view in fondo a {@code refresh}: se
     * non-null, la view stampa il box d'errore subito dopo il
     * delimitatore di chiusura del game state, cos\u00ec l'utente lo
     * vede a ridosso del prompt.
     */
    public synchronized String consumePendingError() {
        String e = this.pendingError;
        this.pendingError = null;
        return e;
    }

    /**
     * Il connect del client \u00e8 stato rifiutato dal server. Mostriamo
     * l'errore e chiudiamo la connessione: triggeriamo il latch di
     * fine sessione cos\u00ec che il main thread esca da
     * {@link #awaitGameEnd} e proceda al cleanup (disconnect +
     * unexport per RMI, chiusura socket per socket).
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
        view.refresh(this);
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
