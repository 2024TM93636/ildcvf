package com.ildcvf.experiment.llm.evaluation;

import java.util.List;
import java.util.Objects;

public record GroundTruthComparison(
        List<FieldComparison> fields,
        int matchedFieldCount,
        int totalFieldCount,
        double fieldAccuracy,
        boolean exactMatch
) {

    public static final int FROZEN_FIELD_COUNT = 11;

    public GroundTruthComparison {
        Objects.requireNonNull(
                fields,
                "fields must not be null");

        fields = List.copyOf(fields);

        if (fields.size() != FROZEN_FIELD_COUNT) {
            throw new IllegalArgumentException(
                    "Exactly 11 field comparisons are required");
        }

        if (totalFieldCount != FROZEN_FIELD_COUNT) {
            throw new IllegalArgumentException(
                    "totalFieldCount must be 11");
        }

        long actualMatches =
                fields.stream()
                        .filter(FieldComparison::match)
                        .count();

        if (matchedFieldCount != actualMatches) {
            throw new IllegalArgumentException(
                    "matchedFieldCount is inconsistent");
        }

        double expectedAccuracy =
                matchedFieldCount
                        / (double) FROZEN_FIELD_COUNT;

        if (Double.compare(
                fieldAccuracy,
                expectedAccuracy) != 0) {
            throw new IllegalArgumentException(
                    "fieldAccuracy is inconsistent");
        }

        if (exactMatch
                != (matchedFieldCount
                == FROZEN_FIELD_COUNT)) {
            throw new IllegalArgumentException(
                    "exactMatch is inconsistent");
        }
    }
}