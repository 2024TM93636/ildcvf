package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.llm.validation.ProposalValidationCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CampaignMetricCalculatorTest {

    @Test
    void syntheticCampaignProducesExactFrozenMetricCounts() {
        List<AttemptEvaluation> attempts =
                new SyntheticCampaignFixture()
                        .evaluations();

        CampaignMetrics metrics =
                new CampaignMetricCalculator()
                        .calculate(attempts);

        assertEquals(
                40,
                metrics.scheduledAttemptCount());

        assertRate(
                metrics.providerSuccessRate(),
                36,
                40);

        assertRate(
                metrics.parseSuccessRate(),
                32,
                36);

        assertRate(
                metrics.endToEndParsedRate(),
                32,
                40);

        assertRate(
                metrics.parseFailureRate(),
                4,
                36);

        assertRate(
                metrics.validatorAcceptanceRate(),
                24,
                32);

        assertRate(
                metrics.validatorRejectionRate(),
                8,
                32);

        assertRate(
                metrics.exactProposalCorrectness(),
                28,
                32);

        assertRate(
                metrics.endToEndExactSuccess(),
                28,
                40);

        assertRate(
                metrics.hallucinatedProposalRate(),
                4,
                32);

        double expectedFieldAccuracy =
                348.0d / 352.0d;

        assertEquals(
                expectedFieldAccuracy,
                metrics.macroFieldAccuracy());

        assertEquals(
                expectedFieldAccuracy,
                metrics.microFieldAccuracy());

        assertEquals(
                8,
                metrics.rejectedProposalCount());

        assertEquals(
                8,
                metrics.totalValidationErrorCount());

        assertEquals(
                4L,
                metrics.validationCodeOccurrences()
                        .get(
                                ProposalValidationCode
                                        .MISSING_REQUIRED_VALUE));

        assertEquals(
                4L,
                metrics.validationCodeOccurrences()
                        .get(
                                ProposalValidationCode
                                        .METHOD_MISMATCH));

        assertEquals(
                4,
                metrics.hallucinatedProposalCount());

        assertEquals(
                4,
                metrics.totalHallucinationReasonCount());

        assertEquals(
                4L,
                metrics.hallucinationReasonOccurrences()
                        .get(
                                HallucinationReasonCode
                                        .METHOD_UNSUPPORTED));
    }

    @Test
    void latencyAndTokenMetricsUseOnlyObservedMetadata() {
        CampaignMetrics metrics =
                new CampaignMetricCalculator()
                        .calculate(
                                new SyntheticCampaignFixture()
                                        .evaluations());

        LatencyMetrics latency =
                metrics.latencyMetrics();

        assertEquals(
                40,
                latency.observedAttemptCount());

        assertEquals(
                20.5d,
                latency.meanMilliseconds());

        assertEquals(
                20.0d,
                latency.p50Milliseconds());

        assertEquals(
                38.0d,
                latency.p95Milliseconds());

        assertEquals(
                40.0d,
                latency.p99Milliseconds());

        TokenMetrics tokens =
                metrics.tokenMetrics();

        assertEquals(
                36,
                tokens.inputObservedAttemptCount());

        assertEquals(
                36,
                tokens.outputObservedAttemptCount());

        assertEquals(
                36,
                tokens.totalObservedAttemptCount());

        assertEquals(
                4266,
                tokens.sumInputTokens());

        assertEquals(
                1386,
                tokens.sumOutputTokens());

        assertEquals(
                5652,
                tokens.sumTotalTokens());

        assertEquals(
                118.5d,
                tokens.meanInputTokens());

        assertEquals(
                38.5d,
                tokens.meanOutputTokens());

        assertEquals(
                157.0d,
                tokens.meanTotalTokens());
    }

    private void assertRate(
            RateMetric metric,
            long numerator,
            long denominator
    ) {
        assertEquals(
                numerator,
                metric.numerator());

        assertEquals(
                denominator,
                metric.denominator());

        assertEquals(
                numerator / (double) denominator,
                metric.rate());
    }
}
