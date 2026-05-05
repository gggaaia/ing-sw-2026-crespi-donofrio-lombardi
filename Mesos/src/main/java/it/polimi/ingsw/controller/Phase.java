package it.polimi.ingsw.controller;

import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.network.shared.ClientCallback;
import it.polimi.ingsw.network.shared.GamePhase;

import java.util.ArrayList;
import java.util.List;

/**
 * State-pattern strategy for one game phase.
 * <p>
 * Every {@link GamePhase} value is mapped to exactly one {@code Phase}
 * subclass via the {@link GameController#STRATEGIES} EnumMap. The
 * controller's public message handlers ({@code handleNickname},
 * {@code handlePickOfferingTile}, …) delegate the per-phase decision to
 * the current strategy instead of branching on the enum directly.
 * <p>
 * <b>Default behaviour for every protocol verb is "reject".</b> Concrete
 * phases override only the verbs they actually accept; everything else
 * falls through to the base class's polite refusal. This means:
 * <ul>
 *   <li>You cannot accidentally forget a phase guard — the type system
 *       guarantees that every phase has a defined behavior for every
 *       message.</li>
 *   <li>{@code instanceof} is never needed — JVM virtual dispatch picks
 *       the right override automatically.</li>
 *   <li>Adding a new phase = one new subclass + one EnumMap entry.</li>
 * </ul>
 * <p>
 * Concrete subclasses live in this same file (package-private) so the
 * whole state machine is reviewable in one place.
 */
public abstract class Phase {

    // ------------------------------------------------------------------
    // Action constants for ActionResultMessage. Must match the strings
    // the client's onActionResult dispatcher expects.
    // ------------------------------------------------------------------

    /** Action label for nickname-related ActionResultMessages. */
    static final String ACTION_NICKNAME = "NICKNAME";
    /** Action label for player-count ActionResultMessages. */
    static final String ACTION_PLAYER_COUNT = "PLAYER_COUNT";
    /** Action label for color-pick ActionResultMessages. */
    static final String ACTION_PICK_COLOR = "PICK_COLOR";
    /** Action label for offering-tile-pick ActionResultMessages. */
    static final String ACTION_PICK_OFFERING_TILE = "PICK_OFFERING_TILE";
    /** Action label for character-pick ActionResultMessages. */
    static final String ACTION_PICK_CHARACTERS = "PICK_CHARACTERS";
    /** Action label for building-pick ActionResultMessages. */
    static final String ACTION_PICK_BUILDINGS = "PICK_BUILDINGS";
    /** Action label for extra-pick ActionResultMessages. */
    static final String ACTION_PICK_EXTRA = "PICK_EXTRA";

    // ==================================================================
    // Protocol verbs (default = reject). Concrete phases override.
    // ==================================================================

    /**
     * The "new joiner" branch of {@code handleNickname}: the client typed
     * a nickname that doesn't match any existing slot or restored player.
     * <p>
     * Default: reject (only lobby phases override). The reconnect cases
     * (existing slot, ghost from snapshot) are handled centrally in
     * {@link GameController#handleNickname} BEFORE dispatching here.
     */
    public void onNewJoiner(GameController c, ClientCallback client, String trimmed) {
        c.sendActionResult(client, ACTION_NICKNAME, false,
                "You cannot join the game now");
        c.sendGameStateTo(client, "Nickname rejected");
    }

    /** Handle a {@code SetPlayerCountMessage}. Default: reject. */
    public void onPlayerCount(GameController c, ClientCallback client, int count) {
        c.sendActionResult(client, ACTION_PLAYER_COUNT, false,
                "You cannot set player count now");
        c.sendGameStateTo(client, "Wrong phase");
    }

    /** Handle a {@code PickColorMessage}. Default: reject. */
    public void onPickColor(GameController c, ClientCallback client, TotemColor color) {
        c.sendActionResult(client, ACTION_PICK_COLOR, false,
                "You cannot pick a color now");
        c.sendGameStateTo(client, "Wrong phase");
    }

