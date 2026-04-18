CREATE TABLE players
(
    id             BIGSERIAL PRIMARY KEY,
    display_name   VARCHAR(50) NOT NULL UNIQUE,
    region         VARCHAR(20) NOT NULL,
    skill_rating   INT         NOT NULL DEFAULT 1000 CHECK (skill_rating >= 0),
    preferred_mode VARCHAR(30) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_players_region ON players (region);

CREATE TABLE matches
(
    id             BIGSERIAL PRIMARY KEY,
    region         VARCHAR(20) NOT NULL,
    game_mode      VARCHAR(30) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    average_rating INT         NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    completed_at   TIMESTAMPTZ
);

CREATE INDEX idx_matches_status_created ON matches (status, created_at);

CREATE TABLE match_players
(
    id           BIGSERIAL PRIMARY KEY,
    match_id     BIGINT      NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    player_id    BIGINT      NOT NULL REFERENCES players (id),
    skill_rating INT         NOT NULL,
    slot_number  INT         NOT NULL DEFAULT 1,
    joined_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_match_player UNIQUE (match_id, player_id)
);

CREATE INDEX idx_match_players_player_id ON match_players (player_id);

CREATE TABLE game_sessions
(
    id             BIGSERIAL PRIMARY KEY,
    match_id       BIGINT       NOT NULL UNIQUE REFERENCES matches (id) ON DELETE CASCADE,
    status         VARCHAR(30)  NOT NULL,
    server_address VARCHAR(100),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    ended_at       TIMESTAMPTZ
);

CREATE INDEX idx_game_sessions_status ON game_sessions (status);

CREATE TABLE session_players
(
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT      NOT NULL REFERENCES game_sessions (id) ON DELETE CASCADE,
    player_id       BIGINT      NOT NULL REFERENCES players (id),
    is_ready        BOOLEAN     NOT NULL DEFAULT FALSE,
    is_connected    BOOLEAN     NOT NULL DEFAULT TRUE,
    last_heartbeat  TIMESTAMPTZ NOT NULL,
    ready_at        TIMESTAMPTZ,
    disconnected_at TIMESTAMPTZ,
    CONSTRAINT uq_session_player UNIQUE (session_id, player_id)
);

CREATE INDEX idx_session_players_connected ON session_players (session_id, is_connected);

CREATE TABLE match_history
(
    id            BIGSERIAL PRIMARY KEY,
    match_id      BIGINT      NOT NULL REFERENCES matches (id),
    player_id     BIGINT      NOT NULL REFERENCES players (id),
    outcome       VARCHAR(20),
    rating_change INT         NOT NULL DEFAULT 0,
    recorded_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_match_history_player ON match_history (player_id, recorded_at);
