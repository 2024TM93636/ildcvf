package com.ildcvf.experiment.llm.groq;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroqExperimentClientTest {

    @Test
    void validFakeResponseProducesSuccessAndMetadata() {
        GroqProviderAttempt attempt =
                client(
                        successfulTransport(
                                200,
                                GroqTestFixtures
                                        .validProviderResponse()))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus.SUCCESS,
                attempt.status());

        assertNotNull(
                attempt.proposalParseResult());

        assertEquals(
                ProposalParseStatus.SUCCESS,
                attempt.proposalParseResult()
                        .status());

        assertEquals(
                "openai/gpt-oss-20b",
                attempt.metadata()
                        .returnedModel());

        assertEquals(
                "fake-system-fingerprint",
                attempt.metadata()
                        .systemFingerprint());

        assertEquals(
                321L,
                attempt.metadata()
                        .inputTokens());

        assertEquals(
                84L,
                attempt.metadata()
                        .outputTokens());

        assertEquals(
                405L,
                attempt.metadata()
                        .totalTokens());

        assertFalse(
                attempt.metadata()
                        .toString()
                        .contains(
                                GroqTestFixtures.FAKE_SECRET));
    }

    @Test
    void http400IsClassifiedAs4xx() {
        GroqProviderAttempt attempt =
                client(
                        successfulTransport(
                                400,
                                """
                                {"error":{"message":"fake bad request"}}
                                """))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus.HTTP_4XX,
                attempt.status());

        assertEquals(
                400,
                attempt.metadata().httpStatus());
    }

    @Test
    void http429Remains4xxAndPreservesStatus() {
        GroqProviderAttempt attempt =
                client(
                        successfulTransport(
                                429,
                                """
                                {"error":{"message":"fake rate limit"}}
                                """))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus.HTTP_4XX,
                attempt.status());

        assertEquals(
                429,
                attempt.metadata().httpStatus());
    }

    @Test
    void http500IsClassifiedAs5xx() {
        GroqProviderAttempt attempt =
                client(
                        successfulTransport(
                                500,
                                """
                                {"error":{"message":"fake server error"}}
                                """))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus.HTTP_5XX,
                attempt.status());

        assertEquals(
                500,
                attempt.metadata().httpStatus());
    }

    @Test
    void timeoutIsClassifiedWithoutSecretLeakage() {
        GroqProviderAttempt attempt =
                client(
                        failingTransport(
                                GroqTransportException.Kind.TIMEOUT))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus.TIMEOUT,
                attempt.status());

        assertNull(
                attempt.metadata().httpStatus());

        assertFalse(
                attempt.diagnostic()
                        .contains(
                                GroqTestFixtures.FAKE_SECRET));
    }

    @Test
    void transportFailureIsClassifiedWithoutSecretLeakage() {
        GroqProviderAttempt attempt =
                client(
                        failingTransport(
                                GroqTransportException.Kind
                                        .TRANSPORT_FAILURE))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus.TRANSPORT_FAILURE,
                attempt.status());

        assertFalse(
                attempt.diagnostic()
                        .contains(
                                GroqTestFixtures.FAKE_SECRET));
    }

    @Test
    void emptyProviderBodyIsClassified() {
        GroqProviderAttempt attempt =
                client(
                        successfulTransport(
                                200,
                                ""))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus.EMPTY_PROVIDER_RESPONSE,
                attempt.status());
    }

    @Test
    void malformedProviderEnvelopeIsClassified() {
        GroqProviderAttempt attempt =
                client(
                        successfulTransport(
                                200,
                                "{broken"))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus
                        .PROVIDER_RESPONSE_PARSE_FAILURE,
                attempt.status());
    }

    @Test
    void emptyAssistantContentIsProposalParseFailure() {
        GroqProviderAttempt attempt =
                client(
                        successfulTransport(
                                200,
                                GroqTestFixtures
                                        .emptyContentResponse()))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus.PROPOSAL_PARSE_FAILURE,
                attempt.status());

        assertEquals(
                ProposalParseStatus.EMPTY_CONTENT,
                attempt.proposalParseResult()
                        .status());
    }

    @Test
    void malformedAssistantJsonIsProposalParseFailure() {
        GroqProviderAttempt attempt =
                client(
                        successfulTransport(
                                200,
                                GroqTestFixtures
                                        .malformedAssistantJsonResponse()))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus.PROPOSAL_PARSE_FAILURE,
                attempt.status());

        assertEquals(
                ProposalParseStatus.MALFORMED_JSON,
                attempt.proposalParseResult()
                        .status());
    }

    @Test
    void incompatibleAssistantJsonIsProposalParseFailure() {
        GroqProviderAttempt attempt =
                client(
                        successfulTransport(
                                200,
                                GroqTestFixtures
                                        .incompatibleAssistantJsonResponse()))
                        .generate(evidence());

        assertEquals(
                ProviderCallStatus.PROPOSAL_PARSE_FAILURE,
                attempt.status());

        assertEquals(
                ProposalParseStatus.STRUCTURE_MAPPING_FAILURE,
                attempt.proposalParseResult()
                        .status());
    }

    @Test
    void fakeTransportReceivesFrozenEndpointAndNoNetworkIsRequired() {
        CapturingFakeTransport transport =
                new CapturingFakeTransport();

        client(transport)
                .generate(evidence());

        assertEquals(
                GroqConfiguration.FROZEN_ENDPOINT,
                transport.endpoint);

        assertEquals(
                GroqTestFixtures.FAKE_SECRET,
                transport.secret);

        assertNotNull(
                transport.requestBody);

        assertFalse(
                transport.requestBody
                        .contains(
                                GroqTestFixtures.FAKE_SECRET));

        assertEquals(
                Duration.ofSeconds(30),
                transport.timeout);
    }

    private GroqExperimentClient client(
            GroqTransport transport
    ) {
        return new GroqExperimentClient(
                GroqConfiguration
                        .frozenPreliminary(),
                new GroqApiKeyResolver(
                        () ->
                                Map.of(
                                        GroqApiKeyResolver
                                                .ENVIRONMENT_VARIABLE,
                                        GroqTestFixtures.FAKE_SECRET)),
                new GroqRequestBuilder(),
                transport,
                new GroqProviderResponseParser(),
                new ContractChangeProposalParser()
        );
    }

    private com.ildcvf.llm.evidence.GroundedEvidencePackage
    evidence() {
        LlmReliabilityScenario scenario =
                new LlmScenarioCatalog()
                        .scenarios()
                        .get(1);

        return scenario.evidence();
    }

    private GroqTransport successfulTransport(
            int status,
            String body
    ) {
        return (
                endpoint,
                authorizationSecret,
                requestBody,
                timeout
        ) ->
                GroqTestFixtures.response(
                        status,
                        body);
    }

    private GroqTransport failingTransport(
            GroqTransportException.Kind kind
    ) {
        return (
                endpoint,
                authorizationSecret,
                requestBody,
                timeout
        ) -> {
            throw new GroqTransportException(
                    kind,
                    "Synthetic E6-B transport failure.");
        };
    }

    private static final class CapturingFakeTransport
            implements GroqTransport {

        private URI endpoint;
        private String secret;
        private String requestBody;
        private Duration timeout;

        @Override
        public GroqHttpResponse post(
                URI endpoint,
                String authorizationSecret,
                String requestBody,
                Duration timeout
        ) {
            this.endpoint = endpoint;
            this.secret = authorizationSecret;
            this.requestBody = requestBody;
            this.timeout = timeout;

            return GroqTestFixtures.response(
                    200,
                    GroqTestFixtures
                            .validProviderResponse());
        }
    }
}