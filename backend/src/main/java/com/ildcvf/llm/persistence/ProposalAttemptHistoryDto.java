package com.ildcvf.llm.persistence;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Read-only public view: never includes provider request or raw response bodies. */
public record ProposalAttemptHistoryDto(
    UUID attemptId, String trafficEventId, String findingId,
    ProposalAttemptStatus status, String providerStatus, Integer providerHttpStatus,
    JsonNode proposal, JsonNode validation, String message,
    Instant createdAt, Instant completedAt, boolean reviewEligible) {
    public static ProposalAttemptHistoryDto from(ProposalAttemptEntity e) {
        return new ProposalAttemptHistoryDto(e.id, e.trafficEventId, e.findingId,
            e.status, e.providerStatus, e.providerHttpStatus,
            e.proposalJson == null ? null : e.proposalJson.deepCopy(),
            e.validationJson == null ? null : e.validationJson.deepCopy(),
            e.failureMessage, e.createdAt, e.completedAt,
            e.status == ProposalAttemptStatus.VALIDATED);
    }
}
