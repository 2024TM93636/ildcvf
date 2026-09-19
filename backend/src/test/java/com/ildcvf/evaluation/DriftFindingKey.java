package com.ildcvf.evaluation;

import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftType;

import java.util.Objects;

public record DriftFindingKey(
        DriftType type,
        String fieldPath
) {
    private static final String NULL_FIELD_PATH = "<null>";

    public DriftFindingKey {
        Objects.requireNonNull(type, "type");
        fieldPath = fieldPath == null ? NULL_FIELD_PATH : fieldPath;
    }

    public static DriftFindingKey from(DriftFinding finding) {
        Objects.requireNonNull(finding, "finding");

        return new DriftFindingKey(
                finding.type(),
                finding.fieldPath()
        );
    }
}
