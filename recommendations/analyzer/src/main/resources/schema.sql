CREATE TABLE IF NOT EXISTS event_similarity (
    id BIGSERIAL PRIMARY KEY,
    event_a BIGINT NOT NULL,
    event_b BIGINT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE(event_a, event_b)
);

CREATE TABLE IF NOT EXISTS user_interactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    weight DOUBLE PRECISION NOT NULL,
    last_action_at BIGINT NOT NULL,
    UNIQUE(user_id, event_id)
);