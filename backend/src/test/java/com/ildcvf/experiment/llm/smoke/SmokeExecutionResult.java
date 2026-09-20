package com.ildcvf.experiment.llm.smoke;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.evaluation.GroundTruthComparison;
import com.ildcvf.experiment.llm.evaluation.HallucinationEvaluation;
import com.ildcvf.experiment.llm.groq.GroqHttpResponse;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.llm.validation.ProposalValidationResult;

import java.util.Objects;

public record SmokeExecutionResult(
        SmokeDefinition definition,
        LlmReliabilityScenario scenario,
        GroqProviderAttempt providerAttempt,
        GroqHttpResponse rawHttpResponse,
        ProposalValidationResult validationResult,
        GroundTruthComparison groundTruthComparison,
        HallucinationEvaluation hallucinationEvaluation,
        int providerInvocationCount
) {

    public SmokeExecutionResult {
        Objects.requireNonNull(
                definition,
                "definition must not be null");

        Objects.requireNonNull(
                scenario,
                "scenario must not be null");

        Objects.requireNonNull(
                providerAttempt,
                "providerAttempt must not be null");

        if (providerInvocationCount != 1) {
            throw new IllegalArgumentException(
                    "A completed D2 smoke execution "
                            + "must contain exactly one "
                            + "provider invocation.");
        }

        boolean scientificResultAvailable =
                providerAttempt.proposalParseResult()
                        != null
                        && providerAttempt
                        .proposalParseResult()
                        .proposal()
                        != null;

        if (scientificResultAvailable) {
            Objects.requireNonNull(
                    validationResult,
                    "validationResult must be available "
                            + "for a parsed proposal");

            Objects.requireNonNull(
                    groundTruthComparison,
                    "groundTruthComparison must be available "
                            + "for a parsed proposal");

            Objects.requireNonNull(
                    hallucinationEvaluation,
                    "hallucinationEvaluation must be available "
                            + "for a parsed proposal");
        } else if (validationResult != null
                || groundTruthComparison != null
                || hallucinationEvaluation != null) {

            throw new IllegalArgumentException(
                    "Scientific results must be null "
                            + "when no parsed proposal exists.");
        }
    }

    public boolean infrastructurePassed() {
        return providerAttempt.envelope() != null
                && providerAttempt.metadata().httpStatus() != null
                && providerAttempt.metadata().httpStatus() >= 200
                && providerAttempt.metadata().httpStatus() < 300
                && providerAttempt.envelope()
                .assistantContent() != null;
    }
}
