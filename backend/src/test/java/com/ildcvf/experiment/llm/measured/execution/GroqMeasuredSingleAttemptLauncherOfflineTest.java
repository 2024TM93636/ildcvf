package com.ildcvf.experiment.llm.measured.execution;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroqMeasuredSingleAttemptLauncherOfflineTest {

    private static final String LAUNCHER_SOURCE_RELATIVE_PATH =
            "src/test/java/com/ildcvf/experiment/llm/measured/"
                    + "execution/"
                    + "GroqMeasuredSingleAttemptLauncher.java";

    @Test
    void launcherHasExpectedClassName() {
        assertEquals(
                "GroqMeasuredSingleAttemptLauncher",
                GroqMeasuredSingleAttemptLauncher.class
                        .getSimpleName()
        );
    }

    @Test
    void launcherIsNotNamedAsJUnitTest() {
        assertFalse(
                GroqMeasuredSingleAttemptLauncher.class
                        .getSimpleName()
                        .endsWith("Test")
        );
    }

    @Test
    void launcherExposesStandardPublicStaticMain()
            throws Exception {

        Method main =
                GroqMeasuredSingleAttemptLauncher.class
                        .getDeclaredMethod(
                                "main",
                                String[].class
                        );

        assertTrue(
                Modifier.isPublic(
                        main.getModifiers()
                )
        );

        assertTrue(
                Modifier.isStatic(
                        main.getModifiers()
                )
        );

        assertEquals(
                void.class,
                main.getReturnType()
        );
    }

    @Test
    void zeroArgumentsAreRejectedBeforeRealExecution() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GroqMeasuredSingleAttemptLauncher.main(
                                new String[0]
                        )
        );
    }

    @Test
    void oneArgumentIsRejectedBeforeRealExecution() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GroqMeasuredSingleAttemptLauncher.main(
                                new String[]{
                                        "."
                                }
                        )
        );
    }

    @Test
    void twoArgumentsAreRejectedBeforeRealExecution() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GroqMeasuredSingleAttemptLauncher.main(
                                new String[]{
                                        ".",
                                        "synthetic-authorization"
                                }
                        )
        );
    }

    @Test
    void moreThanThreeArgumentsAreRejectedBeforeRealExecution() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GroqMeasuredSingleAttemptLauncher.main(
                                new String[]{
                                        ".",
                                        "synthetic-authorization",
                                        "synthetic-attempt",
                                        "unexpected-fourth-argument"
                                }
                        )
        );
    }

    @Test
    void blankRepositoryRootIsRejectedBeforeRealExecution() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GroqMeasuredSingleAttemptLauncher.main(
                                new String[]{
                                        "   ",
                                        "synthetic-authorization",
                                        "synthetic-attempt"
                                }
                        )
        );
    }

    @Test
    void blankExpectedAuthorizationIsRejectedBeforeRealExecution() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GroqMeasuredSingleAttemptLauncher.main(
                                new String[]{
                                        ".",
                                        "   ",
                                        "synthetic-attempt"
                                }
                        )
        );
    }

    @Test
    void blankAttemptIdIsRejectedBeforeRealExecution() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GroqMeasuredSingleAttemptLauncher.main(
                                new String[]{
                                        ".",
                                        "synthetic-authorization",
                                        "   "
                                }
                        )
        );
    }

    @Test
    void sourceContainsExactlyOneRealCallSite()
            throws Exception {

        String source = launcherSource();

        Matcher matcher =
                Pattern.compile(
                        "\\.executeAuthorizedMeasuredAttemptExactlyOnce\\s*\\("
                ).matcher(source);

        int count = 0;

        while (matcher.find()) {
            count++;
        }

        assertEquals(1, count);
    }

    @Test
    void sourceContainsNoProviderConstruction()
            throws Exception {

        String source = launcherSource();

        assertFalse(source.contains("GroqExperimentClient"));
        assertFalse(source.contains("JdkGroqTransport"));
        assertFalse(source.contains("MeasuredRecordingGroqTransport"));
        assertFalse(source.contains("GroqRequestBuilder"));
    }

    @Test
    void sourceContainsNoCredentialEnvironmentAccess()
            throws Exception {

        String source = launcherSource();

        assertFalse(source.contains("GROQ_API_KEY"));
        assertFalse(
                source.contains(
                        "ILDCVF_E6_MEASURED_AUTHORIZATION"
                )
        );
        assertFalse(source.contains("System.getenv"));
    }

    @Test
    void sourceContainsNoRetryOrScheduleIteration()
            throws Exception {

        String source = launcherSource();

        assertFalse(source.contains("MeasuredAttemptSchedule"));
        assertFalse(source.contains("while ("));
        assertFalse(source.contains("for ("));
        assertFalse(source.contains("retry"));
        assertFalse(source.contains("Retry"));
    }

    @Test
    void sourceContainsNoEmbeddedMeasuredAuthorizationLiteral()
            throws Exception {

        String source = launcherSource();

        assertFalse(
                Pattern.compile(
                        "E6-MEASURED-OBS\\d"
                ).matcher(source).find()
        );
    }

    @Test
    void realCallMethodAcceptsExplicitAttemptId()
            throws Exception {

        Method method =
                GroqMeasuredSingleAttemptRealCall.class
                        .getDeclaredMethod(
                                "executeAuthorizedMeasuredAttemptExactlyOnce",
                                Path.class,
                                String.class,
                                String.class
                        );

        assertNotNull(method);
        assertTrue(
                Modifier.isPublic(
                        method.getModifiers()
                )
        );
        assertTrue(
                Modifier.isStatic(
                        method.getModifiers()
                )
        );
    }

    private String launcherSource()
            throws Exception {

        Path source =
                Path.of(LAUNCHER_SOURCE_RELATIVE_PATH)
                        .toAbsolutePath()
                        .normalize();

        assertTrue(
                Files.isRegularFile(source),
                "Launcher source must exist: " + source
        );

        return Files.readString(source);
    }
}