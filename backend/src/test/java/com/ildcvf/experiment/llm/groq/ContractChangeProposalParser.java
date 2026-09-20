package com.ildcvf.experiment.llm.groq;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.proposal.ContractChangeKind;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class ContractChangeProposalParser {

    private final ObjectMapper objectMapper;

    public ContractChangeProposalParser() {
        this(new ObjectMapper());
    }

    ContractChangeProposalParser(
            ObjectMapper objectMapper
    ) {
        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper must not be null");
    }

    public ProposalParseResult parse(
            String assistantContent
    ) {
        if (assistantContent == null
                || assistantContent.isBlank()) {
            return ProposalParseResult.failure(
                    ProposalParseStatus.EMPTY_CONTENT,
                    "Assistant content is empty.");
        }

        final JsonNode root;

        try {
            root =
                    objectMapper.readTree(
                            assistantContent);
        } catch (RuntimeException exception) {
            return ProposalParseResult.failure(
                    ProposalParseStatus.MALFORMED_JSON,
                    "Assistant content is not valid JSON.");
        }

        if (root == null || !root.isObject()) {
            return ProposalParseResult.failure(
                    ProposalParseStatus.STRUCTURE_MAPPING_FAILURE,
                    "Assistant content must be a JSON object.");
        }

        try {
            ContractChangeProposal proposal =
                    new ContractChangeProposal(
                            requiredText(
                                    root,
                                    "proposalId"),
                            requiredText(
                                    root,
                                    "sourceTrafficEventId"),
                            requiredText(
                                    root,
                                    "sourceFindingId"),
                            DriftType.valueOf(
                                    requiredText(
                                            root,
                                            "driftType")),
                            ContractChangeKind.valueOf(
                                    requiredText(
                                            root,
                                            "changeKind")),
                            requiredText(
                                    root,
                                    "method"),
                            requiredText(
                                    root,
                                    "runtimePath"),
                            nullableText(
                                    root,
                                    "matchedOpenApiPath"),
                            nullableText(
                                    root,
                                    "targetFieldPath"),
                            nullableText(
                                    root,
                                    "expected"),
                            nullableText(
                                    root,
                                    "observed"),
                            nullableText(
                                    root,
                                    "proposedValue"),
                            requiredText(
                                    root,
                                    "rationale")
                    );

            return ProposalParseResult.success(
                    proposal);

        } catch (RuntimeException exception) {
            return ProposalParseResult.failure(
                    ProposalParseStatus.STRUCTURE_MAPPING_FAILURE,
                    "Assistant JSON cannot map to "
                            + "ContractChangeProposal.");
        }
    }

    private String requiredText(
            JsonNode root,
            String field
    ) {
        JsonNode value =
                root.get(field);

        if (value == null
                || value.isNull()
                || !value.isTextual()
                || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or invalid field: " + field);
        }

        return value.asText();
    }

    private String nullableText(
            JsonNode root,
            String field
    ) {
        JsonNode value =
                root.get(field);

        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing field: " + field);
        }

        if (value.isNull()) {
            return null;
        }

        if (!value.isTextual()) {
            throw new IllegalArgumentException(
                    "Invalid field: " + field);
        }

        return value.asText();
    }
}