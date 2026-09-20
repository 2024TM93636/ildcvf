package com.ildcvf.experiment.llm.measured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class MeasuredResultExporter {

    private final MeasuredResultPaths paths;
    private final ObjectMapper objectMapper;

    public MeasuredResultExporter(
            MeasuredResultPaths paths
    ) {
        this.paths =
                Objects.requireNonNull(paths, "paths");

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(
                        SerializationFeature
                                .WRITE_DATES_AS_TIMESTAMPS
                );
    }

    public boolean resultExists(
            MeasuredAttempt attempt
    ) {
        return Files.exists(
                paths.resultFile(attempt)
        );
    }

    public void requireNotRecorded(
            MeasuredAttempt attempt
    ) {
        if (resultExists(attempt)) {
            throw new IllegalStateException(
                    "Measured attempt already recorded: "
                            + attempt.attemptId()
            );
        }
    }

    public Path export(
            MeasuredAttempt attempt,
            MeasuredAttemptResult result
    ) throws IOException {

        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(result, "result");

        if (!attempt.attemptId().equals(result.attemptId())
                || !attempt.scenarioId().equals(result.scenarioId())
                || attempt.repetition() != result.repetition()) {
            throw new IllegalArgumentException(
                    "Result identity does not match measured attempt."
            );
        }

        requireNotRecorded(attempt);

        Path directory =
                paths.attemptDirectory(attempt);

        Files.createDirectories(directory);

        Path resultFile =
                paths.resultFile(attempt);

        byte[] json =
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(result);

        Files.write(
                resultFile,
                json,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );

        return resultFile;
    }
}