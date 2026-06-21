package it.polimi.ingsw.common.dto;

import java.util.Date;
import java.util.Map;
import java.util.Collections;

public class FinalStats {
    private final int matchId;
    private final Date date;
    private final int numPlayers;
    private final Map<String, Integer> players;

    public FinalStats(int matchId,Date date, int numPlayers, Map<String, Integer> players) {
        this.matchId = matchId;
        this.date = date;
        this.numPlayers = numPlayers;
        this.players = Collections.unmodifiableMap(players);
    }

    public int getMatchId() { return matchId; }
    public Date getDate() { return this.date; }
    public int getNumPlayers() { return this.numPlayers; }
    public Map<String, Integer> getPlayers() { return this.players; }
}