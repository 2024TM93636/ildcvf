package com.ildcvf.experiment.llm.groq;

public enum ProposalParseStatus {
    SUCCESS,
    EMPTY_CONTENT,
    MALFORMED_JSON,
    STRUCTURE_MAPPING_FAILURE
}