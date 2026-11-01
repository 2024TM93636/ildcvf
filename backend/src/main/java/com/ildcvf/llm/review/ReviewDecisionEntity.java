package com.ildcvf.llm.review;

import com.ildcvf.llm.persistence.ProposalAttemptEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "llm_review_decisions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_llm_review_attempt",
                        columnNames = "proposal_attempt_id"
                )
        }
)
public class ReviewDecisionEntity {

    @Id
    public UUID id;

    @Column(
            name = "proposal_attempt_id",
            nullable = false,
            updatable = false
    )
    public UUID proposalAttemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "proposal_attempt_id",
            nullable = false,
            updatable = false,
            insertable = false
    )
    public ProposalAttemptEntity proposalAttempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    public ReviewDecision decision;

    @Column(
            name = "reviewer_id",
            nullable = false,
            updatable = false,
            length = 128
    )
    public String reviewerId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    public String justification;

    @Column(name = "decided_at", nullable = false, updatable = false)
    public Instant decidedAt;

    protected ReviewDecisionEntity() {}

    public ReviewDecisionEntity(
            UUID id,
            ProposalAttemptEntity proposalAttempt,
            ReviewDecision decision,
            String reviewerId,
            String justification,
            Instant decidedAt) {

        this.id = id;
        this.proposalAttempt = proposalAttempt;
        this.proposalAttemptId = proposalAttempt.id;
        this.decision = decision;
        this.reviewerId = reviewerId;
        this.justification = justification;
        this.decidedAt = decidedAt;
    }
}