    /** Handle a {@code PickOfferingTileMessage}. Default: reject. */
    public void onPickOfferingTile(GameController c, ClientCallback client, int idx) {
        c.sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                "You cannot pick an offering tile now");
        c.sendGameStateTo(client, "Wrong phase");
    }

    /** Handle a {@code PickCharacterCardsMessage}. Default: reject. */
    public void onPickCharacterCards(GameController c, ClientCallback client,
                                     List<Integer> upper, List<Integer> lower) {
        c.sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                "You cannot pick character cards now");
        c.sendGameStateTo(client, "Wrong phase");
    }

    /** Handle a {@code PickBuildingCardsMessage}. Default: reject. */
    public void onPickBuildingCards(GameController c, ClientCallback client,
                                    List<Integer> upper, List<Integer> lower) {
        c.sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                "You cannot buy buildings now");
        c.sendGameStateTo(client, "Wrong phase");
    }

    /** Handle a {@code PickExtraCardMessage}. Default: reject. */
    public void onPickExtraCard(GameController c, ClientCallback client,
                                Integer cardId, boolean isCharacter) {
        c.sendActionResult(client, ACTION_PICK_EXTRA, false,
                "You cannot make an extra pick now");
        c.sendGameStateTo(client, "Wrong phase");
    }

    // ==================================================================
    // Disconnect / lobby behaviour (defaults are no-op)
    // ==================================================================

    /**
     * Skip the (now-disconnected) current player and advance to the next
     * connected one. Called by
     * {@link GameController#advanceCurrentPlayerIfDisconnected()} when
     * the current player's callback is no longer connected.
     * <p>
     * Default: do nothing. Phases with a "current player" concept
     * override this to advance their own per-phase index.
     */
    public void advanceCurrentPlayerIfDisconnected(GameController c) {
        // No current-player concept in most phases.
    }

    /**
     * Per-phase part of {@link GameController#handleLobbyDisconnect}.
     * Only called for lobby phases (in-match disconnects go through a
     * different path). The leaver has already been removed from
     * {@code clients} and {@code colorPickQueue} when this is invoked.
     *
     * @param c                the controller
     * @param firstPlayerLeft  {@code true} iff the leaver was the first
     *                         player (the one allowed to set the count)
     */
    public void onLobbyDisconnect(GameController c, boolean firstPlayerLeft) {
        // Default: nothing extra to do.
    }

    // ==================================================================
    // Classifiers
    // ==================================================================

    /** @return {@code true} for phases where a {@link it.polimi.ingsw.model.Match}
     *          object exists and the game is in progress (or paused / ended). */
    public boolean isInMatch() { return false; }

    /** @return {@code true} only for {@code PAUSED_WAITING_RECONNECT}. */
    public boolean isPaused() { return false; }

    /** @return {@code true} only for {@code GAME_ENDED}. */
    public boolean isGameOver() { return false; }

    /** @return {@code true} for the three lobby phases (NICKNAME,
     *          PLAYER_COUNT, PLAYERS) — the ones that accept brand-new
     *          joiners. {@code COLOR_PICKING} returns {@code false}. */
    public boolean acceptsNewJoiners() { return false; }

    /** @return {@code true} when this phase's state is worth saving to
     *          disk. Derived from {@link #isInMatch()} and
     *          {@link #isGameOver()}. */
    public boolean shouldPersist() { return isInMatch() && !isGameOver(); }
}

// ======================================================================
// Lobby phases (WAITING_FOR_NICKNAME, WAITING_FOR_PLAYER_COUNT,
// WAITING_FOR_PLAYERS).
//
// All three accept new joiners and share the same join logic. The
// PLAYER_COUNT variant additionally accepts onPlayerCount.
// ======================================================================

/**
 * Base class for the three lobby phases. Implements the join logic and
 * the lobby-side of the disconnect handler. The PLAYER_COUNT variant
 * extends this and adds {@link #onPlayerCount}.
 */
class LobbyPhase extends Phase {

    @Override
    public boolean acceptsNewJoiners() { return true; }

