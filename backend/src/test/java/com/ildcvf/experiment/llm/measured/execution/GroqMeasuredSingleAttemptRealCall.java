package com.ildcvf.experiment.llm.measured.execution;

import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import com.ildcvf.experiment.llm.evaluation.AttemptEvaluator;
import com.ildcvf.experiment.llm.groq.ContractChangeProposalParser;
import com.ildcvf.experiment.llm.groq.GroqApiKeyResolver;
import com.ildcvf.experiment.llm.groq.GroqConfiguration;
import com.ildcvf.experiment.llm.groq.GroqExperimentClient;
import com.ildcvf.experiment.llm.groq.GroqProviderResponseParser;
import com.ildcvf.experiment.llm.groq.GroqRequestBuilder;
import com.ildcvf.experiment.llm.groq.JdkGroqTransport;
import com.ildcvf.experiment.llm.measured.MeasuredAttempt;
import com.ildcvf.experiment.llm.measured.MeasuredAttemptResult;
import com.ildcvf.experiment.llm.measured.MeasuredAttemptRunner;
import com.ildcvf.experiment.llm.measured.MeasuredAttemptSchedule;
import com.ildcvf.experiment.llm.measured.MeasuredResultExporter;
import com.ildcvf.experiment.llm.measured.MeasuredResultPaths;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class GroqMeasuredSingleAttemptRealCall {

    private GroqMeasuredSingleAttemptRealCall() {
    }

    public static MeasuredAttemptResult
    executeAuthorizedMeasuredAttemptExactlyOnce(
            Path repositoryRoot,
            String expectedControllerAuthorization,
            String attemptId
    ) throws Exception {

        Objects.requireNonNull(
                repositoryRoot,
                "repositoryRoot must not be null"
        );

        if (expectedControllerAuthorization == null
                || expectedControllerAuthorization.isBlank()) {
            throw new SecurityException(
                    "Expected controller authorization "
                            + "must be supplied externally."
            );
        }

        if (attemptId == null || attemptId.isBlank()) {
            throw new IllegalArgumentException(
                    "attemptId must not be blank"
            );
        }

        MeasuredAttempt attempt =
                MeasuredAttemptSchedule.requireAttempt(
                        attemptId
                );

        Path measuredRoot =
                repositoryRoot
                        .toAbsolutePath()
                        .normalize()
                        .resolve(
                                Path.of(
                                        "experiments",
                                        "e6-llm-reliability",
                                        "results",
                                        "measured"
                                )
                        );

        MeasuredResultPaths resultPaths =
                new MeasuredResultPaths(
                        measuredRoot
                );

        MeasuredResultExporter exporter =
                new MeasuredResultExporter(
                        resultPaths
                );

        /*
         * Attempt-specific existing-result guard.
         *
         * This remains before construction of the real
         * transport/client. An already recorded selected
         * attempt therefore cannot invoke the provider.
         */
        exporter.requireNotRecorded(attempt);

        GroqConfiguration configuration =
                MeasuredGroqAttemptExecutor
                        .frozenMeasuredGroqConfiguration();

        GroqRequestBuilder requestBuilder =
                new GroqRequestBuilder();

        MeasuredRecordingGroqTransport transport =
                new MeasuredRecordingGroqTransport(
                        new JdkGroqTransport()
                );

        GroqExperimentClient client =
                new GroqExperimentClient(
                        configuration,
                        new GroqApiKeyResolver(),
                        requestBuilder,
                        transport,
                        new GroqProviderResponseParser(),
                        new ContractChangeProposalParser()
                );

        MeasuredGroqAttemptExecutor executor =
                new MeasuredGroqAttemptExecutor(
                        new LlmScenarioCatalog(),
                        new AttemptEvaluator(),
                        requestBuilder,
                        configuration,
                        client::generate,
                        transport
                );

        MeasuredAttemptRunner runner =
                new MeasuredAttemptRunner(
                        exporter,
                        executor::executeExactlyOnce
                );

        Map<String, String> environment =
                System.getenv();

        return runner.executeOne(
                attempt,
                environment,
                expectedControllerAuthorization
        );
    }
}