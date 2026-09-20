package com.ildcvf.experiment.llm.smoke;

import com.ildcvf.experiment.llm.evaluation.FieldComparison;
import com.ildcvf.experiment.llm.evaluation.GroundTruthComparison;
import com.ildcvf.experiment.llm.evaluation.HallucinationEvaluation;
import com.ildcvf.experiment.llm.groq.GroqConfiguration;
import com.ildcvf.experiment.llm.groq.GroqExperimentAssets;
import com.ildcvf.experiment.llm.groq.GroqProviderAttempt;
import com.ildcvf.experiment.llm.groq.GroqProviderEnvelope;
import com.ildcvf.experiment.llm.groq.ProposalParseResult;
import com.ildcvf.experiment.llm.groq.ProviderAttemptMetadata;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.validation.ProposalValidationError;
import com.ildcvf.llm.validation.ProposalValidationResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class SmokeResultExporter {

    public static final String PROMPT_VERSION =
            "proposal-v1";

    private final Path smokeResultsRoot;
    private final ObjectMapper objectMapper;

    public SmokeResultExporter() {
        this(
                Path.of(
                        "..",
                        "experiments",
                        "e6-llm-reliability",
                        "results",
                        "smoke"),
                new ObjectMapper());
    }

    SmokeResultExporter(
            Path smokeResultsRoot
    ) {
        this(
                smokeResultsRoot,
                new ObjectMapper());
    }

    SmokeResultExporter(
            Path smokeResultsRoot,
            ObjectMapper objectMapper
    ) {
        this.smokeResultsRoot =
                Objects.requireNonNull(
                                smokeResultsRoot,
                                "smokeResultsRoot must not be null")
                        .normalize();

        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper must not be null");
    }

    public Path export(
            SmokeExecutionResult result
    ) throws IOException {

        Objects.requireNonNull(
                result,
                "result must not be null");

        Path directory =
                outputDirectory(
                        result.definition());

        Files.createDirectories(directory);

        ObjectNode summary =
                createSummary(result);

        writeJson(
                directory.resolve("result.json"),
                summary);

        writeRawProviderResponse(
                directory,
                result);

        writeAssistantContent(
                directory,
                result);

        writeParsedProposal(
                directory,
                result);

        writeValidation(
                directory,
                result);

        writeGroundTruth(
                directory,
                result);

        writeHallucination(
                directory,
                result);

        return directory;
    }

    public Path outputDirectory(
            SmokeDefinition definition
    ) {
        Objects.requireNonNull(
                definition,
                "definition must not be null");

        if (!SmokeCatalog.AUTHORIZED_SMOKE_ID.equals(
                definition.smokeId())) {
            throw new IllegalArgumentException(
                    "Exporter accepts only the "
                            + "authorized E6-D2 smoke.");
        }

        Path result =
                smokeResultsRoot
                        .resolve(
                                definition.smokeId())
                        .normalize();

        if (!result.startsWith(smokeResultsRoot)) {
            throw new IllegalStateException(
                    "Smoke export escaped results/smoke.");
        }

        String normalized =
                result.toString()
                        .replace('\\', '/');

        if (normalized.contains("/results/measured/")
                || normalized.endsWith(
                "/results/measured")) {
            throw new IllegalStateException(
                    "Measured-results path is prohibited.");
        }

        return result;
    }

    private ObjectNode createSummary(
            SmokeExecutionResult result
    ) {
        GroqProviderAttempt attempt =
                result.providerAttempt();

        ProviderAttemptMetadata metadata =
                attempt.metadata();

        GroqProviderEnvelope envelope =
                attempt.envelope();

        ProposalParseResult parse =
                attempt.proposalParseResult();

        GroqConfiguration configuration =
                GroqConfiguration.frozenPreliminary();

        ObjectNode root =
                objectMapper.createObjectNode();

        root.put(
                "smokeId",
                result.definition().smokeId());

        root.put(
                "scenarioId",
                result.definition().scenarioId());

        root.put(
                "evidenceFingerprint",
                result.scenario()
                        .evidenceFingerprint());

        root.put(
                "promptVersion",
                PROMPT_VERSION);

        root.put(
                "promptSha256",
                GroqExperimentAssets
                        .FROZEN_PROMPT_SHA256);

        root.put(
                "provider",
                metadata.provider());

        root.put(
                "requestedModel",
                metadata.requestedModel());

        putNullable(
                root,
                "returnedModel",
                metadata.returnedModel());

        putNullable(
                root,
                "systemFingerprint",
                metadata.systemFingerprint());

        putNullable(
                root,
                "serviceTier",
                metadata.serviceTier());

        root.put(
                "temperature",
                configuration.temperature());

        root.put(
                "topP",
                configuration.topP());

        root.put(
                "seed",
                configuration.seed());

        root.put(
                "maxCompletionTokens",
                configuration.maxCompletionTokens());

        root.put(
                "timeoutMilliseconds",
                configuration.timeout().toMillis());

        putNullable(
                root,
                "timestamp",
                metadata.timestamp() == null
                        ? null
                        : metadata.timestamp().toString());

        putNullable(
                root,
                "httpStatus",
                metadata.httpStatus());

        putNullable(
                root,
                "providerResponseId",
                envelope == null
                        ? null
                        : envelope.providerResponseId());

        root.put(
                "providerCallStatus",
                attempt.status().name());

        putNullable(
                root,
                "proposalParseStatus",
                parse == null
                        ? null
                        : parse.status().name());

        root.put(
                "infrastructurePassed",
                result.infrastructurePassed());

        root.put(
                "providerInvocationCount",
                result.providerInvocationCount());

        root.put(
                "retryCount",
                0);

        putNullable(
                root,
                "latencyMilliseconds",
                metadata.latency() == null
                        ? null
                        : metadata.latency()
                        .toNanos()
                        / 1_000_000.0d);

        putNullable(
                root,
                "inputTokens",
                metadata.inputTokens());

        putNullable(
                root,
                "outputTokens",
                metadata.outputTokens());

        putNullable(
                root,
                "totalTokens",
                metadata.totalTokens());

        putNullable(
                root,
                "finishReason",
                metadata.finishReason());

        putNullable(
                root,
                "diagnostic",
                attempt.diagnostic());

        root.put(
                "measuredCampaignAttempt",
                false);

        root.put(
                "authorizationHeaderSerialized",
                false);

        root.put(
                "apiKeySerialized",
                false);

        return root;
    }

    private void writeRawProviderResponse(
            Path directory,
            SmokeExecutionResult result
    ) throws IOException {

        String raw = null;

        if (result.providerAttempt().envelope() != null) {
            raw =
                    result.providerAttempt()
                            .envelope()
                            .rawProviderResponse();
        }

        if (raw == null
                && result.rawHttpResponse() != null) {
            raw =
                    result.rawHttpResponse().body();
        }

        writeNullableText(
                directory.resolve(
                        "raw-provider-response.json"),
                raw);
    }

    private void writeAssistantContent(
            Path directory,
            SmokeExecutionResult result
    ) throws IOException {

        String content =
                result.providerAttempt().envelope()
                        == null
                        ? null
                        : result.providerAttempt()
                        .envelope()
                        .assistantContent();

        writeNullableText(
                directory.resolve(
                        "assistant-content.txt"),
                content);
    }

    private void writeParsedProposal(
            Path directory,
            SmokeExecutionResult result
    ) throws IOException {

        ContractChangeProposal proposal =
                result.providerAttempt()
                        .proposalParseResult()
                        == null
                        ? null
                        : result.providerAttempt()
                        .proposalParseResult()
                        .proposal();

        ObjectNode root =
                objectMapper.createObjectNode();

        if (proposal == null) {
            root.putNull("proposal");
        } else {
            root.set(
                    "proposal",
                    proposalNode(proposal));
        }

        writeJson(
                directory.resolve(
                        "parsed-proposal.json"),
                root);
    }

    private void writeValidation(
            Path directory,
            SmokeExecutionResult result
    ) throws IOException {

        ObjectNode root =
                objectMapper.createObjectNode();

        ProposalValidationResult validation =
                result.validationResult();

        if (validation == null) {
            root.put(
                    "applicability",
                    "NOT_APPLICABLE");

            root.putNull("accepted");

            root.putArray("codes");
            root.putArray("errors");
        } else {
            root.put(
                    "applicability",
                    "APPLICABLE");

            root.put(
                    "accepted",
                    validation.valid());

            ArrayNode codes =
                    root.putArray("codes");

            ArrayNode errors =
                    root.putArray("errors");

            for (ProposalValidationError error
                    : validation.errors()) {

                codes.add(
                        error.code().name());

                ObjectNode errorNode =
                        errors.addObject();

                errorNode.put(
                        "code",
                        error.code().name());

                errorNode.put(
                        "message",
                        error.message());
            }
        }

        writeJson(
                directory.resolve(
                        "validation.json"),
                root);
    }

    private void writeGroundTruth(
            Path directory,
            SmokeExecutionResult result
    ) throws IOException {

        ObjectNode root =
                objectMapper.createObjectNode();

        GroundTruthComparison comparison =
                result.groundTruthComparison();

        if (comparison == null) {
            root.put(
                    "applicability",
                    "NOT_APPLICABLE");

            root.putNull("exactMatch");
            root.putNull("matchedFieldCount");
            root.put(
                    "totalFieldCount",
                    11);

            root.putArray(
                    "fieldComparisons");
        } else {
            root.put(
                    "applicability",
                    "APPLICABLE");

            root.put(
                    "exactMatch",
                    comparison.exactMatch());

            root.put(
                    "matchedFieldCount",
                    comparison.matchedFieldCount());

            root.put(
                    "totalFieldCount",
                    comparison.totalFieldCount());

            root.put(
                    "fieldAccuracy",
                    comparison.fieldAccuracy());

            ArrayNode fields =
                    root.putArray(
                            "fieldComparisons");

            for (FieldComparison field
                    : comparison.fields()) {

                ObjectNode node =
                        fields.addObject();

                node.put(
                        "field",
                        field.field().name());

                putNullable(
                        node,
                        "expected",
                        field.expected());

                putNullable(
                        node,
                        "actual",
                        field.actual());

                node.put(
                        "match",
                        field.match());
            }
        }

        writeJson(
                directory.resolve(
                        "ground-truth-comparison.json"),
                root);
    }

    private void writeHallucination(
            Path directory,
            SmokeExecutionResult result
    ) throws IOException {

        ObjectNode root =
                objectMapper.createObjectNode();

        HallucinationEvaluation hallucination =
                result.hallucinationEvaluation();

        if (hallucination == null) {
            root.put(
                    "applicability",
                    "NOT_APPLICABLE");

            root.putNull("hallucinated");

            root.putArray(
                    "reasonCodes");
        } else {
            root.put(
                    "applicability",
                    hallucination
                            .applicability()
                            .name());

            if (hallucination.hallucinated()
                    == null) {
                root.putNull("hallucinated");
            } else {
                root.put(
                        "hallucinated",
                        hallucination.hallucinated());
            }

            ArrayNode reasons =
                    root.putArray(
                            "reasonCodes");

            hallucination.reasonCodes()
                    .forEach(reason ->
                            reasons.add(
                                    reason.name()));
        }

        writeJson(
                directory.resolve(
                        "hallucination.json"),
                root);
    }

    private ObjectNode proposalNode(
            ContractChangeProposal proposal
    ) {
        ObjectNode node =
                objectMapper.createObjectNode();

        node.put(
                "proposalId",
                proposal.proposalId());

        node.put(
                "sourceTrafficEventId",
                proposal.sourceTrafficEventId());

        node.put(
                "sourceFindingId",
                proposal.sourceFindingId());

        node.put(
                "driftType",
                proposal.driftType().name());

        node.put(
                "changeKind",
                proposal.changeKind().name());

        node.put(
                "method",
                proposal.method());

        node.put(
                "runtimePath",
                proposal.runtimePath());

        putNullable(
                node,
                "matchedOpenApiPath",
                proposal.matchedOpenApiPath());

        putNullable(
                node,
                "targetFieldPath",
                proposal.targetFieldPath());

        putNullable(
                node,
                "expected",
                proposal.expected());

        putNullable(
                node,
                "observed",
                proposal.observed());

        putNullable(
                node,
                "proposedValue",
                proposal.proposedValue());

        node.put(
                "rationale",
                proposal.rationale());

        return node;
    }

    private void writeJson(
            Path path,
            ObjectNode node
    ) throws IOException {

        Files.writeString(
                path,
                node.toPrettyString()
                        + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    private void writeNullableText(
            Path path,
            String value
    ) throws IOException {

        Files.writeString(
                path,
                value == null
                        ? "null"
                        + System.lineSeparator()
                        : value,
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

    private void putNullable(
            ObjectNode node,
            String field,
            Double value
    ) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
