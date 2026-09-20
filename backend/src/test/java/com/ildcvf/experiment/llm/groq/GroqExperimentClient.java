package com.ildcvf.experiment.llm.groq;

import com.ildcvf.llm.evidence.EvidenceFingerprint;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;

import java.time.Instant;
import java.util.Objects;

public final class GroqExperimentClient {

    private static final String PROVIDER = "groq";

    private final GroqConfiguration configuration;
    private final GroqApiKeyResolver apiKeyResolver;
    private final GroqRequestBuilder requestBuilder;
    private final GroqTransport transport;
    private final GroqProviderResponseParser responseParser;
    private final ContractChangeProposalParser proposalParser;
    private final TransportDiagnosticClassifier
            transportDiagnosticClassifier;

    public GroqExperimentClient(
            GroqConfiguration configuration,
            GroqApiKeyResolver apiKeyResolver,
            GroqRequestBuilder requestBuilder,
            GroqTransport transport,
            GroqProviderResponseParser responseParser,
            ContractChangeProposalParser proposalParser
    ) {
        this.configuration =
                Objects.requireNonNull(configuration);

        this.apiKeyResolver =
                Objects.requireNonNull(apiKeyResolver);

        this.requestBuilder =
                Objects.requireNonNull(requestBuilder);

        this.transport =
                Objects.requireNonNull(transport);

        this.responseParser =
                Objects.requireNonNull(responseParser);

        this.proposalParser =
                Objects.requireNonNull(proposalParser);

        this.transportDiagnosticClassifier =
                new TransportDiagnosticClassifier();
    }

    public GroqProviderAttempt generate(
            GroundedEvidencePackage evidence
    ) {
        Objects.requireNonNull(
                evidence,
                "evidence must not be null");

        Instant timestamp =
                Instant.now();

        String evidenceSha256 =
                EvidenceFingerprint.sha256(evidence);

        String requestBody =
                requestBuilder.build(
                        configuration,
                        evidence);

        String secret =
                apiKeyResolver.resolve();

        final GroqHttpResponse response;

        try {
            response =
                    transport.post(
                            configuration.endpoint(),
                            secret,
                            requestBody,
                            configuration.timeout());

        } catch (GroqTransportException exception) {
            ProviderCallStatus status =
                    exception.kind()
                            == GroqTransportException.Kind.TIMEOUT
                            ? ProviderCallStatus.TIMEOUT
                            : ProviderCallStatus.TRANSPORT_FAILURE;

            TransportDiagnostic transportDiagnostic =
                    transportDiagnosticClassifier.classify(
                            exception
                    );

            return new GroqProviderAttempt(
                    status,
                    baseMetadata(
                            evidenceSha256,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            timestamp),
                    null,
                    null,
                    exception.kind()
                            == GroqTransportException.Kind.TIMEOUT
                            ? "Provider request timed out."
                            : "Provider transport failed.",
                    transportDiagnostic
            );
        }

        if (response.statusCode() >= 400
                && response.statusCode() <= 499) {
            return new GroqProviderAttempt(
                    ProviderCallStatus.HTTP_4XX,
                    baseMetadata(
                            evidenceSha256,
                            response.statusCode(),
                            response.latency(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            timestamp),
                    null,
                    null,
                    "Provider returned HTTP "
                            + response.statusCode()
                            + "."
            );
        }

        if (response.statusCode() >= 500) {
            return new GroqProviderAttempt(
                    ProviderCallStatus.HTTP_5XX,
                    baseMetadata(
                            evidenceSha256,
                            response.statusCode(),
                            response.latency(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            timestamp),
                    null,
                    null,
                    "Provider returned HTTP "
                            + response.statusCode()
                            + "."
            );
        }

        if (response.body().isBlank()) {
            return new GroqProviderAttempt(
                    ProviderCallStatus.EMPTY_PROVIDER_RESPONSE,
                    baseMetadata(
                            evidenceSha256,
                            response.statusCode(),
                            response.latency(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            timestamp),
                    null,
                    null,
                    "Provider response body is empty."
            );
        }

        final GroqProviderEnvelope envelope;

        try {
            envelope =
                    responseParser.parse(
                            response.body());
        } catch (RuntimeException exception) {
            return new GroqProviderAttempt(
                    ProviderCallStatus
                            .PROVIDER_RESPONSE_PARSE_FAILURE,
                    baseMetadata(
                            evidenceSha256,
                            response.statusCode(),
                            response.latency(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            timestamp),
                    null,
                    null,
                    "Provider response envelope could not be parsed."
            );
        }

        ProviderAttemptMetadata metadata =
                baseMetadata(
                        evidenceSha256,
                        response.statusCode(),
                        response.latency(),
                        envelope.returnedModel(),
                        envelope.systemFingerprint(),
                        envelope.serviceTier(),
                        envelope.inputTokens(),
                        envelope.outputTokens(),
                        envelope.totalTokens(),
                        timestamp,
                        envelope.finishReason());

        ProposalParseResult proposalResult =
                proposalParser.parse(
                        envelope.assistantContent());

        if (proposalResult.status()
                != ProposalParseStatus.SUCCESS) {
            return new GroqProviderAttempt(
                    ProviderCallStatus.PROPOSAL_PARSE_FAILURE,
                    metadata,
                    envelope,
                    proposalResult,
                    "Assistant proposal could not be parsed."
            );
        }

        return new GroqProviderAttempt(
                ProviderCallStatus.SUCCESS,
                metadata,
                envelope,
                proposalResult,
                null
        );
    }

    private ProviderAttemptMetadata baseMetadata(
            String evidenceSha256,
            Integer httpStatus,
            java.time.Duration latency,
            String returnedModel,
            String systemFingerprint,
            String serviceTier,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            Instant timestamp
    ) {
        return baseMetadata(
                evidenceSha256,
                httpStatus,
                latency,
                returnedModel,
                systemFingerprint,
                serviceTier,
                inputTokens,
                outputTokens,
                totalTokens,
                timestamp,
                null);
    }

    private ProviderAttemptMetadata baseMetadata(
            String evidenceSha256,
            Integer httpStatus,
            java.time.Duration latency,
            String returnedModel,
            String systemFingerprint,
            String serviceTier,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            Instant timestamp,
            String finishReason
    ) {
        return new ProviderAttemptMetadata(
                PROVIDER,
                configuration.model(),
                returnedModel,
                systemFingerprint,
                serviceTier == null
                        ? configuration.serviceTier()
                        : serviceTier,
                configuration.temperature(),
                configuration.topP(),
                configuration.seed(),
                configuration.maxCompletionTokens(),
                GroqExperimentAssets.FROZEN_PROMPT_SHA256,
                evidenceSha256,
                httpStatus,
                latency,
                inputTokens,
                outputTokens,
                totalTokens,
                finishReason,
                timestamp
        );
    }
}