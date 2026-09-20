package com.ildcvf.experiment.llm.evaluation;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CampaignSchedule {

    public static final int FROZEN_SCENARIO_COUNT = 10;
    public static final int REPETITIONS_PER_SCENARIO = 4;
    public static final int SCHEDULED_ATTEMPT_COUNT = 40;

    public List<AttemptIdentity> create(
            List<LlmReliabilityScenario> scenarios
    ) {
        Objects.requireNonNull(
                scenarios,
                "scenarios must not be null");

        if (scenarios.size()
                != FROZEN_SCENARIO_COUNT) {
            throw new IllegalArgumentException(
                    "Frozen E6 campaign requires exactly "
                            + FROZEN_SCENARIO_COUNT
                            + " scenarios");
        }

        List<AttemptIdentity> schedule =
                new ArrayList<>(
                        SCHEDULED_ATTEMPT_COUNT);

        for (LlmReliabilityScenario scenario
                : scenarios) {

            for (int repetition = 1;
                 repetition
                         <= REPETITIONS_PER_SCENARIO;
                 repetition++) {

                schedule.add(
                        new AttemptIdentity(
                                scenario.scenarioId(),
                                repetition));
            }
        }

        if (schedule.size()
                != SCHEDULED_ATTEMPT_COUNT) {
            throw new IllegalStateException(
                    "Frozen campaign schedule must contain 40 attempts");
        }

        return List.copyOf(schedule);
    }
}
