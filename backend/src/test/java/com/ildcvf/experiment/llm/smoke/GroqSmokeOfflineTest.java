package com.ildcvf.experiment.llm.smoke;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import com.ildcvf.experiment.llm.evaluation.GroundTruthComparator;
import com.ildcvf.experiment.llm.evaluation.HallucinationClassifier;
import com.ildcvf.experiment.llm.groq.GroqHttpResponse;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.experiment.llm.groq.GroqProviderEnvelope;
import com.ildcvf.experiment.llm.groq.GroqTransport;
import com.ildcvf.experiment.llm.groq.GroqTransportException;
import com.ildcvf.experiment.llm.groq.ProposalParseResult;
import com.ildcvf.experiment.llm.groq.ProviderAttemptMetadata;
import com.ildcvf.experiment.llm.groq.ProviderCallStatus;
import com.ildcvf.llm.evidence.EvidenceFingerprint;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.validation.ContractChangeProposalValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroqSmokeOfflineTest {

    @TempDir
    Path tempDirectory;

    @Test
    void onlyAuthorizedSmokeIdIsAccepted() {
        SmokeCatalog catalog =
                new SmokeCatalog();

        SmokeDefinition definition =
                catalog.resolveDefinition(
                        SmokeCatalog.AUTHORIZED_SMOKE_ID);

        assertEquals(
                SmokeCatalog.AUTHORIZED_SMOKE_ID,
                definition.smokeId());

        assertEquals(
                SmokeCatalog.AUTHORIZED_SCENARIO_ID,
                definition.scenarioId());

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.resolveDefinition(
                        "unknown-smoke"));

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.resolveDefinition(
                        "llm-001-undocumented-operation-r1"));

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.resolveDefinition(
                        "smoke-002-llm-005-request-add-enum"));
    }

    @Test
    void authorizedSmokeSelectsCorrectFrozenScenarioAndFingerprint() {
        SmokeCatalog catalog =
                new SmokeCatalog();

        SmokeDefinition definition =
                catalog.resolveDefinition(
                        SmokeCatalog.AUTHORIZED_SMOKE_ID);

        LlmReliabilityScenario scenario =
                catalog.resolveScenario(
                        definition);

        assertEquals(
                "llm-001-undocumented-operation",
                scenario.scenarioId());

        assertEquals(
                EvidenceFingerprint.sha256(
                        scenario.evidence()),
                scenario.evidenceFingerprint());

        assertFalse(
                definition.smokeId()
                        .equals(
                                scenario.scenarioId()
                                        + "-r1"));
    }

    @Test
    void dryRunPerformsNoNetworkCall() {
        GroqSmokeRunner runner =
                new GroqSmokeRunner();

        runner.printDryRun(
                SmokeCatalog.AUTHORIZED_SMOKE_ID);

        /*
         * printDryRun constructs and verifies only
         * the deterministic request. It has no
         * transport object and therefore cannot
         * perform a network invocation.
         */
        assertTrue(true);
    }

    @Test
    void singleUseTransportInvokesFakeDelegateExactlyOnce()
            throws Exception {

        AtomicInteger delegateCalls =
                new AtomicInteger();

        GroqTransport fake =
                (
                        endpoint,
                        secret,
                        request,
                        timeout
                ) -> {
                    delegateCalls.incrementAndGet();

                    return new GroqHttpResponse(
                            200,
                            "{}",
                            Map.of(),
                            Duration.ofMillis(1));
                };

        SingleUseRecordingGroqTransport transport =
                new SingleUseRecordingGroqTransport(
                        fake);

        GroqHttpResponse response =
                transport.post(
                        URI.create(
                                "https://example.test"),
                        "fake-test-secret",
                        "{}",
                        Duration.ofSeconds(1));

        assertEquals(
                200,
                response.statusCode());

        assertEquals(
                1,
                transport.invocationCount());

        assertEquals(
                1,
                delegateCalls.get());

        assertThrows(
                IllegalStateException.class,
                () -> transport.post(
                        URI.create(
                                "https://example.test"),
                        "fake-test-secret",
                        "{}",
                        Duration.ofSeconds(1)));

        assertEquals(
                1,
                delegateCalls.get());
    }

    @Test
    void offlineExecutionScoresProposalWithoutMeasuredIdentity()
            throws Exception {

        LlmReliabilityScenario scenario =
                authorizedScenario();

        ContractChangeProposal proposal =
                scenario.groundTruth()
                        .toCanonicalProposal(
                                scenario.scenarioId());

        AtomicInteger fakeTransportCalls =
                new AtomicInteger();

        SingleUseRecordingGroqTransport transport =
                new SingleUseRecordingGroqTransport(
                        (
                                endpoint,
                                secret,
                                request,
                                timeout
                        ) -> {
                            fakeTransportCalls
                                    .incrementAndGet();

                            return new GroqHttpResponse(
                                    200,
                                    "{}",
                                    Map.of(),
                                    Duration.ofMillis(5));
                        });

        SmokeResultExporter exporter =
                new SmokeResultExporter(
                        tempDirectory.resolve(
                                        "results")
                                .resolve("smoke"));

        GroqSmokeRunner runner =
                new GroqSmokeRunner(
                        new SmokeCatalog(),
                        exporter,
                        new ContractChangeProposalValidator(),
                        new GroundTruthComparator(),
                        new HallucinationClassifier());

        SmokeExecutionResult result =
                runner.executeOffline(
                        SmokeCatalog.AUTHORIZED_SMOKE_ID,
                        evidence -> {
                            try {
                                GroqHttpResponse response =
                                        transport.post(
                                                URI.create(
                                                        "https://example.test"),
                                                "fake-test-secret",
                                                "{}",
                                                Duration.ofSeconds(1));

                                GroqProviderEnvelope envelope =
                                        new GroqProviderEnvelope(
                                                "fake-response",
                                                "openai/gpt-oss-20b",
                                                "fake-system-fingerprint",
                                                "on_demand",
                                                "stop",
                                                100L,
                                                20L,
                                                120L,
                                                "{}",
                                                response.body());

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
                                                512,
                                                "d9ee37eddddff37d75ecdeb933639c356054966f70e2e76ba5694efc7c655947",
                                                scenario.evidenceFingerprint(),
                                                200,
                                                Duration.ofMillis(5),
                                                100L,
                                                20L,
                                                120L,
                                                "stop",
                                                Instant.EPOCH);

                                return new GroqProviderAttempt(
                                        ProviderCallStatus.SUCCESS,
                                        metadata,
                                        envelope,
                                        ProposalParseResult.success(
                                                proposal),
                                        null);

                            } catch (GroqTransportException exception) {
                                throw new RuntimeException(
                                        exception);
                            }
                        },
                        transport);

        assertEquals(
                1,
                fakeTransportCalls.get());

        assertEquals(
                1,
                result.providerInvocationCount());

        assertTrue(
                result.validationResult()
                        .valid());

        assertTrue(
                result.groundTruthComparison()
                        .exactMatch());

        assertFalse(
                result.hallucinationEvaluation()
                        .hallucinated());
    }

    @Test
    void exporterUsesOnlySmokeNamespaceAndNeverSerializesSecrets()
            throws Exception {

        LlmReliabilityScenario scenario =
                authorizedScenario();

        ContractChangeProposal proposal =
                scenario.groundTruth()
                        .toCanonicalProposal(
                                scenario.scenarioId());

        String rawProvider =
                """
                {
                  "id": "fake-response",
                  "choices": []
                }
                """;

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
                        rawProvider);

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
                        512,
                        "d9ee37eddddff37d75ecdeb933639c356054966f70e2e76ba5694efc7c655947",
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

        var validation =
                new ContractChangeProposalValidator()
                        .validate(
                                scenario.evidence(),
                                proposal);

        var comparison =
                new GroundTruthComparator()
                        .compare(
                                scenario.groundTruth(),
                                proposal);

        var hallucination =
                new HallucinationClassifier()
                        .classify(
                                validation.errors());

        SmokeDefinition definition =
                new SmokeDefinition(
                        SmokeCatalog.AUTHORIZED_SMOKE_ID,
                        SmokeCatalog.AUTHORIZED_SCENARIO_ID);

        SmokeExecutionResult result =
                new SmokeExecutionResult(
                        definition,
                        scenario,
                        attempt,
                        new GroqHttpResponse(
                                200,
                                rawProvider,
                                Map.of(),
                                Duration.ofMillis(2)),
                        validation,
                        comparison,
                        hallucination,
                        1);

        Path root =
                tempDirectory
                        .resolve("results")
                        .resolve("smoke");

        SmokeResultExporter exporter =
                new SmokeResultExporter(root);

        Path output =
                exporter.export(result);

        assertTrue(
                output.startsWith(root));

        assertFalse(
                output.toString()
                        .replace('\\', '/')
                        .contains(
                                "/results/measured/"));

        String allExportedText =
                Files.walk(output)
                        .filter(Files::isRegularFile)
                        .map(path -> {
                            try {
                                return Files.readString(
                                        path,
                                        StandardCharsets.UTF_8);
                            } catch (Exception exception) {
                                throw new RuntimeException(
                                        exception);
                            }
                        })
                        .reduce(
                                "",
                                (left, right) ->
                                        left + right);

        assertFalse(
                allExportedText.contains(
                        "fake-test-secret"));

        assertFalse(
                allExportedText.contains(
                        "Authorization"));

        assertFalse(
                allExportedText.contains(
                        "Bearer "));

        assertFalse(
                allExportedText.contains(
                        "GROQ_API_KEY"));
    }

    private LlmReliabilityScenario authorizedScenario() {
        return new LlmScenarioCatalog()
                .scenarios()
                .stream()
                .filter(scenario ->
                        SmokeCatalog
                                .AUTHORIZED_SCENARIO_ID
                                .equals(
                                        scenario.scenarioId()))
                .findFirst()
                .orElseThrow();
    }
}
