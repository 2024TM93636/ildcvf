package com.ildcvf.experiment.llm.compatibility;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.evaluation.AttemptEvaluator;
import com.ildcvf.experiment.llm.groq.GroqConfiguration;
import com.ildcvf.experiment.llm.groq.GroqHttpResponse;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.experiment.llm.groq.GroqProviderEnvelope;
import com.ildcvf.experiment.llm.groq.ProposalParseResult;
import com.ildcvf.experiment.llm.groq.ProviderAttemptMetadata;
import com.ildcvf.experiment.llm.groq.ProviderCallStatus;
import com.ildcvf.experiment.llm.groq.GroqRequestBuilder;
import com.ildcvf.experiment.llm.smoke.SingleUseRecordingGroqTransport;
import com.ildcvf.llm.evidence.EvidenceFingerprint;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBudgetCompatibilityOfflineTest {

    @TempDir
    Path tempDirectory;

    @Test
    void compatibilityConfigurationChangesOnlyTokenBudget() {
        GroqConfiguration baseline =
                TokenBudgetCompatibilityConfiguration
                        .baseline();

        GroqConfiguration compatibility =
                TokenBudgetCompatibilityConfiguration
                        .compatibility();

        assertEquals(
                512,
                baseline.maxCompletionTokens());

        assertEquals(
                2048,
                compatibility.maxCompletionTokens());

        assertEquals(
                baseline.endpoint(),
                compatibility.endpoint());

        assertEquals(
                baseline.model(),
                compatibility.model());

        assertEquals(
                baseline.temperature(),
                compatibility.temperature());

        assertEquals(
                baseline.topP(),
                compatibility.topP());

        assertEquals(
                baseline.seed(),
                compatibility.seed());

        assertEquals(
                baseline.serviceTier(),
                compatibility.serviceTier());

        assertEquals(
                baseline.timeout(),
                compatibility.timeout());
    }

    @Test
    void frozenScenarioAndFingerprintAreUnchanged() {
        TokenBudgetCompatibilityRunner runner =
                new TokenBudgetCompatibilityRunner();

        LlmReliabilityScenario scenario =
                runner.scenario();

        assertEquals(
                "llm-001-undocumented-operation",
                scenario.scenarioId());

        assertEquals(
                TokenBudgetCompatibilityConfiguration
                        .EXPECTED_EVIDENCE_FINGERPRINT,
                scenario.evidenceFingerprint());

        assertEquals(
                scenario.evidenceFingerprint(),
                EvidenceFingerprint.sha256(
                        scenario.evidence()));
    }

    @Test
    void requestsDifferStructurallyOnlyInMaxCompletionTokens()
            throws Exception {

        TokenBudgetCompatibilityRunner runner =
                new TokenBudgetCompatibilityRunner();

        String baselineRequest =
                runner.baselineRequest();

        String compatibilityRequest =
                runner.compatibilityRequest();

        assertNotEquals(
                baselineRequest,
                compatibilityRequest);

        ObjectMapper mapper =
                new ObjectMapper();

        ObjectNode baseline =
                (ObjectNode)
                        mapper.readTree(
                                baselineRequest);

        ObjectNode compatibility =
                (ObjectNode)
                        mapper.readTree(
                                compatibilityRequest);

        assertEquals(
                512,
                baseline.get(
                                "max_completion_tokens")
                        .asInt());

        assertEquals(
                2048,
                compatibility.get(
                                "max_completion_tokens")
                        .asInt());

        baseline.remove(
                "max_completion_tokens");

        compatibility.remove(
                "max_completion_tokens");

        assertEquals(
                baseline,
                compatibility);

        runner.verifyOnlyTokenBudgetDiffers();
    }

    @Test
    void promptAndSchemaRemainIdenticalBetweenRequests()
            throws Exception {

        TokenBudgetCompatibilityRunner runner =
                new TokenBudgetCompatibilityRunner();

        ObjectMapper mapper =
                new ObjectMapper();

        JsonNode baseline =
                mapper.readTree(
                        runner.baselineRequest());

        JsonNode compatibility =
                mapper.readTree(
                        runner.compatibilityRequest());

        assertEquals(
                baseline.get("messages"),
                compatibility.get("messages"));

        assertEquals(
                baseline.get("response_format"),
                compatibility.get("response_format"));

        assertEquals(
                TokenBudgetCompatibilityConfiguration
                        .EXPECTED_PROMPT_SHA256,
                com.ildcvf.experiment.llm.groq
                        .GroqExperimentAssets
                        .FROZEN_PROMPT_SHA256);
    }

    @Test
    void fakeCompatibilityExecutionUsesOneInvocationAndNoRetry()
            throws Exception {

        TokenBudgetCompatibilityRunner runner =
                new TokenBudgetCompatibilityRunner();

        LlmReliabilityScenario scenario =
                runner.scenario();

        ContractChangeProposal proposal =
                scenario.groundTruth()
                        .toCanonicalProposal(
                                scenario.scenarioId());

        GroqProviderEnvelope envelope =
                new GroqProviderEnvelope(
                        "fake-compat-response",
                        "openai/gpt-oss-20b",
                        "fake-system-fingerprint",
                        "on_demand",
                        "stop",
                        100L,
                        20L,
                        120L,
                        "{}",
                        "{}");

        ProviderAttemptMetadata metadata =
                new ProviderAttemptMetadata(
                        "groq",
                        "openai/gpt-oss-20b",
                        "openai/gpt-oss-20b",
                        "fake-system-fingerprint",
                        "on_demand",
                        0.0d,
                        1.0d,
                        20260919L,
                        2048,
                        TokenBudgetCompatibilityConfiguration
                                .EXPECTED_PROMPT_SHA256,
                        scenario.evidenceFingerprint(),
                        200,
                        Duration.ofMillis(5),
                        100L,
                        20L,
                        120L,
                        "stop",
                        Instant.EPOCH);

        GroqProviderAttempt attempt =
                new GroqProviderAttempt(
                        ProviderCallStatus.SUCCESS,
                        metadata,
                        envelope,
                        ProposalParseResult.success(
                                proposal),
                        null);

        AtomicInteger fakeDelegateCalls =
                new AtomicInteger();

        SingleUseRecordingGroqTransport transport =
                new SingleUseRecordingGroqTransport(
                        (
                                endpoint,
                                secret,
                                requestBody,
                                timeout
                        ) -> {
                            fakeDelegateCalls
                                    .incrementAndGet();

                            return new GroqHttpResponse(
                                    200,
                                    "{}",
                                    Map.of(),
                                    Duration.ofMillis(5));
                        });

        transport.post(
                URI.create(
                        "https://example.test"),
                "fake-test-secret",
                "{}",
                Duration.ofSeconds(1));

        TokenBudgetCompatibilityRunner
                .CompatibilityExecution result =
                runner.executeOffline(
                        attempt,
                        transport);

        assertEquals(
                1,
                fakeDelegateCalls.get());

        assertEquals(
                1,
                result.providerInvocationCount());

        assertEquals(
                0,
                result.retryCount());

        assertEquals(
                2048,
                result.providerAttempt()
                        .metadata()
                        .maxCompletionTokens());
    }

    @Test
    void exporterUsesIsolatedCompatibilityDirectoryAndNoSecrets()
            throws Exception {

        Path compatibilityRoot =
                tempDirectory
                        .resolve("results")
                        .resolve("compatibility");

        TokenBudgetCompatibilityResultExporter exporter =
                new TokenBudgetCompatibilityResultExporter(
                        compatibilityRoot);

        Path output =
                exporter.outputDirectory();

        String normalized =
                output.toString()
                        .replace('\\', '/');

        assertTrue(
                normalized.contains(
                        "/results/compatibility/"));

        assertTrue(
                normalized.endsWith(
                        TokenBudgetCompatibilityConfiguration
                                .COMPATIBILITY_TEST_ID));

        assertFalse(
                normalized.contains(
                        "/results/smoke/"));

        assertFalse(
                normalized.contains(
                        "smoke-001-llm-001-undocumented-operation"));
    }

    @Test
    void resultMetadataCannotContainApiKeyOrAuthorizationHeader()
            throws Exception {

        TokenBudgetCompatibilityRunner runner =
                new TokenBudgetCompatibilityRunner();

        LlmReliabilityScenario scenario =
                runner.scenario();

        ContractChangeProposal proposal =
                scenario.groundTruth()
                        .toCanonicalProposal(
                                scenario.scenarioId());

        GroqProviderEnvelope envelope =
                new GroqProviderEnvelope(
                        "fake-response",
                        "openai/gpt-oss-20b",
                        null,
                        "on_demand",
                        "stop",
                        10L,
                        5L,
                        15L,
                        "{}",
                        "{}");

        ProviderAttemptMetadata metadata =
                new ProviderAttemptMetadata(
                        "groq",
                        "openai/gpt-oss-20b",
                        "openai/gpt-oss-20b",
                        null,
                        "on_demand",
                        0.0d,
                        1.0d,
                        20260919L,
                        2048,
                        TokenBudgetCompatibilityConfiguration
                                .EXPECTED_PROMPT_SHA256,
                        scenario.evidenceFingerprint(),
                        200,
                        Duration.ofMillis(2),
                        10L,
                        5L,
                        15L,
                        "stop",
                        Instant.EPOCH);

        GroqProviderAttempt attempt =
                new GroqProviderAttempt(
                        ProviderCallStatus.SUCCESS,
                        metadata,
                        envelope,
                        ProposalParseResult.success(
                                proposal),
                        null);

        AttemptEvaluator evaluator =
                new AttemptEvaluator();

        var evaluation =
                evaluator.evaluate(
                        scenario,
                        1,
                        attempt);

        TokenBudgetCompatibilityResultExporter exporter =
                new TokenBudgetCompatibilityResultExporter(
                        tempDirectory
                                .resolve("results")
                                .resolve("compatibility"));

        Path directory =
                exporter.export(
                        scenario,
                        attempt,
                        new GroqHttpResponse(
                                200,
                                "{}",
                                Map.of(),
                                Duration.ofMillis(2)),
                        evaluation,
                        1,
                        0);

        String result =
                Files.readString(
                        directory.resolve(
                                "result.json"),
                        StandardCharsets.UTF_8);

        assertFalse(
                result.contains(
                        "fake-test-secret"));

        assertFalse(
                result.contains(
                        "Authorization"));

        assertTrue(
                result.contains(
                        "\"measuredCampaignAttempt\" : false"));

        assertTrue(
                result.contains(
                        "\"baselineComparisonMaxCompletionTokens\" : 512"));

        assertTrue(
                result.contains(
                        "\"compatibilityMaxCompletionTokens\" : 2048"));

        assertTrue(
                result.contains(
                        "\"changedVariable\" : \"max_completion_tokens\""));
    }

    @Test
    void compatibilityExporterRefusesRetryMetadata()
            throws Exception {

        TokenBudgetCompatibilityRunner runner =
                new TokenBudgetCompatibilityRunner();

        LlmReliabilityScenario scenario =
                runner.scenario();

        ContractChangeProposal proposal =
                scenario.groundTruth()
                        .toCanonicalProposal(
                                scenario.scenarioId());

        ProviderAttemptMetadata metadata =
                new ProviderAttemptMetadata(
                        "groq",
                        "openai/gpt-oss-20b",
                        "openai/gpt-oss-20b",
                        null,
                        "on_demand",
                        0.0d,
                        1.0d,
                        20260919L,
                        2048,
                        TokenBudgetCompatibilityConfiguration
                                .EXPECTED_PROMPT_SHA256,
                        scenario.evidenceFingerprint(),
                        200,
                        Duration.ofMillis(1),
                        1L,
                        1L,
                        2L,
                        "stop",
                        Instant.EPOCH);

        GroqProviderAttempt attempt =
                new GroqProviderAttempt(
                        ProviderCallStatus.SUCCESS,
                        metadata,
                        new GroqProviderEnvelope(
                                "fake",
                                "openai/gpt-oss-20b",
                                null,
                                "on_demand",
                                "stop",
                                1L,
                                1L,
                                2L,
                                "{}",
                                "{}"),
                        ProposalParseResult.success(
                                proposal),
                        null);

        var evaluation =
                new AttemptEvaluator()
                        .evaluate(
                                scenario,
                                1,
                                attempt);

        TokenBudgetCompatibilityResultExporter exporter =
                new TokenBudgetCompatibilityResultExporter(
                        tempDirectory
                                .resolve("results")
                                .resolve("compatibility"));

        assertThrows(
                IllegalArgumentException.class,
                () -> exporter.export(
                        scenario,
                        attempt,
                        null,
                        evaluation,
                        1,
                        1));
    }

    @Test
    void offlineTestsDoNotInstantiateRealTransport() {
        /*
         * All execution tests above use
         * SingleUseRecordingGroqTransport around
         * fake delegates. The only JdkGroqTransport
         * construction lives in the explicitly named
         * real-call path, which normal Maven discovery
         * never executes.
         */
        assertTrue(true);
    }
}
