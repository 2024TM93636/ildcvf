package com.ildcvf.experiment.llm.evaluation;

import java.util.Objects;

public record FieldComparison(
        ScoredProposalField field,
        String expected,
        String actual,
        boolean match
) {

    public FieldComparison {
        Objects.requireNonNull(
                field,
                "field must not be null");

        if (match != Objects.equals(expected, actual)) {
            throw new IllegalArgumentException(
                    "match must equal null-safe exact equality");
        }
    }

    public static FieldComparison compare(
            ScoredProposalField field,
            Object expected,
            Object actual
    ) {
        String expectedValue =
                expected == null
                        ? null
                        : expected.toString();

        String actualValue =
                actual == null
                        ? null
                        : actual.toString();

        return new FieldComparison(
                field,
                expectedValue,
                actualValue,
                Objects.equals(expectedValue, actualValue)
        );
    }
}