package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.llm.validation.ProposalValidationCode;

import java.util.Map;
import java.util.Objects;

public record CampaignMetrics(
        int scheduledAttemptCount,
        RateMetric providerSuccessRate,
        RateMetric parseSuccessRate,
        RateMetric endToEndParsedRate,
        RateMetric parseFailureRate,
        RateMetric validatorAcceptanceRate,
        RateMetric validatorRejectionRate,
        RateMetric exactProposalCorrectness,
        RateMetric endToEndExactSuccess,
        Double macroFieldAccuracy,
        Double microFieldAccuracy,
        RateMetric hallucinatedProposalRate,
        Map<ProposalValidationCode, Long>
        validationCodeOccurrences,
        long rejectedProposalCount,
        long totalValidationErrorCount,
        Map<HallucinationReasonCode, Long>
        hallucinationReasonOccurrences,
        long hallucinatedProposalCount,
        long totalHallucinationReasonCount,
        LatencyMetrics latencyMetrics,
        TokenMetrics tokenMetrics
) {

    public CampaignMetrics {
        Objects.requireNonNull(providerSuccessRate);
        Objects.requireNonNull(parseSuccessRate);
        Objects.requireNonNull(endToEndParsedRate);
        Objects.requireNonNull(parseFailureRate);
        Objects.requireNonNull(validatorAcceptanceRate);
        Objects.requireNonNull(validatorRejectionRate);
        Objects.requireNonNull(exactProposalCorrectness);
        Objects.requireNonNull(endToEndExactSuccess);
        Objects.requireNonNull(hallucinatedProposalRate);
        Objects.requireNonNull(validationCodeOccurrences);
        Objects.requireNonNull(hallucinationReasonOccurrences);
        Objects.requireNonNull(latencyMetrics);
        Objects.requireNonNull(tokenMetrics);

        validationCodeOccurrences =
                Map.copyOf(validationCodeOccurrences);

        hallucinationReasonOccurrences =
                Map.copyOf(
                        hallucinationReasonOccurrences);
    }
}
