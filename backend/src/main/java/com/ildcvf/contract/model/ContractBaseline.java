package com.ildcvf.contract.model;

import io.swagger.v3.oas.models.OpenAPI;

import java.util.List;
import java.util.Objects;

public record ContractBaseline(
        String openApiVersion,
        List<ContractOperation> operations,
        OpenAPI openApi) {

    public ContractBaseline {
        Objects.requireNonNull(openApiVersion, "openApiVersion must not be null");
        Objects.requireNonNull(operations, "operations must not be null");
        Objects.requireNonNull(openApi, "openApi must not be null");

        operations = List.copyOf(operations);
    }
}