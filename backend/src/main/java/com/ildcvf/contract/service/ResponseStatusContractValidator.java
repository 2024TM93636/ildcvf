package com.ildcvf.contract.service;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import com.ildcvf.contract.model.ResponseStatusValidationResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.traffic.domain.TrafficEvent;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class ResponseStatusContractValidator {

    public ResponseStatusValidationResult validate(
            TrafficEvent event,
            ContractOperation matchedOperation,
            ContractBaseline baseline) {

        Operation operation =
                resolveOperation(baseline, matchedOperation);

        if (operation == null) {
            return ResponseStatusValidationResult.partiallyEvaluated(
                    "Matched OpenAPI operation could not be resolved "
                            + "for response-status validation.");
        }

        ApiResponses responses = operation.getResponses();

        if (responses == null || responses.isEmpty()) {
            return ResponseStatusValidationResult.partiallyEvaluated(
                    "Matched OpenAPI operation has no documented "
                            + "response statuses.");
        }

        String runtimeStatus =
                Integer.toString(event.responseStatus());

        /*
         * Exact documented status takes precedence.
         */
        if (responses.containsKey(runtimeStatus)) {
            return ResponseStatusValidationResult.evaluated(
                    List.of());
        }

        /*
         * OpenAPI default response covers statuses that do not have
         * their own explicit response entry.
         */
        if (responses.containsKey("default")) {
            return ResponseStatusValidationResult.evaluated(
                    List.of());
        }

        String expected =
                responses.keySet().stream()
                        .sorted()
                        .collect(Collectors.joining("|"));

        DriftFinding finding =
                new DriftFinding(
                        event.eventId()
                                + ":"
                                + DriftType.UNDOCUMENTED_RESPONSE_STATUS.name()
                                + ":"
                                + runtimeStatus,
                        event.eventId(),
                        event.sequence(),
                        DriftType.UNDOCUMENTED_RESPONSE_STATUS,
                        DriftSeverity.WARNING,
                        event.method(),
                        event.requestPath(),
                        matchedOperation.pathTemplate(),
                        null,
                        expected,
                        runtimeStatus,
                        "Runtime response status is not documented "
                                + "for the matched OpenAPI operation.");

        return ResponseStatusValidationResult.evaluated(
                List.of(finding));
    }

    private Operation resolveOperation(
            ContractBaseline baseline,
            ContractOperation matchedOperation) {

        if (baseline.openApi() == null
                || baseline.openApi().getPaths() == null) {
            return null;
        }

        PathItem pathItem =
                baseline.openApi()
                        .getPaths()
                        .get(matchedOperation.pathTemplate());

        if (pathItem == null) {
            return null;
        }

        PathItem.HttpMethod method;

        try {
            method = PathItem.HttpMethod.valueOf(
                    matchedOperation.method()
                            .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        return pathItem.readOperationsMap().get(method);
    }
}