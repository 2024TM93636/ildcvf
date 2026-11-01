package com.ildcvf.llm.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_proposal_attempts")
public class ProposalAttemptEntity {
    @Id public UUID id;
    @Column(name="traffic_event_id",nullable=false,length=64) public String trafficEventId;
    @Column(name="finding_id",nullable=false,length=255) public String findingId;
    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=32) public ProposalAttemptStatus status;
    @Column(name="provider_status",length=64) public String providerStatus;
    @Column(name="provider_http_status") public Integer providerHttpStatus;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="proposal_json",columnDefinition="jsonb") public JsonNode proposalJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="validation_json",columnDefinition="jsonb") public JsonNode validationJson;
    @Column(name="failure_message",columnDefinition="text") public String failureMessage;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Column(name="completed_at") public Instant completedAt;
    @Version @Column(name="row_version",nullable=false) public long rowVersion;
    protected ProposalAttemptEntity() {}
    public ProposalAttemptEntity(UUID id,String trafficEventId,String findingId,Instant createdAt) {
        this.id=id;this.trafficEventId=trafficEventId;this.findingId=findingId;
        this.status=ProposalAttemptStatus.IN_PROGRESS;this.createdAt=createdAt;
    }
}
