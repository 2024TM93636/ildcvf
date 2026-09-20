package com.ildcvf.experiment.llm.compatibility;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import com.ildcvf.experiment.llm.evaluation.AttemptEvaluation;
import com.ildcvf.experiment.llm.evaluation.AttemptEvaluator;
import com.ildcvf.experiment.llm.groq.ContractChangeProposalParser;
import com.ildcvf.experiment.llm.groq.GroqApiKeyResolver;
import com.ildcvf.experiment.llm.groq.GroqConfiguration;
import com.ildcvf.experiment.llm.groq.GroqExperimentClient;
import com.ildcvf.experiment.llm.groq.GroqHttpResponse;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.experiment.llm.groq.GroqProviderResponseParser;
import com.ildcvf.experiment.llm.groq.GroqRequestBuilder;
import com.ildcvf.experiment.llm.groq.GroqTransport;
import com.ildcvf.experiment.llm.groq.JdkGroqTransport;
import com.ildcvf.experiment.llm.smoke.SingleUseRecordingGroqTransport;
import com.ildcvf.llm.evidence.EvidenceFingerprint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class TokenBudgetCompatibilityRunner {

    private final List<LlmReliabilityScenario> scenarios;
    private final TokenBudgetCompatibilityResultExporter exporter;
    private final AttemptEvaluator evaluator;

    public TokenBudgetCompatibilityRunner() {
        this(
                new LlmScenarioCatalog().scenarios(),
                new TokenBudgetCompatibilityResultExporter(),
                new AttemptEvaluator()
        );
    }

    TokenBudgetCompatibilityRunner(
            List<LlmReliabilityScenario> scenarios,
            TokenBudgetCompatibilityResultExporter exporter,
            AttemptEvaluator evaluator
    ) {
        this.scenarios =
                List.copyOf(
                        Objects.requireNonNull(
                                scenarios));

        this.exporter =
                Objects.requireNonNull(
                        exporter);

        this.evaluator =
                Objects.requireNonNull(
                        evaluator);
    }

    public LlmReliabilityScenario scenario() {
        LlmReliabilityScenario scenario =
                scenarios.stream()
                        .filter(candidate ->
                                TokenBudgetCompatibilityConfiguration
                                        .SCENARIO_ID
                                        .equals(
                                                candidate
                                                        .scenarioId()))
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Frozen compatibility "
                                                + "scenario unavailable."));

        String actualFingerprint =
                EvidenceFingerprint.sha256(
                        scenario.evidence());

        if (!TokenBudgetCompatibilityConfiguration
                .EXPECTED_EVIDENCE_FINGERPRINT
                .equals(actualFingerprint)) {

            throw new IllegalStateException(
                    "Compatibility evidence fingerprint changed.");
        }

        if (!actualFingerprint.equals(
                scenario.evidenceFingerprint())) {

            throw new IllegalStateException(
                    "Scenario fingerprint does not match evidence.");
        }

        return scenario;
    }

    public String baselineRequest() {
        return new GroqRequestBuilder()
                .build(
                        TokenBudgetCompatibilityConfiguration
                                .baseline(),
                        scenario().evidence());
    }

    public String compatibilityRequest() {
        return new GroqRequestBuilder()
                .build(
                        TokenBudgetCompatibilityConfiguration
                                .compatibility(),
                        scenario().evidence());
    }

    public void verifyOnlyTokenBudgetDiffers() {
        ObjectMapper mapper =
                new ObjectMapper();

        JsonNode baseline =
                mapper.readTree(
                        baselineRequest());

        JsonNode compatibility =
                mapper.readTree(
                        compatibilityRequest());

        if (baseline == null
                || compatibility == null
                || !baseline.isObject()
                || !compatibility.isObject()) {

            throw new IllegalStateException(
                    "Requests are not JSON objects.");
        }

        int baselineTokens =
                baseline.get(
                                "max_completion_tokens")
                        .asInt();

        int compatibilityTokens =
                compatibility.get(
                                "max_completion_tokens")
                        .asInt();

        if (baselineTokens
                != TokenBudgetCompatibilityConfiguration
                .BASELINE_MAX_COMPLETION_TOKENS) {

            throw new IllegalStateException(
                    "Baseline request is not 512 tokens.");
        }

        if (compatibilityTokens
                != TokenBudgetCompatibilityConfiguration
                .COMPATIBILITY_MAX_COMPLETION_TOKENS) {

            throw new IllegalStateException(
                    "Compatibility request is not 2048 tokens.");
        }

        ((tools.jackson.databind.node.ObjectNode) baseline)
                .remove("max_completion_tokens");

        ((tools.jackson.databind.node.ObjectNode) compatibility)
                .remove("max_completion_tokens");

        if (!baseline.equals(compatibility)) {
            throw new IllegalStateException(
                    "Compatibility request changes more than "
                            + "max_completion_tokens.");
        }

    }

    public CompatibilityExecution executeOffline(
            GroqProviderAttempt attempt,
            SingleUseRecordingGroqTransport transport
    ) throws IOException {

        Objects.requireNonNull(attempt);
        Objects.requireNonNull(transport);

        verifyOnlyTokenBudgetDiffers();

        if (transport.invocationCount() != 1) {
            throw new IllegalStateException(
                    "Offline compatibility proof requires "
                            + "exactly one fake transport invocation.");
        }

        LlmReliabilityScenario scenario =
                scenario();

        if (attempt.metadata()
                .maxCompletionTokens()
                != TokenBudgetCompatibilityConfiguration
                .COMPATIBILITY_MAX_COMPLETION_TOKENS) {

            throw new IllegalStateException(
                    "Provider attempt is not the 2048-token variant.");
        }

        AttemptEvaluation evaluation =
                evaluator.evaluate(
                        scenario,
                        1,
                        attempt);

        return new CompatibilityExecution(
                scenario,
                attempt,
                transport.lastResponse(),
                evaluation,
                transport.invocationCount(),
                0);
    }

    public CompatibilityExecution executeRealExactlyOnce()
            throws IOException {

        verifyOnlyTokenBudgetDiffers();

        Path directory =
                exporter.outputDirectory();

        if (Files.exists(directory)) {
            throw new IllegalStateException(
                    "Compatibility result directory already exists. "
                            + "Refusing to repeat or overwrite the call.");
        }

        LlmReliabilityScenario scenario =
                scenario();

        SingleUseRecordingGroqTransport transport =
                new SingleUseRecordingGroqTransport(
                        new JdkGroqTransport());

        GroqExperimentClient client =
                new GroqExperimentClient(
                        TokenBudgetCompatibilityConfiguration
                                .compatibility(),
                        new GroqApiKeyResolver(),
                        new GroqRequestBuilder(),
                        transport,
                        new GroqProviderResponseParser(),
                        new ContractChangeProposalParser());

        GroqProviderAttempt attempt =
                client.generate(
                        scenario.evidence());

        if (transport.invocationCount() != 1) {
            throw new IllegalStateException(
                    "Compatibility execution did not perform "
                            + "exactly one provider invocation.");
        }

        AttemptEvaluation evaluation =
                evaluator.evaluate(
                        scenario,
                        1,
                        attempt);

        CompatibilityExecution result =
                new CompatibilityExecution(
                        scenario,
                        attempt,
                        transport.lastResponse(),
                        evaluation,
                        transport.invocationCount(),
                        0);

        exporter.export(
                scenario,
                attempt,
                transport.lastResponse(),
                evaluation,
                transport.invocationCount(),
                0);

        return result;
    }

    public record CompatibilityExecution(
            LlmReliabilityScenario scenario,
            GroqProviderAttempt providerAttempt,
            GroqHttpResponse rawHttpResponse,
            AttemptEvaluation evaluation,
            int providerInvocationCount,
            int retryCount
    ) {
        public CompatibilityExecution {
            Objects.requireNonNull(scenario);
            Objects.requireNonNull(providerAttempt);
            Objects.requireNonNull(evaluation);

            if (providerInvocationCount != 1) {
                throw new IllegalArgumentException(
                        "Exactly one provider invocation required.");
            }

            if (retryCount != 0) {
                throw new IllegalArgumentException(
                        "Retries are forbidden.");
            }
        }
    }
}
