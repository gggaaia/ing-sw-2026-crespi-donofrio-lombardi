package it.polimi.ingsw.controller.servercontroller;

import it.polimi.ingsw.Server;
import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;
import it.polimi.ingsw.common.dto.Phase;
import it.polimi.ingsw.controller.servercontroller.notification.GameStateNotification;
import it.polimi.ingsw.controller.servercontroller.notification.InfoNotification;
import it.polimi.ingsw.controller.servercontroller.notification.Notification;
import it.polimi.ingsw.controller.servercontroller.notification.Target;
import it.polimi.ingsw.controller.servercontroller.phase.ColorPickingStrategy;
import it.polimi.ingsw.controller.servercontroller.phase.GameEndedStrategy;
import it.polimi.ingsw.controller.servercontroller.phase.LobbyWaitingForNicknameStrategy;
import it.polimi.ingsw.controller.servercontroller.phase.PausedWaitingReconnectStrategy;
import it.polimi.ingsw.controller.servercontroller.phase.PhaseStrategy;
import it.polimi.ingsw.controller.servercontroller.phase.PickOfferingTileStrategy;
import it.polimi.ingsw.controller.servercontroller.phase.ResetTurnOrderAndPickExtraBuildingStrategy;
import it.polimi.ingsw.controller.servercontroller.phase.ResolveBuildingsAndEventsStrategy;
import it.polimi.ingsw.controller.servercontroller.phase.TurnPickCardsCharacterStrategy;
import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.GameManager;
import it.polimi.ingsw.model.Match;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.TurnOrderSlot;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Server-side controller that runs the game's state machine. The
 * actual phase-by-phase behavior lives in PhaseStrategy subclasses;
 * this class is the per-process state holder + the entry-point each
 * adapter calls.
 *
 * Each delegate (handleConnect, handleSetPlayerCount, ...) is
 * synchronized on the controller instance, so two RMI worker threads
 * handling different inputs from different clients are serialized
 * through the controller's state machine. Network delivery
 * (Server.dispatch) runs AFTER each handle returns, outside the lock,
 * so it cannot deadlock with the RMI pull-getters that read
 * ClientData buffers.
 *
 * The static nested class ClientData is the per-client state holder
 * used by the registry in Server.java and by the dispatcher.
 */
public class ServerController {

    // --- lobby state (used in the 3 lobby phases) ------------------------

    private Phase phase = Phase.WAITING_FOR_NICKNAME;
    private final List<ClientData> clients = new ArrayList<>();
    private String firstPlayerNickname;
    private int expectedPlayers;
    private String currentPlayerNickname;
    private final Set<TotemColor> availableColors = new HashSet<>();
    private PhaseStrategy strategy;

    // --- match state (populated after color picking finishes) ------------

    private final GameManager gameManager = new GameManager();
    private Match match;
    /**
     * Order in which clients still have to pick a color. Filled by
     * enterColorPicking() when the lobby transitions into
     * COLOR_PICKING.
     */
    private final Queue<ClientData> colorPickQueue = new LinkedList<>();
    /**
     * Order in which players will place their totem on an offering
     * tile in the current round. Filled by tryStartMatch() once
     * Match.startMatch has succeeded.
     */
    private List<Player> turnOrderForOfferingPick = new ArrayList<>();
    private int currentOfferingPickIndex;

    /**
     * Order in which players resolve their turn (draw character
     * cards, then buy buildings) inside one round. Filled by
     * enterCharacterPickForFirstResolutionPlayer() after every
     * connected player has placed (or been skipped on) the offering
     * track.
     */
    private List<Player> resolutionOrder = new ArrayList<>();
    private int currentResolutionIndex;

    /**
     * Budgets for the player currently resolving. Set by
     * enterCharacterPickForCurrentResolutionPlayer() from the
     * offering tile they posted on. Upper/Lower budgets cap the
     * character pick; the residual fields are reused as the cap on
     * the building pick of the same turn.
     */
    private int currentUpperBudget;
    private int currentLowerBudget;
    private int currentUpperRemainingForBuilding;
    private int currentLowerRemainingForBuilding;

    /**
     * The player entitled to the optional extra pick granted by the
     * special building (an upper-row building). Set by
     * findSpecialBuildingOwner() inside afterAllResolutionsDone();
     * cleared again when the extra pick has been processed.
     */
    private Player extraPickPlayer;

