package com.ildcvf.experiment.llm.measured.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import com.ildcvf.experiment.llm.evaluation.AttemptEvaluator;
import com.ildcvf.experiment.llm.groq.*;
import com.ildcvf.experiment.llm.measured.MeasuredAttempt;
import com.ildcvf.experiment.llm.measured.MeasuredAttemptResult;
import com.ildcvf.experiment.llm.measured.MeasuredAttemptRunner;
import com.ildcvf.experiment.llm.measured.MeasuredAttemptSchedule;
import com.ildcvf.experiment.llm.measured.MeasuredProtocolConfiguration;
import com.ildcvf.experiment.llm.measured.MeasuredResultExporter;
import com.ildcvf.experiment.llm.measured.MeasuredResultPaths;
import com.ildcvf.llm.evidence.EvidenceFingerprint;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasuredGroqAttemptExecutorOfflineTest {

    private static final String SYNTHETIC_KEY =
            "synthetic-offline-key";

    private static final String SYNTHETIC_AUTH =
            "synthetic-offline-controller-authorization";

    private static final String FIRST_ATTEMPT_ID =
            "llm-001-undocumented-operation-r1";

    private static final String SECOND_REPETITION_ATTEMPT_ID =
            "llm-001-undocumented-operation-r2";

    private static final String DIFFERENT_SCENARIO_ATTEMPT_ID =
            "llm-002-request-add-field-r1";

    @TempDir
    Path tempDir;

    @Test
    void firstAttemptIdentityResolvesExactly() {
        MeasuredAttempt attempt =
                attempt(FIRST_ATTEMPT_ID);

        assertEquals(
                FIRST_ATTEMPT_ID,
                attempt.attemptId()
        );

        assertEquals(
                "llm-001-undocumented-operation",
                attempt.scenarioId()
        );

        assertEquals(
                1,
                attempt.repetition()
        );
    }

    @Test
    void secondRepetitionIdentityResolvesExactly() {
        MeasuredAttempt attempt =
                attempt(SECOND_REPETITION_ATTEMPT_ID);

        assertEquals(
                SECOND_REPETITION_ATTEMPT_ID,
                attempt.attemptId()
        );

        assertEquals(
                "llm-001-undocumented-operation",
                attempt.scenarioId()
        );

        assertEquals(
                2,
                attempt.repetition()
        );
    }

    @Test
    void differentScenarioIdentityResolvesExactly() {
        MeasuredAttempt attempt =
                attempt(DIFFERENT_SCENARIO_ATTEMPT_ID);

        assertEquals(
                DIFFERENT_SCENARIO_ATTEMPT_ID,
                attempt.attemptId()
        );

        assertEquals(
                "llm-002-request-add-field",
                attempt.scenarioId()
        );

        assertEquals(
                1,
                attempt.repetition()
        );
    }

    @Test
    void selectedScenarioFingerprintIsRecomputedAndFrozen() {
        LlmReliabilityScenario scenario =
                scenario(
                        "llm-001-undocumented-operation"
                );

        assertEquals(
                scenario.evidenceFingerprint(),
                EvidenceFingerprint.sha256(
                        scenario.evidence()
                )
        );

        assertEquals(
                "evaluation-004",
                scenario.evidence()
                        .trafficEventId()
        );

        assertEquals(
                "evaluation-004:UNDOCUMENTED_OPERATION:1",
                scenario.evidence()
                        .findingId()
        );
    }

    @Test
    void differentScenarioHasIndependentFrozenFingerprint() {
        LlmReliabilityScenario first =
                scenario(
                        "llm-001-undocumented-operation"
                );

        LlmReliabilityScenario second =
                scenario(
                        "llm-002-request-add-field"
                );

        assertEquals(
                second.evidenceFingerprint(),
                EvidenceFingerprint.sha256(
                        second.evidence()
                )
        );

        assertNotEquals(
                first.evidenceFingerprint(),
                second.evidenceFingerprint()
        );
    }

    @Test
    void executorResolvesScenarioFromSelectedAttempt() {
        Harness harness =
                successfulHarness(
                        DIFFERENT_SCENARIO_ATTEMPT_ID
                );

        LlmReliabilityScenario resolved =
                harness.executor()
                        .resolveScenario(
                                attempt(
                                        DIFFERENT_SCENARIO_ATTEMPT_ID
                                )
                        );

        assertEquals(
                "llm-002-request-add-field",
                resolved.scenarioId()
        );

        assertEquals(
                resolved.evidenceFingerprint(),
                EvidenceFingerprint.sha256(
                        resolved.evidence()
                )
        );
    }

    @Test
    void frozenRequestUsesMeasuredConfiguration()
            throws Exception {

        Harness harness =
                successfulHarness(
                        FIRST_ATTEMPT_ID
                );

        String request =
                harness.executor()
                        .buildFrozenRequest(
                                attempt(FIRST_ATTEMPT_ID)
                        );

        JsonNode root =
                new ObjectMapper()
                        .readTree(request);

        assertEquals(
                "openai/gpt-oss-20b",
                root.get("model").asText()
        );

        assertEquals(
                0.0d,
                root.get("temperature").asDouble()
        );

        assertEquals(
                1.0d,
                root.get("top_p").asDouble()
        );

        assertEquals(
                20260919L,
                root.get("seed").asLong()
        );

        assertEquals(
                2048,
                root.get("max_completion_tokens")
                        .asInt()
        );

        assertFalse(
                root.get("stream").asBoolean()
        );

        assertEquals(
                "on_demand",
                root.get("service_tier")
                        .asText()
        );

        assertEquals(
                "json_schema",
                root.get("response_format")
                        .get("type")
                        .asText()
        );

        assertTrue(
                root.get("response_format")
                        .get("json_schema")
                        .get("strict")
                        .asBoolean()
        );
    }

    @Test
    void differentScenarioRequestUsesSelectedBoundedEvidence()
            throws Exception {

        Harness harness =
                successfulHarness(
                        DIFFERENT_SCENARIO_ATTEMPT_ID
                );

        MeasuredAttempt selected =
                attempt(
                        DIFFERENT_SCENARIO_ATTEMPT_ID
                );

        LlmReliabilityScenario selectedScenario =
                scenario(
                        selected.scenarioId()
                );

        String request =
                harness.executor()
                        .buildFrozenRequest(selected);

        JsonNode root =
                new ObjectMapper()
                        .readTree(request);

        String evidenceJson =
                root.get("messages")
                        .get(1)
                        .get("content")
                        .asText();

        assertTrue(
                evidenceJson.contains(
                        selectedScenario.evidence()
                                .trafficEventId()
                )
        );

        assertTrue(
                evidenceJson.contains(
                        selectedScenario.evidence()
                                .findingId()
                )
        );
    }

    @Test
    void promptAndSchemaHashesRemainFrozen() {
        assertEquals(
                "d9ee37eddddff37d75ecdeb933639c356054966f70e2e76ba5694efc7c655947",
                MeasuredProtocolConfiguration
                        .PROMPT_SHA256
        );

        assertEquals(
                "7247e79e1cb5be50e7baeb28ac185caeab68fbc8336736f9c8c212d091768ea4",
                MeasuredProtocolConfiguration
                        .SCHEMA_SHA256
        );
    }

    @Test
    void requestContainsOnlyBoundedEvidenceNotRawTraffic()
            throws Exception {

        Harness harness =
                successfulHarness(
                        FIRST_ATTEMPT_ID
                );

        String request =
                harness.executor()
                        .buildFrozenRequest(
                                attempt(FIRST_ATTEMPT_ID)
                        );

        JsonNode root =
                new ObjectMapper()
                        .readTree(request);

        String evidenceJson =
                root.get("messages")
                        .get(1)
                        .get("content")
                        .asText();

        LlmReliabilityScenario scenario =
                scenario(
                        "llm-001-undocumented-operation"
                );

        assertTrue(
                evidenceJson.contains(
                        scenario.evidence()
                                .trafficEventId()
                )
        );

        assertTrue(
                evidenceJson.contains(
                        scenario.evidence()
                                .findingId()
                )
        );

        assertFalse(
                request.contains(
                        "\"requestHeaders\""
                )
        );

        assertFalse(
                request.contains(
                        "\"responseHeaders\""
                )
        );

        assertFalse(
                request.contains(
                        "\"requestBody\""
                )
        );

        assertFalse(
                request.contains(
                        "\"responseBody\""
                )
        );

        assertFalse(
                request.contains(
                        "\"cookie\""
                )
        );

        assertFalse(
                request.contains(
                        "\"jwt\""
                )
        );
    }

    @Test
    void missingAuthorizationProducesZeroTransportCalls()
            throws Exception {

        Harness harness =
                successfulHarness(
                        FIRST_ATTEMPT_ID
                );

        MeasuredAttemptRunner runner =
                runner(harness);

        Map<String, String> environment =
                Map.of(
                        "GROQ_API_KEY",
                        SYNTHETIC_KEY
                );

        assertThrows(
                SecurityException.class,
                () -> runner.executeOne(
                        attempt(FIRST_ATTEMPT_ID),
                        environment,
                        SYNTHETIC_AUTH
                )
        );

        assertEquals(
                0,
                harness.transport()
                        .invocationCount()
        );
    }

    @Test
    void wrongAuthorizationProducesZeroTransportCalls()
            throws Exception {

        Harness harness =
                successfulHarness(
                        FIRST_ATTEMPT_ID
                );

        MeasuredAttemptRunner runner =
                runner(harness);

        assertThrows(
                SecurityException.class,
                () -> runner.executeOne(
                        attempt(FIRST_ATTEMPT_ID),
                        Map.of(
                                "GROQ_API_KEY",
                                SYNTHETIC_KEY,
                                "ILDCVF_E6_MEASURED_AUTHORIZATION",
                                "wrong-value"
                        ),
                        SYNTHETIC_AUTH
                )
        );

        assertEquals(
                0,
                harness.transport()
                        .invocationCount()
        );
    }

    @Test
    void existingResultStopsBeforeTransportInvocation()
            throws Exception {

        Harness harness =
                successfulHarness(
                        SECOND_REPETITION_ATTEMPT_ID
                );

        MeasuredAttempt selected =
                attempt(
                        SECOND_REPETITION_ATTEMPT_ID
                );

        MeasuredResultPaths paths =
                resultPaths();

        MeasuredResultExporter exporter =
                new MeasuredResultExporter(paths);

        Files.createDirectories(
                paths.attemptDirectory(selected)
        );

        Files.writeString(
                paths.resultFile(selected),
                "{}"
        );

        MeasuredAttemptRunner runner =
                new MeasuredAttemptRunner(
                        exporter,
                        harness.executor()
                                ::executeExactlyOnce
                );

        assertThrows(
                IllegalStateException.class,
                () -> runner.executeOne(
                        selected,
                        authorizedEnvironment(),
                        SYNTHETIC_AUTH
                )
        );

        assertEquals(
                0,
                harness.transport()
                        .invocationCount()
        );
    }

    @Test
    void syntheticAuthorizationInvokesFakeTransportExactlyOnce()
            throws Exception {

        String selectedAttemptId =
                SECOND_REPETITION_ATTEMPT_ID;

        Harness harness =
                successfulHarness(
                        selectedAttemptId
                );

        MeasuredAttemptResult result =
                runner(harness)
                        .executeOne(
                                attempt(selectedAttemptId),
                                authorizedEnvironment(),
                                SYNTHETIC_AUTH
                        );

        assertEquals(
                1,
                harness.transport()
                        .invocationCount()
        );

        assertEquals(
                1,
                result.providerInvocationCount()
        );

        assertEquals(
                0,
                result.retryCount()
        );

        assertEquals(
                selectedAttemptId,
                result.attemptId()
        );

        assertEquals(
                2,
                result.repetition()
        );
    }

    @Test
    void differentScenarioExecutesAgainstItsOwnEvidenceExactlyOnce()
            throws Exception {

        String selectedAttemptId =
                DIFFERENT_SCENARIO_ATTEMPT_ID;

        Harness harness =
                successfulHarness(
                        selectedAttemptId
                );

        MeasuredAttempt selected =
                attempt(selectedAttemptId);

        LlmReliabilityScenario selectedScenario =
                scenario(
                        selected.scenarioId()
                );

        MeasuredAttemptResult result =
                runner(harness)
                        .executeOne(
                                selected,
                                authorizedEnvironment(),
                                SYNTHETIC_AUTH
                        );

        assertEquals(
                1,
                harness.transport()
                        .invocationCount()
        );

        assertEquals(
                selectedAttemptId,
                result.attemptId()
        );

        assertEquals(
                selected.scenarioId(),
                result.scenarioId()
        );

        assertEquals(
                selected.repetition(),
                result.repetition()
        );

        assertEquals(
                selectedScenario.evidenceFingerprint(),
                result.evidenceFingerprint()
        );

        assertTrue(
                result.validatorAccepted()
        );

        assertTrue(
                result.groundTruthExactMatch()
        );

        assertFalse(
                result.hallucinated()
        );
    }

    @Test
    void providerFailureIsRetainedWithoutRetry()
            throws Exception {

        String selectedAttemptId =
                SECOND_REPETITION_ATTEMPT_ID;

        Harness harness =
                providerFailureHarness(
                        selectedAttemptId
                );

        MeasuredAttemptResult result =
                runner(harness)
                        .executeOne(
                                attempt(selectedAttemptId),
                                authorizedEnvironment(),
                                SYNTHETIC_AUTH
                        );

        assertEquals(
                "HTTP_5XX",
                result.providerCallStatus()
        );

        assertEquals(
                500,
                result.httpStatus()
        );

        assertEquals(
                1,
                harness.transport()
                        .invocationCount()
        );

        assertEquals(
                0,
                result.retryCount()
        );

        assertEquals(
                "NOT_APPLICABLE",
                result.validatorApplicability()
        );
    }

    @Test
    void parseFailureIsRetainedWithoutRetry()
            throws Exception {

        String selectedAttemptId =
                SECOND_REPETITION_ATTEMPT_ID;

        Harness harness =
                parseFailureHarness(
                        selectedAttemptId
                );

        MeasuredAttemptResult result =
                runner(harness)
                        .executeOne(
                                attempt(selectedAttemptId),
                                authorizedEnvironment(),
                                SYNTHETIC_AUTH
                        );

        assertEquals(
                "PROPOSAL_PARSE_FAILURE",
                result.providerCallStatus()
        );

        assertEquals(
                "MALFORMED_JSON",
                result.proposalParseStatus()
        );

        assertEquals(
                1,
                harness.transport()
                        .invocationCount()
        );

        assertEquals(
                0,
                result.retryCount()
        );
    }

    @Test
    void validatorRejectionGroundTruthMismatchAndHallucinationAreRetained()
            throws Exception {

        MeasuredAttempt selected =
                attempt(FIRST_ATTEMPT_ID);

        LlmReliabilityScenario scenario =
                scenario(
                        selected.scenarioId()
                );

        ContractChangeProposal wrong =
                new ContractChangeProposal(
                        "synthetic-proposal",
                        "evaluation-004",
                        "evaluation-004:UNDOCUMENTED_OPERATION:1",
                        scenario.groundTruth()
                                .driftType(),
                        scenario.groundTruth()
                                .changeKind(),
                        "PATCH",
                        "/api/residents",
                        null,
                        null,
                        "documented OpenAPI operation",
                        "PATCH /api/residents",
                        null,
                        "Synthetic deliberately incorrect "
                                + "proposed value."
                );

        Harness harness =
                successfulHarness(
                        selected,
                        wrong
                );

        MeasuredAttemptResult result =
                runner(harness)
                        .executeOne(
                                selected,
                                authorizedEnvironment(),
                                SYNTHETIC_AUTH
                        );

        assertEquals(
                "APPLICABLE",
                result.validatorApplicability()
        );

        assertFalse(
                result.validatorAccepted()
        );

        assertTrue(
                result.validatorCodes()
                        .contains(
                                "PROPOSED_VALUE_NOT_GROUNDED"
                        )
        );

        assertEquals(
                "APPLICABLE",
                result.groundTruthApplicability()
        );

        assertFalse(
                result.groundTruthExactMatch()
        );

        assertEquals(
                Boolean.FALSE,
                result.groundTruthFieldComparison()
                        .get("proposedValue")
        );

        assertEquals(
                "APPLICABLE",
                result.hallucinationApplicability()
        );

        assertTrue(
                result.hallucinated()
        );

        assertTrue(
                result.hallucinationReasonCodes()
                        .contains(
                                "PROPOSED_VALUE_UNSUPPORTED"
                        )
        );
    }

    @Test
    void successfulProposalRetainsExactScientificOutcome()
            throws Exception {

        String selectedAttemptId =
                DIFFERENT_SCENARIO_ATTEMPT_ID;

        Harness harness =
                successfulHarness(
                        selectedAttemptId
                );

        MeasuredAttemptResult result =
                runner(harness)
                        .executeOne(
                                attempt(selectedAttemptId),
                                authorizedEnvironment(),
                                SYNTHETIC_AUTH
                        );

        assertTrue(
                result.validatorAccepted()
        );

        assertTrue(
                result.groundTruthExactMatch()
        );

        assertFalse(
                result.hallucinated()
        );

        assertEquals(
                11,
                result.groundTruthFieldComparison()
                        .size()
        );

        assertTrue(
                result.groundTruthFieldComparison()
                        .values()
                        .stream()
                        .allMatch(Boolean.TRUE::equals)
        );
    }

    @Test
    void tokenAndProviderMetadataAreRetained()
            throws Exception {

        Harness harness =
                successfulHarness(
                        SECOND_REPETITION_ATTEMPT_ID
                );

        MeasuredAttemptResult result =
                runner(harness)
                        .executeOne(
                                attempt(
                                        SECOND_REPETITION_ATTEMPT_ID
                                ),
                                authorizedEnvironment(),
                                SYNTHETIC_AUTH
                        );

        assertEquals(
                100L,
                result.inputTokens()
        );

        assertEquals(
                20L,
                result.outputTokens()
        );

        assertEquals(
                7L,
                result.reasoningTokens()
        );

        assertEquals(
                120L,
                result.totalTokens()
        );

        assertEquals(
                "openai/gpt-oss-20b",
                result.returnedModel()
        );

        assertEquals(
                "synthetic-system-fingerprint",
                result.systemFingerprint()
        );

        assertEquals(
                "stop",
                result.finishReason()
        );
    }

    @Test
    void resultIdentityUsesFrozenMeasuredProtocol()
            throws Exception {

        Harness harness =
                successfulHarness(
                        SECOND_REPETITION_ATTEMPT_ID
                );

        MeasuredAttemptResult result =
                runner(harness)
                        .executeOne(
                                attempt(
                                        SECOND_REPETITION_ATTEMPT_ID
                                ),
                                authorizedEnvironment(),
                                SYNTHETIC_AUTH
                        );

        assertEquals(
                "e6-measured-v1",
                result.protocolId()
        );

        assertEquals(
                "groq-gpt-oss-20b-token2048-v1",
                result.configurationLabel()
        );

        assertTrue(
                result.measuredCampaignAttempt()
        );

        assertEquals(
                2048,
                result.maxCompletionTokens()
        );
    }

    @Test
    void outputPathIsSelectedAttemptOnly()
            throws Exception {

        MeasuredResultPaths paths =
                resultPaths();

        MeasuredAttempt selected =
                attempt(
                        SECOND_REPETITION_ATTEMPT_ID
                );

        String normalized =
                paths.attemptDirectory(selected)
                        .toString()
                        .replace('\\', '/');

        assertTrue(
                normalized.contains(
                        "/results/measured/"
                                + SECOND_REPETITION_ATTEMPT_ID
                )
        );

        assertFalse(
                normalized.contains(
                        "/results/smoke/"
                )
        );

        assertFalse(
                normalized.contains(
                        "/results/compatibility/"
                )
        );
    }

    @Test
    void serializedResultContainsNoSecretOrAuthorization()
            throws Exception {

        String selectedAttemptId =
                SECOND_REPETITION_ATTEMPT_ID;

        Harness harness =
                successfulHarness(
                        selectedAttemptId
                );

        MeasuredAttempt selected =
                attempt(selectedAttemptId);

        MeasuredAttemptResult result =
                runner(harness)
                        .executeOne(
                                selected,
                                authorizedEnvironment(),
                                SYNTHETIC_AUTH
                        );

        String serialized =
                Files.readString(
                        resultPaths()
                                .resultFile(selected)
                );

        assertFalse(
                serialized.contains(
                        SYNTHETIC_KEY
                )
        );

        assertFalse(
                serialized.contains(
                        SYNTHETIC_AUTH
                )
        );

        assertFalse(
                serialized.contains(
                        "Authorization"
                )
        );

        assertFalse(
                serialized.contains(
                        "Bearer "
                )
        );

        assertTrue(
                result.measuredCampaignAttempt()
        );
    }

    @Test
    void realEntryPointIsNotAJunitTestAndAcceptsExplicitAttemptId()
            throws Exception {

        assertFalse(
                GroqMeasuredSingleAttemptRealCall.class
                        .getSimpleName()
                        .endsWith("Test")
        );

        Method method =
                GroqMeasuredSingleAttemptRealCall.class
                        .getDeclaredMethod(
                                "executeAuthorizedMeasuredAttemptExactlyOnce",
                                Path.class,
                                String.class,
                                String.class
                        );

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

        assertEquals(
                MeasuredAttemptResult.class,
                method.getReturnType()
        );
    }

    @Test
    void unknownAttemptIdIsRejectedByFrozenSchedule() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MeasuredAttemptSchedule
                                .requireAttempt(
                                        "not-a-measured-attempt"
                                )
        );
    }

    @Test
    void offlineConstructionDoesNotConstructOrInvokeRealTransport() {
        AtomicInteger fakeCalls =
                new AtomicInteger();

        GroqTransport fake =
                (
                        endpoint,
                        secret,
                        body,
                        timeout
                ) -> {
                    fakeCalls.incrementAndGet();

                    return fakeResponse(
                            200,
                            successfulRawProviderResponse(
                                    canonicalProposal(
                                            FIRST_ATTEMPT_ID
                                    )
                            )
                    );
                };

        MeasuredRecordingGroqTransport transport =
                new MeasuredRecordingGroqTransport(
                        fake
                );

        assertEquals(
                0,
                fakeCalls.get()
        );

        assertEquals(
                0,
                transport.invocationCount()
        );
    }

    @Test
    void offlineTestsUseTemporaryMeasuredDirectoryOnly() {
        String normalized =
                resultPaths()
                        .measuredRoot()
                        .toString()
                        .replace('\\', '/');

        assertTrue(
                normalized.contains(
                        "/results/measured"
                )
        );

        assertTrue(
                normalized.contains(
                        tempDir.toAbsolutePath()
                                .normalize()
                                .toString()
                                .replace('\\', '/')
                )
        );
    }

    private MeasuredAttempt attempt(
            String attemptId
    ) {
        return MeasuredAttemptSchedule
                .requireAttempt(attemptId);
    }

    private LlmReliabilityScenario scenario(
            String scenarioId
    ) {
        return new LlmScenarioCatalog()
                .scenarios()
                .stream()
                .filter(candidate ->
                        scenarioId.equals(
                                candidate.scenarioId()
                        )
                )
                .findFirst()
                .orElseThrow();
    }

    private ContractChangeProposal canonicalProposal(
            String attemptId
    ) {
        MeasuredAttempt selected =
                attempt(attemptId);

        LlmReliabilityScenario scenario =
                scenario(
                        selected.scenarioId()
                );

        return scenario.groundTruth()
                .toCanonicalProposal(
                        scenario.scenarioId()
                );
    }

    private Map<String, String> authorizedEnvironment() {
        return Map.of(
                "GROQ_API_KEY",
                SYNTHETIC_KEY,
                "ILDCVF_E6_MEASURED_AUTHORIZATION",
                SYNTHETIC_AUTH
        );
    }

    private MeasuredResultPaths resultPaths() {
        return new MeasuredResultPaths(
                tempDir.resolve(
                        "results"
                ).resolve(
                        "measured"
                )
        );
    }

    private MeasuredAttemptRunner runner(
            Harness harness
    ) {
        return new MeasuredAttemptRunner(
                new MeasuredResultExporter(
                        resultPaths()
                ),
                harness.executor()
                        ::executeExactlyOnce
        );
    }

    private Harness successfulHarness(
            String attemptId
    ) {
        MeasuredAttempt selected =
                attempt(attemptId);

        return successfulHarness(
                selected,
                canonicalProposal(attemptId)
        );
    }

    private Harness successfulHarness(
            MeasuredAttempt selected,
            ContractChangeProposal proposal
    ) {
        String raw =
                successfulRawProviderResponse(
                        proposal
                );

        return harness(
                selected,
                fakeResponse(
                        200,
                        raw
                ),
                new GroqProviderAttempt(
                        ProviderCallStatus.SUCCESS,
                        metadata(
                                selected,
                                200,
                                100L,
                                20L,
                                120L
                        ),
                        new GroqProviderEnvelope(
                                "synthetic-response",
                                "openai/gpt-oss-20b",
                                "synthetic-system-fingerprint",
                                "on_demand",
                                "stop",
                                100L,
                                20L,
                                120L,
                                proposalJson(proposal),
                                raw
                        ),
                        ProposalParseResult.success(
                                proposal
                        ),
                        null
                )
        );
    }

    private Harness providerFailureHarness(
            String attemptId
    ) {
        MeasuredAttempt selected =
                attempt(attemptId);

        String raw =
                """
                {
                  "error": {
                    "message": "synthetic provider failure"
                  }
                }
                """;

        return harness(
                selected,
                fakeResponse(
                        500,
                        raw
                ),
                new GroqProviderAttempt(
                        ProviderCallStatus.HTTP_5XX,
                        metadata(
                                selected,
                                500,
                                null,
                                null,
                                null
                        ),
                        null,
                        null,
                        "Provider returned HTTP 500."
                )
        );
    }

    private Harness parseFailureHarness(
            String attemptId
    ) {
        MeasuredAttempt selected =
                attempt(attemptId);

        String raw =
                """
                {
                  "id": "synthetic-response",
                  "model": "openai/gpt-oss-20b",
                  "system_fingerprint": "synthetic-system-fingerprint",
                  "service_tier": "on_demand",
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": {
                        "content": "{broken"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 100,
                    "completion_tokens": 20,
                    "total_tokens": 120,
                    "completion_tokens_details": {
                      "reasoning_tokens": 7
                    }
                  }
                }
                """;

        return harness(
                selected,
                fakeResponse(
                        200,
                        raw
                ),
                new GroqProviderAttempt(
                        ProviderCallStatus.PROPOSAL_PARSE_FAILURE,
                        metadata(
                                selected,
                                200,
                                100L,
                                20L,
                                120L
                        ),
                        new GroqProviderEnvelope(
                                "synthetic-response",
                                "openai/gpt-oss-20b",
                                "synthetic-system-fingerprint",
                                "on_demand",
                                "stop",
                                100L,
                                20L,
                                120L,
                                "{broken",
                                raw
                        ),
                        ProposalParseResult.failure(
                                ProposalParseStatus
                                        .MALFORMED_JSON,
                                "Synthetic malformed JSON."
                        ),
                        "Assistant proposal could not be parsed."
                )
        );
    }

    private Harness harness(
            MeasuredAttempt selected,
            GroqHttpResponse response,
            GroqProviderAttempt providerAttempt
    ) {
        AtomicInteger delegateCalls =
                new AtomicInteger();

        GroqTransport fakeDelegate =
                (
                        endpoint,
                        secret,
                        body,
                        timeout
                ) -> {
                    delegateCalls.incrementAndGet();
                    return response;
                };

        MeasuredRecordingGroqTransport transport =
                new MeasuredRecordingGroqTransport(
                        fakeDelegate
                );

        GroqConfiguration configuration =
                MeasuredGroqAttemptExecutor
                        .frozenMeasuredGroqConfiguration();

        MeasuredGroqAttemptExecutor executor =
                new MeasuredGroqAttemptExecutor(
                        new LlmScenarioCatalog(),
                        new AttemptEvaluator(),
                        new GroqRequestBuilder(),
                        configuration,
                        evidence -> {
                            LlmReliabilityScenario expectedScenario =
                                    scenario(
                                            selected.scenarioId()
                                    );

                            assertEquals(
                                    expectedScenario.evidence(),
                                    evidence
                            );

                            assertEquals(
                                    expectedScenario
                                            .evidenceFingerprint(),
                                    EvidenceFingerprint.sha256(
                                            evidence
                                    )
                            );

                            try {
                                transport.post(
                                        URI.create(
                                                "https://example.test"
                                        ),
                                        SYNTHETIC_KEY,
                                        new GroqRequestBuilder()
                                                .build(
                                                        configuration,
                                                        evidence
                                                ),
                                        Duration.ofSeconds(30)
                                );
                            } catch (GroqTransportException exception) {
                                throw new AssertionError(
                                        "Synthetic measured transport "
                                                + "unexpectedly failed.",
                                        exception
                                );
                            }

                            assertEquals(
                                    1,
                                    delegateCalls.get()
                            );

                            return providerAttempt;
                        },
                        transport
                );

        return new Harness(
                executor,
                transport
        );
    }

    private ProviderAttemptMetadata metadata(
            MeasuredAttempt selected,
            Integer httpStatus,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens
    ) {
        LlmReliabilityScenario scenario =
                scenario(
                        selected.scenarioId()
                );

        return new ProviderAttemptMetadata(
                "groq",
                "openai/gpt-oss-20b",
                httpStatus != null
                        && httpStatus == 200
                        ? "openai/gpt-oss-20b"
                        : null,
                httpStatus != null
                        && httpStatus == 200
                        ? "synthetic-system-fingerprint"
                        : null,
                "on_demand",
                0.0d,
                1.0d,
                20260919L,
                2048,
                MeasuredProtocolConfiguration
                        .PROMPT_SHA256,
                scenario.evidenceFingerprint(),
                httpStatus,
                Duration.ofMillis(5),
                inputTokens,
                outputTokens,
                totalTokens,
                httpStatus != null
                        && httpStatus == 200
                        ? "stop"
                        : null,
                Instant.parse(
                        "2026-09-20T00:00:00Z"
                )
        );
    }

    private GroqHttpResponse fakeResponse(
            int status,
            String body
    ) {
        return new GroqHttpResponse(
                status,
                body,
                Map.of(),
                Duration.ofMillis(5)
        );
    }

    private String successfulRawProviderResponse(
            ContractChangeProposal proposal
    ) {
        return """
                {
                  "id": "synthetic-response",
                  "model": "openai/gpt-oss-20b",
                  "system_fingerprint": "synthetic-system-fingerprint",
                  "service_tier": "on_demand",
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": {
                        "content": %s
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 100,
                    "completion_tokens": 20,
                    "total_tokens": 120,
                    "completion_tokens_details": {
                      "reasoning_tokens": 7
                    }
                  }
                }
                """.formatted(
                jsonString(
                        proposalJson(proposal)
                )
        );
    }

    private String proposalJson(
            ContractChangeProposal proposal
    ) {
        try {
            return new ObjectMapper()
                    .writeValueAsString(
                            proposal
                    );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    exception
            );
        }
    }

    private String jsonString(
            String value
    ) {
        try {
            return new ObjectMapper()
                    .writeValueAsString(
                            value
                    );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    exception
            );
        }
    }

    private record Harness(
            MeasuredGroqAttemptExecutor executor,
            MeasuredRecordingGroqTransport transport
    ) {
    }
}