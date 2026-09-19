package com.ildcvf.contract.model;

import java.util.Locale;

public record ContractOperation(
        String pathTemplate,
        String method,
        String operationId) {

    public ContractOperation {
        if (pathTemplate == null || pathTemplate.isBlank()) {
            throw new IllegalArgumentException("pathTemplate must not be blank");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }

        method = method.toUpperCase(Locale.ROOT);
    }
}