package it.polimi.ingsw.controller.server;

import it.polimi.ingsw.Server;
import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.dto.GameSnapshot;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Controller server-side. Tiene lo stato del gioco e gestisce le
 * transizioni di fase tramite {@link PhaseLogic}.
 *
 * Schema:
 *   - i metodi pubblici corrispondono uno a uno ai metodi di
 *     {@link it.polimi.ingsw.common.VirtualServer} e sono synchronized.
 *     Delegano al {@code phase} corrente con dispatch polimorfico.
 *   - {@code PhaseLogic} fa il dispatch: se l'azione \u00e8 ammessa
 *     nella fase corrente chiama un metodo {@code accept*} di questo
 *     controller; altrimenti chiama {@code rejectAction}.
 *   - i metodi {@code accept*} fanno: validazione fine + mutazione
 *     del model + broadcast di eventi alle VirtualView dei client.
 *   - i broadcast usano {@link Server#send}, che spawna un thread
 *     dedicato per i client RMI (le chiamate remote possono essere
 *     lente) e fa una scrittura inline per i client socket.
 *
 * Lock: tutto sotto un unico monitor (this). Il dispatch tramite
 * {@code server.send} \u00e8 veloce (spawn di thread o writeObject su
 * un buffer OS), quindi tenerlo dentro il synchronized non blocca
 * gli altri thread per tempi percettibili.
 */
public class ServerController {

    // === stato fisso ===

    private final Server server;
    private final GameManager gameManager = new GameManager();

    // === stato fase ===

    private PhaseLogic phase = new PhaseLogic.WaitingForNickname();

    // === stato lobby ===

    private String firstPlayerNickname;
    private int expectedPlayers;
    private final List<String> joinedNicknames = new ArrayList<>();

    // === stato match ===

    private Match match;
    private String currentPlayerNickname;
    private final Set<TotemColor> availableColors = EnumSet.allOf(TotemColor.class);

    /** Mappa nickname -> Player. Popolata a partire da color picking. */
    private final Map<String, Player> playerByNickname = new HashMap<>();
    /** Mappa nickname -> colore scelto. */
    private final Map<String, TotemColor> colorByNickname = new HashMap<>();

    /** Coda dei giocatori che devono ancora scegliere il colore. */
    private final Queue<String> colorPickQueue = new LinkedList<>();

    /** Ordine per il piazzamento dei totem sull'offering track del round corrente. */
    private List<Player> turnOrderForOfferingPick = new ArrayList<>();
    private int currentOfferingPickIndex;

    /** Ordine per la risoluzione (pesca + acquisto) nel round corrente. */
    private List<Player> resolutionOrder = new ArrayList<>();
    private int currentResolutionIndex;

    /** Budget della pesca corrente. */
    private int currentUpperBudget;
    private int currentLowerBudget;
    private int currentUpperRemainingForBuilding;
    private int currentLowerRemainingForBuilding;

    /** Proprietario dello special building (extra pick). */
    private Player extraPickPlayer;

    // === stato di pausa / fine ===

    private PhaseLogic phaseBeforePause;
    private Timer lonePlayerTimer;
    public static final long LONE_PLAYER_TIMEOUT_MS = 120_000L;

    private List<String> finalLeaderboard;
    private List<String> finalWinners;
    private List<String> forcedWinners = new ArrayList<>();

    public ServerController(Server server) {
        this.server = server;
    }

    // ====================================================================
    // 1. API pubblica (chiamata da Rmi_Server e Socket_ClientHandler)
    // ====================================================================

    public synchronized void connect(String nick, VirtualView view) {
        phase.onConnect(this, nick, view);
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
     * Chiamato dal Server (heartbeat o failure di un send) quando un
     * client \u00e8 ritenuto disconnesso. Stesso effetto di un
     * {@link #disconnect(String)} esplicito.
     */
    public synchronized void handleDeliveryFailure(String nick) {
        phase.onDisconnect(this, nick);
    }

    // ====================================================================
    // 2. Helper di dispatch (broadcast / single-target)
    // ====================================================================

    /**
     * SAM-shape interna usata dai metodi {@link #broadcast},
     * {@link #broadcastExcept} e {@link #sendToNickname} per passare
     * "quale metodo della view va chiamato" come argomento.
     */
    interface ViewCall {
        void apply(VirtualView v) throws RemoteException;
    }

    /** Manda una chiamata di view a tutti i client connessi. */
    private void broadcast(final ViewCall call) {
        Map<String, VirtualView> snapshot = server.snapshotViews();
        for (final Map.Entry<String, VirtualView> e : snapshot.entrySet()) {
            sendTo(e.getKey(), e.getValue(), call);
        }
    }

    /** Manda una chiamata di view a tutti i client TRANNE il sender. */
    private void broadcastExcept(final String exceptNick, final ViewCall call) {
        Map<String, VirtualView> snapshot = server.snapshotViews();
        for (final Map.Entry<String, VirtualView> e : snapshot.entrySet()) {
            if (e.getKey().equals(exceptNick)) {
                continue;
            }
            sendTo(e.getKey(), e.getValue(), call);
        }
    }

    /** Manda una chiamata di view a un singolo client per nickname. */
    private void sendToNickname(final String nick, final ViewCall call) {
        VirtualView v = server.getView(nick);
        if (v == null) {
            return;
        }
        sendTo(nick, v, call);
    }

    /** Dispatcher di basso livello: usa Server.send (thread RMI / inline socket). */
    private void sendTo(final String nick, final VirtualView v, final ViewCall call) {
        server.send(nick, new Runnable() {
            @Override
            public void run() {
                try {
                    call.apply(v);
                } catch (RemoteException ex) {
                    // Il client \u00e8 morto: cascata di disconnect.
                    handleDeliveryFailure(nick);
                }
            }
        });
    }

    // ====================================================================
    // 3. Reject e snapshot (helper esposti a PhaseLogic)
    // ====================================================================

    /**
     * Manda al chiamante un {@code onActionRejected} con la ragione
     * data, e SUBITO DOPO re-invia il {@code *Turn} corrente cos\u00ec
     * il client non resta appeso senza prompt. La logica del re-invio
     * \u00e8 polimorfica su {@link PhaseLogic}.
     *
     * Usato per rifiutare AZIONI di gioco (es. pickColor su una fase
     * sbagliata). Per rifiutare un tentativo di connect usa invece
     * {@link #rejectNickname}.
     */
    void rejectAction(String nick, final String reason) {
        sendToNickname(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onActionRejected(reason);
            }
        });
        phase.emitCurrentTurnTo(this, nick);
    }

    /**
     * Manda al chiamante un {@code onNicknameRejected} con la ragione
     * data. Triggera sul client il re-prompt del nickname, cos\u00ec
     * l'utente pu\u00f2 ritentare (con stesso o nuovo nickname).
     */
    void rejectNickname(String nick, final String reason) {
        sendToNickname(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onNicknameRejected(reason);
            }
        });
    }

    /** Costruisce il GameSnapshot corrente per i join / reconnect. */
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
    // 4. ACCEPT methods (chiamati da PhaseLogic dopo aver passato il
    //    dispatch per fase). Qui sta il grosso della game logic.
    // ====================================================================

    // --- lobby ----------------------------------------------------------

    /** Accettato in {@link PhaseLogic.WaitingForNickname}: primo giocatore. */
    void acceptFirstPlayerJoin(final String nick, VirtualView view) {
        String error = validateNicknameSyntax(nick);
        if (error != null) {
            rejectNickname(nick, error);
            return;
        }
        joinedNicknames.add(nick);
        firstPlayerNickname = nick;
        currentPlayerNickname = nick;
        phase = new PhaseLogic.WaitingForPlayerCount();

        // Niente snapshot al primo player (vedi GameSnapshot javadoc):
        // basta dirgli "tocca a te scegliere il count". Il client
        // aggiunge sa stesso ai joinedPlayers in awaitPlayerCount.
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onAwaitPlayerCount(nick);
            }
        });
    }

    /** Accettato in {@link PhaseLogic.WaitingForPlayerCount}. */
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
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onPlayerCountSet(count);
            }
        });
        if (joinedNicknames.size() == count) {
            enterColorPicking();
        } else {
            phase = new PhaseLogic.WaitingForPlayers();
            currentPlayerNickname = null;
            final int joined = joinedNicknames.size();
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onAwaitMorePlayers(joined, count);
                }
            });
        }
    }

    /** Accettato in {@link PhaseLogic.WaitingForPlayers}. */
    void acceptAdditionalPlayerJoin(final String nick, VirtualView view) {
        String error = validateNicknameSyntax(nick);
        if (error != null) {
            rejectNickname(nick, error);
            return;
        }
        if (joinedNicknames.contains(nick)) {
            rejectNickname(nick, "nickname already in use");
            return;
        }
        joinedNicknames.add(nick);
        // Annuncio a tutti gli altri (il joiner non riceve onPlayerJoined
        // di se stesso: lo apprende dallo snapshot pi\u00f9 sotto).
        broadcastExcept(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onPlayerJoined(nick);
            }
        });
        if (joinedNicknames.size() == expectedPlayers) {
            // Lobby piena: facciamo PRIMA la transizione a COLOR_PICKING
            // (che broadcasta onColorTurn(firstPicker) a tutti, incluso
            // il nuovo joiner), e SOLO DOPO costruiamo lo snapshot per
            // il joiner. Cos\u00ec lo snapshot riflette la fase nuova
            // (COLOR_PICKING) e il primo color picker, invece di una
            // WAITING_FOR_PLAYERS / turn=null che era valida solo per
            // qualche microsecondo. Il joiner vede:
            //   [INFO] Waiting for X to pick a color   (da onColorTurn)
            //   === GAME STATE === phase: COLOR_PICKING turn: X (da snapshot)
            // invece del precedente dump stale di WAITING_FOR_PLAYERS.
            enterColorPicking();
            final GameSnapshot snapAfter = buildSnapshot();
            sendToNickname(nick, new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onGameSnapshot(snapAfter);
                }
            });
        } else {
            // Lobby ancora incompleta: lo snapshot WAITING_FOR_PLAYERS
            // \u00e8 corretto, lo mandiamo al joiner e poi notifichiamo
            // a tutti il nuovo conteggio.
            final GameSnapshot snap = buildSnapshot();
            sendToNickname(nick, new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onGameSnapshot(snap);
                }
            });
            final int joined = joinedNicknames.size();
            final int total = expectedPlayers;
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onAwaitMorePlayers(joined, total);
                }
            });
        }
    }

    private void enterColorPicking() {
        colorPickQueue.clear();
        colorPickQueue.addAll(joinedNicknames);
        currentPlayerNickname = colorPickQueue.peek();
        phase = new PhaseLogic.ColorPicking();
        final String first = currentPlayerNickname;
        final Set<TotemColor> avail = new HashSet<>(availableColors);
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onColorTurn(first, avail);
            }
        });
    }

    // --- color picking --------------------------------------------------

    /** Accettato in {@link PhaseLogic.ColorPicking}. */
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

        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onColorPicked(nick, color);
            }
        });

        if (colorPickQueue.isEmpty()) {
            startMatch();
        } else {
            currentPlayerNickname = colorPickQueue.peek();
            final String next = currentPlayerNickname;
            final Set<TotemColor> avail = new HashSet<>(availableColors);
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onColorTurn(next, avail);
                }
            });
        }
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
            // Setup fallito: reset alla lobby con errore.
            final String reason = "match setup failed: " + e.getMessage();
            broadcast(new ViewCall() {
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
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onMatchStarted(order, era);
            }
        });
        final int round = match.getCurrentRound();
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onRoundStarted(round);
            }
        });
        phase = new PhaseLogic.PickOfferingTile();
        advanceOfferingPickerUntilConnected();
    }

    // --- offering tile --------------------------------------------------

    /** Accettato in {@link PhaseLogic.PickOfferingTile}. */
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
        // Bundle: prima manda la board aggiornata, poi notifica il
        // pick. Cos\u00ec il client refresh-a (vedi
        // ClientController.offeringTilePicked) con la track gi\u00e0
        // contenente il totem appena piazzato. Senza il bundle il
        // refresh userebbe la board "pre-pick" mandata col precedente
        // onOfferingTileTurn.
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
                // Bundle: board snapshot + *Turn nello stesso ViewCall,
                // cos\u00ec arrivano in ordine sullo stesso thread RMI
                // (e nello stesso writeObject su socket). Senza bundle,
                // due broadcast separate spawnerebbero due thread RMI
                // distinti per client, con possibile riordino.
                broadcast(new ViewCall() {
                    @Override public void apply(VirtualView v) throws RemoteException {
                        v.onBoardSnapshot(board);
                        v.onOfferingTileTurn(fNick, trackSize);
                    }
                });
                return;
            }
            currentOfferingPickIndex++;
        }
        // Nessun altro picker: passa alla resolution.
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
        // Bundle: food change + board snapshot + character pick turn
        // in un unico ViewCall. Senza il bundle, su RMI il
        // characterPickTurn potrebbe vincere la race contro il
        // foodChanged e refreshare con il food vecchio.
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onFoodChanged(nick, newFood);
                v.onBoardSnapshot(board);
                v.onCharacterPickTurn(nick, upper, lower);
            }
        });
    }

    /** Accettato in {@link PhaseLogic.TurnPickCardsCharacter}. */
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
        // Bundle critico: i tre callback (board snapshot + cards picked
        // + next *Turn) DEVONO arrivare al client in ordine e sullo
        // stesso thread. Se li mandassimo in due broadcast separate,
        // Server.send spawnerebbe due thread RMI per client e il
        // secondo (onBuildingPickTurn) potrebbe vincere la race,
        // facendo refresh con la fase nuova ma con la mano ancora
        // vuota (la mano viene aggiornata da onCharacterCardsPicked,
        // che resterebbe in coda dietro al prompt dei building).
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onBoardSnapshot(boardAfterCharPick);
                v.onCharacterCardsPicked(nick, finalUpper, finalLower);
                v.onBuildingPickTurn(nick, uRem, lRem);
            }
        });
    }

    /** Accettato in {@link PhaseLogic.TurnPickCardsBuilding}. */
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
        // Bundle: board snapshot + cards bought + food change in un
        // unico ViewCall, cos\u00ec sui client RMI arrivano in ordine
        // e l'eventuale refresh post-evento vede gi\u00e0 sia la riga
        // delle buildings che il food aggiornato.
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
        // Housekeeping di fine giro:
        //   clear turn order tile, sposta offering->turn-order tile,
        //   append giocatori mancanti, applica bonus delle slot,
        //   trova il proprietario dell'edificio speciale.
        for (Player p : match.getPlayersList()) {
            match.getBoard().getTurnOrderTile().clearSlot(p);
        }
        match.getBoard().moveOfferingTrackToTurnOrder(match.getPlayersList());
        appendMissingPlayersToTurnOrderTile();
        match.resolveTurnOrderTile();
        // Broadcast aggiornamento prestige/food per ogni giocatore (bonus tile).
        for (Player p : match.getPlayersList()) {
            final String nick = p.getNickname();
            final int newP = p.getPrestigePoints();
            final int newF = p.getFood();
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onPrestigeChanged(nick, newP);
                }
            });
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onFoodChanged(nick, newF);
                }
            });
        }
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
        broadcast(new ViewCall() {
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

    /** Accettato in {@link PhaseLogic.ResetTurnOrderAndPickExtraBuilding}. */
    void acceptExtraCardPick(final String nick, final Integer cardId, final boolean isCharacter) {
        Player player = playerByNickname.get(nick);
        if (player == null || player != extraPickPlayer) {
            rejectAction(nick, "extra pick belongs to another player");
            return;
        }
        if (cardId == null) {
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onExtraCardPicked(nick, null, isCharacter);
                }
            });
            enterResolveBuildingsAndEvents();
            return;
        }
        Board board = match.getBoard();
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
            final int newFood = player.getFood();
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onFoodChanged(nick, newFood);
                }
            });
        }
        final Board boardAfterExtra = match.getBoard();
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onBoardSnapshot(boardAfterExtra);
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
        // Broadcast aggiornamento risorse dopo resolveBuildings / resolveEvents
        for (Player p : match.getPlayersList()) {
            final String nick = p.getNickname();
            final int newP = p.getPrestigePoints();
            final int newF = p.getFood();
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onPrestigeChanged(nick, newP);
                }
            });
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onFoodChanged(nick, newF);
                }
            });
        }
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
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
            if (newEra != prevEra) {
                broadcast(new ViewCall() {
                    @Override public void apply(VirtualView v) throws RemoteException {
                        v.onEraChanged(newEra);
                    }
                });
            }
            final int round = match.getCurrentRound();
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
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

    private void endGame() {
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
        }
        currentPlayerNickname = null;
        phase = new PhaseLogic.GameEnded();
        final List<String> leaderboard = finalLeaderboard != null
                ? new ArrayList<>(finalLeaderboard) : new ArrayList<String>();
        final List<String> winners = finalWinners != null
                ? new ArrayList<>(finalWinners) : new ArrayList<String>();
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onGameEnded(leaderboard, winners);
            }
        });
    }

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

    /** Accettato in qualsiasi fase (gestisce la cascata). */
    void acceptDisconnect(final String nick) {
        // Idempotenza: se il client \u00e8 gi\u00e0 stato rimosso da un altro
        // thread (es. due send paralleli falliti verso lo stesso client),
        // non ripetere la cascata di broadcast.
        if (!server.unregisterIfPresent(nick)) {
            return;
        }

        boolean wasJoined;
        if (match == null) {
            wasJoined = joinedNicknames.remove(nick);
            if (nick.equals(firstPlayerNickname)) {
                firstPlayerNickname = joinedNicknames.isEmpty() ? null : joinedNicknames.get(0);
            }
            if (joinedNicknames.isEmpty()) {
                resetToLobby();
                return;
            }
        } else {
            wasJoined = joinedNicknames.contains(nick);
        }

        // Se il client non era ufficialmente in lobby/match (es. era
        // stato rifiutato al connect), non c'\u00e8 nessuno da
        // informare della sua disconnessione.
        if (!wasJoined) {
            return;
        }

        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onPlayerDisconnected(nick);
            }
        });
        // Se in match e la partita non \u00e8 finita: gestisci eventuale pausa.
        if (match != null && !phase.isFinished()) {
            int connected = countConnectedInMatch();
            if (connected <= 1) {
                enterPause();
                final String reason = connected == 0
                        ? "all clients disconnected"
                        : "only one client left";
                broadcast(new ViewCall() {
                    @Override public void apply(VirtualView v) throws RemoteException {
                        v.onMatchPaused(reason);
                    }
                });
                return;
            }
            // 2+: skip turno se era il suo
            if (nick.equals(currentPlayerNickname)) {
                phase.advanceCurrent(this);
            }
        }
    }

    /** Accettato in {@link PhaseLogic.PausedWaitingReconnect}. */
    void acceptReconnect(final String nick, VirtualView view) {
        // Riconnessione: il nickname deve essere noto.
        if (!joinedNicknames.contains(nick)) {
            rejectAction(nick, "match in progress; cannot join now");
            return;
        }
        // Spedisce lo snapshot al riconnesso.
        final GameSnapshot snap = buildSnapshot();
        sendToNickname(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onGameSnapshot(snap);
            }
        });
        // Annuncia agli altri.
        broadcastExcept(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onPlayerReconnected(nick);
            }
        });
        // Se ora ci sono 2+ connessi, esci dalla pausa.
        int connected = countConnectedInMatch();
        if (connected >= 2) {
            resumeFromPause();
            broadcast(new ViewCall() {
                @Override public void apply(VirtualView v) throws RemoteException {
                    v.onMatchResumed();
                }
            });
            // Re-invia il *Turn al giocatore di turno corrente, cos\u00ec
            // se il giocatore appena riconnesso \u00e8 lui, riceve il
            // prompt; se non lo \u00e8, vede comunque chi tocca.
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
        // Se il currentPlayerNickname \u00e8 ancora disconnesso, salta.
        if (currentPlayerNickname != null && !server.isConnected(currentPlayerNickname)) {
            phase.advanceCurrent(this);
        }
    }

    /** Chiamato da {@link PhaseLogic.PickOfferingTile#advanceCurrent}. */
    void advanceOfferingPicker() {
        currentOfferingPickIndex++;
        advanceOfferingPickerUntilConnected();
    }

    /** Chiamato da {@link PhaseLogic.TurnPickCardsCharacter#advanceCurrent}
     *  e {@link PhaseLogic.TurnPickCardsBuilding#advanceCurrent}. */
    void advanceResolver() {
        currentResolutionIndex++;
        advanceResolverUntilConnectedAndContinue();
    }

    /** Chiamato da {@link PhaseLogic.ResetTurnOrderAndPickExtraBuilding#advanceCurrent}. */
    void skipExtraPick() {
        extraPickPlayer = null;
        enterResolveBuildingsAndEvents();
    }

    // === re-invio del *Turn corrente (per rejection / reconnect) =========

    /** Re-invia a {@code nick} l'evento {@code onColorTurn} corrente. */
    void sendColorTurnTo(String nick) {
        final String current = currentPlayerNickname;
        final Set<TotemColor> avail = new HashSet<>(availableColors);
        if (current == null) return;
        sendToNickname(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onColorTurn(current, avail);
            }
        });
    }

    /** Re-invia a {@code nick} l'evento {@code onOfferingTileTurn} corrente. */
    void sendOfferingTileTurnTo(String nick) {
        if (currentPlayerNickname == null || match == null) return;
        final String current = currentPlayerNickname;
        final int trackSize = match.getBoard().getOfferingTrack().size();
        final Board board = match.getBoard();
        sendToNickname(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onBoardSnapshot(board);
                v.onOfferingTileTurn(current, trackSize);
            }
        });
    }

    /** Re-invia a {@code nick} l'evento {@code onCharacterPickTurn} corrente. */
    void sendCharacterPickTurnTo(String nick) {
        if (currentPlayerNickname == null) return;
        final String current = currentPlayerNickname;
        final int upper = currentUpperBudget;
        final int lower = currentLowerBudget;
        final Board board = match != null ? match.getBoard() : null;
        sendToNickname(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                if (board != null) v.onBoardSnapshot(board);
                v.onCharacterPickTurn(current, upper, lower);
            }
        });
    }

    /** Re-invia a {@code nick} l'evento {@code onBuildingPickTurn} corrente. */
    void sendBuildingPickTurnTo(String nick) {
        if (currentPlayerNickname == null) return;
        final String current = currentPlayerNickname;
        final int upperRem = currentUpperRemainingForBuilding;
        final int lowerRem = currentLowerRemainingForBuilding;
        final Board board = match != null ? match.getBoard() : null;
        sendToNickname(nick, new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                if (board != null) v.onBoardSnapshot(board);
                v.onBuildingPickTurn(current, upperRem, lowerRem);
            }
        });
    }

    /** Re-invia a {@code nick} l'evento {@code onExtraPickTurn} corrente. */
    void sendExtraPickTurnTo(String nick) {
        if (currentPlayerNickname == null) return;
        final String current = currentPlayerNickname;
        final Board board = match != null ? match.getBoard() : null;
        sendToNickname(nick, new ViewCall() {
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

    private void handleLonePlayerTimeout() {
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
        final List<String> lb = new ArrayList<>(finalLeaderboard);
        final List<String> wn = new ArrayList<>(finalWinners);
        broadcast(new ViewCall() {
            @Override public void apply(VirtualView v) throws RemoteException {
                v.onGameEnded(lb, wn);
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
    // 6. Reset alla lobby e utility statiche
    // ====================================================================

    private void resetToLobby() {
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
