package com.ildcvf.experiment.llm.measured;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasuredProtocolOfflineTest {

    @Test
    void protocolIdentityIsFrozen() {
        assertEquals(
                "e6-measured-v1",
                MeasuredProtocolConfiguration.PROTOCOL_ID
        );

        assertEquals(
                "groq-gpt-oss-20b-token2048-v1",
                MeasuredProtocolConfiguration.CONFIGURATION_LABEL
        );
    }

    @Test
    void measuredConfigurationIsFrozen() {
        assertEquals(
                2048,
                MeasuredProtocolConfiguration.MAX_COMPLETION_TOKENS
        );

        assertEquals(
                0,
                MeasuredProtocolConfiguration.AUTOMATIC_RETRY_COUNT
        );

        assertEquals(
                "d9ee37eddddff37d75ecdeb933639c356054966f70e2e76ba5694efc7c655947",
                MeasuredProtocolConfiguration.PROMPT_SHA256
        );

        assertEquals(
                "7247e79e1cb5be50e7baeb28ac185caeab68fbc8336736f9c8c212d091768ea4",
                MeasuredProtocolConfiguration.SCHEMA_SHA256
        );
    }

    @Test
    void campaignDimensionsAreFrozen() {
        assertEquals(
                10,
                MeasuredProtocolConfiguration.SCENARIO_COUNT
        );

        assertEquals(
                4,
                MeasuredProtocolConfiguration.REPETITIONS_PER_SCENARIO
        );

        assertEquals(
                40,
                MeasuredProtocolConfiguration.EXPECTED_ATTEMPT_COUNT
        );

        assertEquals(
                10,
                MeasuredProtocolConfiguration.SCENARIO_ORDER.size()
        );
    }

    @Test
    void scheduleContainsExactlyFortyUniqueAttempts() {
        List<MeasuredAttempt> attempts =
                MeasuredAttemptSchedule.attempts();

        assertEquals(40, attempts.size());

        assertEquals(
                40,
                new HashSet<>(
                        attempts.stream()
                                .map(MeasuredAttempt::attemptId)
                                .toList()
                ).size()
        );
    }

    @Test
    void scheduleUsesScenarioMajorRepetitionMinorOrder() {
        List<MeasuredAttempt> attempts =
                MeasuredAttemptSchedule.attempts();

        int index = 0;

        for (String scenarioId
                : MeasuredProtocolConfiguration.SCENARIO_ORDER) {

            for (int repetition = 1;
                 repetition <= 4;
                 repetition++) {

                MeasuredAttempt attempt =
                        attempts.get(index++);

                assertEquals(
                        scenarioId + "-r" + repetition,
                        attempt.attemptId()
                );

                assertEquals(
                        scenarioId,
                        attempt.scenarioId()
                );

                assertEquals(
                        repetition,
                        attempt.repetition()
                );
            }
        }
    }

    @Test
    void scoredFieldsAreExactlyFrozenEleven() {
        assertEquals(
                List.of(
                        "sourceTrafficEventId",
                        "sourceFindingId",
                        "driftType",
                        "changeKind",
                        "method",
                        "runtimePath",
                        "matchedOpenApiPath",
                        "targetFieldPath",
                        "expected",
                        "observed",
                        "proposedValue"
                ),
                MeasuredProtocolConfiguration.SCORED_FIELDS
        );

        assertEquals(
                11,
                MeasuredProtocolConfiguration.SCORED_FIELDS.size()
        );

        assertFalse(
                MeasuredProtocolConfiguration.SCORED_FIELDS
                        .contains("proposalId")
        );

        assertFalse(
                MeasuredProtocolConfiguration.SCORED_FIELDS
                        .contains("rationale")
        );
    }

    @Test
    void everyScheduledAttemptBelongsToFrozenScenarioOrder() {
        assertTrue(
                MeasuredAttemptSchedule.attempts()
                        .stream()
                        .allMatch(attempt ->
                                MeasuredProtocolConfiguration
                                        .SCENARIO_ORDER
                                        .contains(
                                                attempt.scenarioId()
                                        )
                        )
        );
    }
}