    /**
     * Forced winners for the lone-survivor / pause-timeout end. Step
     * 8 will populate this from forceLonePlayerVictory(). Step 6c
     * leaves it empty in the normal-end path.
     */
    private List<String> forcedWinners = new ArrayList<>();

    /**
     * Final leaderboard (nicknames sorted by prestige descending,
     * food descending) and winners (the prefix of the leaderboard
     * whose prestige + food equals the top player's). Both are
     * computed once inside endGame() so buildGameState() does not
     * have to re-sort the player list on every notification dispatch
     * after the match is over.
     */
    private List<String> finalLeaderboard;
    private List<String> finalWinners;

    // --- pause / reconnection state (step 7) -----------------------------

    /**
     * Reference to the Server (the network-side dispatcher). Used
     * only by the lone-player timeout: the timer thread fires
     * outside any RMI / socket call, so when it transitions the
     * controller to GAME_ENDED it must dispatch the resulting
     * notifications itself. Wired by Server.java's constructor via
     * setServer(this).
     */
    private Server server;

    /**
     * Phase the controller was in immediately before the most recent
     * pause, captured by enterPause() and restored by
     * resumeFromPause(). The matching strategy is captured in
     * strategyBeforePause so we restore both atomically.
     */
    private Phase phaseBeforePause;
    private PhaseStrategy strategyBeforePause;

    /**
     * The 2-minute lone-player timer. Started by
     * startLonePlayerTimeout when only one client is connected
     * during pause. Cancelled when the connected count rises back to
     * 2+ (we resume) or drops to 0 (no one to win), or when the
     * timer fires and forceLonePlayerVictory takes over.
     */
    private Timer lonePlayerTimer;

    /**
     * The architecture spec says 2 minutes; the constant lives here
     * so it can be tweaked for tests if needed.
     */
    public static final long LONE_PLAYER_TIMEOUT_MS = 120_000L;

    public ServerController() {
        availableColors.add(TotemColor.RED);
        availableColors.add(TotemColor.WHITE);
        availableColors.add(TotemColor.BLACK);
        availableColors.add(TotemColor.YELLOW);
        availableColors.add(TotemColor.BLUE);
        this.strategy = new LobbyWaitingForNicknameStrategy();
    }

    // --- state getters/setters used by phase strategies ------------------

    public Phase getPhase() {
        return phase;
    }

    /**
     * Atomic phase + strategy transition. Strategies call this when
     * they want the controller to switch behavior.
     */
    public void setPhase(Phase newPhase, PhaseStrategy newStrategy) {
        this.phase = newPhase;
        this.strategy = newStrategy;
    }

    public List<ClientData> getClients() {
        return clients;
    }

    public String getFirstPlayerNickname() {
        return firstPlayerNickname;
    }

    public void setFirstPlayerNickname(String nick) {
        this.firstPlayerNickname = nick;
    }

    public int getExpectedPlayers() {
        return expectedPlayers;
    }

    public void setExpectedPlayers(int n) {
        this.expectedPlayers = n;
    }

    public String getCurrentPlayerNickname() {
        return currentPlayerNickname;
    }

    public void setCurrentPlayerNickname(String nick) {
        this.currentPlayerNickname = nick;
    }

    public Set<TotemColor> getAvailableColors() {
        return availableColors;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match m) {
        this.match = m;
    }

    public Queue<ClientData> getColorPickQueue() {
        return colorPickQueue;
    }

    public List<Player> getTurnOrderForOfferingPick() {
        return turnOrderForOfferingPick;
    }

    public void setTurnOrderForOfferingPick(List<Player> turnOrder) {
        this.turnOrderForOfferingPick = turnOrder;
    }

    public int getCurrentOfferingPickIndex() {
        return currentOfferingPickIndex;
    }

    public void setCurrentOfferingPickIndex(int i) {
        this.currentOfferingPickIndex = i;
    }

    public List<Player> getResolutionOrder() {
        return resolutionOrder;
    }

    public void setResolutionOrder(List<Player> resolutionOrder) {
        this.resolutionOrder = resolutionOrder;
    }

    public int getCurrentResolutionIndex() {
        return currentResolutionIndex;
    }

    public void setCurrentResolutionIndex(int i) {
        this.currentResolutionIndex = i;
    }

    public int getCurrentUpperBudget() {
        return currentUpperBudget;
    }

    public int getCurrentLowerBudget() {
        return currentLowerBudget;
    }

