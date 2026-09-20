package com.ildcvf.experiment.llm.measured.execution;

import java.nio.file.Path;

public final class GroqMeasuredSingleAttemptLauncher {

    private GroqMeasuredSingleAttemptLauncher() {
    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 3) {
            throw new IllegalArgumentException(
                    "Exactly three arguments are required: "
                            + "<repository-root> "
                            + "<expected-controller-authorization> "
                            + "<attempt-id>"
            );
        }

        String repositoryRootArgument = args[0];
        String expectedControllerAuthorization = args[1];
        String attemptId = args[2];

        if (repositoryRootArgument == null
                || repositoryRootArgument.isBlank()) {
            throw new IllegalArgumentException(
                    "Repository root must not be blank."
            );
        }

        if (expectedControllerAuthorization == null
                || expectedControllerAuthorization.isBlank()) {
            throw new IllegalArgumentException(
                    "Expected controller authorization "
                            + "must not be blank."
            );
        }

        if (attemptId == null || attemptId.isBlank()) {
            throw new IllegalArgumentException(
                    "Measured attempt ID must not be blank."
            );
        }

        Path repositoryRoot =
                Path.of(repositoryRootArgument)
                        .toAbsolutePath()
                        .normalize();

        GroqMeasuredSingleAttemptRealCall
                .executeAuthorizedMeasuredAttemptExactlyOnce(
                        repositoryRoot,
                        expectedControllerAuthorization,
                        attemptId
                );

        System.out.println(
                "Measured single-attempt execution completed."
        );
    }
}