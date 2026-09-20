package com.ildcvf.experiment.llm.evaluation;

public record AgreementMetric(
        long numerator,
        long denominator,
        Double rate
) {

    public static AgreementMetric of(
            long numerator,
            long denominator
    ) {
        if (numerator < 0
                || denominator < 0
                || numerator > denominator) {
            throw new IllegalArgumentException(
                    "Invalid agreement counts");
        }

        return new AgreementMetric(
                numerator,
                denominator,
                denominator == 0
                        ? null
                        : numerator
                        / (double) denominator
        );
    }

    public boolean applicable() {
        return denominator > 0;
    }
}
