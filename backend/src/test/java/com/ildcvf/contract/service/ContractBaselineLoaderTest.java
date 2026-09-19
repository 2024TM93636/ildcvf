package com.ildcvf.contract.service;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContractBaselineLoaderTest {

    @Test
    void loadsFrozenResidentBaselineWithExactlyFiveOperations() {
        Path baselinePath = Path.of(
                "..",
                "datasets",
                "openapi",
                "baseline",
                "resident-api-v1.yaml");

        ContractBaselineLoader loader =
                new ContractBaselineLoader(baselinePath.toString());

        ContractBaseline baseline = loader.getBaseline();

        assertNotNull(baseline);
        assertNotNull(baseline.openApi());

        assertEquals("3.1.0", baseline.openApiVersion());
        assertEquals(5, baseline.operations().size());

        Set<String> actualOperations = baseline.operations().stream()
                .map(this::operationKey)
                .collect(Collectors.toSet());

        Set<String> expectedOperations = Set.of(
                "POST /api/residents",
                "GET /api/residents",
                "GET /api/residents/{id}",
                "PUT /api/residents/{id}",
                "DELETE /api/residents/{id}");

        assertEquals(expectedOperations, actualOperations);
    }

    private String operationKey(ContractOperation operation) {
        return operation.method() + " " + operation.pathTemplate();
    }
}