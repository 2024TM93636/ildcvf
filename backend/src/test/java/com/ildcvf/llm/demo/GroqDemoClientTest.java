package com.ildcvf.llm.demo;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.support.M7TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroqDemoClientTest {

    private static final Path FROZEN_ASSETS =
            Path.of("../experiments/e6-llm-reliability");

    @TempDir
    Path temporaryAssets;

    @Test
    void rejectsMissingFrozenAssetsWithoutHttpRequest() {
        HttpClient http = mock(HttpClient.class);
        GroqDemoClient client = new GroqDemoClient(temporaryAssets, http);

        GroqDemoClient.Outcome outcome =
                client.generate(testEvidence());

        assertEquals("PROVIDER_FAILURE", outcome.status());
        assertEquals("CONFIGURATION_FAILURE", outcome.providerStatus());
        assertNull(outcome.proposal());
        verifyNoInteractions(http);
    }

    @Test
    void reportsHttp503WithoutRetry() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mockResponse(503, "service unavailable");

        when(http.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        GroqDemoClient.Outcome outcome =
                new GroqDemoClient(FROZEN_ASSETS, http)
                        .generate(testEvidence());

        assertEquals("PROVIDER_FAILURE", outcome.status());
        assertEquals("HTTP_ERROR", outcome.providerStatus());
        assertEquals(503, outcome.httpStatus());

        verify(http, times(1)).send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        );
    }

    @Test
    void reportsTimeoutWithoutRetry() throws Exception {
        HttpClient http = mock(HttpClient.class);

        when(http.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenThrow(new HttpTimeoutException("simulated timeout"));

        GroqDemoClient.Outcome outcome =
                new GroqDemoClient(FROZEN_ASSETS, http)
                        .generate(testEvidence());

        assertEquals("TIMEOUT", outcome.status());
        assertEquals("TIMEOUT", outcome.providerStatus());

        verify(http, times(1)).send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        );
    }

    @Test
    void reportsTransportFailureWithoutRetry() throws Exception {
        HttpClient http = mock(HttpClient.class);

        when(http.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenThrow(new IOException("simulated network failure"));

        GroqDemoClient.Outcome outcome =
                new GroqDemoClient(FROZEN_ASSETS, http)
                        .generate(testEvidence());

        assertEquals("PROVIDER_FAILURE", outcome.status());
        assertEquals("TRANSPORT_FAILURE", outcome.providerStatus());

        verify(http, times(1)).send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        );
    }

    @Test
    void rejectsMalformedProviderResponseWithoutRetry() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mockResponse(
                200,
                "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}"
        );

        when(http.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        GroqDemoClient.Outcome outcome =
                new GroqDemoClient(FROZEN_ASSETS, http)
                        .generate(testEvidence());

        assertEquals("PARSE_FAILURE", outcome.status());
        assertNull(outcome.proposal());

        verify(http, times(1)).send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        );
    }

    private static GroundedEvidencePackage testEvidence() {
        return M7TestFixtures.evidence(
                DriftType.RESPONSE_TYPE_MISMATCH
        );
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(
            int status,
            String body
    ) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void parsesValidStructuredProposal() throws Exception {
        HttpClient http = mock(HttpClient.class);

        String proposal = """
                {
                  "proposalId": "offline-test-proposal",
                  "sourceTrafficEventId": "traffic-00000101",
                  "sourceFindingId": "traffic-00000101:RESPONSE_TYPE_MISMATCH:$.nickname",
                  "driftType": "RESPONSE_TYPE_MISMATCH",
                  "changeKind": "CHANGE_RESPONSE_FIELD_TYPE",
                  "method": "POST",
                  "runtimePath": "/api/residents",
                  "matchedOpenApiPath": "/api/residents",
                  "targetFieldPath": "$.nickname",
                  "expected": "field not documented",
                  "observed": "string",
                  "proposedValue": "string",
                  "rationale": "Offline parser verification"
                }
                """;

        var mapper = new tools.jackson.databind.ObjectMapper();

        var responseJson = mapper.createObjectNode();
        var choices = responseJson.putArray("choices");
        var choice = choices.addObject();
        var message = choice.putObject("message");

        message.put("content", proposal);

        String responseBody = mapper.writeValueAsString(responseJson);

        HttpResponse<String> response = mockResponse(200, responseBody);

        when(http.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        GroqDemoClient.Outcome outcome =
                new GroqDemoClient(FROZEN_ASSETS, http)
                        .generate(testEvidence());

        assertEquals("PARSED", outcome.status());
        assertEquals("SUCCESS", outcome.providerStatus());
        assertNotNull(outcome.proposal());
        assertEquals("offline-test-proposal", outcome.proposal().proposalId());

        verify(http, times(1)).send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        );
    }

    @Test
    void rejectsInvalidChangeKindAsParseFailure() throws Exception {
        HttpClient http = mock(HttpClient.class);

        String proposal = """
                {
                  "proposalId": "offline-invalid-enum",
                  "sourceTrafficEventId": "traffic-00000101",
                  "sourceFindingId": "traffic-00000101:RESPONSE_TYPE_MISMATCH:$.nickname",
                  "driftType": "RESPONSE_TYPE_MISMATCH",
                  "changeKind": "INVENTED_CHANGE_KIND",
                  "method": "POST",
                  "runtimePath": "/api/residents",
                  "matchedOpenApiPath": "/api/residents",
                  "targetFieldPath": "$.nickname",
                  "expected": "field not documented",
                  "observed": "string",
                  "proposedValue": "string",
                  "rationale": "Offline parser verification"
                }
                """;

        var mapper = new tools.jackson.databind.ObjectMapper();

        var responseJson = mapper.createObjectNode();
        var choices = responseJson.putArray("choices");
        var choice = choices.addObject();
        var message = choice.putObject("message");

        message.put("content", proposal);

        String responseBody = mapper.writeValueAsString(responseJson);

        HttpResponse<String> response = mockResponse(200, responseBody);

        when(http.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        GroqDemoClient.Outcome outcome =
                new GroqDemoClient(FROZEN_ASSETS, http)
                        .generate(testEvidence());

        assertEquals("PARSE_FAILURE", outcome.status());
        assertNull(outcome.proposal());

        verify(http, times(1)).send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        );
    }

    @Test
    void diagnosticChecksCredentialAndFrozenAssets() {
        assertNotNull(System.getenv("GROQ_API_KEY"),
                "GROQ_API_KEY is missing from the test JVM");

        assertFalse(System.getenv("GROQ_API_KEY").isBlank(),
                "GROQ_API_KEY is blank");

        assertTrue(Files.exists(FROZEN_ASSETS.resolve("prompts/proposal-v1.txt")));
        assertTrue(Files.exists(FROZEN_ASSETS.resolve("prompts/proposal-v1.sha256")));
        assertTrue(Files.exists(FROZEN_ASSETS.resolve("schema/contract-change-proposal.schema.json")));
    }
}