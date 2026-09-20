package com.ildcvf.experiment.llm.groq;

import com.ildcvf.llm.proposal.ContractChangeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroqParsingTest {

    @Test
    void validProviderEnvelopeExtractsMetadata() {
        GroqProviderEnvelope envelope =
                new GroqProviderResponseParser()
                        .parse(
                                GroqTestFixtures
                                        .validProviderResponse());

        assertEquals(
                "fake-response-001",
                envelope.providerResponseId());

        assertEquals(
                "openai/gpt-oss-20b",
                envelope.returnedModel());

        assertEquals(
                "fake-system-fingerprint",
                envelope.systemFingerprint());

        assertEquals(
                "on_demand",
                envelope.serviceTier());

        assertEquals(
                "stop",
                envelope.finishReason());

        assertEquals(
                321L,
                envelope.inputTokens());

        assertEquals(
                84L,
                envelope.outputTokens());

        assertEquals(
                405L,
                envelope.totalTokens());
    }

    @Test
    void optionalProviderMetadataMayBeAbsent() {
        GroqProviderEnvelope envelope =
                new GroqProviderResponseParser()
                        .parse(
                                GroqTestFixtures
                                        .responseWithoutOptionalMetadata());

        assertNull(
                envelope.providerResponseId());

        assertNull(
                envelope.returnedModel());

        assertNull(
                envelope.systemFingerprint());

        assertNull(
                envelope.inputTokens());

        assertNull(
                envelope.outputTokens());

        assertNull(
                envelope.totalTokens());
    }

    @Test
    void malformedProviderEnvelopeFailsClearly() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GroqProviderResponseParser()
                                .parse("{broken"));
    }

    @Test
    void validProposalMapsToExistingM7Proposal() {
        ProposalParseResult result =
                new ContractChangeProposalParser()
                        .parse(
                                GroqTestFixtures
                                        .validProposalJson());

        assertEquals(
                ProposalParseStatus.SUCCESS,
                result.status());

        assertEquals(
                ContractChangeKind.ADD_REQUEST_FIELD,
                result.proposal().changeKind());

        assertEquals(
                "nickname",
                result.proposal().targetFieldPath());

        assertEquals(
                "string",
                result.proposal().proposedValue());
    }

    @Test
    void emptyProposalContentIsClassified() {
        ProposalParseResult result =
                new ContractChangeProposalParser()
                        .parse(" ");

        assertEquals(
                ProposalParseStatus.EMPTY_CONTENT,
                result.status());
    }

    @Test
    void malformedProposalJsonIsClassified() {
        ProposalParseResult result =
                new ContractChangeProposalParser()
                        .parse("{not-json");

        assertEquals(
                ProposalParseStatus.MALFORMED_JSON,
                result.status());
    }

    @Test
    void incompatibleProposalJsonIsClassified() {
        ProposalParseResult result =
                new ContractChangeProposalParser()
                        .parse(
                                """
                                {
                                  "proposalId": 123
                                }
                                """);

        assertEquals(
                ProposalParseStatus.STRUCTURE_MAPPING_FAILURE,
                result.status());
    }

    @Test
    void emptyAssistantContentRemainsSeparateFromEnvelopeParsing() {
        GroqProviderEnvelope envelope =
                new GroqProviderResponseParser()
                        .parse(
                                GroqTestFixtures
                                        .emptyContentResponse());

        ProposalParseResult result =
                new ContractChangeProposalParser()
                        .parse(
                                envelope.assistantContent());

        assertEquals(
                ProposalParseStatus.EMPTY_CONTENT,
                result.status());
    }
}