    @Override
    public void onNewJoiner(GameController c, ClientCallback client, String trimmed) {
        // The controller has already validated nickname syntax and
        // checked that the nickname doesn't collide with an existing
        // slot (which would have routed the request to the reconnect
        // path instead). All that's left is the lobby-full check and
        // the actual join.
        if (c.getExpectedPlayers() > 0
                && c.getClients().size() >= c.getExpectedPlayers()) {
            c.sendActionResult(client, ACTION_NICKNAME, false,
                    "The game is already full");
            c.sendGameStateTo(client, "Game full");
            return;
        }

        client.setNickname(trimmed);
        c.getClients().add(client);
        c.sendActionResult(client, ACTION_NICKNAME, true,
                "Nickname accepted: " + trimmed);

        // First joiner: promote to "first player" so they can set the
        // total count.
        if (c.getClients().size() == 1) {
            c.setFirstPlayerNickname(trimmed);
            c.setCurrentPlayerNickname(trimmed);
            c.setPhase(GamePhase.WAITING_FOR_PLAYER_COUNT);
            c.broadcastInfo(trimmed + " joined as first player");
            c.broadcastGameState("First player must choose player count");
            return;
        }

        c.broadcastInfo(trimmed + " joined the game");

        if (c.getExpectedPlayers() == 0) {
            // First player hasn't set the count yet.
            c.setPhase(GamePhase.WAITING_FOR_PLAYER_COUNT);
            c.setCurrentPlayerNickname(c.getFirstPlayerNickname());
            c.broadcastGameState("First player must choose player count");
        } else if (c.getClients().size() == c.getExpectedPlayers()) {
            c.startColorPickingPhase();
        } else {
            c.setPhase(GamePhase.WAITING_FOR_PLAYERS);
            c.setCurrentPlayerNickname(null);
            c.broadcastGameState("Waiting for more players");
        }
    }

    @Override
    public void onLobbyDisconnect(GameController c, boolean firstPlayerLeft) {
        // Promote a new first-player and rewind to the player-count phase
        // so they can pick the lobby size.
        if (firstPlayerLeft && !c.getClients().isEmpty()) {
            ClientCallback newFirst = c.getClients().get(0);
            c.setFirstPlayerNickname(newFirst.getNickname());
            c.setCurrentPlayerNickname(newFirst.getNickname());
            c.setPhase(GamePhase.WAITING_FOR_PLAYER_COUNT);
        }
    }
}

/**
 * Lobby phase that ALSO accepts a {@code SetPlayerCountMessage} from the
 * first player. Used for {@link GamePhase#WAITING_FOR_PLAYER_COUNT}.
 */
class LobbyPlayerCountPhase extends LobbyPhase {

    @Override
    public void onPlayerCount(GameController c, ClientCallback client, int count) {
        if (client.getNickname() == null
                || !client.getNickname().equalsIgnoreCase(c.getFirstPlayerNickname())) {
            c.sendActionResult(client, ACTION_PLAYER_COUNT, false,
                    "Only the first player can choose player count");
            return;
        }
        if (count < GameController.MIN_PLAYERS) {
            c.sendActionResult(client, ACTION_PLAYER_COUNT, false,
                    "Player count must be at least " + GameController.MIN_PLAYERS);
            return;
        }
        if (count > GameController.MAX_PLAYERS) {
            c.sendActionResult(client, ACTION_PLAYER_COUNT, false,
                    "Player count cannot be greater than " + GameController.MAX_PLAYERS);
            return;
        }
        if (count < c.getClients().size()) {
            c.sendActionResult(client, ACTION_PLAYER_COUNT, false,
                    "Player count cannot be lower than already connected players");
            return;
        }

        c.setExpectedPlayers(count);
        c.sendActionResult(client, ACTION_PLAYER_COUNT, true,
                "Player count set to " + count);

        if (c.getClients().size() == count) {
            c.startColorPickingPhase();
        } else {
            c.setPhase(GamePhase.WAITING_FOR_PLAYERS);
            c.setCurrentPlayerNickname(null);
            c.broadcastGameState("Waiting for " + count + " players");
        }
    }
}

// ======================================================================
// Color picking phase
// ======================================================================

/** Strategy for {@link GamePhase#COLOR_PICKING}. */
class ColorPickingPhase extends Phase {

