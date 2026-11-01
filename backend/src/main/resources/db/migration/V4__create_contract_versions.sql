CREATE TABLE contract_versions (
    id UUID PRIMARY KEY,
    baseline_identifier VARCHAR(255) NOT NULL,
    baseline_sha256 CHAR(64) NOT NULL,
    proposal_attempt_id UUID NOT NULL,
    review_decision_id UUID NOT NULL,
    openapi_yaml TEXT NOT NULL,
    document_sha256 CHAR(64) NOT NULL,
    validation_status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_contract_version_attempt FOREIGN KEY (proposal_attempt_id)
        REFERENCES llm_proposal_attempts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_version_review FOREIGN KEY (review_decision_id)
        REFERENCES llm_review_decisions(id) ON DELETE RESTRICT,
    CONSTRAINT uq_contract_version_attempt UNIQUE (proposal_attempt_id),
    CONSTRAINT uq_contract_version_review UNIQUE (review_decision_id),
    CONSTRAINT ck_contract_version_status CHECK (validation_status = 'VALID'),
    CONSTRAINT ck_contract_version_baseline CHECK (length(btrim(baseline_identifier)) > 0),
    CONSTRAINT ck_contract_version_yaml CHECK (length(btrim(openapi_yaml)) > 0),
    CONSTRAINT ck_contract_version_baseline_sha CHECK (baseline_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_contract_version_document_sha CHECK (document_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_contract_versions_recent ON contract_versions (created_at DESC, id DESC);
