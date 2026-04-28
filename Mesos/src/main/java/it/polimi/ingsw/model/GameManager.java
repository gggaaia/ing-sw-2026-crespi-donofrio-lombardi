package it.polimi.ingsw.model;

import java.util.List;

public class GameManager {
    private List<Match> matches;

    /**
     * Creates a new match, adds it to the list of matches, and returns its unique
     * identifier.
     *
     * @return the unique ID of the newly created match
     */
    public int createMatch() {
        Match newMatch = new Match();
        matches.add(newMatch);
        return newMatch.getMatchID();
    }

    public void saveMatch(int matchID) {
        // TODO: add databse saving functionality
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

    public boolean closeMatch(int matchID) {
        // TODO add database closing functionality + ??
        return false;
    }
}
