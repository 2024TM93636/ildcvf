package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.groq.ProviderAttemptMetadata;
import com.ildcvf.experiment.llm.groq.ProviderCallStatus;
import com.ildcvf.experiment.llm.groq.ProposalParseStatus;

import java.util.Objects;

public record AttemptEvaluation(
        AttemptIdentity identity,
        String evidenceFingerprint,
        ProviderCallStatus providerCallStatus,
        ProposalParseStatus proposalParseStatus,
        ValidatorEvaluation validator,
        GroundTruthEvaluation groundTruth,
        HallucinationEvaluation hallucination,
        ProviderAttemptMetadata providerMetadata
) {

    public AttemptEvaluation {
        Objects.requireNonNull(
                identity,
                "identity must not be null");

        if (evidenceFingerprint == null
                || evidenceFingerprint.isBlank()) {
            throw new IllegalArgumentException(
                    "evidenceFingerprint must not be blank");
        }

        Objects.requireNonNull(
                providerCallStatus,
                "providerCallStatus must not be null");

        Objects.requireNonNull(
                validator,
                "validator must not be null");

        Objects.requireNonNull(
                groundTruth,
                "groundTruth must not be null");

        Objects.requireNonNull(
                hallucination,
                "hallucination must not be null");

        Objects.requireNonNull(
                providerMetadata,
                "providerMetadata must not be null");
    }

    public String attemptId() {
        return identity.attemptId();
    }

    public String scenarioId() {
        return identity.scenarioId();
    }

    public int repetition() {
        return identity.repetition();
    }

    public boolean parsedProposalAvailable() {
        return proposalParseStatus
                == ProposalParseStatus.SUCCESS;
    }
}
