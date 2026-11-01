CREATE TABLE llm_proposal_attempts (
    id UUID PRIMARY KEY,
    traffic_event_id VARCHAR(64) NOT NULL,
    finding_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_status VARCHAR(64),
    provider_http_status INTEGER,
    proposal_json JSONB,
    validation_json JSONB,
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT llm_attempt_status_check CHECK (status IN
      ('IN_PROGRESS','VALIDATED','REJECTED','PARSE_FAILURE','PROVIDER_FAILURE','TIMEOUT')),
    CONSTRAINT llm_attempt_completion_check CHECK
      ((status = 'IN_PROGRESS' AND completed_at IS NULL) OR
       (status <> 'IN_PROGRESS' AND completed_at IS NOT NULL)),
    CONSTRAINT llm_attempt_http_status_check CHECK
      (provider_http_status IS NULL OR provider_http_status BETWEEN 100 AND 599),
    CONSTRAINT llm_attempt_validation_check CHECK
      (status NOT IN ('VALIDATED','REJECTED') OR
       (proposal_json IS NOT NULL AND validation_json IS NOT NULL))
);
CREATE UNIQUE INDEX uq_llm_active_finding
    ON llm_proposal_attempts (traffic_event_id, finding_id)
    WHERE status = 'IN_PROGRESS';
CREATE INDEX idx_llm_attempt_recent
    ON llm_proposal_attempts (created_at DESC, id DESC);
CREATE INDEX idx_llm_attempt_source_recent
    ON llm_proposal_attempts (traffic_event_id, finding_id, created_at DESC);
