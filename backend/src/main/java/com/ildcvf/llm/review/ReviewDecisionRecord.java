package com.ildcvf.llm.review;

import java.time.Instant;
import java.util.UUID;

public record ReviewDecisionRecord(
        UUID reviewId,
        UUID proposalAttemptId,
        ReviewDecision decision,
        String reviewerId,
        String justification,
        Instant decidedAt
) {
    public static ReviewDecisionRecord from(ReviewDecisionEntity e) {
        return new ReviewDecisionRecord(
                e.id,
                e.proposalAttemptId,
                e.decision,
                e.reviewerId,
                e.justification,
                e.decidedAt
        );
    }
}
