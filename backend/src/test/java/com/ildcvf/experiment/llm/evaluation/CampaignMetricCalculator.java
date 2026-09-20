package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.groq.ProposalParseStatus;
import com.ildcvf.experiment.llm.groq.ProviderCallStatus;
import com.ildcvf.llm.validation.ProposalValidationCode;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CampaignMetricCalculator {

    private final DescriptiveMetricsCalculator descriptive =
            new DescriptiveMetricsCalculator();

    public CampaignMetrics calculate(
            List<AttemptEvaluation> attempts
    ) {
        Objects.requireNonNull(
                attempts,
                "attempts must not be null");

        List<AttemptEvaluation> immutable =
                List.copyOf(attempts);

        int scheduled =
                immutable.size();

        long providerSuccess =
                immutable.stream()
                        .filter(this::providerSucceeded)
                        .count();

        List<AttemptEvaluation> parsed =
                immutable.stream()
                        .filter(
                                AttemptEvaluation
                                        ::parsedProposalAvailable)
                        .toList();

        long parsedCount =
                parsed.size();

        long parseFailures =
                immutable.stream()
                        .filter(this::proposalParseFailure)
                        .count();

        long validatorAccepted =
                parsed.stream()
                        .filter(attempt ->
                                Boolean.TRUE.equals(
                                        attempt.validator()
                                                .accepted()))
                        .count();

        long validatorRejected =
                parsedCount - validatorAccepted;

        long exact =
                parsed.stream()
                        .filter(attempt ->
                                attempt.groundTruth()
                                        .comparison()
                                        != null
                                        && attempt
                                        .groundTruth()
                                        .comparison()
                                        .exactMatch())
                        .count();

        long hallucinated =
                parsed.stream()
                        .filter(attempt ->
                                Boolean.TRUE.equals(
                                        attempt.hallucination()
                                                .hallucinated()))
                        .count();

        long matchedFields =
                parsed.stream()
                        .map(
                                AttemptEvaluation
                                        ::groundTruth)
                        .map(
                                GroundTruthEvaluation
                                        ::comparison)
                        .filter(Objects::nonNull)
                        .mapToLong(
                                GroundTruthComparison
                                        ::matchedFieldCount)
                        .sum();

        Double macroFieldAccuracy =
                parsed.isEmpty()
                        ? null
                        : parsed.stream()
                        .map(
                                AttemptEvaluation
                                        ::groundTruth)
                        .map(
                                GroundTruthEvaluation
                                        ::comparison)
                        .mapToDouble(
                                GroundTruthComparison
                                        ::fieldAccuracy)
                        .average()
                        .orElseThrow();

        Double microFieldAccuracy =
                parsed.isEmpty()
                        ? null
                        : matchedFields
                        / (double)
                        (GroundTruthComparison
                                .FROZEN_FIELD_COUNT
                                * parsedCount);

        EnumMap<
                ProposalValidationCode,
                Long> validationCodes =
                new EnumMap<>(
                        ProposalValidationCode.class);

        long validationErrorCount = 0;

        for (AttemptEvaluation attempt
                : parsed) {

            if (!Boolean.FALSE.equals(
                    attempt.validator()
                            .accepted())) {
                continue;
            }

            for (ProposalValidationCode code
                    : attempt.validator().codes()) {

                validationCodes.merge(
                        code,
                        1L,
                        Long::sum);

                validationErrorCount++;
            }
        }

        EnumMap<
                HallucinationReasonCode,
                Long> hallucinationReasons =
                new EnumMap<>(
                        HallucinationReasonCode.class);

        long hallucinationReasonCount = 0;

        for (AttemptEvaluation attempt
                : parsed) {

            if (!Boolean.TRUE.equals(
                    attempt.hallucination()
                            .hallucinated())) {
                continue;
            }

            for (HallucinationReasonCode code
                    : attempt.hallucination()
                    .reasonCodes()) {

                hallucinationReasons.merge(
                        code,
                        1L,
                        Long::sum);

                hallucinationReasonCount++;
            }
        }

        return new CampaignMetrics(
                scheduled,

                RateMetric.of(
                        providerSuccess,
                        scheduled),

                RateMetric.of(
                        parsedCount,
                        providerSuccess),

                RateMetric.of(
                        parsedCount,
                        scheduled),

                RateMetric.of(
                        parseFailures,
                        providerSuccess),

                RateMetric.of(
                        validatorAccepted,
                        parsedCount),

                RateMetric.of(
                        validatorRejected,
                        parsedCount),

                RateMetric.of(
                        exact,
                        parsedCount),

                RateMetric.of(
                        exact,
                        scheduled),

                macroFieldAccuracy,
                microFieldAccuracy,

                RateMetric.of(
                        hallucinated,
                        parsedCount),

                validationCodes,
                validatorRejected,
                validationErrorCount,

                hallucinationReasons,
                hallucinated,
                hallucinationReasonCount,

                descriptive.latency(immutable),
                descriptive.tokens(immutable)
        );
    }

    private boolean providerSucceeded(
            AttemptEvaluation attempt
    ) {
        ProviderCallStatus status =
                attempt.providerCallStatus();

        return status
                == ProviderCallStatus.SUCCESS
                || status
                == ProviderCallStatus.PROPOSAL_PARSE_FAILURE;
    }

    private boolean proposalParseFailure(
            AttemptEvaluation attempt
    ) {
        return attempt.providerCallStatus()
                == ProviderCallStatus.PROPOSAL_PARSE_FAILURE;
    }
}
