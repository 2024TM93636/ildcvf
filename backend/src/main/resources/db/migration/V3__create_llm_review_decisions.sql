CREATE TABLE llm_review_decisions (
    id UUID PRIMARY KEY,
    proposal_attempt_id UUID NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reviewer_id VARCHAR(128) NOT NULL,
    justification TEXT NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_llm_review_attempt FOREIGN KEY (proposal_attempt_id)
        REFERENCES llm_proposal_attempts(id) ON DELETE RESTRICT,
    CONSTRAINT uq_llm_review_attempt UNIQUE (proposal_attempt_id),
    CONSTRAINT llm_review_decision_check CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT llm_review_reviewer_check CHECK (length(btrim(reviewer_id)) > 0),
    CONSTRAINT llm_review_justification_check CHECK (length(btrim(justification)) > 0)
);
CREATE INDEX idx_llm_review_recent ON llm_review_decisions (decided_at DESC, id DESC);
