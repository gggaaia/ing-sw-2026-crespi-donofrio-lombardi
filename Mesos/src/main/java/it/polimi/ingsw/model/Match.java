package it.polimi.ingsw.model;

import it.polimi.ingsw.exceptions.DeckLoadException;
import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.model.state.LobbyState;
import it.polimi.ingsw.model.state.MatchState;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Match {
    /**
     * The round on which the game ends. After {@code resolveBuildings} and
     * {@code resolveEvents} have been applied for round {@code FINAL_ROUND},
     * the controller runs the end-of-game sequence ({@code shiftUpToLow},
     * a final {@code resolveEvents}, {@code resolveEndMatch}, leaderboard
     * creation) instead of advancing to the next round.
     */
    public static final int FINAL_ROUND = 11;

    private final int matchID;
    private MatchState status = new LobbyState();
    private final Date date = new Date();
    private final List<Player> playersList = new ArrayList<>();
    private int currentRound = 0;
    private Era currentEra = Era.ERA_1;
    private Board board;
    private Decks decks;

    /**
     * Creates a new match and assigns a time-based unique identifier.
     * <p>
     * The identifier is built from:
     * <ul>
     *   <li>the last 6 digits of the current epoch seconds, and</li>
     *   <li>the current milliseconds part (0-999).</li>
     * </ul>
     * This produces a 9-digit value in the form {@code SSSSSSMMM}.
     */
    public Match() {
        long now = System.currentTimeMillis();

        // Get digits of seconds (10 digits)
        long seconds = now / 1000;

        // Get last 6 digits of seconds
        long last_6_digits_of_seconds = seconds % 1000000;
        // Get milliseconds part (0-999)
        long milliseconds = now % 1000;

        // Unique ID for the match based on the time of creation (9 digits)
        this.matchID = (int) (last_6_digits_of_seconds * 1000 + milliseconds);

        // Initialize board and decks
        this.board = new Board();
        this.decks = new Decks();
    }

    public int getMatchID() {
        return matchID;
    }

    /**
     * Returns the {@link Board} owned by this match.
     *
     * @return the match's board (never {@code null} for a fully constructed Match)
     */
    public Board getBoard() {
        return board;
    }

    /**
     * Returns the players currently registered in this match.
     * <p>
     * The returned list is the live, internal list (not a defensive copy),
     * so callers must not modify it directly.
     *
     * @return the players registered in this match
     */
    public List<Player> getPlayersList() {
        return playersList;
    }

    /**
     * Returns the current round number. The first round of a started match is
     * {@code 1}; the value is {@code 0} for a match that has not yet been
     * started via {@link #startMatch()}.
     *
     * @return the current round number
     */
    public int getCurrentRound() {
        return currentRound;
    }

    /**
     * Returns the current {@link Era} of this match. Updated by
     * {@link #moveToNextEra(Era)} when the era changes.
     *
     * @return the current era
     */
    public Era getCurrentEra() {
        return currentEra;
    }

    /**
     * Adds a player to this match if they are not already registered.
     * <p>
     * Players are compared by reference equality (i.e. the same {@link Player}
     * instance is never added twice). The caller is expected to ensure
     * nicknames and totem colors are unique.
     *
     * @param p the player to add (ignored if already in the list)
     */
    public void addPlayer(Player p) {
        if (!playersList.contains(p)) {
            playersList.add(p);
        }
    }

    public void removePlayer(Player p) {
        if (playersList.contains(p)) {
            playersList.remove(p);
        }
    }

    /**
     * Initializes the match, setting up all game components according to the current number of players.
     * <p>
     * Specifically, this method:
     * <ul>
     *   <li>Sets the current round to 1,</li>
     *   <li>Populates the decks with the appropriate cards for the number of players,</li>
     *   <li>Sets up the offering tiles on the board,</li>
     *   <li>Sets up the turn order tiles on the board,</li>
     *   <li>Sets up the lower card row on the board,</li>
     *   <li>Sets up the upper card row on the board,</li>
     *   <li>Sets up the upper building row on the board, and</li>
     *   <li>Places the players' initial totems on the turn order tile.</li>
     * </ul>
     *
     * @throws DeckLoadException  if the player count is invalid or the cards file cannot be loaded
     * @throws EmptyDeckException if a deck runs out of cards while populating the board rows
     */
    public void startMatch() throws DeckLoadException, EmptyDeckException {
        this.currentRound = 1;

        int numPlayers = playersList.size();

        decks.populateDecks(numPlayers);

        // Shuffle right after population so the order in which cards are
        // drawn is not the deterministic JSON order. The shuffle preserves
        // the era-based draw order (and the final-events constraint) — see
        // Decks.shuffleDecks for details.
        decks.shuffleDecks();

        board.setupOfferingTiles(numPlayers);

        board.setupTurnOrderTiles(numPlayers);

        board.setupLower(decks, numPlayers);

        board.newUpper(decks, numPlayers);

        board.newUpperBuilding(decks, Era.ERA_1, numPlayers);

        board.placeInitTotems(playersList);
    }

    /**
     * End-of-match cleanup hook. Currently this only triggers
     * {@link #resolveEndMatch()} (i.e. the final scoring pass). Any further
     * cleanup that is internal to the {@link Match} aggregate belongs here;
     * snapshot persistence on disk is handled outside the model, by the
     * controller layer's {@code PersistenceManager}.
     */
    public void endMatch() {
        resolveEndMatch();
    }

    /**
     * Advances the match to the next round.
     * <p>
     * This method:
     * <ul>
     *   <li>Increments the current round counter,</li>
     *   <li>Captures the current era from the board before any modification,</li>
     *   <li>Shifts the upper row of cards down to the lower row,</li>
     *   <li>Refills the upper row with new cards from the deck, and</li>
     *   <li>Captures the current era again to detect if a new era has been triggered.</li>
     * </ul>
     * If the era has advanced (it can only ever increase), {@link #moveToNextEra(Era)} is called
     * with the new era.
     *
     * @throws EmptyDeckException if a deck runs out of cards while refilling the board rows
     */
    public void moveToNextRound() throws EmptyDeckException {
        this.currentRound++;

        Era previousEra = board.getCurrentEra();

        board.shiftUpToLow();

        board.newUpper(decks, playersList.size());

        Era updatedEra = board.getCurrentEra();
        if (updatedEra.compareTo(previousEra) > 0) {
            moveToNextEra(updatedEra);
        }
    }

    /**
     * Advances the match to the specified era.
     * <p>
     * This method:
     * <ul>
     *   <li>Updates the match's current era to the given {@code newEra},</li>
     *   <li>Shifts the building cards on the board down, and</li>
     *   <li>Refills the upper building row with new buildings of the given era.</li>
     * </ul>
     *
     * @param newEra the new {@link Era} the match should transition to
     * @throws EmptyDeckException if the building deck runs out of cards while refilling the upper row
     */
    public void moveToNextEra(Era newEra) throws EmptyDeckException {
        this.currentEra = newEra;

        board.shiftBuildingsDown();

        board.newUpperBuilding(decks, newEra, playersList.size());
    }

    /**
     * Creates and returns a leaderboard of players in the match, sorted from
     * the best result to the worst.
     * <p>
     * Sorting rules (in order):
     * <ol>
     *   <li>Higher prestige points come first.</li>
     *   <li>If two players have the same prestige points, the one with more
     *       food comes first.</li>
     *   <li>If they are still tied, both are considered to share the victory
     *       (the relative order between them in the returned list is
     *       arbitrary).</li>
     * </ol>
     * Detecting the "shared victory" case is left to the caller: it is
     * enough to inspect the prefix of the returned list and check how many
     * leading players are tied on both prestige points and food.
     *
     * @return a {@code List<Player>} representing the sorted leaderboard
     */
    public List<Player> createLeaderboard() {
        // Start from a copy of the players list so we don't mutate the
        // match's own ordering.
        List<Player> leaderboard = new ArrayList<>(playersList);

        // Sort by hand (insertion sort) so the comparison rule is right
        // there in the loop body. For each i, scan back and shift any
        // earlier player that should come AFTER leaderboard[i] one step
        // to the right.
        for (int i = 1; i < leaderboard.size(); i++) {
            Player current = leaderboard.get(i);
            int j = i - 1;
            while (j >= 0 && shouldComeAfter(leaderboard.get(j), current)) {
                leaderboard.set(j + 1, leaderboard.get(j));
                j--;
            }
            leaderboard.set(j + 1, current);
        }
        return leaderboard;
    }

    /**
     * Returns {@code true} when {@code earlier} should appear AFTER
     * {@code later} in the leaderboard, i.e. {@code later} has a strictly
     * better score. The score is compared on prestige points first and
     * food as a tie-breaker (matching the contract documented on
     * {@link #createLeaderboard()}).
     */
    private boolean shouldComeAfter(Player earlier, Player later) {
        if (later.getPrestigePoints() > earlier.getPrestigePoints()) {
            return true;
        }
        if (later.getPrestigePoints() < earlier.getPrestigePoints()) {
            return false;
        }
        // Tie on prestige points: more food wins.
        return later.getFood() > earlier.getFood();
    }

    /**
     * Checks whether a player with the given nickname is present in this match.
     *
     * @param nickname the nickname to search for
     * @return {@code true} if a player with the specified nickname exists in {@code playersList},
     *         {@code false} otherwise
     */
    public boolean isPlayerHere(String nickname) {
        
        for (Player player : playersList) {
            if (player.getNickname().equals(nickname)) {
                return true;
            }
        }
        return false;
    }

    public void chooseOfferingTrackPhase() {
    }

    public void resolveAllOfferingTilesEffect() {
        //get the player order and status
        //ask the tile how much food to give the player and does it, ask how many top cards, how many bottom cards to get
        //receive the index of the card the player want, add the card to the player hand, repeat for the number of card needed
        //ask the player if it wants to buy a building
        //cycle to new player
        //when all player are finished it cycle the player hand calling the DrawingUpperRow method if there are more than 0 cards
        //in the upper row
    }

    public void resolveEndMatch() {
        for (Player player : playersList) {
            player.changePP(player.getInventorCounter()*player.countDistinctInventorIcons());
            player.changePP(10*(player.getPainterCounter()/2));
            List<BuildingCard> buildings = player.getPlayerBuildings();
            for (BuildingCard card : buildings) {
                card.applyEndEffect(player);
                player.changePP(card.getEndOfGamePP());
            }
            player.changePP(player.hasUpperRowBuilding());
        }
    }

    public void resolveEvents() {
        CardRow cardRow= board.getLowerRow();
        List<EventCard> events=cardRow.getEventCards();
        for (EventCard card : events) {
            card.applyEvent(playersList);
        }
    }

    public void resolveBuildings() {
        for (Player player : playersList) {
            List<BuildingCard> buildings = player.getPlayerBuildings();
            for (BuildingCard card : buildings) {
                card.applyEndRoundEffect(player);
            }
        }
    }

    /**
     * Resolves the bonuses (and penalties) granted by the {@link TurnOrderTile}
     * to every player whose totem currently sits on it.
     * <p>
     * Each {@link TurnOrderSlot} on the tile is inspected in order. For every
     * occupied slot the corresponding {@link Player} is resolved by matching
     * {@link TurnOrderSlot#getTotemColor()} against
     * {@link Player#getPlayerColor()} in {@link #playersList}, and the slot's
     * food and prestige point values (see {@link TurnOrderSlot#getFood()} and
     * {@link TurnOrderSlot#getPP()}) are applied to that player via
     * {@link Player#changeFood(int)} and {@link Player#changePP(int)}.
     * <p>
     * In addition, when the slot grants strictly positive food
     * (i.e. {@code getFood() > 0}), every {@link BuildingCard} owned by the
     * player that returns {@code true} from
     * {@link BuildingCard#hasUpperRowBuilding(Player)} contributes one
     * extra food. The sum of these contributions is added on top of the
     * slot's food via {@link Player#changeFood(int)}.
     *
     * @throws IllegalStateException if a slot is occupied by a totem color
     *                               that does not match any player in this match
     */
    public void resolveTurnOrderTile() {
        TurnOrderSlot[] slots = board.getTurnOrderTile().getTurnOrderSlots();
        for (TurnOrderSlot slot : slots) {
            if (!slot.isOccupied()) {
                continue;
            }

            TotemColor color = slot.getTotemColor();
            Player owner = null;
            for (Player p : playersList) {
                if (p.getPlayerColor() == color) {
                    owner = p;
                    break;
                }
            }
            if (owner == null) {
                throw new IllegalStateException(
                        "No player found for totem color " + color + " on turn order tile.");
            }

            int slotFood = slot.getFood();
            int slotPP = slot.getPP();

            owner.changePP(slotPP);
            owner.changeFood(slotFood);

            // When the slot grants positive food, the owner gets one
            // extra food per owned building that grants the upper-row
            // building bonus (see BuildingCard.hasUpperRowBuilding).
            if (slotFood > 0) {
                int buildingBonus = 0;
                for (BuildingCard card : owner.getPlayerBuildings()) {
                    if (card.hasUpperRowBuilding(owner)) {
                        buildingBonus += 1;
                    }
                }
                owner.changeFood(buildingBonus);
            }
        }
    }

    public void startRound() {

        // TODO: implement the logic of the startRound() method

        // movetoNextRoundi)

        // chooseOfferingTrackPhase():
        
        // resolveAllOfferinaTilesEffect():

        // resolveEvents():

        // resolveBuildings():
    }


}