    @Override
    public void onPickColor(GameController c, ClientCallback client, TotemColor color) {
        ClientCallback expected = c.getColorPickQueue().peek();
        if (expected == null) {
            c.sendActionResult(client, ACTION_PICK_COLOR, false,
                    "No current color picker");
            return;
        }
        if (client != expected) {
            c.sendActionResult(client, ACTION_PICK_COLOR, false,
                    "It is not your turn to pick a color");
            c.sendGameStateTo(client, "Not your turn");
            return;
        }
        if (color == null) {
            c.sendActionResult(client, ACTION_PICK_COLOR, false,
                    "Color cannot be null");
            return;
        }
        if (!c.getAvailableColors().contains(color)) {
            c.sendActionResult(client, ACTION_PICK_COLOR, false,
                    "Color " + color + " is not available");
            c.sendGameStateTo(client, "Choose another color");
            return;
        }

        // Apply: build the Player object and bind both ways.
        client.setPickedColor(color);
        Player player = new Player(client.getNickname(), color);
        client.setPlayer(player);
        c.getAvailableColors().remove(color);
        c.getColorPickQueue().poll();

        c.sendActionResult(client, ACTION_PICK_COLOR, true,
                "Color picked: " + color);
        c.broadcastInfo(client.getNickname() + " picked " + color);

        if (c.getColorPickQueue().isEmpty()) {
            c.startMatchAndEnterPickOfferingTile();
        } else {
            c.setCurrentPlayerNickname(c.getColorPickQueue().peek().getNickname());
            c.broadcastGameState("Next player must pick a color");
        }
    }

    @Override
    public void onLobbyDisconnect(GameController c, boolean firstPlayerLeft) {
        // The "current player" during COLOR_PICKING is whoever sits at
        // the head of the queue. If the leaver was the head, the new
        // head is the new picker. Without this, the new head's client
        // would never realize it's their turn and the lobby would stall.
        if (!c.getColorPickQueue().isEmpty()) {
            c.setCurrentPlayerNickname(c.getColorPickQueue().peek().getNickname());
        }
        // Promote a new first-player just so legacy code that tracks
        // firstPlayerNickname has a sensible value; we do NOT rewind
        // the phase because color picking is already in progress.
        if (firstPlayerLeft) {
            c.setFirstPlayerNickname(c.getClients().get(0).getNickname());
        }
    }
}

// ======================================================================
// In-match phases
// ======================================================================

/** Strategy for {@link GamePhase#PICK_OFFERING_TILE}. */
class PickOfferingTilePhase extends Phase {

    @Override
    public boolean isInMatch() { return true; }

    @Override
    public void onPickOfferingTile(GameController c, ClientCallback client, int tileIndex) {
        Player player = client.getPlayer();
        if (player == null) {
            c.sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "You have no Player bound");
            return;
        }
        Player expectedPlayer =
                c.getTurnOrderForOfferingPick().get(c.getCurrentOfferingPickIndex());
        if (player != expectedPlayer) {
            c.sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "It is not your turn to pick an offering tile");
            c.sendGameStateTo(client, "Not your turn");
            return;
        }

        Board board = c.getMatch().getBoard();
        List<OfferingTile> track = board.getOfferingTrack();
        if (tileIndex < 0 || tileIndex >= track.size()) {
            c.sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "Tile index out of range");
            return;
        }
        OfferingTile tile = track.get(tileIndex);
        if (tile.isOccupied()) {
            c.sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "Tile " + tileIndex + " is already occupied");
            c.sendGameStateTo(client, "Pick a free tile");
            return;
        }
        if (tile.getMinPlayers() > c.getMatch().getPlayersList().size()) {
            c.sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "Tile " + tile.getLetter() + " requires more players");
            c.sendGameStateTo(client, "Pick a tile your group can use");
            return;
        }

        boolean ok = board.moveTotem(tileIndex, player);
        if (!ok) {
            c.sendActionResult(client, ACTION_PICK_OFFERING_TILE, false,
                    "Internal error: moveTotem failed");
            return;
        }
        c.sendActionResult(client, ACTION_PICK_OFFERING_TILE, true,
                "Totem placed on tile " + tile.getLetter());
        c.broadcastInfo(player.getNickname()
                + " placed totem on tile " + tile.getLetter());

        // Next picker, or transition to the resolution phase if everyone
        // placed. The advance helper handles both cases.
        c.setCurrentOfferingPickIndex(c.getCurrentOfferingPickIndex() + 1);
        c.advanceOfferingPickerUntilConnectedAndBroadcast();
    }

    @Override
    public void advanceCurrentPlayerIfDisconnected(GameController c) {
        c.setCurrentOfferingPickIndex(c.getCurrentOfferingPickIndex() + 1);
        c.advanceOfferingPickerUntilConnectedAndBroadcast();
    }
}

/** Strategy for {@link GamePhase#TURN_PICK_CARDS_CHARACTER}. */
class TurnPickCharactersPhase extends Phase {

    @Override
    public boolean isInMatch() { return true; }

