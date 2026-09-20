package com.ildcvf.experiment.llm;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeProposal;

import java.util.Objects;

public record LlmProposalGroundTruth(
        String sourceTrafficEventId,
        String sourceFindingId,
        DriftType driftType,
        ContractChangeKind changeKind,
        String method,
        String runtimePath,
        String matchedOpenApiPath,
        String targetFieldPath,
        String expected,
        String observed,
        String proposedValue
) {

    public LlmProposalGroundTruth {
        Objects.requireNonNull(
                sourceTrafficEventId,
                "sourceTrafficEventId must not be null");
        Objects.requireNonNull(
                sourceFindingId,
                "sourceFindingId must not be null");
        Objects.requireNonNull(
                driftType,
                "driftType must not be null");
        Objects.requireNonNull(
                changeKind,
                "changeKind must not be null");
        Objects.requireNonNull(
                method,
                "method must not be null");
        Objects.requireNonNull(
                runtimePath,
                "runtimePath must not be null");
    }

    public ContractChangeProposal toCanonicalProposal(
            String scenarioId
    ) {
        Objects.requireNonNull(
                scenarioId,
                "scenarioId must not be null");

        return new ContractChangeProposal(
                "ground-truth-" + scenarioId,
                sourceTrafficEventId,
                sourceFindingId,
                driftType,
                changeKind,
                method,
                runtimePath,
                matchedOpenApiPath,
                targetFieldPath,
                expected,
                observed,
                proposedValue,
                "Canonical deterministic E6 ground-truth proposal."
        );
    }

    public static LlmProposalGroundTruth declare(
            GroundedEvidencePackage evidence,
            ContractChangeKind expectedChangeKind,
            String expectedProposedValue
    ) {
        Objects.requireNonNull(
                evidence,
                "evidence must not be null");
        Objects.requireNonNull(
                expectedChangeKind,
                "expectedChangeKind must not be null");

        return new LlmProposalGroundTruth(
                evidence.trafficEventId(),
                evidence.findingId(),
                evidence.driftType(),
                expectedChangeKind,
                evidence.method(),
                evidence.runtimePath(),
                evidence.matchedOpenApiPath(),
                evidence.fieldPath(),
                evidence.expected(),
                evidence.observed(),
                expectedProposedValue
        );
    }
}