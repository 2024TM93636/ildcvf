package com.ildcvf.experiment.llm.measured;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasuredSafetyOfflineTest {

    @TempDir
    Path tempDir;

    @Test
    void measuredPathIsIsolated() {
        MeasuredResultPaths paths =
                new MeasuredResultPaths(
                        tempDir.resolve("results/measured")
                );

        assertTrue(
                paths.measuredRoot()
                        .toString()
                        .replace('\\', '/')
                        .endsWith("results/measured")
        );
    }

    @Test
    void smokePathIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeasuredResultPaths(
                        tempDir.resolve("results/smoke")
                )
        );
    }

    @Test
    void compatibilityPathIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeasuredResultPaths(
                        tempDir.resolve(
                                "results/compatibility"
                        )
                )
        );
    }

    @Test
    void missingAuthorizationPreventsProviderInvocation()
            throws Exception {

        AtomicInteger calls =
                new AtomicInteger();

        MeasuredAttemptRunner runner =
                runner(calls);

        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.attempts().getFirst();

        Map<String, String> environment =
                Map.of(
                        MeasuredCampaignAuthorization
                                .API_KEY_ENVIRONMENT_VARIABLE,
                        "synthetic-key"
                );

        assertThrows(
                SecurityException.class,
                () -> runner.executeOne(
                        attempt,
                        environment,
                        MeasuredTestFixtures
                                .expectedAuthorization()
                )
        );

        assertEquals(0, calls.get());
    }

    @Test
    void missingApiKeyPreventsProviderInvocation()
            throws Exception {

        AtomicInteger calls =
                new AtomicInteger();

        MeasuredAttemptRunner runner =
                runner(calls);

        Map<String, String> environment =
                Map.of(
                        MeasuredCampaignAuthorization
                                .AUTHORIZATION_ENVIRONMENT_VARIABLE,
                        MeasuredTestFixtures
                                .expectedAuthorization()
                );

        assertThrows(
                SecurityException.class,
                () -> runner.executeOne(
                        MeasuredAttemptSchedule
                                .attempts()
                                .getFirst(),
                        environment,
                        MeasuredTestFixtures
                                .expectedAuthorization()
                )
        );

        assertEquals(0, calls.get());
    }

    @Test
    void incorrectAuthorizationPreventsProviderInvocation()
            throws Exception {

        AtomicInteger calls =
                new AtomicInteger();

        MeasuredAttemptRunner runner =
                runner(calls);

        Map<String, String> environment =
                new HashMap<>(
                        MeasuredTestFixtures
                                .authorizedEnvironment()
                );

        environment.put(
                MeasuredCampaignAuthorization
                        .AUTHORIZATION_ENVIRONMENT_VARIABLE,
                "wrong-authorization"
        );

        assertThrows(
                SecurityException.class,
                () -> runner.executeOne(
                        MeasuredAttemptSchedule
                                .attempts()
                                .getFirst(),
                        environment,
                        MeasuredTestFixtures
                                .expectedAuthorization()
                )
        );

        assertEquals(0, calls.get());
    }

    @Test
    void authorizedAttemptInvokesProviderExactlyOnce()
            throws Exception {

        AtomicInteger calls =
                new AtomicInteger();

        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.attempts().getFirst();

        MeasuredAttemptRunner runner =
                runner(calls);

        runner.executeOne(
                attempt,
                MeasuredTestFixtures.authorizedEnvironment(),
                MeasuredTestFixtures.expectedAuthorization()
        );

        assertEquals(1, calls.get());
    }

    @Test
    void completedAttemptCannotBeOverwrittenOrReinvoked()
            throws Exception {

        AtomicInteger calls =
                new AtomicInteger();

        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.attempts().getFirst();

        MeasuredAttemptRunner runner =
                runner(calls);

        runner.executeOne(
                attempt,
                MeasuredTestFixtures.authorizedEnvironment(),
                MeasuredTestFixtures.expectedAuthorization()
        );

        assertEquals(1, calls.get());

        assertThrows(
                IllegalStateException.class,
                () -> runner.executeOne(
                        attempt,
                        MeasuredTestFixtures
                                .authorizedEnvironment(),
                        MeasuredTestFixtures
                                .expectedAuthorization()
                )
        );

        assertEquals(
                1,
                calls.get(),
                "Existing result must be detected before provider invocation."
        );
    }

    @Test
    void resultSerializationDoesNotContainSecretOrAuthorization()
            throws Exception {

        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.attempts().getFirst();

        MeasuredResultPaths paths =
                new MeasuredResultPaths(
                        tempDir.resolve("results/measured")
                );

        MeasuredResultExporter exporter =
                new MeasuredResultExporter(paths);

        exporter.export(
                attempt,
                MeasuredTestFixtures.validResult(attempt)
        );

        String json =
                Files.readString(
                        paths.resultFile(attempt)
                );

        assertFalse(json.contains("GROQ_API_KEY"));
        assertFalse(json.contains("Authorization"));
        assertFalse(json.contains("Bearer "));
        assertFalse(
                json.contains("synthetic-test-api-key")
        );
        assertFalse(
                json.contains(
                        MeasuredTestFixtures
                                .expectedAuthorization()
                )
        );
    }

    @Test
    void defaultConstructionPerformsNoProviderCall() {
        AtomicInteger calls =
                new AtomicInteger();

        new MeasuredAttemptRunner(
                new MeasuredResultExporter(
                        new MeasuredResultPaths(
                                tempDir.resolve(
                                        "results/measured"
                                )
                        )
                ),
                attempt -> {
                    calls.incrementAndGet();
                    return MeasuredTestFixtures
                            .validResult(attempt);
                }
        );

        assertEquals(
                0,
                calls.get(),
                "Constructing offline test support must never call provider."
        );
    }

    private MeasuredAttemptRunner runner(
            AtomicInteger calls
    ) {
        MeasuredResultPaths paths =
                new MeasuredResultPaths(
                        tempDir.resolve("results/measured")
                );

        MeasuredResultExporter exporter =
                new MeasuredResultExporter(paths);

        return new MeasuredAttemptRunner(
                exporter,
                attempt -> {
                    calls.incrementAndGet();
                    return MeasuredTestFixtures
                            .validResult(attempt);
                }
        );
    }
}