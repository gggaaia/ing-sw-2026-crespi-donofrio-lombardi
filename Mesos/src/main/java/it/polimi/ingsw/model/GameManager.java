package it.polimi.ingsw.model;

import java.util.List;

public class GameManager {
    private List<Match> matches;

    public void createMatch() {
    }

    public void saveMatch() {
    }

    
    /**
     * Retrieves a match by its unique identifier.
     *
     * @param matchID the unique ID of the match to retrieve
     * @return the matching {@link Match} if found, otherwise {@code null}
     */
    public Match retrieveMatch(int matchID) {

        Match foundedMatch = null;

        for (Match match : matches) {
            if (match.getMatchID() == matchID) {
                foundedMatch = match;
                break;
            }
        }

        return foundedMatch;
    }

    public boolean closeMatch() {
        return false;
    }
}
