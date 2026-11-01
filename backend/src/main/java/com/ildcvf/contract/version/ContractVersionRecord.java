package com.ildcvf.contract.version;

import java.time.Instant;
import java.util.UUID;

/** Immutable outward-facing snapshot; do not return JPA entities. */
public record ContractVersionRecord(UUID id, String baselineIdentifier, String baselineSha256,
        UUID proposalAttemptId, UUID reviewDecisionId, String openapiYaml,
        String documentSha256, String validationStatus, Instant createdAt) {
    static ContractVersionRecord from(ContractVersionEntity e) {
        return new ContractVersionRecord(e.id, e.baselineIdentifier, e.baselineSha256,
                e.proposalAttemptId, e.reviewDecisionId, e.openapiYaml,
                e.documentSha256, e.validationStatus, e.createdAt);
    }
}
