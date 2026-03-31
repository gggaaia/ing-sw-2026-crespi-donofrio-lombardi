package it.polimi.ingsw.model.state;

import it.polimi.ingsw.model.Match;
import it.polimi.ingsw.model.Player;

public abstract class MatchState {
    public abstract void addPlayer(Match m, Player p);
    public abstract void removePlayer(Match m, Player p);
    public abstract void savePoints(Match m);
    public abstract void startMatch(Match m);
    public abstract void endMatch(Match m);
}
