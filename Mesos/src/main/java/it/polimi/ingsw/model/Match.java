package it.polimi.ingsw.model;

import it.polimi.ingsw.exceptions.DeckLoadException;
import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.state.LobbyState;
import it.polimi.ingsw.model.state.MatchState;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Match {
    private final int matchID;
    private MatchState status = new LobbyState();
    private final Date date = new Date();
    private final List<Player> playersList = new ArrayList<>();
    private int currentRound = 0;
    private Era currentEra = Era.ERA_1;
    private int currentPlayersNumber = 0;
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

    public void addPlayer(Match m, Player p) {
        if (!playersList.contains(p)) {
            playersList.add(p);
        }
    }

    public void removePlayer(Player p) {
        if (playersList.contains(p)) {
            playersList.remove(p);
        }
    }

    public void savePoints() {
        // TODO: database saving functionality
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
     *   <li>Sets up the upper card row on the board, and</li>
     *   <li>Sets up the upper building row on the board.</li>
     * </ul>
     *
     * @throws DeckLoadException  if the player count is invalid or the cards file cannot be loaded
     * @throws EmptyDeckException if a deck runs out of cards while populating the board rows
     */
    public void startMatch() throws DeckLoadException, EmptyDeckException {
        this.currentRound = 1;

        int numPlayers = playersList.size();

        decks.populateDecks(numPlayers);

        board.setupOfferingTiles(numPlayers);

        board.setupTurnOrderTiles(numPlayers);

        board.setupLower(decks, numPlayers);

        board.newUpper(decks, numPlayers);

        board.newUpperBuilding(decks, Era.ERA_1, numPlayers);
    }

    public void endMatch() {
        resolveEndMatch();
        // TODO: saving to database functionality
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
     * Creates and returns a leaderboard of players in the match, sorted in descending order
     * by their prestige points (i.e., players with more prestige points come first).
     *
     * @return a {@code List<Player>} representing the sorted leaderboard
     */
    public List<Player> createLeaderboard() {
        List<Player> leaderboard = new ArrayList<>(playersList);
        leaderboard.sort((player1, player2) -> Integer.compare(player2.getPrestigePoints(), player1.getPrestigePoints()));
        return leaderboard;
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
            player.changePP(player.getPPBuilderBonus());
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

    public void startRound() {

        // TODO: implement the logic of the startRound() method

        // movetoNextRoundi)

        // chooseOfferingTrackPhase():
        
        // resolveAllOfferinaTilesEffect():

        // resolveEvents():

        // resolveBuildings():
    }

    public void createBoard() {
    }
}
