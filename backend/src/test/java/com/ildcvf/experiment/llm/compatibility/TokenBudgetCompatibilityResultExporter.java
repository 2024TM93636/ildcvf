package com.ildcvf.experiment.llm.compatibility;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.evaluation.AttemptEvaluation;
import com.ildcvf.experiment.llm.evaluation.EvaluationApplicability;
import com.ildcvf.experiment.llm.groq.GroqHttpResponse;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.experiment.llm.groq.GroqProviderEnvelope;
import com.ildcvf.experiment.llm.groq.ProposalParseResult;
import com.ildcvf.experiment.llm.groq.ProviderAttemptMetadata;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class TokenBudgetCompatibilityResultExporter {

    private final Path compatibilityResultsRoot;
    private final ObjectMapper objectMapper;

    public TokenBudgetCompatibilityResultExporter() {
        this(
                Path.of(
                        "..",
                        "experiments",
                        "e6-llm-reliability",
                        "results",
                        "compatibility"),
                new ObjectMapper()
        );
    }

    TokenBudgetCompatibilityResultExporter(
            Path compatibilityResultsRoot
    ) {
        this(
                compatibilityResultsRoot,
                new ObjectMapper()
        );
    }

    TokenBudgetCompatibilityResultExporter(
            Path compatibilityResultsRoot,
            ObjectMapper objectMapper
    ) {
        this.compatibilityResultsRoot =
                Objects.requireNonNull(
                                compatibilityResultsRoot,
                                "compatibilityResultsRoot must not be null")
                        .normalize();

        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper must not be null");
    }

    public Path outputDirectory() {
        return compatibilityResultsRoot
                .resolve(
                        TokenBudgetCompatibilityConfiguration
                                .COMPATIBILITY_TEST_ID)
                .normalize();
    }

    public Path export(
            LlmReliabilityScenario scenario,
            GroqProviderAttempt attempt,
            GroqHttpResponse rawResponse,
            AttemptEvaluation evaluation,
            int providerInvocationCount,
            int retryCount
    ) throws IOException {

        Objects.requireNonNull(scenario);
        Objects.requireNonNull(attempt);
        Objects.requireNonNull(evaluation);

        if (!TokenBudgetCompatibilityConfiguration.SCENARIO_ID
                .equals(scenario.scenarioId())) {
            throw new IllegalArgumentException(
                    "Unexpected compatibility scenario.");
        }

        if (providerInvocationCount != 1) {
            throw new IllegalArgumentException(
                    "Compatibility smoke must contain exactly "
                            + "one provider invocation.");
        }

        if (retryCount != 0) {
            throw new IllegalArgumentException(
                    "Compatibility smoke does not permit retries.");
        }

        Path directory = outputDirectory();

        /*
         * Never permit this exporter to target the consumed
         * Smoke A namespace.
         */
        String normalized =
                directory.toString()
                        .replace('\\', '/');

        if (normalized.contains("/results/smoke/")
                || normalized.endsWith("/results/smoke")) {
            throw new IllegalStateException(
                    "Compatibility result cannot use Smoke A namespace.");
        }

        Files.createDirectories(directory);

        writeJson(
                directory.resolve("result.json"),
                createSummary(
                        scenario,
                        attempt,
                        evaluation,
                        providerInvocationCount,
                        retryCount));

        writeRawProviderResponse(
                directory,
                rawResponse);

        writeAssistantContent(
                directory,
                attempt);

        writeParsedProposal(
                directory,
                attempt);

        writeValidation(
                directory,
                evaluation);

        writeGroundTruth(
                directory,
                evaluation);

        writeHallucination(
                directory,
                evaluation);

        return directory;
    }

    private ObjectNode createSummary(
            LlmReliabilityScenario scenario,
            GroqProviderAttempt attempt,
            AttemptEvaluation evaluation,
            int providerInvocationCount,
            int retryCount
    ) {
        ProviderAttemptMetadata metadata =
                attempt.metadata();

        ObjectNode node =
                objectMapper.createObjectNode();

        node.put(
                "compatibilityTestId",
                TokenBudgetCompatibilityConfiguration
                        .COMPATIBILITY_TEST_ID);

        node.put(
                "configurationLabel",
                TokenBudgetCompatibilityConfiguration
                        .CONFIGURATION_LABEL);

        node.put(
                "scenarioId",
                scenario.scenarioId());

        node.put(
                "purpose",
                TokenBudgetCompatibilityConfiguration.PURPOSE);

        node.put(
                "evidenceFingerprint",
                scenario.evidenceFingerprint());

        node.put(
                "provider",
                metadata.provider());

        node.put(
                "requestedModel",
                metadata.requestedModel());

        putNullable(
                node,
                "returnedModel",
                metadata.returnedModel());

        putNullable(
                node,
                "systemFingerprint",
                metadata.systemFingerprint());

        putNullable(
                node,
                "serviceTier",
                metadata.serviceTier());

        node.put(
                "temperature",
                metadata.temperature());

        node.put(
                "topP",
                metadata.topP());

        node.put(
                "seed",
                metadata.seed());

        node.put(
                "maxCompletionTokens",
                metadata.maxCompletionTokens());

        node.put(
                "timeoutMilliseconds",
                TokenBudgetCompatibilityConfiguration
                        .compatibility()
                        .timeout()
                        .toMillis());

        node.put(
                "promptVersion",
                TokenBudgetCompatibilityConfiguration
                        .PROMPT_VERSION);

        node.put(
                "promptSha256",
                metadata.promptSha256());

        putNullable(
                node,
                "httpStatus",
                metadata.httpStatus());

        node.put(
                "providerCallStatus",
                attempt.status().name());

        if (attempt.proposalParseResult() == null) {
            node.putNull("proposalParseStatus");
        } else {
            node.put(
                    "proposalParseStatus",
                    attempt.proposalParseResult()
                            .status()
                            .name());
        }

        node.put(
                "infrastructurePassed",
                infrastructurePassed(attempt));

        node.put(
                "providerInvocationCount",
                providerInvocationCount);

        node.put(
                "retryCount",
                retryCount);

        if (metadata.latency() == null) {
            node.putNull("latencyMilliseconds");
        } else {
            node.put(
                    "latencyMilliseconds",
                    metadata.latency()
                            .toNanos()
                            / 1_000_000.0d);
        }

        putNullable(
                node,
                "inputTokens",
                metadata.inputTokens());

        putNullable(
                node,
                "outputTokens",
                metadata.outputTokens());

        putNullable(
                node,
                "totalTokens",
                metadata.totalTokens());

        putNullable(
                node,
                "finishReason",
                metadata.finishReason());

        node.put(
                "validatorApplicability",
                evaluation.validator()
                        .applicability()
                        .name());

        if (evaluation.validator()
                .applicability()
                == EvaluationApplicability.APPLICABLE) {

            node.put(
                    "validatorAccepted",
                    evaluation.validator()
                            .accepted());

            ArrayNode codes =
                    node.putArray(
                            "validatorCodes");

            evaluation.validator()
                    .codes()
                    .forEach(code ->
                            codes.add(code.name()));
        } else {
            node.putNull("validatorAccepted");
            node.putArray("validatorCodes");
        }

        node.put(
                "groundTruthApplicability",
                evaluation.groundTruth()
                        .applicability()
                        .name());

        if (evaluation.groundTruth()
                .applicability()
                == EvaluationApplicability.APPLICABLE) {

            node.put(
                    "groundTruthExactMatch",
                    evaluation.groundTruth()
                            .comparison()
                            .exactMatch());
        } else {
            node.putNull(
                    "groundTruthExactMatch");
        }

        node.put(
                "hallucinationApplicability",
                evaluation.hallucination()
                        .applicability()
                        .name());

        if (evaluation.hallucination()
                .applicability()
                == EvaluationApplicability.APPLICABLE) {

            node.put(
                    "hallucinated",
                    evaluation.hallucination()
                            .hallucinated());
        } else {
            node.putNull("hallucinated");
        }

        node.put(
                "baselineComparisonMaxCompletionTokens",
                TokenBudgetCompatibilityConfiguration
                        .BASELINE_MAX_COMPLETION_TOKENS);

        node.put(
                "compatibilityMaxCompletionTokens",
                TokenBudgetCompatibilityConfiguration
                        .COMPATIBILITY_MAX_COMPLETION_TOKENS);

        node.put(
                "changedVariable",
                TokenBudgetCompatibilityConfiguration
                        .CHANGED_VARIABLE);

        node.put(
                "measuredCampaignAttempt",
                false);

        node.put(
                "authorizationHeaderSerialized",
                false);

        node.put(
                "apiKeySerialized",
                false);

        putNullable(
                node,
                "diagnostic",
                attempt.diagnostic());

        return node;
    }

    private boolean infrastructurePassed(
            GroqProviderAttempt attempt
    ) {
        return attempt.envelope() != null
                && attempt.metadata()
                .httpStatus() != null
                && attempt.metadata()
                .httpStatus() >= 200
                && attempt.metadata()
                .httpStatus() < 300
                && attempt.envelope()
                .assistantContent() != null;
    }

    private void writeRawProviderResponse(
            Path directory,
            GroqHttpResponse response
    ) throws IOException {

        String body =
                response == null
                        ? ""
                        : response.body();

        Files.writeString(
                directory.resolve(
                        "raw-provider-response.json"),
                body == null ? "" : body,
                StandardCharsets.UTF_8);
    }

    private void writeAssistantContent(
            Path directory,
            GroqProviderAttempt attempt
    ) throws IOException {

        GroqProviderEnvelope envelope =
                attempt.envelope();

        String content =
                envelope == null
                        ? ""
                        : envelope.assistantContent();

        Files.writeString(
                directory.resolve(
                        "assistant-content.txt"),
                content == null ? "" : content,
                StandardCharsets.UTF_8);
    }

    private void writeParsedProposal(
            Path directory,
            GroqProviderAttempt attempt
    ) throws IOException {

        ObjectNode node =
                objectMapper.createObjectNode();

        ProposalParseResult parseResult =
                attempt.proposalParseResult();

        if (parseResult == null) {
            node.put(
                    "applicability",
                    "NOT_APPLICABLE");
        } else {
            node.put(
                    "applicability",
                    "APPLICABLE");

            node.put(
                    "status",
                    parseResult.status()
                            .name());

            ContractChangeProposal proposal =
                    parseResult.proposal();

            if (proposal == null) {
                node.putNull("proposal");
            } else {
                node.set(
                        "proposal",
                        objectMapper.valueToTree(
                                proposal));
            }
        }

        writeJson(
                directory.resolve(
                        "parsed-proposal.json"),
                node);
    }

    private void writeValidation(
            Path directory,
            AttemptEvaluation evaluation
    ) throws IOException {

        ObjectNode node =
                objectMapper.createObjectNode();

        node.put(
                "applicability",
                evaluation.validator()
                        .applicability()
                        .name());

        if (evaluation.validator()
                .applicability()
                == EvaluationApplicability.APPLICABLE) {

            node.put(
                    "accepted",
                    evaluation.validator()
                            .accepted());

            ArrayNode codes =
                    node.putArray("codes");

            evaluation.validator()
                    .codes()
                    .forEach(code ->
                            codes.add(code.name()));
        } else {
            node.putNull("accepted");
            node.putArray("codes");
        }

        writeJson(
                directory.resolve(
                        "validation.json"),
                node);
    }

    private void writeGroundTruth(
            Path directory,
            AttemptEvaluation evaluation
    ) throws IOException {

        ObjectNode node =
                objectMapper.createObjectNode();

        node.put(
                "applicability",
                evaluation.groundTruth()
                        .applicability()
                        .name());

        if (evaluation.groundTruth()
                .applicability()
                == EvaluationApplicability.APPLICABLE) {

            node.put(
                    "exactMatch",
                    evaluation.groundTruth()
                            .comparison()
                            .exactMatch());
        } else {
            node.putNull("exactMatch");
        }

        writeJson(
                directory.resolve(
                        "ground-truth-comparison.json"),
                node);
    }

    private void writeHallucination(
            Path directory,
            AttemptEvaluation evaluation
    ) throws IOException {

        ObjectNode node =
                objectMapper.createObjectNode();

        node.put(
                "applicability",
                evaluation.hallucination()
                        .applicability()
                        .name());

        if (evaluation.hallucination()
                .applicability()
                == EvaluationApplicability.APPLICABLE) {

            node.put(
                    "hallucinated",
                    evaluation.hallucination()
                            .hallucinated());

            ArrayNode reasons =
                    node.putArray(
                            "reasonCodes");

            evaluation.hallucination()
                    .reasonCodes()
                    .forEach(reason ->
                            reasons.add(
                                    reason.name()));
        } else {
            node.putNull("hallucinated");
            node.putArray("reasonCodes");
        }

        writeJson(
                directory.resolve(
                        "hallucination.json"),
                node);
    }

    private void writeJson(
            Path path,
            ObjectNode node
    ) throws IOException {

        Files.writeString(
                path,
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(node)
                        + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    private void putNullable(
            ObjectNode node,
            String field,
            String value
    ) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private void putNullable(
            ObjectNode node,
            String field,
            Integer value
    ) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private void putNullable(
            ObjectNode node,
            String field,
            Long value
    ) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
