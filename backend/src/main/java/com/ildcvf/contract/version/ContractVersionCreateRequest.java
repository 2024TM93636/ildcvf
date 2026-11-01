package com.ildcvf.contract.version;

import java.util.UUID;

public record ContractVersionCreateRequest(
        UUID proposalAttemptId
) {}
