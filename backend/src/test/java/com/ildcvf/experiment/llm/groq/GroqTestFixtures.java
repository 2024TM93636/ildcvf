package com.ildcvf.experiment.llm.groq;

import java.time.Duration;
import java.util.Map;

final class GroqTestFixtures {

    static final String FAKE_SECRET =
            "fake-e6-test-credential";

    private GroqTestFixtures() {
    }

    static String validProposalJson() {
        return """
                {
                  "proposalId": "fake-proposal-001",
                  "sourceTrafficEventId": "traffic-00000002",
                  "sourceFindingId": "fake-finding-001",
                  "driftType": "UNEXPECTED_REQUEST_FIELD",
                  "changeKind": "ADD_REQUEST_FIELD",
                  "method": "POST",
                  "runtimePath": "/api/residents",
                  "matchedOpenApiPath": "/api/residents",
                  "targetFieldPath": "nickname",
                  "expected": null,
                  "observed": "string",
                  "proposedValue": "string",
                  "rationale": "Runtime evidence contains the field."
                }
                """;
    }

    static String validProviderResponse() {
        String escapedProposal =
                escapeJson(validProposalJson());

        return """
                {
                  "id": "fake-response-001",
                  "model": "openai/gpt-oss-20b",
                  "system_fingerprint": "fake-system-fingerprint",
                  "service_tier": "on_demand",
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": {
                        "role": "assistant",
                        "content": "%s"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 321,
                    "completion_tokens": 84,
                    "total_tokens": 405
                  }
                }
                """.formatted(escapedProposal);
    }

    static String responseWithoutOptionalMetadata() {
        String escapedProposal =
                escapeJson(validProposalJson());

        return """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "%s"
                      }
                    }
                  ]
                }
                """.formatted(escapedProposal);
    }

    static String emptyContentResponse() {
        return """
                {
                  "id": "fake-empty-content",
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": {
                        "content": ""
                      }
                    }
                  ]
                }
                """;
    }

    static String malformedAssistantJsonResponse() {
        return """
                {
                  "id": "fake-malformed-assistant",
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": {
                        "content": "{not-json"
                      }
                    }
                  ]
                }
                """;
    }

    static String incompatibleAssistantJsonResponse() {
        return """
                {
                  "id": "fake-incompatible-assistant",
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": {
                        "content": "{\\"proposalId\\":123}"
                      }
                    }
                  ]
                }
                """;
    }

    static GroqHttpResponse response(
            int status,
            String body
    ) {
        return new GroqHttpResponse(
                status,
                body,
                Map.of(),
                Duration.ofMillis(12)
        );
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}