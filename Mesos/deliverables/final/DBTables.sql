CREATE TABLE players (
                         player_id SERIAL PRIMARY KEY,
                         nickname VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE matches (
                         match_id PRIMARY KEY,
                         match_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         player_count INT NOT NULL
);

CREATE TABLE match_participations (
                                      match_id INT REFERENCES matches(match_id) ON DELETE CASCADE,
                                      player_id INT REFERENCES players(player_id) ON DELETE CASCADE,
                                      final_score INT NOT NULL,
                                      PRIMARY KEY (match_id, player_id)
);