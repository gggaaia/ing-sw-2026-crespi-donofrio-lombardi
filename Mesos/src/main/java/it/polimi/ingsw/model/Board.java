package it.polimi.ingsw.model;

import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.card.TribeCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Board implements Serializable {
    /**
     * Serialization version for the wire/snapshot format.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Lookup table for the number of building cards added to the upper row
     * at the start of each era, indexed by {@code [numPlayers - 2][era.ordinal()]}.
     * <p>
     * Rows correspond to player counts (2..5 players) and columns to eras
     * ({@link Era#ERA_1}, {@link Era#ERA_2}, {@link Era#ERA_3}); this matches
     * the rulebook table for step 6 of the setup.
     */
    private static final int[][] BUILDINGS_PER_ERA = {
            { 1, 2, 3 }, // 2 players
            { 2, 2, 4 }, // 3 players
            { 2, 3, 4 }, // 4 players
            { 2, 3, 5 }, // 5 players
    };

    private CardRow upperRow = new CardRow();
    private CardRow lowerRow = new CardRow();
    private List<OfferingTile> offeringTrack;
    private TurnOrderTile turnOrderTile;

    public CardRow getUpperRow() {return upperRow;}
    public CardRow getLowerRow() {return lowerRow;}
    public TurnOrderTile getTurnOrderTile() {return turnOrderTile;}

    /**
     * Returns the offering track currently configured on this board.
     * <p>
     * The returned list is the live, internal list (so changes by the model
     * itself, e.g. {@link #moveTotem(int, Player)}, are visible to readers).
     * The list is {@code null} until {@link #setupOfferingTiles(int)} has
     * been called.
     *
     * @return the offering track, or {@code null} if not initialized yet
     */
    public List<OfferingTile> getOfferingTrack() {
        return offeringTrack;
    }

    /**
     * Initializes the offering track for the board with all eligible offering tiles
     * according to the current number of players in the match.
     * <p>
     * Only tiles whose minimum player requirement is less than or equal to {@code numPlayers}
     * are included in the track.
     *
     * @param numPlayers the number of players in the match (used to filter which tiles are included)
     */
    public void setupOfferingTiles(int numPlayers) {
        this.offeringTrack = OfferingTile.createAllTiles().stream()
                .filter(tile -> tile.getMinPlayers() <= numPlayers)
                .collect(Collectors.toList());
    }

    /**
     * Initializes the turn order tile for the board according to the current number of players.
     * <p>
     * Creates and assigns a new {@link TurnOrderTile} instance to the board, with the number
     * of slots and reward values configured for the given player count.
     *
     * @param numPlayers the number of players participating in the match (used to configure the turn order tile)
     */
    public void setupTurnOrderTiles(int numPlayers) {
        this.turnOrderTile = new TurnOrderTile(numPlayers);
    }



    /**
     * Initializes the lower row at the start of the match by drawing
     * {@code numPlayers + 1} non-event tribe cards from the tribe deck.
     * <p>
     * Cards are drawn one at a time via {@link Decks#drawTribe()}. For each
     * drawn card, {@link Card#firstRoundChangeRow(CardRow)} is invoked with
     * the {@link #upperRow} as the target:
     * <ul>
     *   <li>If the card is an event, it adds itself to the upper row and the
     *       method returns {@code true}; the draw counter is <b>not</b>
     *       incremented and another card is drawn.</li>
     *   <li>If the card is a character, the default implementation returns
     *       {@code false} without modifying any row; the card is then placed
     *       on the lower row via {@link Card#addToRow(CardRow)} and the
     *       counter is incremented.</li>
     * </ul>
     * The loop continues until the lower row holds exactly {@code numPlayers + 1}
     * non-event tribe cards.
     *
     * @param d          the decks to draw tribe cards from
     * @param numPlayers the number of players in the match
     * @throws EmptyDeckException if the tribe deck runs out of cards before
     *                            the lower row is fully populated
     */
    public void setupLower(Decks d, int numPlayers) throws EmptyDeckException {
        int cardsToAdd = numPlayers + 1;
        int addedCards = 0;
        while (addedCards < cardsToAdd) {
            Card drawnCard = d.drawTribe();
            boolean movedToUpper = drawnCard.firstRoundChangeRow(this.upperRow);
            if (!movedToUpper) {
                drawnCard.addToRow(this.lowerRow);
                addedCards++;
            }
        }
    }

    /**
     * Refills the upper row with tribe cards drawn from the tribe deck.
     * <p>
     * The target size of the upper row is {@code numPlayers + 4} tribe cards
     * (characters plus events). Any tribe cards already present in the upper
     * row count toward this target; only the missing cards are drawn. This
     * matters at the start of the match, where {@link #setupLower(Decks, int)}
     * may have diverted some event cards to the upper row, so the number of
     * cards drawn here is reduced accordingly.
     * <p>
     * In rounds after the first, {@link #shiftUpToLow()} empties the upper
     * row of all tribe cards before this method is called, so the full
     * {@code numPlayers + 4} cards are drawn.
     * <p>
     * Each drawn card is dispatched to the correct internal list of the
     * upper row via {@link Card#addToRow(CardRow)} (characters and events
     * are stored separately in {@link CardRow}).
     * <p>
     * If the tribe deck runs out of cards before the upper row reaches the
     * target size, the failure is logged to standard error and the refill
     * loop exits early, leaving the upper row partially filled.
     *
     * @param d          the decks to draw tribe cards from
     * @param numPlayers the number of players in the match
     */
    public void newUpper(Decks d, int numPlayers) {
        int target = numPlayers + 4;
        int current = this.upperRow.getCharacterCards().size()
                + this.upperRow.getEventCards().size();
        int cardsToAdd = target - current;
        for (int i = 0; i < cardsToAdd; i++) {
            try {
                Card drawnCard = d.drawTribe();
                drawnCard.addToRow(this.upperRow);
            } catch (EmptyDeckException e) {
                System.err.println("newUpper: tribe deck empty, stopping refill: "
                        + e.getMessage());
                break;
            }
        }
    }

    /**
     * Refills the upper row with building cards of the given era, drawn
     * from the building deck.
     * <p>
     * The number of buildings to add is determined by the {@link #BUILDINGS_PER_ERA}
     * lookup table (era and player count). The upper row's building list is
     * assumed to be empty when this method is called (e.g. immediately after
     * {@link #shiftBuildingsDown()} or at game start).
     * <p>
     * Because {@link Decks#drawBuilding()} draws from the top of a deck that
     * is sorted era-first, leftover buildings from previous eras may sit on
     * top of the deck when called for {@code ERA_2} or {@code ERA_3}. Any card
     * whose era does not match the requested {@code era} is silently
     * discarded (not added to the upper row, not placed in any pile) and
     * drawing continues. This implements the "buildings of previous eras are
     * discarded at the era change" rule.
     *
     * @param d          the decks to draw building cards from
     * @param era        the era of the buildings to place on the upper row
     * @param numPlayers the number of players in the match
     * @throws EmptyDeckException if the building deck runs out of cards
     *                            before enough cards of the requested era
     *                            are drawn
     */
    public void newUpperBuilding(Decks d, Era era, int numPlayers) throws EmptyDeckException {
        // Example: era=ERA_2, numPlayers=3
        //   -> era.ordinal() = 1 (ERA_1=0, ERA_2=1, ERA_3=2)
        //   -> numPlayers - 2 = 1 (row index for 3-player count)
        //   -> BUILDINGS_PER_ERA[1][1] = 2 buildings to draw
        int target = BUILDINGS_PER_ERA[numPlayers - 2][era.ordinal()];
        int addedCards = 0;
        while (addedCards < target) {
            Card drawnCard = d.drawBuilding();
            if (drawnCard.getEra() == era) {
                drawnCard.addToRow(this.upperRow);
                addedCards++;
            }
        }
    }



    /**
     * Removes the card matching the given card's {@code cardID} from either
     * the upper row or the lower row.
     * <p>
     * Both rows are searched (upper first, then lower) via
     * {@link CardRow#takeCharacterCard(Card)} and
     * {@link CardRow#takeBuildingCard(Card)}; event cards are not searched.
     * Within each row, character cards are checked before building cards.
     * The first lookup that finds a match removes the card and returns it;
     * subsequent lookups are skipped.
     *
     * @param card the card whose {@code cardID} identifies the card to remove
     * @return the removed {@link Card} (the instance previously stored in
     *         the matching row), or {@code null} if no matching card was
     *         found in either row
     * @throws IllegalArgumentException if {@code card} is {@code null}
     * @throws IllegalStateException    if the same {@code cardID} appears
     *                                  more than once within a single row's
     *                                  character or building cards
     */
    public Card removeCardFromRows(Card card) {
        Card removed = this.upperRow.takeCharacterCard(card);
        if (removed != null) {
            return removed;
        }
        removed = this.upperRow.takeBuildingCard(card);
        if (removed != null) {
            return removed;
        }
        removed = this.lowerRow.takeCharacterCard(card);
        if (removed != null) {
            return removed;
        }
        return this.lowerRow.takeBuildingCard(card);
    }

    /**
     * Returns whether a character card with the given card's {@code cardID}
     * is present in either the upper row or the lower row.
     * <p>
     * Each row is checked via {@link CardRow#containsCharacterCard(Card)};
     * event and building cards are not searched. Thanks to short-circuit
     * evaluation, the lower row is not scanned if the card is already found
     * in the upper row.
     *
     * @param card the card whose {@code cardID} is searched for
     * @return {@code true} if a character card with the same {@code cardID}
     *         is present in either row, {@code false} otherwise
     * @throws IllegalArgumentException if {@code card} is {@code null}
     */
    public boolean containsCharacterCard(Card card) {
        return this.upperRow.containsCharacterCard(card)
                || this.lowerRow.containsCharacterCard(card);
    }

    /**
     * Returns whether a building card with the given card's {@code cardID}
     * is present in either the upper row or the lower row.
     * <p>
     * Each row is checked via {@link CardRow#containsBuildingCard(Card)};
     * event and character cards are not searched. Thanks to short-circuit
     * evaluation, the lower row is not scanned if the card is already found
     * in the upper row.
     *
     * @param card the card whose {@code cardID} is searched for
     * @return {@code true} if a building card with the same {@code cardID}
     *         is present in either row, {@code false} otherwise
     * @throws IllegalArgumentException if {@code card} is {@code null}
     */
    public boolean containsBuildingCard(Card card) {
        return this.upperRow.containsBuildingCard(card)
                || this.lowerRow.containsBuildingCard(card);
    }

    /**
     * Shifts the upper row's tribe cards (characters and events) down into
     * the lower row.
     * <p>
     * The character and event cards currently in the lower row are
     * discarded, then the upper row's character and event cards are moved
     * into the lower row, leaving the upper row empty of tribe cards
     * (ready to be repopulated by {@link #newUpper(Decks, int)}).
     * <p>
     * Building cards on either row are <b>not</b> affected by this
     * operation; they are shifted independently via
     * {@link #shiftBuildingsDown()}.
     */
    public void shiftUpToLow() {
        this.lowerRow.discardTribeCards();
        
        List<CharacterCard> charactersToMove = this.upperRow.extractAllCharacterCards();
        for (CharacterCard card : charactersToMove) {
            this.lowerRow.addCharacterCard(card);
        }
        
        List<EventCard> eventsToMove = this.upperRow.extractAllEventCards();
        for (EventCard card : eventsToMove) {
            this.lowerRow.addEventCard(card);
        }
    }

    /**
     * Shifts the upper row's building cards down into the lower row.
     * <p>
     * The building cards currently in the lower row are discarded, then
     * the upper row's building cards are moved into the lower row, leaving
     * the upper row empty of building cards.
     * <p>
     * Character and event cards on either row are <b>not</b> affected by
     * this operation; they are shifted independently via
     * {@link #shiftUpToLow()}.
     */
    public void shiftBuildingsDown() {
        this.lowerRow.discardBuildingCards();
        
        List<BuildingCard> buildingsToMove = this.upperRow.extractAllBuildingCards();
        for (BuildingCard card : buildingsToMove) {
            this.lowerRow.addBuildingCard(card);
        }
    }

    /**
     * Randomly places each player's totem (by their {@link it.polimi.ingsw.model.enums.TotemColor})
     * onto the slots of the {@link TurnOrderTile}.
     * <p>
     * The given players list is shuffled and each player is assigned to a
     * unique slot via {@link TurnOrderTile#placeTotem(Player, int)}.
     *
     * @param players the players whose totems should be placed on the turn order tile
     */
    public void placeInitTotems(List<Player> players) {
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) {
            this.turnOrderTile.placeTotem(shuffled.get(i), i);
        }
    }

    /**
     * Returns the players currently placed on the {@link TurnOrderTile}, in
     * the order of the slots they occupy (slot 0 first, then slot 1, etc.).
     * <p>
     * The turn order slots are scanned from index {@code 0} to the last
     * index; for every {@link TurnOrderSlot#isOccupied() occupied} slot, the
     * player whose {@link Player#getPlayerColor() totem color} matches the
     * slot's {@link TurnOrderSlot#getTotemColor() totem color} is appended
     * to the result list. Empty slots are skipped, so the returned list
     * contains no gaps and at most one entry per occupied slot.
     * <p>
     * The {@code players} argument is required because slots store totems
     * as {@link TotemColor}, and this method needs to resolve each color
     * back to its owning {@link Player}.
     *
     * @param players the players currently in the match (used to resolve a
     *                {@link TotemColor} back to its owning {@link Player})
     * @return a list of players ordered by their slot position on the turn
     *         order tile (slot 0 first); empty if no totems are on the tile
     * @throws IllegalStateException if a totem on the turn order tile has a
     *                               color that does not match any player in
     *                               {@code players}
     */
    public List<Player> getPlayersTurnOrder(List<Player> players) {
        List<Player> turnOrder = new ArrayList<>();
        for (TurnOrderSlot slot : this.turnOrderTile.getTurnOrderSlots()) {
            if (!slot.isOccupied()) {
                continue;
            }
            TotemColor color = slot.getTotemColor();
            Player owner = null;
            for (Player p : players) {
                if (p.getPlayerColor() == color) {
                    owner = p;
                    break;
                }
            }
            if (owner == null) {
                throw new IllegalStateException(
                        "No player found for totem color " + color + " on turn order tile.");
            }
            turnOrder.add(owner);
        }
        return turnOrder;
    }

    /**
     * Moves the given player's totem from the {@link TurnOrderTile} onto the
     * offering tile at the specified position in the offering track.
     * <p>
     * The operation succeeds only if all of the following are true:
     * <ul>
     *   <li>{@code index} is a valid position in {@link #offeringTrack}</li>
     *   <li>The target offering tile is not already occupied
     *       (see {@link OfferingTile#isOccupied()})</li>
     * </ul>
     * On success, the player's totem is removed from the turn order tile
     * (via {@link TurnOrderTile#clearSlot(Player)}) and the player's
     * {@link it.polimi.ingsw.model.enums.TotemColor} is placed on the offering
     * tile via {@link OfferingTile#setCurrentTotemColor}.
     * <p>
     * <b>Note:</b> if {@code index} is out of bounds (negative or
     * {@code >= offeringTrack.size()}), this method silently returns
     * {@code false} instead of throwing. Callers must check the return value.
     *
     * @param index the index of the target tile in the offering track
     * @param p     the player whose totem should be moved
     * @return {@code true} if the totem was successfully moved,
     *         {@code false} if the index is invalid or the target tile is occupied
     */
    public boolean moveTotem(int index, Player p) {
        if (index < 0 || index >= offeringTrack.size()) {
            return false;
        }
        OfferingTile tile = offeringTrack.get(index);
        if (tile.isOccupied()) {
            return false;
        }
        this.turnOrderTile.clearSlot(p);
        tile.setCurrentTotemColor(p.getPlayerColor());
        return true;
    }

    /**
     * Returns the index of the offering tile currently occupied by the given
     * player's totem.
     * <p>
     * The offering track is scanned from left to right; a tile matches when
     * it is occupied (see {@link OfferingTile#isOccupied()}) and its
     * {@link OfferingTile#getCurrentTotemColor()} equals
     * {@link Player#getPlayerColor()}. Because each player owns a unique
     * {@link TotemColor}, at most one tile can match.
     *
     * @param p the player whose totem position on the offering track is requested
     * @return the index of the matching tile in {@link #offeringTrack}, or
     *         {@code -1} if the player's totem is not on the track
     */
    public int getOfferingTrackIndex(Player p) {
        TotemColor color = p.getPlayerColor();
        for (int i = 0; i < offeringTrack.size(); i++) {
            OfferingTile tile = offeringTrack.get(i);
            if (tile.isOccupied() && tile.getCurrentTotemColor() == color) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the players currently placed on the offering track, in the
     * left-to-right order of the offering tiles they occupy.
     * <p>
     * The offering track is scanned from index {@code 0} to the last index;
     * for every {@link OfferingTile#isOccupied() occupied} tile, the player
     * whose {@link Player#getPlayerColor() totem color} matches the tile's
     * {@link OfferingTile#getCurrentTotemColor() current totem color} is
     * appended to the result list. Empty offering tiles are skipped, so the
     * returned list contains no gaps and at most one entry per occupied tile.
     * <p>
     * The {@code players} argument is required because totems are stored on
     * offering tiles as {@link TotemColor}, and this method needs to resolve
     * each color back to its owning {@link Player}.
     *
     * @param players the players currently in the match (used to resolve a
     *                {@link TotemColor} back to its owning {@link Player})
     * @return a list of players ordered by their position on the offering
     *         track (first occupied tile first); empty if no totems are on
     *         the track
     * @throws IllegalStateException if a totem on the offering track has a
     *                               color that does not match any player in
     *                               {@code players}
     */
    public List<Player> getOfferingTrackPlayersOrder(List<Player> players) {
        List<Player> orderedPlayers = new ArrayList<>();
        for (OfferingTile tile : offeringTrack) {
            if (!tile.isOccupied()) {
                continue;
            }
            TotemColor color = tile.getCurrentTotemColor();
            Player owner = null;
            for (Player p : players) {
                if (p.getPlayerColor() == color) {
                    owner = p;
                    break;
                }
            }
            if (owner == null) {
                throw new IllegalStateException(
                        "No player found for totem color " + color + " on offering tile.");
            }
            orderedPlayers.add(owner);
        }
        return orderedPlayers;
    }

    /**
     * Moves every totem currently on the offering track back onto the
     * {@link TurnOrderTile}, preserving the relative order of the offering
     * track.
     * <p>
     * The offering track is scanned from left to right; <b>only occupied
     * offering tiles are considered</b>. The first occupied tile's totem is
     * placed in slot 0 of the turn order tile, the second occupied tile's
     * totem in slot 1, and so on (empty offering tiles are skipped).
     * <p>
     * For each totem moved, the corresponding offering tile is cleared via
     * {@link OfferingTile#clearTile()}.
     * <p>
     * The {@code players} argument is required because totems are stored on
     * offering tiles as {@link it.polimi.ingsw.model.enums.TotemColor}, while
     * {@link TurnOrderTile#placeTotem(Player, int)} requires the corresponding
     * {@link Player}; this method looks up each player by their color.
     *
     * @param players the players currently in the match (used to resolve a
     *                {@link it.polimi.ingsw.model.enums.TotemColor} back to its
     *                owning {@link Player})
     * @throws IllegalStateException if a totem on the offering track has a
     *                               color that does not match any player in
     *                               {@code players}
     */
    public void moveOfferingTrackToTurnOrder(List<Player> players) {
        int slotIndex = 0;
        for (OfferingTile tile : offeringTrack) {
            if (!tile.isOccupied()) {
                continue;
            }
            TotemColor color = tile.getCurrentTotemColor();
            Player owner = null;
            for (Player p : players) {
                if (p.getPlayerColor() == color) {
                    owner = p;
                    break;
                }
            }
            if (owner == null) {
                throw new IllegalStateException(
                        "No player found for totem color " + color + " on offering tile.");
            }
            this.turnOrderTile.placeTotem(owner, slotIndex);
            tile.clearTile();
            slotIndex++;
        }
    }

    /**
     * Scans every tribe card currently sitting in the upper row and returns
     * the highest {@link Era} found among them.
     * <p>
     * The comparison relies on the natural order of the {@code Era} enum
     * (i.e. {@code ERA_1 < ERA_2 < ERA_3}). If the upper row is empty, the
     * earliest era ({@link Era#ERA_1}) is returned as a default.
     *
     * @return the highest {@code Era} present among the tribe cards in the upper
     *         row
     */
    public Era getCurrentEra() {
        Era highest = Era.ERA_1;

        for (TribeCard card : upperRow.getCharacterCards()) {
            if (card.getEra().compareTo(highest) > 0) {
                highest = card.getEra();
            }
        }

        return highest;
    }



}