    @Override
    public void onPickCharacterCards(GameController c, ClientCallback client,
                                     List<Integer> upperRowIds, List<Integer> lowerRowIds) {
        Player player = client.getPlayer();
        Player expectedPlayer = c.getResolutionOrder().get(c.getCurrentResolutionIndex());
        if (player == null || player != expectedPlayer) {
            c.sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "It is not your turn to pick characters");
            c.sendGameStateTo(client, "Not your turn");
            return;
        }
        if (upperRowIds == null) upperRowIds = new ArrayList<Integer>();
        if (lowerRowIds == null) lowerRowIds = new ArrayList<Integer>();

        // Per-row size budgets.
        if (upperRowIds.size() > c.getCurrentUpperBudget()) {
            c.sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "You can take at most " + c.getCurrentUpperBudget()
                            + " upper-row character card(s)");
            return;
        }
        if (lowerRowIds.size() > c.getCurrentLowerBudget()) {
            c.sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "You can take at most " + c.getCurrentLowerBudget()
                            + " lower-row character card(s)");
            return;
        }
        if (c.hasDuplicates(upperRowIds)) {
            c.sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "Duplicate upper-row card ID");
            return;
        }
        if (c.hasDuplicates(lowerRowIds)) {
            c.sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                    "Duplicate lower-row card ID");
            return;
        }

        // Look up every requested card; abort the whole batch on the
        // first miss so we never apply a half-batch.
        Board board = c.getMatch().getBoard();
        List<CharacterCard> foundUpper = new ArrayList<CharacterCard>();
        for (int i = 0; i < upperRowIds.size(); i++) {
            int id = upperRowIds.get(i);
            CharacterCard found = c.findCharacterById(board.getUpperRow(), id);
            if (found == null) {
                c.sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                        "Upper-row character ID " + id + " not available");
                c.sendGameStateTo(client, "Pick again");
                return;
            }
            foundUpper.add(found);
        }
        List<CharacterCard> foundLower = new ArrayList<CharacterCard>();
        for (int i = 0; i < lowerRowIds.size(); i++) {
            int id = lowerRowIds.get(i);
            CharacterCard found = c.findCharacterById(board.getLowerRow(), id);
            if (found == null) {
                c.sendActionResult(client, ACTION_PICK_CHARACTERS, false,
                        "Lower-row character ID " + id + " not available");
                c.sendGameStateTo(client, "Pick again");
                return;
            }
            foundLower.add(found);
        }

        // Apply: remove each card from the board, trigger drawEffect,
        // add to player's hand.
        for (int i = 0; i < foundUpper.size(); i++) {
            CharacterCard card = foundUpper.get(i);
            Card removed = board.removeCardFromRows(card);
            CharacterCard cc = (CharacterCard) removed;
            cc.drawEffect(player);
            player.addCardToHand(cc);
        }
        for (int i = 0; i < foundLower.size(); i++) {
            CharacterCard card = foundLower.get(i);
            Card removed = board.removeCardFromRows(card);
            CharacterCard cc = (CharacterCard) removed;
            cc.drawEffect(player);
            player.addCardToHand(cc);
        }

        // Per-row leftover for the building phase.
        c.setCurrentUpperRemainingForBuilding(
                c.getCurrentUpperBudget() - upperRowIds.size());
        c.setCurrentLowerRemainingForBuilding(
                c.getCurrentLowerBudget() - lowerRowIds.size());

        c.sendActionResult(client, ACTION_PICK_CHARACTERS, true,
                "Drew " + upperRowIds.size() + " upper + "
                        + lowerRowIds.size() + " lower character card(s)");
        c.broadcastInfo(player.getNickname() + " drew character cards");

        // Same player goes into the building sub-phase.
        c.setPhase(GamePhase.TURN_PICK_CARDS_BUILDING);
        c.broadcastGameState("Pick buildings (or none) within remaining budget");
        c.persistAfterTransition();
    }

    @Override
    public void advanceCurrentPlayerIfDisconnected(GameController c) {
        c.setCurrentResolutionIndex(c.getCurrentResolutionIndex() + 1);
        c.advanceResolverUntilConnectedAndContinue();
    }
}

/** Strategy for {@link GamePhase#TURN_PICK_CARDS_BUILDING}. */
class TurnPickBuildingsPhase extends Phase {

