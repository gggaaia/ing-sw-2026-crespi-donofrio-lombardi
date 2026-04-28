package it.polimi.ingsw.model;

import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.card.TribeCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Board {
    private CardRow upperRow;
    private CardRow lowerRow;
    private List<OfferingTile> offeringTrack;
    private TurnOrderTile turnOrderTile;

    public CardRow getUpperRow() {return upperRow;}
    public CardRow getLowerRow() {return lowerRow;}

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



    public void setupLower(Decks d, int numPlayers) {
    // TODO
    }

    public void newUpper(Decks d, int numPlayers) {
    // TODO
    }

    public void newUpperBuilding(Decks d, Era era) {
    // TODO
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
