package com.ildcvf.experiment.llm.measured.execution;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class E6MeasuredCampaignScriptOfflineTest {

    private Path script() {
        return Path.of(
                "..",
                "scripts",
                "e6-measured.ps1"
        ).toAbsolutePath().normalize();
    }

    @Test
    void campaignScriptExists() {
        assertTrue(
                Files.isRegularFile(script())
        );
    }

    @Test
    void explicitAttemptIdIsMandatoryAcrossOperations()
            throws Exception {

        String source =
                Files.readString(script());

        assertTrue(
                source.contains(
                        "[string]$AttemptId"
                )
        );

        assertTrue(
                source.contains(
                        "Assert-ExplicitScheduledAttempt"
                )
        );
    }

    @Test
    void scriptContainsNoAutomaticAttemptSelection()
            throws Exception {

        String source =
                Files.readString(script())
                        .toLowerCase();

        assertFalse(
                source.contains(
                        "next unexecuted"
                )
        );

        assertFalse(
                source.contains(
                        "next attempt"
                )
        );

        assertFalse(
                source.contains(
                        "first missing"
                )
        );
    }

    @Test
    void scriptContainsNoRetryMechanism()
            throws Exception {

        String source =
                Files.readString(script())
                        .toLowerCase();

        assertFalse(source.contains("start-sleep"));
        assertFalse(source.contains("retrycount++"));
        assertFalse(source.contains("while ($true)"));
    }

    @Test
    void existingResultGuardIsPresent()
            throws Exception {

        String source =
                Files.readString(script());

        assertTrue(
                source.contains(
                        "Assert-NotRecorded"
                )
        );

        assertTrue(
                source.contains(
                        "Test-Path -LiteralPath $Result"
                )
        );

        assertTrue(
                source.contains(
                        "No rerun/overwrite is permitted"
                )
        );
    }

    @Test
    void executionUsesGeneralizedLauncher()
            throws Exception {

        String source =
                Files.readString(script());

        assertTrue(
                source.contains(
                        "GroqMeasuredSingleAttemptLauncher"
                )
        );

        assertTrue(
                source.contains(
                        "'$AttemptId'"
                )
        );
    }

    @Test
    void cleanupUsesFinally()
            throws Exception {

        String source =
                Files.readString(script());

        assertTrue(source.contains("finally"));
        assertTrue(
                source.contains(
                        "Remove-Item"
                )
        );
        assertTrue(
                source.contains(
                        "Env:GROQ_API_KEY"
                )
        );
        assertTrue(
                source.contains(
                        "Env:ILDCVF_E6_MEASURED_AUTHORIZATION"
                )
        );
    }

    @Test
    void scriptContainsNoRealCredentialLiteral()
            throws Exception {

        String source =
                Files.readString(script());

        assertFalse(
                source.matches(
                        "(?s).*gsk_[A-Za-z0-9_-]{12,}.*"
                )
        );

        assertFalse(
                source.contains(
                        "E6-MEASURED-OBS1-20260920"
                )
        );

        assertFalse(
                source.contains(
                        "E6-MEASURED-OBS2-20260920"
                )
        );
    }
}