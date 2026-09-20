package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.experiment.llm.groq.ProposalParseResult;
import com.ildcvf.experiment.llm.groq.ProposalParseStatus;
import com.ildcvf.experiment.llm.groq.ProviderAttemptMetadata;
import com.ildcvf.experiment.llm.groq.ProviderCallStatus;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttemptEvaluatorTest {

    private final AttemptEvaluator evaluator =
            new AttemptEvaluator();

    @Test
    void exactProposalProducesThreeIndependentPositiveDimensions() {
        LlmReliabilityScenario scenario =
                scenario(1);

        ContractChangeProposal proposal =
                canonical(scenario);

        AttemptEvaluation result =
                evaluator.evaluate(
                        scenario,
                        1,
                        parsedAttempt(
                                scenario,
                                proposal,
                                10));

        assertEquals(
                Boolean.TRUE,
                result.validator().accepted());

        assertEquals(
                Boolean.FALSE,
                result.hallucination()
                        .hallucinated());

        assertTrue(
                result.groundTruth()
                        .comparison()
                        .exactMatch());
    }

    @Test
    void inventedMethodProducesRejectionHallucinationAndIncorrectness() {
        LlmReliabilityScenario scenario =
                scenario(1);

        ContractChangeProposal canonical =
                canonical(scenario);

        ContractChangeProposal invented =
                new ContractChangeProposal(
                        canonical.proposalId(),
                        canonical.sourceTrafficEventId(),
                        canonical.sourceFindingId(),
                        canonical.driftType(),
                        canonical.changeKind(),
                        "DELETE",
                        canonical.runtimePath(),
                        canonical.matchedOpenApiPath(),
                        canonical.targetFieldPath(),
                        canonical.expected(),
                        canonical.observed(),
                        canonical.proposedValue(),
                        canonical.rationale());

        AttemptEvaluation result =
                evaluator.evaluate(
                        scenario,
                        1,
                        parsedAttempt(
                                scenario,
                                invented,
                                10));

        assertEquals(
                Boolean.FALSE,
                result.validator().accepted());

        assertEquals(
                Boolean.TRUE,
                result.hallucination()
                        .hallucinated());

        assertFalse(
                result.groundTruth()
                        .comparison()
                        .exactMatch());

        assertTrue(
                result.hallucination()
                        .reasonCodes()
                        .contains(
                                HallucinationReasonCode
                                        .METHOD_UNSUPPORTED));
    }

    @Test
    void structuralRejectionCanBeNonHallucinated() {
        LlmReliabilityScenario scenario =
                scenario(1);

        ContractChangeProposal canonical =
                canonical(scenario);

        ContractChangeProposal incomplete =
                new ContractChangeProposal(
                        "",
                        canonical.sourceTrafficEventId(),
                        canonical.sourceFindingId(),
                        canonical.driftType(),
                        canonical.changeKind(),
                        canonical.method(),
                        canonical.runtimePath(),
                        canonical.matchedOpenApiPath(),
                        canonical.targetFieldPath(),
                        canonical.expected(),
                        canonical.observed(),
                        canonical.proposedValue(),
                        canonical.rationale());

        AttemptEvaluation result =
                evaluator.evaluate(
                        scenario,
                        1,
                        parsedAttempt(
                                scenario,
                                incomplete,
                                10));

        assertEquals(
                Boolean.FALSE,
                result.validator().accepted());

        assertEquals(
                Boolean.FALSE,
                result.hallucination()
                        .hallucinated());

        /*
         * proposalId is intentionally not one of the 11 scored fields.
         * Therefore the proposal remains an exact structured ground-truth
         * match even though M7 rejects its required structure.
         *
         * This is the explicit evidence that validator acceptance and
         * 11-field correctness are independent dimensions.
         */
        assertTrue(
                result.groundTruth()
                        .comparison()
                        .exactMatch());
    }

    @Test
    void parseFailureMakesAllScientificProposalScoringNotApplicable() {
        LlmReliabilityScenario scenario =
                scenario(1);

        AttemptEvaluation result =
                evaluator.evaluate(
                        scenario,
                        1,
                        parseFailureAttempt(
                                scenario,
                                10));

        assertEquals(
                EvaluationApplicability.NOT_APPLICABLE,
                result.validator()
                        .applicability());

        assertEquals(
                EvaluationApplicability.NOT_APPLICABLE,
                result.groundTruth()
                        .applicability());

        assertEquals(
                EvaluationApplicability.NOT_APPLICABLE,
                result.hallucination()
                        .applicability());
    }

    @Test
    void providerFailureMakesAllScientificProposalScoringNotApplicable() {
        LlmReliabilityScenario scenario =
                scenario(1);

        AttemptEvaluation result =
                evaluator.evaluate(
                        scenario,
                        1,
                        failureAttempt(
                                scenario,
                                ProviderCallStatus.TIMEOUT));

        assertEquals(
                EvaluationApplicability.NOT_APPLICABLE,
                result.validator()
                        .applicability());

        assertEquals(
                EvaluationApplicability.NOT_APPLICABLE,
                result.groundTruth()
                        .applicability());

        assertEquals(
                EvaluationApplicability.NOT_APPLICABLE,
                result.hallucination()
                        .applicability());
    }

    @Test
    void mismatchedEvidenceFingerprintFailsClosed() {
        LlmReliabilityScenario scenario =
                scenario(1);

        ProviderAttemptMetadata badMetadata =
                new ProviderAttemptMetadata(
                        "groq",
                        "openai/gpt-oss-20b",
                        null,
                        null,
                        "on_demand",
                        0.0d,
                        1.0d,
                        20260919L,
                        512,
                        "prompt",
                        "wrong-fingerprint",
                        200,
                        Duration.ofMillis(10),
                        null,
                        null,
                        null,
                        "stop",
                        Instant.EPOCH);

        GroqProviderAttempt attempt =
                new GroqProviderAttempt(
                        ProviderCallStatus.SUCCESS,
                        badMetadata,
                        null,
                        ProposalParseResult.success(
                                canonical(scenario)),
                        null);

        assertThrows(
                IllegalStateException.class,
                () -> evaluator.evaluate(
                        scenario,
                        1,
                        attempt));
    }

    private LlmReliabilityScenario scenario(
            int index
    ) {
        return new LlmScenarioCatalog()
                .scenarios()
                .get(index);
    }

    private ContractChangeProposal canonical(
            LlmReliabilityScenario scenario
    ) {
        return scenario.groundTruth()
                .toCanonicalProposal(
                        scenario.scenarioId());
    }

    private GroqProviderAttempt parsedAttempt(
            LlmReliabilityScenario scenario,
            ContractChangeProposal proposal,
            long latencyMs
    ) {
        return new GroqProviderAttempt(
                ProviderCallStatus.SUCCESS,
                metadata(
                        scenario,
                        latencyMs,
                        100L,
                        20L,
                        120L),
                null,
                ProposalParseResult.success(
                        proposal),
                null
        );
    }

    private GroqProviderAttempt parseFailureAttempt(
            LlmReliabilityScenario scenario,
            long latencyMs
    ) {
        return new GroqProviderAttempt(
                ProviderCallStatus.PROPOSAL_PARSE_FAILURE,
                metadata(
                        scenario,
                        latencyMs,
                        100L,
                        20L,
                        120L),
                null,
                ProposalParseResult.failure(
                        ProposalParseStatus.MALFORMED_JSON,
                        "synthetic malformed proposal"),
                "synthetic parse failure"
        );
    }

    private GroqProviderAttempt failureAttempt(
            LlmReliabilityScenario scenario,
            ProviderCallStatus status
    ) {
        return new GroqProviderAttempt(
                status,
                metadata(
                        scenario,
                        10,
                        null,
                        null,
                        null),
                null,
                null,
                "synthetic provider failure"
        );
    }

    private ProviderAttemptMetadata metadata(
            LlmReliabilityScenario scenario,
            long latencyMs,
            Long input,
            Long output,
            Long total
    ) {
        return new ProviderAttemptMetadata(
                "groq",
                "openai/gpt-oss-20b",
                "openai/gpt-oss-20b",
                "synthetic",
                "on_demand",
                0.0d,
                1.0d,
                20260919L,
                512,
                "synthetic-prompt",
                scenario.evidenceFingerprint(),
                200,
                Duration.ofMillis(latencyMs),
                input,
                output,
                total,
                "stop",
                Instant.EPOCH
        );
    }
}
