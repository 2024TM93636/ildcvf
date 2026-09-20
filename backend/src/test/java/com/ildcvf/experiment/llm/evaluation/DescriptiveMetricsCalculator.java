package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.groq.ProviderAttemptMetadata;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DescriptiveMetricsCalculator {

    public LatencyMetrics latency(
            List<AttemptEvaluation> attempts
    ) {
        Objects.requireNonNull(
                attempts,
                "attempts must not be null");

        List<Double> values =
                attempts.stream()
                        .map(
                                AttemptEvaluation
                                        ::providerMetadata)
                        .map(
                                ProviderAttemptMetadata
                                        ::latency)
                        .filter(Objects::nonNull)
                        .map(this::milliseconds)
                        .sorted()
                        .toList();

        if (values.isEmpty()) {
            return new LatencyMetrics(
                    0,
                    null,
                    null,
                    null,
                    null
            );
        }

        double mean =
                values.stream()
                        .mapToDouble(
                                Double::doubleValue)
                        .average()
                        .orElseThrow();

        return new LatencyMetrics(
                values.size(),
                mean,
                nearestRank(values, 0.50d),
                nearestRank(values, 0.95d),
                nearestRank(values, 0.99d)
        );
    }

    public TokenMetrics tokens(
            List<AttemptEvaluation> attempts
    ) {
        Objects.requireNonNull(
                attempts,
                "attempts must not be null");

        List<Long> inputs =
                observedTokens(
                        attempts,
                        TokenSelector.INPUT);

        List<Long> outputs =
                observedTokens(
                        attempts,
                        TokenSelector.OUTPUT);

        List<Long> totals =
                observedTokens(
                        attempts,
                        TokenSelector.TOTAL);

        return new TokenMetrics(
                inputs.size(),
                outputs.size(),
                totals.size(),
                sum(inputs),
                sum(outputs),
                sum(totals),
                mean(inputs),
                mean(outputs),
                mean(totals)
        );
    }

    private List<Long> observedTokens(
            List<AttemptEvaluation> attempts,
            TokenSelector selector
    ) {
        List<Long> values =
                new ArrayList<>();

        for (AttemptEvaluation attempt
                : attempts) {

            ProviderAttemptMetadata metadata =
                    attempt.providerMetadata();

            Long value =
                    switch (selector) {
                        case INPUT ->
                                metadata.inputTokens();

                        case OUTPUT ->
                                metadata.outputTokens();

                        case TOTAL ->
                                metadata.totalTokens();
                    };

            if (value != null) {
                values.add(value);
            }
        }

        return List.copyOf(values);
    }

    private long sum(List<Long> values) {
        return values.stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private Double mean(List<Long> values) {
        if (values.isEmpty()) {
            return null;
        }

        return values.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElseThrow();
    }

    private double milliseconds(
            Duration duration
    ) {
        return duration.toNanos()
                / 1_000_000.0d;
    }

    /*
     * Deterministic nearest-rank percentile:
     *
     * rank = ceil(p * N), using one-based indexing.
     * The selected zero-based index is rank - 1.
     */
    private double nearestRank(
            List<Double> sorted,
            double percentile
    ) {
        int rank =
                (int) Math.ceil(
                        percentile * sorted.size());

        int index =
                Math.max(0, rank - 1);

        return sorted.get(index);
    }

    private enum TokenSelector {
        INPUT,
        OUTPUT,
        TOTAL
    }
}
