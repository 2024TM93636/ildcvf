package com.ildcvf.experiment.llm.measured;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MeasuredAttemptSchedule {

    private static final List<MeasuredAttempt> ATTEMPTS =
            buildAttempts();

    private MeasuredAttemptSchedule() {
    }

    public static List<MeasuredAttempt> attempts() {
        return ATTEMPTS;
    }

    public static MeasuredAttempt requireAttempt(
            String attemptId
    ) {
        return ATTEMPTS.stream()
                .filter(attempt -> attempt.attemptId().equals(attemptId))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown measured attempt: " + attemptId
                        )
                );
    }

    private static List<MeasuredAttempt> buildAttempts() {
        List<MeasuredAttempt> attempts = new ArrayList<>();

        for (String scenarioId
                : MeasuredProtocolConfiguration.SCENARIO_ORDER) {

            for (int repetition = 1;
                 repetition
                         <= MeasuredProtocolConfiguration
                         .REPETITIONS_PER_SCENARIO;
                 repetition++) {

                attempts.add(
                        MeasuredAttempt.of(
                                scenarioId,
                                repetition
                        )
                );
            }
        }

        if (attempts.size()
                != MeasuredProtocolConfiguration.EXPECTED_ATTEMPT_COUNT) {
            throw new IllegalStateException(
                    "Measured schedule must contain exactly "
                            + MeasuredProtocolConfiguration
                            .EXPECTED_ATTEMPT_COUNT
                            + " attempts"
            );
        }

        Set<String> ids = new HashSet<>();

        for (MeasuredAttempt attempt : attempts) {
            if (!ids.add(attempt.attemptId())) {
                throw new IllegalStateException(
                        "Duplicate measured attempt ID: "
                                + attempt.attemptId()
                );
            }
        }

        return Collections.unmodifiableList(attempts);
    }
}