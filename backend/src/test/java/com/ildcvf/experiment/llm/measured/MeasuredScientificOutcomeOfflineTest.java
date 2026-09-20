package com.ildcvf.experiment.llm.measured;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasuredScientificOutcomeOfflineTest {

    @TempDir
    Path tempDir;

    @Test
    void providerFailureIsRetainedAndNotRetried()
            throws Exception {

        MeasuredAttemptResult result =
                executeSynthetic(
                        "HTTP_5XX",
                        "NOT_APPLICABLE",
                        "NOT_APPLICABLE",
                        null,
                        List.of(),
                        "NOT_APPLICABLE",
                        null,
                        "NOT_APPLICABLE",
                        null,
                        List.of()
                );

        assertEquals(
                "HTTP_5XX",
                result.providerCallStatus()
        );

        assertEquals(1, result.providerInvocationCount());
        assertEquals(0, result.retryCount());
    }

    @Test
    void parseFailureIsRetainedAndNotRetried()
            throws Exception {

        MeasuredAttemptResult result =
                executeSynthetic(
                        "SUCCESS",
                        "MALFORMED_JSON",
                        "NOT_APPLICABLE",
                        null,
                        List.of(),
                        "NOT_APPLICABLE",
                        null,
                        "NOT_APPLICABLE",
                        null,
                        List.of()
                );

        assertEquals(
                "MALFORMED_JSON",
                result.proposalParseStatus()
        );

        assertEquals(1, result.providerInvocationCount());
        assertEquals(0, result.retryCount());
    }

    @Test
    void validatorRejectionIsScientificData()
            throws Exception {

        MeasuredAttemptResult result =
                executeSynthetic(
                        "SUCCESS",
                        "SUCCESS",
                        "APPLICABLE",
                        false,
                        List.of(
                                "PROPOSED_VALUE_NOT_GROUNDED"
                        ),
                        "APPLICABLE",
                        false,
                        "APPLICABLE",
                        true,
                        List.of(
                                "PROPOSED_VALUE_UNSUPPORTED"
                        )
                );

        assertEquals(
                "APPLICABLE",
                result.validatorApplicability()
        );

        assertFalse(result.validatorAccepted());

        assertEquals(
                List.of(
                        "PROPOSED_VALUE_NOT_GROUNDED"
                ),
                result.validatorCodes()
        );
    }

    @Test
    void groundTruthMismatchIsScientificData()
            throws Exception {

        MeasuredAttemptResult result =
                executeSynthetic(
                        "SUCCESS",
                        "SUCCESS",
                        "APPLICABLE",
                        true,
                        List.of(),
                        "APPLICABLE",
                        false,
                        "APPLICABLE",
                        false,
                        List.of()
                );

        assertEquals(
                "APPLICABLE",
                result.groundTruthApplicability()
        );

        assertFalse(result.groundTruthExactMatch());
    }

    @Test
    void hallucinationIsScientificData()
            throws Exception {

        MeasuredAttemptResult result =
                executeSynthetic(
                        "SUCCESS",
                        "SUCCESS",
                        "APPLICABLE",
                        false,
                        List.of(
                                "PROPOSED_VALUE_NOT_GROUNDED"
                        ),
                        "APPLICABLE",
                        false,
                        "APPLICABLE",
                        true,
                        List.of(
                                "PROPOSED_VALUE_UNSUPPORTED"
                        )
                );

        assertEquals(
                "APPLICABLE",
                result.hallucinationApplicability()
        );

        assertTrue(result.hallucinated());

        assertEquals(
                List.of(
                        "PROPOSED_VALUE_UNSUPPORTED"
                ),
                result.hallucinationReasonCodes()
        );
    }

    @Test
    void measuredRecordsAreExplicitlyMarkedMeasured() {
        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.attempts().getFirst();

        MeasuredAttemptResult result =
                MeasuredTestFixtures.validResult(attempt);

        assertTrue(result.measuredCampaignAttempt());
    }

    @Test
    void aggregatorAcceptsMeasuredObservation() {
        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.attempts().getFirst();

        MeasuredCampaignAggregator aggregator =
                new MeasuredCampaignAggregator();

        aggregator.add(
                MeasuredTestFixtures.validResult(attempt)
        );

        assertEquals(1, aggregator.size());
    }

    @Test
    void aggregatorRejectsUnknownSmokeOrCompatibilityIdentity() {
        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.attempts().getFirst();

        MeasuredAttemptResult valid =
                MeasuredTestFixtures.validResult(attempt);

        MeasuredAttemptResult foreign =
                new MeasuredAttemptResult(
                        "smoke-001-llm-001-undocumented-operation",
                        valid.scenarioId(),
                        valid.repetition(),
                        valid.evidenceFingerprint(),
                        valid.protocolId(),
                        valid.configurationLabel(),
                        valid.promptVersion(),
                        valid.promptSha256(),
                        valid.schemaSha256(),
                        valid.provider(),
                        valid.requestedModel(),
                        valid.returnedModel(),
                        valid.systemFingerprint(),
                        valid.serviceTier(),
                        valid.temperature(),
                        valid.topP(),
                        valid.seed(),
                        valid.maxCompletionTokens(),
                        valid.timeoutMilliseconds(),
                        valid.providerInvocationCount(),
                        valid.retryCount(),
                        valid.httpStatus(),
                        valid.providerCallStatus(),
                        valid.providerDiagnostic(),
                        valid.rawProviderResponse(),
                        valid.assistantContent(),
                        valid.proposalParseStatus(),
                        valid.parsedProposal(),
                        valid.validatorApplicability(),
                        valid.validatorAccepted(),
                        valid.validatorCodes(),
                        valid.groundTruthApplicability(),
                        valid.groundTruthFieldComparison(),
                        valid.groundTruthExactMatch(),
                        valid.hallucinationApplicability(),
                        valid.hallucinated(),
                        valid.hallucinationReasonCodes(),
                        valid.latencyMilliseconds(),
                        valid.inputTokens(),
                        valid.outputTokens(),
                        valid.reasoningTokens(),
                        valid.totalTokens(),
                        valid.finishReason(),
                        valid.timestamp(),
                        true
                );

        MeasuredCampaignAggregator aggregator =
                new MeasuredCampaignAggregator();

        assertThrows(
                IllegalArgumentException.class,
                () -> aggregator.add(foreign)
        );
    }

    @Test
    void duplicateMeasuredObservationIsRejected() {
        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.attempts().getFirst();

        MeasuredCampaignAggregator aggregator =
                new MeasuredCampaignAggregator();

        MeasuredAttemptResult result =
                MeasuredTestFixtures.validResult(attempt);

        aggregator.add(result);

        assertThrows(
                IllegalStateException.class,
                () -> aggregator.add(result)
        );
    }

    private MeasuredAttemptResult executeSynthetic(
            String providerStatus,
            String parseStatus,
            String validatorApplicability,
            Boolean validatorAccepted,
            List<String> validatorCodes,
            String groundTruthApplicability,
            Boolean groundTruthExact,
            String hallucinationApplicability,
            Boolean hallucinated,
            List<String> hallucinationCodes
    ) throws Exception {

        AtomicInteger calls =
                new AtomicInteger();

        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.attempts().getFirst();

        MeasuredResultPaths paths =
                new MeasuredResultPaths(
                        tempDir.resolve(
                                "case-" + System.nanoTime()
                        ).resolve("results/measured")
                );

        MeasuredAttemptRunner runner =
                new MeasuredAttemptRunner(
                        new MeasuredResultExporter(paths),
                        ignored -> {
                            calls.incrementAndGet();

                            return MeasuredTestFixtures.result(
                                    attempt,
                                    providerStatus,
                                    parseStatus,
                                    validatorApplicability,
                                    validatorAccepted,
                                    validatorCodes,
                                    groundTruthApplicability,
                                    groundTruthExact,
                                    hallucinationApplicability,
                                    hallucinated,
                                    hallucinationCodes
                            );
                        }
                );

        MeasuredAttemptResult result =
                runner.executeOne(
                        attempt,
                        MeasuredTestFixtures
                                .authorizedEnvironment(),
                        MeasuredTestFixtures
                                .expectedAuthorization()
                );

        assertEquals(
                1,
                calls.get(),
                "A measured attempt may invoke provider at most once."
        );

        return result;
    }
}