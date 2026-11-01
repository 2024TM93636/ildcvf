package com.ildcvf.contract.version;

import java.time.Instant;
import java.util.UUID;

public record ContractVersionMetadataDto(
        UUID id,
        UUID proposalAttemptId,
        UUID reviewDecisionId,
        String baselineIdentifier,
        String baselineSha256,
        String documentSha256,
        String validationStatus,
        Instant createdAt
) {
    public static ContractVersionMetadataDto from(
            ContractVersionRecord record) {

        return new ContractVersionMetadataDto(
                record.id(),
                record.proposalAttemptId(),
                record.reviewDecisionId(),
                record.baselineIdentifier(),
                record.baselineSha256(),
                record.documentSha256(),
                record.validationStatus(),
                record.createdAt()
        );
    }
}