    @Override
    public boolean isInMatch() { return true; }

    @Override
    public void onPickBuildingCards(GameController c, ClientCallback client,
                                    List<Integer> upperRowIds, List<Integer> lowerRowIds) {
        Player player = client.getPlayer();
        Player expectedPlayer = c.getResolutionOrder().get(c.getCurrentResolutionIndex());
        if (player == null || player != expectedPlayer) {
            c.sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "It is not your turn to buy buildings");
            c.sendGameStateTo(client, "Not your turn");
            return;
        }
        if (upperRowIds == null) upperRowIds = new ArrayList<Integer>();
        if (lowerRowIds == null) lowerRowIds = new ArrayList<Integer>();

        // Per-row remaining budgets.
        if (upperRowIds.size() > c.getCurrentUpperRemainingForBuilding()) {
            c.sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "You can take at most "
                            + c.getCurrentUpperRemainingForBuilding()
                            + " upper-row building(s)");
            return;
        }
        if (lowerRowIds.size() > c.getCurrentLowerRemainingForBuilding()) {
            c.sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "You can take at most "
                            + c.getCurrentLowerRemainingForBuilding()
                            + " lower-row building(s)");
            return;
        }
        if (c.hasDuplicates(upperRowIds)) {
            c.sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "Duplicate upper-row building ID");
            return;
        }
        if (c.hasDuplicates(lowerRowIds)) {
            c.sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "Duplicate lower-row building ID");
            return;
        }

        // Look up each requested building.
        Board board = c.getMatch().getBoard();
        List<BuildingCard> foundUpper = new ArrayList<BuildingCard>();
        for (int i = 0; i < upperRowIds.size(); i++) {
            int id = upperRowIds.get(i);
            BuildingCard found = c.findBuildingById(board.getUpperRow(), id);
            if (found == null) {
                c.sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                        "Upper-row building ID " + id + " not available");
                c.sendGameStateTo(client, "Pick again");
                return;
            }
            foundUpper.add(found);
        }
        List<BuildingCard> foundLower = new ArrayList<BuildingCard>();
        for (int i = 0; i < lowerRowIds.size(); i++) {
            int id = lowerRowIds.get(i);
            BuildingCard found = c.findBuildingById(board.getLowerRow(), id);
            if (found == null) {
                c.sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                        "Lower-row building ID " + id + " not available");
                c.sendGameStateTo(client, "Pick again");
                return;
            }
            foundLower.add(found);
        }

        // Cost (with builder discount, never below zero).
        int grossCost = 0;
        for (int i = 0; i < foundUpper.size(); i++) {
            grossCost += foundUpper.get(i).getFoodCost();
        }
        for (int i = 0; i < foundLower.size(); i++) {
            grossCost += foundLower.get(i).getFoodCost();
        }
        int discount = player.getBuildingDiscountBuilder();
        int netCost = grossCost - discount;
        if (netCost < 0) {
            netCost = 0;
        }
        if (!player.hasEnoughFood(netCost)) {
            c.sendActionResult(client, ACTION_PICK_BUILDINGS, false,
                    "You need " + netCost + " food but have only " + player.getFood());
            return;
        }

        // Apply.
        for (int i = 0; i < foundUpper.size(); i++) {
            BuildingCard card = foundUpper.get(i);
            Card removed = board.removeCardFromRows(card);
            BuildingCard bc = (BuildingCard) removed;
            bc.setOwner(player);
            player.addBuildingToHand(bc);
        }
        for (int i = 0; i < foundLower.size(); i++) {
            BuildingCard card = foundLower.get(i);
            Card removed = board.removeCardFromRows(card);
            BuildingCard bc = (BuildingCard) removed;
            bc.setOwner(player);
            player.addBuildingToHand(bc);
        }
        player.changeFood(-netCost);

        c.sendActionResult(client, ACTION_PICK_BUILDINGS, true,
                "Bought " + (upperRowIds.size() + lowerRowIds.size())
                        + " building(s) for " + netCost + " food");
        c.broadcastInfo(player.getNickname() + " bought building(s)");

        // Advance to the next resolving player; if none left, run the
        // post-resolution housekeeping.
        c.setCurrentResolutionIndex(c.getCurrentResolutionIndex() + 1);
        c.advanceResolverUntilConnectedAndContinue();
    }

    @Override
    public void advanceCurrentPlayerIfDisconnected(GameController c) {
        c.setCurrentResolutionIndex(c.getCurrentResolutionIndex() + 1);
        c.advanceResolverUntilConnectedAndContinue();
    }
}

