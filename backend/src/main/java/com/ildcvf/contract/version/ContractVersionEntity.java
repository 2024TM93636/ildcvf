package com.ildcvf.contract.version;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Stored candidate snapshot; no public setters and no update/delete service. */
@Entity
@Table(name = "contract_versions")
public class ContractVersionEntity {
    @Id public UUID id;
    @Column(name = "baseline_identifier", nullable = false, updatable = false, length = 255)
    public String baselineIdentifier;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "baseline_sha256", nullable = false, updatable = false, length = 64)
    public String baselineSha256;
    @Column(name = "proposal_attempt_id", nullable = false, updatable = false, unique = true)
    public UUID proposalAttemptId;
    @Column(name = "review_decision_id", nullable = false, updatable = false, unique = true)
    public UUID reviewDecisionId;
    @Column(name = "openapi_yaml", nullable = false, updatable = false, columnDefinition = "text")
    public String openapiYaml;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "document_sha256", nullable = false, updatable = false, length = 64)
    public String documentSha256;
    @Column(name = "validation_status", nullable = false, updatable = false, length = 16)
    public String validationStatus;
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    protected ContractVersionEntity() {}

    ContractVersionEntity(UUID id, String baselineIdentifier, String baselineSha256,
                          UUID proposalAttemptId, UUID reviewDecisionId, String openapiYaml,
                          String documentSha256, Instant createdAt) {
        this.id = id;
        this.baselineIdentifier = baselineIdentifier;
        this.baselineSha256 = baselineSha256;
        this.proposalAttemptId = proposalAttemptId;
        this.reviewDecisionId = reviewDecisionId;
        this.openapiYaml = openapiYaml;
        this.documentSha256 = documentSha256;
        this.validationStatus = "VALID";
        this.createdAt = createdAt;
    }
}
