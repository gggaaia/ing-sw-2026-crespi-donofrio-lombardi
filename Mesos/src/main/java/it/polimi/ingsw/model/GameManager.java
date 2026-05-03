package it.polimi.ingsw.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level registry of the {@link Match} instances currently known to the
 * server.
 * <p>
 * The intended flow is:
 * <ul>
 *   <li>{@link #createMatch()} — create a new match and remember it.</li>
 *   <li>{@link #retrieveMatch(int)} — look up an existing match by id.</li>
 *   <li>{@link #closeMatch(int)} — remove a match from the registry once
 *       it has finished (the controller layer is responsible for any disk
 *       cleanup, e.g. removing the saved snapshot file).</li>
 * </ul>
 */
public class GameManager {
    /**
     * The currently active matches. Initialized to an empty list so
     * {@link #createMatch()} can append immediately without an NPE.
     */
    private final List<Match> matches = new ArrayList<>();

    /**
     * Creates a new match, adds it to the list of matches, and returns its
     * unique identifier.
     *
     * @return the unique ID of the newly created match
     */
    public int createMatch() {
        Match newMatch = new Match();
        matches.add(newMatch);
        return newMatch.getMatchID();
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

    /**
     * Removes the match with the given id from the registry.
     * <p>
     * Note: this method only updates the in-memory registry. Cleaning up any
     * persisted snapshot file is the controller layer's responsibility (the
     * {@code PersistenceManager} of Pass 2).
     *
     * @param matchID the id of the match to remove
     * @return {@code true} if a matching match was found and removed,
     *         {@code false} otherwise
     */
    public boolean closeMatch(int matchID) {
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).getMatchID() == matchID) {
                matches.remove(i);
                return true;
            }
        }
        return false;
    }
}