    public int getCurrentUpperRemainingForBuilding() {
        return currentUpperRemainingForBuilding;
    }

    public int getCurrentLowerRemainingForBuilding() {
        return currentLowerRemainingForBuilding;
    }

    public void setCurrentUpperRemainingForBuilding(int n) {
        this.currentUpperRemainingForBuilding = n;
    }

    public void setCurrentLowerRemainingForBuilding(int n) {
        this.currentLowerRemainingForBuilding = n;
    }

    public Player getExtraPickPlayer() {
        return extraPickPlayer;
    }

    public void setExtraPickPlayer(Player p) {
        this.extraPickPlayer = p;
    }

    public List<String> getForcedWinners() {
        return forcedWinners;
    }

    public void setForcedWinners(List<String> winners) {
        this.forcedWinners = winners;
    }

    public PhaseStrategy getStrategy() {
        return strategy;
    }

    /**
     * Wired by Server.java's constructor so the controller can
     * dispatch notifications produced asynchronously by the
     * lone-player timer thread.
     */
    public void setServer(Server server) {
        this.server = server;
    }

    /**
     * Walks the controller's clients list and returns the entry whose
     * nickname matches, or null if not found.
     */
    public ClientData findByNickname(String nickname) {
        for (ClientData c : clients) {
            if (c.getNickname().equals(nickname)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Walks the controller's clients list and returns the entry whose
     * Player object IS (reference equality) the given one. Player
     * objects are unique per nickname so reference equality is exact.
     */
    public ClientData findByPlayer(Player p) {
        for (ClientData c : clients) {
            if (c.getPlayer() == p) {
                return c;
            }
        }
        return null;
    }

    // --- phase transition helpers ----------------------------------------

    /**
     * Lobby strategies call this when the lobby is full and the
     * controller is ready for color picking. Populates the
     * colorPickQueue with all clients in entry order, sets
     * currentPlayerNickname to the head of the queue, and switches
     * the strategy to ColorPickingStrategy.
     */
    public void enterColorPicking() {
        colorPickQueue.clear();
        colorPickQueue.addAll(clients);
        currentPlayerNickname = colorPickQueue.peek() != null
                ? colorPickQueue.peek().getNickname()
                : null;
        setPhase(Phase.COLOR_PICKING, new ColorPickingStrategy());
    }

    /**
     * Walks turnOrderForOfferingPick from the current
     * currentOfferingPickIndex forward, looking for a player whose
     * client is connected. The first connected player becomes the
     * new currentPlayerNickname and the controller stays in
     * PICK_OFFERING_TILE.
     *
     * If no connected player is found before the list ends, this
     * method moves the controller into the next phase by calling
     * enterCharacterPickForFirstResolutionPlayer().
     */
    public void advanceOfferingPickerUntilConnected() {
        while (currentOfferingPickIndex < turnOrderForOfferingPick.size()) {
            Player p = turnOrderForOfferingPick.get(currentOfferingPickIndex);
            ClientData cd = findByPlayer(p);
            if (cd != null && cd.isConnected()) {
                currentPlayerNickname = p.getNickname();
                return;
            }
            currentOfferingPickIndex++;
        }
        enterCharacterPickForFirstResolutionPlayer();
    }

    /**
     * After PICK_OFFERING_TILE has finished (every connected player
     * placed a totem), build resolutionOrder from the offering track
     * and start the per-player character-then-building loop with
     * the first connected player.
     *
     * If resolutionOrder is empty (nobody on the track), skip
     * directly to RESOLVE_BUILDINGS_AND_EVENTS. Step 6c will fill
     * that strategy; for now the placeholder lands the controller in
     * a phase whose default rejection prevents any further input.
     */
    public void enterCharacterPickForFirstResolutionPlayer() {
        resolutionOrder = match.getBoard().getOfferingTrackPlayersOrder(match.getPlayersList());
        currentResolutionIndex = 0;
        if (resolutionOrder.isEmpty()) {
            setPhase(Phase.RESOLVE_BUILDINGS_AND_EVENTS, new ResolveBuildingsAndEventsStrategy());
            currentPlayerNickname = null;
            return;
        }
        advanceResolverUntilConnectedAndContinue();
    }

    /**
     * Walks resolutionOrder from currentResolutionIndex forward,
     * looking for a connected player. The first one becomes the
     * resolving player and the controller transitions to
     * TURN_PICK_CARDS_CHARACTER (via
     * enterCharacterPickForCurrentResolutionPlayer).
     *
     * If nobody is found, runs end-of-round housekeeping.
     */
    public void advanceResolverUntilConnectedAndContinue() {
        while (currentResolutionIndex < resolutionOrder.size()) {
            Player p = resolutionOrder.get(currentResolutionIndex);
            ClientData cd = findByPlayer(p);
            if (cd != null && cd.isConnected()) {
                enterCharacterPickForCurrentResolutionPlayer();
                return;
            }
            currentResolutionIndex++;
        }
        afterAllResolutionsDone();
    }

    /**
     * Sets up the budgets and food bonus for the player at
     * currentResolutionIndex from the offering tile they posted on,
     * then transitions to TURN_PICK_CARDS_CHARACTER.
     */
    public void enterCharacterPickForCurrentResolutionPlayer() {
        Player p = resolutionOrder.get(currentResolutionIndex);
        int tileIndex = match.getBoard().getOfferingTrackIndex(p);
        OfferingTile tile = match.getBoard().getOfferingTrack().get(tileIndex);
        currentUpperBudget = tile.getTopCardsToGet();
        currentLowerBudget = tile.getBottomCardsToGet();
        currentUpperRemainingForBuilding = currentUpperBudget;
        currentLowerRemainingForBuilding = currentLowerBudget;
        p.changeFood(tile.getFoodGained());
        currentPlayerNickname = p.getNickname();
        setPhase(Phase.TURN_PICK_CARDS_CHARACTER, new TurnPickCardsCharacterStrategy());
    }

    /**
     * End-of-round housekeeping (GAME_FLOW.md section 6.2 step 7).
     *
     * Five model-side actions, in order:
     *   1. clearTurnOrderTile: empty every slot of the turn-order
     *      tile so the totems can be re-placed by the offering-track
     *      order.
     *   2. moveOfferingTrackToTurnOrder: walk the offering track
     *      left-to-right, copy each occupied tile's owner onto the
     *      next free turn-order slot, and clear the offering tile.
     *   3. appendMissingPlayersToTurnOrder: any player who never
     *      placed a totem this round is appended at the first free
     *      slot. This catches disconnected players so they keep a
     *      slot for next round.
     *   4. resolveTurnOrderTile: apply the per-slot prestige + food
     *      bonus to the slot owner (and trigger turnOrderBonus on
     *      their buildings if food > 0).
     *   5. findSpecialBuildingOwner: find the player whose
     *      collection contains an upper-row building (DrawUpperRow);
     *      they get an extra pick.
     *
     * After housekeeping, if there is a special-building owner who
     * is connected, the controller transitions to
     * RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING. Otherwise it goes
     * straight to RESOLVE_BUILDINGS_AND_EVENTS.
     */
    public void afterAllResolutionsDone() {
        // Step 1: clear every slot of the turn-order tile.
        for (Player p : match.getPlayersList()) {
            match.getBoard().getTurnOrderTile().clearSlot(p);
        }
        // Step 2: copy offering-track ownership to the turn-order tile.
        match.getBoard().moveOfferingTrackToTurnOrder(match.getPlayersList());
        // Step 3: append players who never placed a totem this round.
        appendMissingPlayersToTurnOrderTile();
        // Step 4: apply slot bonuses.
        match.resolveTurnOrderTile();
        // Step 5: find the special-building owner, if any.
        extraPickPlayer = findSpecialBuildingOwner();
        if (extraPickPlayer == null) {
            enterResolveBuildingsAndEvents();
            return;
        }
        ClientData ownerSlot = findByPlayer(extraPickPlayer);
        if (ownerSlot == null || !ownerSlot.isConnected()) {
            // Owner is gone; skip the extra pick and resolve.
            enterResolveBuildingsAndEvents();
            return;
        }
        currentPlayerNickname = extraPickPlayer.getNickname();
        setPhase(Phase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING,
                new ResetTurnOrderAndPickExtraBuildingStrategy());
    }

    /**
     * For every Player not currently on the turn-order tile, place
     * their totem on the first free slot.
     */
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
            if (alreadyOnTile) {
                continue;
            }
            int freeSlot = -1;
            for (int i = 0; i < slots.length; i++) {
                if (!slots[i].isOccupied()) {
                    freeSlot = i;
                    break;
                }
            }
            if (freeSlot < 0) {
                break;
            }
            turnOrderTile.placeTotem(p, freeSlot);
        }
    }

    /**
     * Walks every Player's building collection looking for a building
     * whose hasUpperRowBuilding(p) returns true (currently only
     * DrawUpperRow does). Returns the first owner found, or null.
     */
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

    /**
     * RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING ends here when the
     * extra pick is done (or skipped). Runs the auto-resolution
     * (resolveBuildings + resolveEvents) and then either ends the
     * match (round 11 / EmptyDeckException on next round setup) or
     * transitions back to PICK_OFFERING_TILE for the next round.
     */
    public void enterResolveBuildingsAndEvents() {
        extraPickPlayer = null;
        currentPlayerNickname = null;
        setPhase(Phase.RESOLVE_BUILDINGS_AND_EVENTS, new ResolveBuildingsAndEventsStrategy());

        match.resolveBuildings();
        match.resolveEvents();

        if (match.getCurrentRound() == Match.FINAL_ROUND) {
            endGame();
            return;
        }
        try {
            match.moveToNextRound();
        } catch (EmptyDeckException e) {
            endGame();
            return;
        }
        // Next round: rebuild the offering-pick order from the new
        // turn-order tile.
        turnOrderForOfferingPick = match.getBoard().getPlayersTurnOrder(match.getPlayersList());
        currentOfferingPickIndex = 0;
        // Reset the per-turn budgets (they only mean something inside
        // TURN_PICK_CARDS_*).
        currentUpperBudget = 0;
        currentLowerBudget = 0;
        currentUpperRemainingForBuilding = 0;
        currentLowerRemainingForBuilding = 0;
        setPhase(Phase.PICK_OFFERING_TILE, new PickOfferingTileStrategy());
        advanceOfferingPickerUntilConnected();
    }

    /**
     * Final round reached or deck exhausted; close the match. Step
     * 8's GameEndedStrategy (currently a stub) is the destination.
     * Real leaderboard/winners computation lives in step 8.
     */
    /**
     * Final round (or deck-exhaustion) cleanup, per GAME_FLOW.md
     * section 6.3 step 2:
     *   1. board.shiftUpToLow() — drop the upper row to the lower
     *      row (any final-round event cards that were still up there
     *      are now active in the lower row).
     *   2. match.resolveEvents() — apply those just-dropped events
     *      on top of the resolveBuildings + resolveEvents pair that
     *      enterResolveBuildingsAndEvents already ran for round 11.
     *   3. match.endMatch() — internally calls
     *      Match.resolveEndMatch(), which is the end-of-game scoring
     *      pass (inventor/painter PP, end-game building effects,
     *      upper-row building bonus).
     *   4. match.createLeaderboard() — sort players by prestige
     *      descending then food descending, and from that compute
     *      the winners (every player tied at the top by both
     *      prestige AND food). Cached so subsequent
     *      buildGameState() calls just read fields.
     *
     * If forcedWinners has been populated by a step-7
     * forceLonePlayerVictory() path, those names override the
     * computed winners; the leaderboard is still sorted normally.
     */
    public void endGame() {
        if (match != null) {
            match.getBoard().shiftUpToLow();
            match.resolveEvents();
            match.endMatch();   // internally invokes match.resolveEndMatch()

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
        setPhase(Phase.GAME_ENDED, new GameEndedStrategy());
    }

    // --- pause / reconnection helpers (step 7) ---------------------------

    /**
     * Counts ClientData entries currently flagged connected.
     */
    public int countConnectedClients() {
        int n = 0;
        for (ClientData c : clients) {
            if (c.isConnected()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Returns the unique connected ClientData when there is exactly
     * one, or null otherwise. Used by handleLonePlayerTimeout to
     * find who wins by default.
     */
    public ClientData findLoneConnectedClient() {
        ClientData lone = null;
        for (ClientData c : clients) {
            if (c.isConnected()) {
                if (lone != null) {
                    return null;
                }
                lone = c;
            }
        }
        return lone;
    }

    /**
     * Switches the controller to PAUSED_WAITING_RECONNECT, capturing
     * the current phase + strategy in phaseBeforePause /
     * strategyBeforePause so resumeFromPause() can restore them.
     * Idempotent: if the controller is already paused (re-entrant
     * disconnect), only the timer state is updated. The timer is
     * (re)started when there is exactly one connected client and
     * cancelled when there are zero.
     */
    public void enterPause() {
        if (!strategy.isPaused()) {
            phaseBeforePause = phase;
            strategyBeforePause = strategy;
            setPhase(Phase.PAUSED_WAITING_RECONNECT, new PausedWaitingReconnectStrategy());
        }
        int connected = countConnectedClients();
        if (connected == 1) {
            startLonePlayerTimeout();
        } else {
            cancelLonePlayerTimeoutIfActive();
        }
    }

    /**
     * Restores the phase + strategy captured by enterPause and asks
     * the restored strategy to skip the current actor if they are
     * still disconnected. Called by
     * PausedWaitingReconnectStrategy.handleConnect when the connected
     * count returns to >= 2.
     */
    public void resumeFromPause() {
        if (!strategy.isPaused()) {
            return;
        }
        cancelLonePlayerTimeoutIfActive();
        Phase resumePhase = phaseBeforePause;
        PhaseStrategy resumeStrategy = strategyBeforePause;
        phaseBeforePause = null;
        strategyBeforePause = null;
        setPhase(resumePhase, resumeStrategy);
        resumeStrategy.advanceCurrentPlayerIfDisconnected(this);
    }

    /**
     * Schedules handleLonePlayerTimeout() to fire LONE_PLAYER_TIMEOUT_MS
     * milliseconds from now. The Timer is daemon so it does not keep
     * the JVM alive on its own.
     */
    public void startLonePlayerTimeout() {
        cancelLonePlayerTimeoutIfActive();
        lonePlayerTimer = new Timer("lone-player-timeout", true);
        lonePlayerTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                handleLonePlayerTimeout();
            }
        }, LONE_PLAYER_TIMEOUT_MS);
    }

    /**
     * Cancels and forgets the current lone-player timer if any.
     */
    public void cancelLonePlayerTimeoutIfActive() {
        if (lonePlayerTimer != null) {
            lonePlayerTimer.cancel();
            lonePlayerTimer = null;
        }
    }

    /**
     * Fires from the lone-player Timer thread when 2 minutes have
     * elapsed. Re-checks the conditions (the situation may have
     * changed in the meantime: a reconnection brought us back to
     * 2+, or another disconnect dropped us to 0); if there is still
     * exactly one connected client, declares them winner via
     * forceLonePlayerVictory and dispatches the resulting
     * notifications. The synchronized block keeps state mutation in
     * step with regular handle... calls; the dispatch happens
     * outside the lock to avoid deadlocking with the RMI pull-getters.
     */
    private void handleLonePlayerTimeout() {
        ClientData lone;
        List<Notification> notifications;
        synchronized (this) {
            if (!strategy.isPaused()) {
                return;
            }
            if (countConnectedClients() != 1) {
                return;
            }
            lone = findLoneConnectedClient();
            if (lone == null || lone.getPlayer() == null) {
                return;
            }
            notifications = forceLonePlayerVictory(lone.getPlayer());
        }
        if (server != null) {
            server.dispatch(lone, notifications);
        }
    }

    /**
     * Concludes the match by declaring the given Player the sole
     * forced winner. Closes the match in GameManager, computes the
     * leaderboard for context, transitions to GAME_ENDED, and
     * returns the notifications that announce the outcome (info +
     * broadcast game state). Used by handleLonePlayerTimeout, but
     * also exposed for hypothetical step-8 admin paths.
     */
    public List<Notification> forceLonePlayerVictory(Player lone) {
        forcedWinners = new ArrayList<>();
        forcedWinners.add(lone.getNickname());
        if (match != null) {
            // For the lone-survivor outcome we close the match in the
            // registry but skip the resolveBuildings/Events/EndMatch
            // cascade: the spec says the match is decided by the
            // timeout, not by scoring.
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
        strategyBeforePause = null;
        currentPlayerNickname = null;
        setPhase(Phase.GAME_ENDED, new GameEndedStrategy());

        List<Notification> out = new ArrayList<>();
        out.add(new InfoNotification(
                Target.BROADCAST,
                "Reconnect timeout reached. " + lone.getNickname() + " wins by default."));
        out.add(new GameStateNotification(
                Target.BROADCAST,
                buildGameState("Match ended - " + lone.getNickname() + " wins by default")));
        return out;
    }

    /**
     * Walks the leaderboard from the top down and collects every
     * player whose prestige AND food equal the top player's. Result
     * has size 1 when there is no tie, or n when n players share the
     * exact top result.
     */
    private List<String> computeWinners(List<Player> sorted) {
        List<String> winners = new ArrayList<>();
        if (sorted.isEmpty()) {
            return winners;
        }
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

    /**
     * Resets all per-match state back to lobby: closes the match in
     * GameManager, clears match-related queues / indexes, restores
     * the full available-color set, clears each client's pickedColor
     * and Player. Used by ColorPickingStrategy when match setup fails
     * (DeckLoadException / EmptyDeckException) and by future
     * disconnect-driven reset paths.
     *
     * After the reset the controller is back in WAITING_FOR_NICKNAME
     * with no first player; clients stay in the registry but their
     * lobby state is wiped so the lobby can be rebuilt.
     */
    public void resetToLobby() {
        if (match != null) {
            gameManager.closeMatch(match.getMatchID());
            match = null;
        }
        turnOrderForOfferingPick = new ArrayList<>();
        currentOfferingPickIndex = 0;
        colorPickQueue.clear();
        availableColors.clear();
        availableColors.add(TotemColor.RED);
        availableColors.add(TotemColor.WHITE);
        availableColors.add(TotemColor.BLACK);
        availableColors.add(TotemColor.YELLOW);
        availableColors.add(TotemColor.BLUE);
        for (ClientData c : clients) {
            c.setPickedColor(null);
            c.setPlayer(null);
        }
        firstPlayerNickname = null;
        expectedPlayers = 0;
        currentPlayerNickname = null;
        setPhase(Phase.WAITING_FOR_NICKNAME, new LobbyWaitingForNicknameStrategy());
    }

    /**
     * Builds the current GameStateDto snapshot. Phase strategies call
     * this whenever they produce a GameStateNotification. Returns
     * defensive copies of every collection so the receiver may not
     * accidentally mutate controller state.
     */
    public GameStateDto buildGameState(String description) {
        List<String> connectedNicknames = new ArrayList<>();
        Map<String, TotemColor> nicknameToColor = new HashMap<>();
        Map<String, Integer> foodByNickname = new HashMap<>();
        Map<String, Integer> prestigeByNickname = new HashMap<>();
        Map<String, List<CharacterCard>> handsByNickname = new HashMap<>();
        Map<String, List<BuildingCard>> buildingsByNickname = new HashMap<>();
        for (ClientData c : clients) {
            connectedNicknames.add(c.getNickname());
            if (c.getPickedColor() != null) {
                nicknameToColor.put(c.getNickname(), c.getPickedColor());
            }
            if (c.getPlayer() != null) {
                foodByNickname.put(c.getNickname(), c.getPlayer().getFood());
                prestigeByNickname.put(c.getNickname(), c.getPlayer().getPrestigePoints());
                // Player.getPlayerHand / getPlayerBuildings already
                // return defensive copies, so no extra wrapping needed.
                handsByNickname.put(c.getNickname(), c.getPlayer().getPlayerHand());
                buildingsByNickname.put(c.getNickname(), c.getPlayer().getPlayerBuildings());
            }
        }
        List<String> colorPickQueueOrder = new ArrayList<>();
        for (ClientData c : colorPickQueue) {
            colorPickQueueOrder.add(c.getNickname());
        }
        return new GameStateDto(
                phase,
                connectedNicknames,
                nicknameToColor,
                colorPickQueueOrder,
                new ArrayList<>(),                 // turnOrderTile: empty until match starts
                currentPlayerNickname,
                new HashSet<>(availableColors),
                match != null ? match.getBoard() : null,
                match != null ? match.getCurrentEra() : null,
                match != null ? match.getCurrentRound() : 0,
                currentUpperBudget,
                currentLowerBudget,
                currentUpperRemainingForBuilding,
                currentLowerRemainingForBuilding,
                foodByNickname,
                prestigeByNickname,
                handsByNickname,
                buildingsByNickname,
                finalLeaderboard,                  // populated only by endGame()
                finalWinners,                      // populated only by endGame()
                description
        );
    }

    // --- adapter entry points (delegate to current strategy) -------------

    public synchronized List<Notification> handleConnect(ClientData client) {
        return strategy.handleConnect(this, client);
    }

    public synchronized List<Notification> handleSetPlayerCount(ClientData client, int count) {
        return strategy.handleSetPlayerCount(this, client, count);
    }

    public synchronized List<Notification> handlePickColor(ClientData client, TotemColor color) {
        return strategy.handlePickColor(this, client, color);
    }

    public synchronized List<Notification> handlePickOfferingTile(ClientData client, int tileIndex) {
        return strategy.handlePickOfferingTile(this, client, tileIndex);
    }

    public synchronized List<Notification> handlePickCharacterCards(ClientData client,
                                                                    List<Integer> upperRowIds,
                                                                    List<Integer> lowerRowIds) {
        return strategy.handlePickCharacterCards(this, client, upperRowIds, lowerRowIds);
    }

    public synchronized List<Notification> handlePickBuildingCards(ClientData client,
                                                                   List<Integer> upperRowIds,
                                                                   List<Integer> lowerRowIds) {
        return strategy.handlePickBuildingCards(this, client, upperRowIds, lowerRowIds);
    }

    public synchronized List<Notification> handlePickExtraCard(ClientData client,
                                                               Integer cardId,
                                                               boolean isCharacter) {
        return strategy.handlePickExtraCard(this, client, cardId, isCharacter);
    }

    public synchronized List<Notification> handleClientDisconnected(ClientData client) {
        return strategy.handleClientDisconnected(this, client);
    }

    // --- per-client state holder -----------------------------------------

    /**
     * Per-client state. The adapter (Rmi_Server,
     * Socket_ClientHandler) gets one of these back from
     * Server.register(nickname, view) when a new client appears, and
     * passes it (or just the nickname) to every ServerController call
     * that originates from that client.
     *
     * Owns the typed back-reference to the client's VirtualView (used
     * only by the dispatcher in Server.java to actually push
     * notifications) plus the controller-managed fields picked color,
     * Player, and connected flag.
     *
     * It also owns four "last-payload" buffers:
     * lastState/lastActionResult/lastInfo/lastError. The dispatcher
     * stores the payload in the matching buffer right before calling
     * the corresponding view.notifyXxx() callback. RMI clients then
     * pull the value via VirtualServer.getXxx(nickname); socket
     * clients use it to assemble the wire message inside
     * Socket_VirtualClient.
     */
    public static class ClientData {

        private final String nickname;
        private VirtualView view;
        private TotemColor pickedColor;
        private Player player;
        // 'connected' is read by the heartbeat / dispatcher threads
        // outside the controller monitor (Server.heartbeat reads it to
        // decide whether to ping; Server.handleDeliveryFailure reads
        // it in its idempotency guard). Both write from inside and
        // outside the monitor (markDisconnected is called by adapter
        // and heartbeat threads BEFORE entering controller.synchronized
        // handleClientDisconnected). 'volatile' is the simplest way
        // to give the cross-thread reads happens-before visibility
        // without forcing the heartbeat to take the controller lock.
        private volatile boolean connected;

        private volatile GameStateDto lastState;
        private volatile ActionResultDto lastActionResult;
        private volatile String lastInfo;
        private volatile String lastError;

        public ClientData(String nickname, VirtualView view) {
            this.nickname = nickname;
            this.view = view;
            this.connected = true;
        }

        public String getNickname() {
            return nickname;
        }

        public VirtualView getView() {
            return view;
        }

        public void setView(VirtualView view) {
            this.view = view;
        }

        public TotemColor getPickedColor() {
            return pickedColor;
        }

        public void setPickedColor(TotemColor pickedColor) {
            this.pickedColor = pickedColor;
        }

        public Player getPlayer() {
            return player;
        }

        public void setPlayer(Player player) {
            this.player = player;
        }

        public boolean isConnected() {
            return connected;
        }

        public void markConnected() {
            this.connected = true;
        }

        public void markDisconnected() {
            this.connected = false;
        }

        public GameStateDto getLastState() {
            return lastState;
        }

        public void setLastState(GameStateDto lastState) {
            this.lastState = lastState;
        }

        public ActionResultDto getLastActionResult() {
            return lastActionResult;
        }

        public void setLastActionResult(ActionResultDto lastActionResult) {
            this.lastActionResult = lastActionResult;
        }

        public String getLastInfo() {
            return lastInfo;
        }

        public void setLastInfo(String lastInfo) {
            this.lastInfo = lastInfo;
        }

        public String getLastError() {
            return lastError;
        }

        public void setLastError(String lastError) {
            this.lastError = lastError;
        }
    }
}
