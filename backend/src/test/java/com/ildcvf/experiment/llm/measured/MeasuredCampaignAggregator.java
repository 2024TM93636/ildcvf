package com.ildcvf.experiment.llm.measured;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MeasuredCampaignAggregator {

    private final List<MeasuredAttemptResult> results =
            new ArrayList<>();

    private final Set<String> attemptIds =
            new HashSet<>();

    public void add(
            MeasuredAttemptResult result
    ) {
        Objects.requireNonNull(result, "result");

        if (!result.measuredCampaignAttempt()) {
            throw new IllegalArgumentException(
                    "Smoke/compatibility observations cannot be "
                            + "imported into measured aggregation."
            );
        }

        if (!MeasuredProtocolConfiguration.PROTOCOL_ID.equals(
                result.protocolId()
        )) {
            throw new IllegalArgumentException(
                    "Unexpected protocol ID."
            );
        }

        if (!MeasuredProtocolConfiguration.CONFIGURATION_LABEL.equals(
                result.configurationLabel()
        )) {
            throw new IllegalArgumentException(
                    "Unexpected configuration label."
            );
        }

        MeasuredAttempt scheduled =
                MeasuredAttemptSchedule.requireAttempt(
                        result.attemptId()
                );

        if (!scheduled.scenarioId().equals(result.scenarioId())
                || scheduled.repetition() != result.repetition()) {
            throw new IllegalArgumentException(
                    "Result identity does not match frozen schedule."
            );
        }

        if (!attemptIds.add(result.attemptId())) {
            throw new IllegalStateException(
                    "Duplicate measured attempt: "
                            + result.attemptId()
            );
        }

        results.add(result);
    }

    public List<MeasuredAttemptResult> results() {
        return Collections.unmodifiableList(results);
    }

    public int size() {
        return results.size();
    }
}