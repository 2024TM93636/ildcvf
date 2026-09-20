package com.ildcvf.experiment.llm.groq;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class GroqProviderResponseParser {

    private final ObjectMapper objectMapper;

    public GroqProviderResponseParser() {
        this(new ObjectMapper());
    }

    GroqProviderResponseParser(
            ObjectMapper objectMapper
    ) {
        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper must not be null");
    }

    public GroqProviderEnvelope parse(
            String rawProviderResponse
    ) {
        if (rawProviderResponse == null
                || rawProviderResponse.isBlank()) {
            throw new IllegalArgumentException(
                    "Provider response body must not be blank.");
        }

        final JsonNode root;

        try {
            root =
                    objectMapper.readTree(
                            rawProviderResponse);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Provider response is not valid JSON.",
                    exception);
        }

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(
                    "Provider response root must be a JSON object.");
        }

        String content = null;
        String finishReason = null;

        JsonNode choices =
                root.get("choices");

        if (choices != null
                && choices.isArray()
                && !choices.isEmpty()) {

            JsonNode first =
                    choices.get(0);

            finishReason =
                    nullableText(
                            first,
                            "finish_reason");

            JsonNode message =
                    first.get("message");

            if (message != null
                    && message.isObject()) {
                content =
                        nullableText(
                                message,
                                "content");
            }
        }

        JsonNode usage =
                root.get("usage");

        Long promptTokens =
                nullableLong(
                        usage,
                        "prompt_tokens");

        Long completionTokens =
                nullableLong(
                        usage,
                        "completion_tokens");

        Long totalTokens =
                nullableLong(
                        usage,
                        "total_tokens");

        return new GroqProviderEnvelope(
                nullableText(root, "id"),
                nullableText(root, "model"),
                nullableText(
                        root,
                        "system_fingerprint"),
                nullableText(
                        root,
                        "service_tier"),
                finishReason,
                promptTokens,
                completionTokens,
                totalTokens,
                content,
                rawProviderResponse
        );
    }

    private String nullableText(
            JsonNode node,
            String field
    ) {
        if (node == null) {
            return null;
        }

        JsonNode value =
                node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private Long nullableLong(
            JsonNode node,
            String field
    ) {
        if (node == null) {
            return null;
        }

        JsonNode value =
                node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        if (!value.isNumber()) {
            return null;
        }

        return value.asLong();
    }
}