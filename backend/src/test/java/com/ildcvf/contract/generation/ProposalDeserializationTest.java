package com.ildcvf.contract.generation;

import com.ildcvf.llm.proposal.ContractChangeProposal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class ProposalDeserializationTest {

    @Test
    void deserializesStoredProposalJson() {
        ObjectMapper mapper = new ObjectMapper();

        String json = """
                {
                  "changeKind": "CHANGE_RESPONSE_FIELD_TYPE",
                  "method": "GET",
                  "matchedOpenApiPath": "/api/residents/{id}",
                  "targetFieldPath": "id",
                  "expected": "integer",
                  "observed": "string",
                  "proposedValue": "string"
                }
                """;

        ContractChangeProposal proposal =
                mapper.treeToValue(
                        mapper.readTree(json),
                        ContractChangeProposal.class
                );

        assertNotNull(proposal);
        assertEquals("GET", proposal.method());
        assertEquals("/api/residents/{id}", proposal.matchedOpenApiPath());
        assertEquals("id", proposal.targetFieldPath());
        assertEquals("integer", proposal.expected());
        assertEquals("string", proposal.observed());
        assertEquals("string", proposal.proposedValue());
    }
}
