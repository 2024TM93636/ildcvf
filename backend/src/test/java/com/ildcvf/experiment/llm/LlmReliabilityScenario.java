package com.ildcvf.experiment.llm;

import com.ildcvf.llm.evidence.GroundedEvidencePackage;

import java.util.Objects;

public record LlmReliabilityScenario(
        String scenarioId,
        String description,
        boolean e6OnlySynthetic,
        GroundedEvidencePackage evidence,
        String evidenceFingerprint,
        LlmProposalGroundTruth groundTruth
) {

    public LlmReliabilityScenario {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException(
                    "scenarioId must not be blank");
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "description must not be blank");
        }

        Objects.requireNonNull(
                evidence,
                "evidence must not be null");

        if (evidenceFingerprint == null
                || evidenceFingerprint.isBlank()) {
            throw new IllegalArgumentException(
                    "evidenceFingerprint must not be blank");
        }

        Objects.requireNonNull(
                groundTruth,
                "groundTruth must not be null");
    }
}