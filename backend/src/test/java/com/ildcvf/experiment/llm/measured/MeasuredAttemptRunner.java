package com.ildcvf.experiment.llm.measured;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public final class MeasuredAttemptRunner {

    @FunctionalInterface
    public interface ProviderInvoker {
        MeasuredAttemptResult invoke(
                MeasuredAttempt attempt
        ) throws Exception;
    }

    private final MeasuredResultExporter exporter;
    private final ProviderInvoker providerInvoker;

    public MeasuredAttemptRunner(
            MeasuredResultExporter exporter,
            ProviderInvoker providerInvoker
    ) {
        this.exporter =
                Objects.requireNonNull(exporter, "exporter");
        this.providerInvoker =
                Objects.requireNonNull(
                        providerInvoker,
                        "providerInvoker"
                );
    }

    public MeasuredAttemptResult executeOne(
            MeasuredAttempt attempt,
            Map<String, String> environment,
            String expectedControllerAuthorization
    ) throws Exception {

        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(environment, "environment");

        /*
         * Critical ordering:
         *
         * 1. Refuse an already recorded attempt.
         * 2. Require both authorization factors.
         * 3. Invoke provider exactly once.
         * 4. Persist whatever scientific outcome was returned.
         *
         * There is deliberately no retry loop.
         */
        exporter.requireNotRecorded(attempt);

        MeasuredCampaignAuthorization.requireAuthorized(
                environment,
                expectedControllerAuthorization
        );

        MeasuredAttemptResult result =
                providerInvoker.invoke(attempt);

        validateReturnedResult(attempt, result);

        exporter.export(attempt, result);

        return result;
    }

    private static void validateReturnedResult(
            MeasuredAttempt attempt,
            MeasuredAttemptResult result
    ) throws IOException {

        if (result == null) {
            throw new IOException(
                    "Provider invoker returned no measured result."
            );
        }

        if (!attempt.attemptId().equals(result.attemptId())
                || !attempt.scenarioId().equals(result.scenarioId())
                || attempt.repetition() != result.repetition()) {
            throw new IOException(
                    "Provider result identity does not match attempt."
            );
        }

        if (result.providerInvocationCount() != 1) {
            throw new IOException(
                    "Authorized measured result must record exactly one "
                            + "provider invocation."
            );
        }

        if (result.retryCount() != 0) {
            throw new IOException(
                    "Measured attempts cannot record retries."
            );
        }

        if (!result.measuredCampaignAttempt()) {
            throw new IOException(
                    "Measured result must be marked as a measured "
                            + "campaign attempt."
            );
        }
    }
}