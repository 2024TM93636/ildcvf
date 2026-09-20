package com.ildcvf.experiment.llm.groq;

import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.evidence.RelevantContractContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;

public final class GroqRequestBuilder {

    private static final String SCHEMA_NAME =
            "contract_change_proposal";

    private final ObjectMapper objectMapper;
    private final GroqExperimentAssets assets;

    public GroqRequestBuilder() {
        this(
                new ObjectMapper(),
                new GroqExperimentAssets());
    }

    GroqRequestBuilder(
            ObjectMapper objectMapper,
            GroqExperimentAssets assets
    ) {
        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper must not be null");

        this.assets =
                Objects.requireNonNull(
                        assets,
                        "assets must not be null");
    }

    public String build(
            GroqConfiguration configuration,
            GroundedEvidencePackage evidence
    ) {
        Objects.requireNonNull(
                configuration,
                "configuration must not be null");

        Objects.requireNonNull(
                evidence,
                "evidence must not be null");

        String prompt =
                assets.loadVerifiedPrompt();

        JsonNode schema =
                assets.loadVerifiedSchema();

        ObjectNode root =
                objectMapper.createObjectNode();

        root.put(
                "model",
                configuration.model());

        var messages =
                root.putArray("messages");

        ObjectNode systemMessage =
                messages.addObject();

        systemMessage.put(
                "role",
                "system");

        systemMessage.put(
                "content",
                prompt);

        ObjectNode userMessage =
                messages.addObject();

        userMessage.put(
                "role",
                "user");

        userMessage.put(
                "content",
                evidenceJson(evidence));

        root.put(
                "temperature",
                configuration.temperature());

        root.put(
                "top_p",
                configuration.topP());

        root.put(
                "seed",
                configuration.seed());

        root.put(
                "max_completion_tokens",
                configuration.maxCompletionTokens());

        root.put(
                "stream",
                false);

        root.put(
                "service_tier",
                configuration.serviceTier());

        ObjectNode responseFormat =
                root.putObject("response_format");

        responseFormat.put(
                "type",
                "json_schema");

        ObjectNode jsonSchema =
                responseFormat.putObject("json_schema");

        jsonSchema.put(
                "name",
                SCHEMA_NAME);

        jsonSchema.put(
                "strict",
                true);

        jsonSchema.set(
                "schema",
                schema.deepCopy());

        return objectMapper.writeValueAsString(root);
    }

    private String evidenceJson(
            GroundedEvidencePackage evidence
    ) {
        ObjectNode root =
                objectMapper.createObjectNode();

        putNullable(
                root,
                "trafficEventId",
                evidence.trafficEventId());

        root.put(
                "trafficSequence",
                evidence.trafficSequence());

        putNullable(
                root,
                "findingId",
                evidence.findingId());

        putNullable(
                root,
                "driftType",
                evidence.driftType() == null
                        ? null
                        : evidence.driftType().name());

        putNullable(
                root,
                "severity",
                evidence.severity() == null
                        ? null
                        : evidence.severity().name());

        putNullable(
                root,
                "method",
                evidence.method());

        putNullable(
                root,
                "runtimePath",
                evidence.runtimePath());

        putNullable(
                root,
                "matchedOpenApiPath",
                evidence.matchedOpenApiPath());

        putNullable(
                root,
                "fieldPath",
                evidence.fieldPath());

        putNullable(
                root,
                "expected",
                evidence.expected());

        putNullable(
                root,
                "observed",
                evidence.observed());

        putNullable(
                root,
                "findingMessage",
                evidence.findingMessage());

        RelevantContractContext context =
                evidence.relevantContractContext();

        if (context == null) {
            root.putNull("relevantContractContext");
        } else {
            ObjectNode contextNode =
                    root.putObject(
                            "relevantContractContext");

            putNullable(
                    contextNode,
                    "openApiVersion",
                    context.openApiVersion());

            putNullable(
                    contextNode,
                    "method",
                    context.method());

            putNullable(
                    contextNode,
                    "pathTemplate",
                    context.pathTemplate());

            putNullable(
                    contextNode,
                    "operationId",
                    context.operationId());
        }

        return objectMapper.writeValueAsString(root);
    }

    private void putNullable(
            ObjectNode node,
            String name,
            String value
    ) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, value);
        }
    }
}