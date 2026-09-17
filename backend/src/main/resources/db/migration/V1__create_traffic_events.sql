CREATE SEQUENCE traffic_event_sequence
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE TABLE traffic_events (
    sequence BIGINT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    timestamp TIMESTAMPTZ NOT NULL,
    target_service VARCHAR(100) NOT NULL,
    upstream_reached BOOLEAN NOT NULL,

    method VARCHAR(16) NOT NULL,
    request_path TEXT NOT NULL,
    query_string TEXT,
    query_parameters JSONB NOT NULL,
    request_headers JSONB NOT NULL,
    request_content_type VARCHAR(255),
    request_body TEXT,
    request_body_size_bytes BIGINT NOT NULL,
    request_body_truncated BOOLEAN NOT NULL,

    response_status INTEGER NOT NULL,
    response_headers JSONB NOT NULL,
    response_content_type VARCHAR(255),
    response_body TEXT,
    response_body_size_bytes BIGINT NOT NULL,
    response_body_truncated BOOLEAN NOT NULL,

    duration_ms BIGINT NOT NULL
);
