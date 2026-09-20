package com.ildcvf.experiment.llm.evaluation;

public record RateMetric(
        long numerator,
        long denominator,
        Double rate
) {

    public RateMetric {
        if (numerator < 0
                || denominator < 0
                || numerator > denominator) {
            throw new IllegalArgumentException(
                    "Invalid rate numerator/denominator");
        }

        if (denominator == 0) {
            if (rate != null) {
                throw new IllegalArgumentException(
                        "Zero denominator requires null rate");
            }
        } else {
            double expected =
                    numerator
                            / (double) denominator;

            if (rate == null
                    || Double.compare(
                    rate,
                    expected) != 0) {
                throw new IllegalArgumentException(
                        "Rate is inconsistent");
            }
        }
    }

    public static RateMetric of(
            long numerator,
            long denominator
    ) {
        return new RateMetric(
                numerator,
                denominator,
                denominator == 0
                        ? null
                        : numerator
                        / (double) denominator
        );
    }
}
