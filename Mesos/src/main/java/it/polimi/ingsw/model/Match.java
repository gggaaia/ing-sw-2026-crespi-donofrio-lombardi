package it.polimi.ingsw.model;

import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.state.MatchState;

import java.util.Date;
import java.util.List;

public class Match {
    private int matchID;
    private MatchState status;
    private Date date;
    private List<Player> playersList;
    private int currentRound;
    private Era currentEra;
    private int currentPlayersNumber;
    private Board board;
    private Decks decks;

    public void addPlayer(Match m, Player p) {
    }

    public void removePlayer(Match m, Player p) {
    }

    public void savePoints(Match m) {
    }

    public void startMatch(Match m) {
    }

    public void endMatch(Match m) {
    }

    public void moveToNextRound() {
    }

    public void moveToNextEra() {
    }

    public List<Player> createLeaderboard() {
        return null;
    }

    public boolean isPlayerHere(String nickname) {
        return false;
    }

    public void chooseOfferingTrackPhase() {
    }

    public void resolveAllOfferingTilesEffect() {
    }

    public void resolveEndMatch() {
    }

    public void resolveEvents() {
    }

    public void resolveBuildings() {
    }

    public void startRound() {
    }

    public void createBoard() {
    }
}
