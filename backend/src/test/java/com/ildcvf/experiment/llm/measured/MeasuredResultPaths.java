package com.ildcvf.experiment.llm.measured;

import java.nio.file.Path;
import java.util.Objects;

public final class MeasuredResultPaths {

    public static final Path DEFAULT_MEASURED_ROOT =
            Path.of(
                    "experiments",
                    "e6-llm-reliability",
                    "results",
                    "measured"
            );

    private final Path measuredRoot;

    public MeasuredResultPaths(Path measuredRoot) {
        Objects.requireNonNull(measuredRoot, "measuredRoot");

        Path normalized = measuredRoot
                .toAbsolutePath()
                .normalize();

        String portable =
                normalized.toString().replace('\\', '/');

        if (portable.contains("/results/smoke/")
                || portable.endsWith("/results/smoke")
                || portable.contains("/results/compatibility/")
                || portable.endsWith("/results/compatibility")) {
            throw new IllegalArgumentException(
                    "Measured results cannot use smoke or compatibility paths."
            );
        }

        this.measuredRoot = normalized;
    }

    public Path measuredRoot() {
        return measuredRoot;
    }

    public Path attemptDirectory(
            MeasuredAttempt attempt
    ) {
        Objects.requireNonNull(attempt, "attempt");

        Path resolved =
                measuredRoot.resolve(attempt.attemptId())
                        .normalize();

        if (!resolved.startsWith(measuredRoot)) {
            throw new IllegalArgumentException(
                    "Attempt path escapes measured result root."
            );
        }

        return resolved;
    }

    public Path resultFile(
            MeasuredAttempt attempt
    ) {
        return attemptDirectory(attempt)
                .resolve("result.json");
    }
}