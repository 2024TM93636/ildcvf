package com.ildcvf.experiment.llm.groq;

public enum ProviderCallStatus {
    SUCCESS,
    TIMEOUT,
    TRANSPORT_FAILURE,
    HTTP_4XX,
    HTTP_5XX,
    EMPTY_PROVIDER_RESPONSE,
    PROVIDER_RESPONSE_PARSE_FAILURE,
    PROPOSAL_PARSE_FAILURE
}