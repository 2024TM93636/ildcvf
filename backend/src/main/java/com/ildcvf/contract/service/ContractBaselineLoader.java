package com.ildcvf.contract.service;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class ContractBaselineLoader {

    private final ContractBaseline baseline;

    public ContractBaselineLoader(
            @Value("${ildcvf.contract.baseline-path}") String baselinePath) {
        this.baseline = load(Path.of(baselinePath));
    }

    public ContractBaseline getBaseline() {
        return baseline;
    }

    private ContractBaseline load(Path path) {
        Path normalizedPath = path.toAbsolutePath().normalize();

        if (!Files.isRegularFile(normalizedPath)) {
            throw new IllegalStateException(
                    "OpenAPI baseline file not found: " + normalizedPath);
        }

        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);

        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(normalizedPath.toString(), null, options);

        OpenAPI openApi = result.getOpenAPI();

        if (openApi == null) {
            throw new IllegalStateException(
                    "Unable to parse OpenAPI baseline: "
                            + String.join("; ", result.getMessages()));
        }

        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            throw new IllegalStateException(
                    "OpenAPI baseline contains parser errors: "
                            + String.join("; ", result.getMessages()));
        }

        if (openApi.getPaths() == null) {
            throw new IllegalStateException(
                    "OpenAPI baseline contains no paths: " + normalizedPath);
        }

        List<ContractOperation> operations = new ArrayList<>();

        openApi.getPaths().forEach((pathTemplate, pathItem) ->
                extractOperations(pathTemplate, pathItem, operations));

        operations.sort(
                Comparator.comparing(ContractOperation::pathTemplate)
                        .thenComparing(ContractOperation::method));

        return new ContractBaseline(
                openApi.getOpenapi(),
                operations,
                openApi);
    }

    private void extractOperations(
            String pathTemplate,
            PathItem pathItem,
            List<ContractOperation> operations) {

        for (Map.Entry<PathItem.HttpMethod, Operation> entry
                : pathItem.readOperationsMap().entrySet()) {

            Operation operation = entry.getValue();

            operations.add(new ContractOperation(
                    pathTemplate,
                    entry.getKey().name(),
                    operation == null ? null : operation.getOperationId()));
        }
    }
}