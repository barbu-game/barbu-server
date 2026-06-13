CREATE TABLE player_ratings (
    user_id      BIGINT PRIMARY KEY REFERENCES users(id),
    rating       INT NOT NULL,
    games_played INT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP NOT NULL
);
