package com.ildcvf.experiment.llm.groq;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroqTransportDiagnosticSemanticsTest {

    @Test
    void transportFailurePreservesExistingProviderSemantics() {
        LlmReliabilityScenario scenario =
                new LlmScenarioCatalog()
                        .scenarios()
                        .getFirst();

        GroqConfiguration configuration =
                GroqConfiguration.frozenPreliminary();

        GroqTransport transport =
                new GroqTransport() {
                    @Override
                    public GroqHttpResponse post(
                            URI endpoint,
                            String authorizationSecret,
                            String requestBody,
                            Duration timeout
                    ) throws GroqTransportException {
                        throw new GroqTransportException(
                                GroqTransportException
                                        .Kind.TRANSPORT_FAILURE,
                                "must-not-be-retained",
                                new IOException(
                                        "sensitive-host-value"
                                )
                        );
                    }
                };

        GroqExperimentClient client =
                new GroqExperimentClient(
                        configuration,
                        new GroqApiKeyResolver(
                                () -> Map.of(
                                        GroqApiKeyResolver
                                                .ENVIRONMENT_VARIABLE,
                                        "synthetic-test-key"
                                )
                        ),
                        new GroqRequestBuilder(),
                        transport,
                        new GroqProviderResponseParser(),
                        new ContractChangeProposalParser()
                );

        GroqProviderAttempt result =
                client.generate(
                        scenario.evidence()
                );

        assertEquals(
                ProviderCallStatus.TRANSPORT_FAILURE,
                result.status()
        );

        assertEquals(
                "Provider transport failed.",
                result.diagnostic()
        );

        assertNotNull(
                result.transportDiagnostic()
        );

        assertEquals(
                TransportDiagnosticCode.OTHER_IO_FAILURE,
                result.transportDiagnostic()
                        .transportDiagnosticCode()
        );

        assertNull(
                result.metadata().httpStatus()
        );
    }
}