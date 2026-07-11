CREATE TABLE IF NOT EXISTS events (
    id BIGSERIAL PRIMARY KEY,
    annotation VARCHAR(2000) NOT NULL,
    category_id BIGINT NOT NULL,
    created_on TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    description VARCHAR(7000) NOT NULL,
    event_date TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    initiator_id BIGINT NOT NULL,
    location_lat FLOAT,
    location_lon FLOAT,
    paid BOOLEAN NOT NULL,
    participant_limit INT NOT NULL,
    published_on TIMESTAMP WITHOUT TIME ZONE,
    request_moderation BOOLEAN NOT NULL,
    state VARCHAR(20) NOT NULL,
    title VARCHAR(120) NOT NULL
);