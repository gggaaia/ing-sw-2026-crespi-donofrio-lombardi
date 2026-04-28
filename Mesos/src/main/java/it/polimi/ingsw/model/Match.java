package it.polimi.ingsw.model;

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
    }

    public int getMatchID() {
        return matchID;
    }

    public void addPlayer(Match m, Player p) {
    }

    public void removePlayer(Player p) {
    }

    public void savePoints() {
    }

    public void startMatch() {
    }

    public void endMatch() {
    }

    public void moveToNextRound() {
    }

    public void moveToNextEra() {
    }

    public List<Player> createLeaderboard() {
        return null;
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
    }

    public void resolveEndMatch() {
        for (Player player : playersList) {
            //TODO: implements the InventorIconCount
            player.changePP(player.getInventorCounter()*/*contatore icone diverse*/);
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
