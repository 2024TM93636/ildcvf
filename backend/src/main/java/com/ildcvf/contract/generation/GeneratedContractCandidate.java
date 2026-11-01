package com.ildcvf.contract.generation;

public record GeneratedContractCandidate(String baselineIdentifier, String baselineSha256,
                                         String openapiYaml, String documentSha256,
                                         CandidateValidationResult validation) {}