/**
 * Strategy for {@link GamePhase#RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING}.
 */
class ResetTurnOrderExtraPickPhase extends Phase {

    @Override
    public boolean isInMatch() { return true; }

    @Override
    public void onPickExtraCard(GameController c, ClientCallback client,
                                Integer cardId, boolean isCharacter) {
        Player player = client.getPlayer();
        if (player == null || player != c.getExtraPickPlayer()) {
            c.sendActionResult(client, ACTION_PICK_EXTRA, false,
                    "Only the special-building owner can make this pick");
            c.sendGameStateTo(client, "Not your pick");
            return;
        }

        // Skip: nothing to draw, jump to the resolve phase.
        if (cardId == null) {
            c.sendActionResult(client, ACTION_PICK_EXTRA, true, "Extra pick skipped");
            c.broadcastInfo(player.getNickname() + " skipped the extra pick");
            c.enterResolveBuildingsAndEvents();
            return;
        }

        Board board = c.getMatch().getBoard();
        if (isCharacter) {
            CharacterCard card = c.findCharacterByIdAnywhere(board, cardId);
            if (card == null) {
                c.sendActionResult(client, ACTION_PICK_EXTRA, false,
                        "Character ID " + cardId + " not found on the board");
                c.sendGameStateTo(client, "Pick again");
                return;
            }
            Card removed = board.removeCardFromRows(card);
            CharacterCard cc = (CharacterCard) removed;
            cc.drawEffect(player);
            player.addCardToHand(cc);
            c.sendActionResult(client, ACTION_PICK_EXTRA, true,
                    "Took extra character card " + cardId);
            c.broadcastInfo(player.getNickname() + " took an extra character card");
            c.enterResolveBuildingsAndEvents();
            return;
        }

        // Building branch.
        BuildingCard card = c.findBuildingByIdAnywhere(board, cardId);
        if (card == null) {
            c.sendActionResult(client, ACTION_PICK_EXTRA, false,
                    "Building ID " + cardId + " not found on the board");
            c.sendGameStateTo(client, "Pick again");
            return;
        }
        int discount = player.getBuildingDiscountBuilder();
        int netCost = card.getFoodCost() - discount;
        if (netCost < 0) {
            netCost = 0;
        }
        if (!player.hasEnoughFood(netCost)) {
            c.sendActionResult(client, ACTION_PICK_EXTRA, false,
                    "You need " + netCost + " food but have only " + player.getFood());
            return;
        }
        Card removed = board.removeCardFromRows(card);
        BuildingCard bc = (BuildingCard) removed;
        bc.setOwner(player);
        player.addBuildingToHand(bc);
        player.changeFood(-netCost);

        c.sendActionResult(client, ACTION_PICK_EXTRA, true,
                "Bought extra building " + cardId + " for " + netCost + " food");
        c.broadcastInfo(player.getNickname() + " bought an extra building");
        c.enterResolveBuildingsAndEvents();
    }

    @Override
    public void advanceCurrentPlayerIfDisconnected(GameController c) {
        c.broadcastInfo("Special-building owner disconnected; skipping the extra pick.");
        c.enterResolveBuildingsAndEvents();
    }
}

/**
 * Strategy for {@link GamePhase#RESOLVE_BUILDINGS_AND_EVENTS}. No player
 * input is expected during this phase — the controller runs its body
 * synchronously.
 */
class ResolvePhase extends Phase {

    @Override
    public boolean isInMatch() { return true; }
}

/**
 * Strategy for {@link GamePhase#PAUSED_WAITING_RECONNECT}. No player
 * input is accepted (everything default-rejects); the controller waits
 * for reconnects and resumes via {@link GameController#resumeFromPause}.
 */
class PausedPhase extends Phase {

    @Override
    public boolean isInMatch() { return true; }

    @Override
    public boolean isPaused() { return true; }
}

/**
 * Strategy for {@link GamePhase#GAME_ENDED}. The match is over; no
 * input is accepted, no persistence happens.
 */
class GameEndedPhase extends Phase {

    @Override
    public boolean isInMatch() { return true; }

    @Override
    public boolean isGameOver() { return true; }
}
