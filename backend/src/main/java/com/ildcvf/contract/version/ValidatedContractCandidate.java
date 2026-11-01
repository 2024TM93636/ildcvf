package com.ildcvf.contract.version;

import java.util.UUID;

/** M15.2 must validate the entire OpenAPI document before constructing this input.
 * This type is a trust-boundary marker, NOT proof of OpenAPI validation. */
public record ValidatedContractCandidate(String baselineIdentifier, String baselineSha256,
        UUID proposalAttemptId, UUID reviewDecisionId, String openapiYaml,
        String documentSha256) {}
