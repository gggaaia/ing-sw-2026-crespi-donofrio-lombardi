package it.polimi.ingsw.database;

import it.polimi.ingsw.common.dto.FinalStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class DatabaseManager {
    //Simpleton pattern to avoid instancing in the serverController
    private static final DatabaseManager instance = new DatabaseManager();
    private DatabaseManager() {}
    public static DatabaseManager getInstance() { return instance; }


    /**
     * Saves the complete match results, including metadata and player statistics,
     * into the database as a single atomic transaction.
     *
     * @param stats the immutable DTO containing all final match data and scores
     */
    public void saveMatch(FinalStats stats) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            //autocommit disabled to prevent a corrupted or partial save if the connection to the DB crash
            conn.setAutoCommit(false);

            try {
                saveMatchInfo(stats, conn);
                saveParticipations(conn, stats);

                conn.commit();
                System.out.println("[DB] Match stats " + stats.getMatchId() + " succesfully saved.");

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[DB ROLLBACK] Error during save. Transaction blocked: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("[DB ERROR] Connection error with database: " + e.getMessage());
        }
    }

    /**
     * Inserts the general match metadata into the 'matches' table.
     * * @param stats the immutable DTO containing the match results
     * @param conn the active SQL connection to be used for the transaction
     * @throws SQLException if a database access error occurs or the query fails
     */
    private void saveMatchInfo(FinalStats stats, Connection conn) throws SQLException {
        String query = "INSERT INTO matches (match_id, match_date, player_count) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, stats.getMatchId());
            ps.setTimestamp(2, new java.sql.Timestamp(stats.getDate().getTime()));
            ps.setInt(3, stats.getNumPlayers());
            //executeUpdate is method specific of the PreparedStatement class that execute the PreparedStatement
            ps.executeUpdate();
        }
    }

    /**
     * Saves both the players' profiles (if new) and their respective match scores
     * inside the 'match_participations' table in a single optimized loop.
     *
     * @param conn  the active SQL connection
     * @param stats the final match statistics
     * @throws SQLException if a database access error occurs
     */
    private void saveParticipations(Connection conn, FinalStats stats) throws SQLException {

        // Query 1:  Insert the player and if already present fakes a nick update to get the player_id from the players table
        String insertPlayerQuery = "INSERT INTO players (nickname) VALUES (?) " +
                "ON CONFLICT (nickname) DO UPDATE SET nickname = EXCLUDED.nickname " +
                "RETURNING player_id";

        // Query 2: insert the score in the MatchPartecipation table
        String insertParticipationQuery = "INSERT INTO match_participations (match_id, player_id, final_score) VALUES (?, ?, ?)";

        try (PreparedStatement psPlayer = conn.prepareStatement(insertPlayerQuery);
             PreparedStatement psPart = conn.prepareStatement(insertParticipationQuery)) {

            for (Map.Entry<String, Integer> entry : stats.getPlayers().entrySet()) {
                String nickname = entry.getKey();
                int score = entry.getValue();
                int generatedPlayerId = -1;

                psPlayer.setString(1, nickname);

                // executeQuery() instead of executeUpdate() due to the query having "RETURNING player_id"
                try (ResultSet rs = psPlayer.executeQuery()) {
                    if (rs.next()) {
                        generatedPlayerId = rs.getInt("player_id");
                    }
                }

                if (generatedPlayerId == -1) {
                    throw new SQLException("Critical Error: player_id not present for " + nickname);
                }

                psPart.setInt(1, stats.getMatchId());
                psPart.setInt(2, generatedPlayerId);
                psPart.setInt(3, score);

                psPart.executeUpdate();

                psPart.clearParameters();
            }
        }
    }

    /**
     * Retrieves the player leaderboard based on the cumulative sum of scores
     * obtained across all matches with a specific player count.
     *
     * @param playerCount the number of players used to filter the matches
     * @return an ordered map (Nickname -> Total Score) sorted from highest to lowest
     */
    public Map<String, Integer> getLeaderboard(int playerCount) {
        String query = "SELECT p.nickname, SUM(mp.final_score) AS total_score " +
                "FROM match_participations mp " +
                "JOIN matches m ON mp.match_id = m.match_id " +
                "JOIN players p ON mp.player_id = p.player_id " +
                "WHERE m.player_count = ? " +
                "GROUP BY p.player_id, p.nickname " +
                "ORDER BY total_score DESC";

        //LinkedHashMap to preserve the order given by the query
        Map<String, Integer> leaderboard = new LinkedHashMap<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setInt(1, playerCount);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nickname = rs.getString("nickname");
                    int totalScore = rs.getInt("total_score");
                    leaderboard.put(nickname, totalScore);
                }
            }

        } catch (SQLException e) {
            System.err.println("[DB ERROR] Failed to retrieve the leaderboard: " + e.getMessage());
        }

        return leaderboard;
    }
}


