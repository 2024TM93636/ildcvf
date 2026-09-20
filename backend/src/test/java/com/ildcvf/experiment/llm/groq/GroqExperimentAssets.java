package com.ildcvf.experiment.llm.groq;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.proposal.ContractChangeKind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

public final class GroqExperimentAssets {

    public static final String FROZEN_PROMPT_SHA256 =
            "d9ee37eddddff37d75ecdeb933639c356054966f70e2e76ba5694efc7c655947";

    private static final Path ROOT =
            Path.of(
                    "..",
                    "experiments",
                    "e6-llm-reliability");

    private static final Path PROMPT =
            ROOT.resolve("prompts")
                    .resolve("proposal-v1.txt");

    private static final Path PROMPT_HASH =
            ROOT.resolve("prompts")
                    .resolve("proposal-v1.sha256");

    private static final Path SCHEMA =
            ROOT.resolve("schema")
                    .resolve(
                            "contract-change-proposal.schema.json");

    private final ObjectMapper objectMapper;

    public GroqExperimentAssets() {
        this(new ObjectMapper());
    }

    GroqExperimentAssets(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String loadVerifiedPrompt() {
        try {
            byte[] bytes =
                    Files.readAllBytes(PROMPT);

            String actualHash =
                    sha256(bytes);

            String committedHash =
                    Files.readString(
                                    PROMPT_HASH,
                                    StandardCharsets.UTF_8)
                            .trim();

            if (!FROZEN_PROMPT_SHA256.equals(committedHash)) {
                throw new IllegalStateException(
                        "Committed prompt hash differs from "
                                + "the frozen E6-A hash.");
            }

            if (!actualHash.equals(committedHash)) {
                throw new IllegalStateException(
                        "Frozen E6 prompt failed SHA-256 verification.");
            }

            return new String(
                    bytes,
                    StandardCharsets.UTF_8);

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read frozen E6 prompt artifacts.",
                    exception);
        }
    }

    public JsonNode loadVerifiedSchema() {
        try {
            JsonNode schema =
                    objectMapper.readTree(
                            Files.readString(
                                    SCHEMA,
                                    StandardCharsets.UTF_8));

            validateSchema(schema);

            return schema;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read frozen E6 schema artifact.",
                    exception);
        }
    }

    private void validateSchema(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            throw new IllegalStateException(
                    "Frozen proposal schema must be a JSON object.");
        }

        if (!"object".equals(
                text(schema, "type"))) {
            throw new IllegalStateException(
                    "Frozen proposal schema root type must be object.");
        }

        JsonNode additionalProperties =
                schema.get("additionalProperties");

        if (additionalProperties == null
                || !additionalProperties.isBoolean()
                || additionalProperties.asBoolean()) {
            throw new IllegalStateException(
                    "Frozen proposal schema must set "
                            + "additionalProperties=false.");
        }

        JsonNode properties =
                schema.get("properties");

        JsonNode required =
                schema.get("required");

        if (properties == null
                || !properties.isObject()
                || properties.size() != 13) {
            throw new IllegalStateException(
                    "Frozen proposal schema must contain "
                            + "exactly 13 properties.");
        }

        if (required == null
                || !required.isArray()
                || required.size() != 13) {
            throw new IllegalStateException(
                    "Frozen proposal schema must contain "
                            + "exactly 13 required fields.");
        }

        Set<String> propertyNames =
                new HashSet<>(properties.propertyNames());

        Set<String> requiredNames =
                new HashSet<>();

        required.forEach(node ->
                requiredNames.add(node.asText()));

        if (!propertyNames.equals(requiredNames)) {
            throw new IllegalStateException(
                    "Frozen proposal schema properties and "
                            + "required fields differ.");
        }

        Set<String> expectedDriftTypes =
                Arrays.stream(DriftType.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet());

        Set<String> expectedChangeKinds =
                Arrays.stream(ContractChangeKind.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet());

        if (!expectedDriftTypes.equals(
                enumValues(
                        properties.get("driftType")))) {
            throw new IllegalStateException(
                    "Frozen driftType enum set is invalid.");
        }

        if (!expectedChangeKinds.equals(
                enumValues(
                        properties.get("changeKind")))) {
            throw new IllegalStateException(
                    "Frozen changeKind enum set is invalid.");
        }
    }

    private Set<String> enumValues(JsonNode property) {
        if (property == null) {
            return Set.of();
        }

        JsonNode values =
                property.get("enum");

        if (values == null || !values.isArray()) {
            return Set.of();
        }

        Set<String> result =
                new HashSet<>();

        values.forEach(node ->
                result.add(node.asText()));

        return result;
    }

    private String text(
            JsonNode object,
            String field
    ) {
        JsonNode value =
                object.get(field);

        return value == null || value.isNull()
                ? null
                : value.asText();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest
                                    .getInstance("SHA-256")
                                    .digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception);
        }
    }
}