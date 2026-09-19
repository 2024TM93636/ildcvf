package com.ildcvf.contract.service;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import com.ildcvf.contract.model.RequestValidationResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.traffic.domain.TrafficEvent;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RequestContractValidator {

    private static final String APPLICATION_JSON = "application/json";
    private static final String LOCAL_SCHEMA_PREFIX =
            "#/components/schemas/";

    private static final Comparator<DriftFinding> FINDING_ORDER =
            Comparator.comparing(
                            (DriftFinding finding) ->
                                    finding.type().name())
                    .thenComparing(
                            finding -> finding.fieldPath() == null
                                    ? ""
                                    : finding.fieldPath());

    private final ObjectMapper objectMapper;

    public RequestContractValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RequestValidationResult validate(
            TrafficEvent event,
            ContractOperation matchedOperation,
            ContractBaseline baseline) {

        /*
         * No captured body means there is no runtime request-body
         * evidence to validate in Part C.
         */
        if (event.requestBody() == null) {
            return RequestValidationResult.evaluated(List.of());
        }

        /*
         * A truncated body is incomplete evidence. Do not infer
         * missing/type/enum findings from it.
         */
        if (event.requestBodyTruncated()) {
            return RequestValidationResult.partiallyEvaluated(
                    "Captured request body is truncated; "
                            + "request schema validation was not completed.");
        }

        /*
         * Content-type drift is outside Part C.
         */
        if (!isApplicationJson(event.requestContentType())) {
            return RequestValidationResult.evaluated(List.of());
        }

        Operation operation =
                resolveOperation(baseline, matchedOperation);

        if (operation == null) {
            return RequestValidationResult.partiallyEvaluated(
                    "Matched OpenAPI operation could not be resolved.");
        }

        Schema<?> requestSchema =
                resolveJsonRequestSchema(baseline, operation);

        if (requestSchema == null) {
            /*
             * A documented operation without an application/json request
             * schema remains successfully operation-level evaluated.
             */
            return RequestValidationResult.evaluated(List.of());
        }

        Schema<?> resolvedRequestSchema =
                resolveLocalSchema(baseline, requestSchema);

        if (resolvedRequestSchema == null) {
            return RequestValidationResult.partiallyEvaluated(
                    "Documented request schema reference could not be resolved.");
        }

        JsonNode root;

        try {
            root = objectMapper.readTree(event.requestBody());
        } catch (Exception ex) {
            return RequestValidationResult.partiallyEvaluated(
                    "Captured request body is not valid JSON; "
                            + "request schema validation was not completed.");
        }

        if (root == null || !root.isObject()) {
            return RequestValidationResult.partiallyEvaluated(
                    "Captured JSON request body is not an object; "
                            + "top-level request schema validation "
                            + "was not completed.");
        }

        List<DriftFinding> findings = new ArrayList<>();

        Map<String, Schema> documentedProperties =
                resolvedRequestSchema.getProperties() == null
                        ? Map.of()
                        : resolvedRequestSchema.getProperties();

        Set<String> requiredProperties =
                resolvedRequestSchema.getRequired() == null
                        ? Set.of()
                        : new LinkedHashSet<>(
                        resolvedRequestSchema.getRequired());

        detectUnexpectedFields(
                event,
                matchedOperation,
                root,
                documentedProperties,
                findings);

        detectMissingRequiredFields(
                event,
                matchedOperation,
                root,
                requiredProperties,
                findings);

        detectPropertyContractViolations(
                event,
                matchedOperation,
                baseline,
                root,
                documentedProperties,
                findings);

        findings.sort(FINDING_ORDER);

        return RequestValidationResult.evaluated(findings);
    }

    private Operation resolveOperation(
            ContractBaseline baseline,
            ContractOperation matchedOperation) {

        if (baseline.openApi().getPaths() == null) {
            return null;
        }

        PathItem pathItem = baseline.openApi()
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

    private Schema<?> resolveJsonRequestSchema(
            ContractBaseline baseline,
            Operation operation) {

        RequestBody requestBody = operation.getRequestBody();

        if (requestBody == null) {
            return null;
        }

        /*
         * Handle a local requestBody ref defensively if one appears.
         * The frozen Resident baseline currently puts the schema ref
         * inside the media type, so no general requestBody-ref engine
         * is introduced here.
         */
        if (requestBody.get$ref() != null) {
            return null;
        }

        Content content = requestBody.getContent();

        if (content == null) {
            return null;
        }

        MediaType mediaType = content.get(APPLICATION_JSON);

        if (mediaType == null) {
            return null;
        }

        return mediaType.getSchema();
    }

    private Schema<?> resolveLocalSchema(
            ContractBaseline baseline,
            Schema<?> schema) {

        if (schema == null) {
            return null;
        }

        String ref = schema.get$ref();

        if (ref == null || ref.isBlank()) {
            return schema;
        }

        if (!ref.startsWith(LOCAL_SCHEMA_PREFIX)) {
            return null;
        }

        if (baseline.openApi().getComponents() == null
                || baseline.openApi().getComponents().getSchemas() == null) {
            return null;
        }

        String schemaName =
                ref.substring(LOCAL_SCHEMA_PREFIX.length());

        return baseline.openApi()
                .getComponents()
                .getSchemas()
                .get(schemaName);
    }

    private void detectUnexpectedFields(
            TrafficEvent event,
            ContractOperation operation,
            JsonNode root,
            Map<String, Schema> documentedProperties,
            List<DriftFinding> findings) {

        for (String fieldName : root.propertyNames()) {
            if (!documentedProperties.containsKey(fieldName)) {
                String fieldPath = fieldPath(fieldName);

                findings.add(finding(
                        event,
                        operation,
                        DriftType.UNEXPECTED_REQUEST_FIELD,
                        DriftSeverity.WARNING,
                        fieldPath,
                        "documented property",
                        "unexpected property",
                        "Runtime request contains an undocumented "
                                + "top-level property."));
            }
        }
    }

    private void detectMissingRequiredFields(
            TrafficEvent event,
            ContractOperation operation,
            JsonNode root,
            Set<String> requiredProperties,
            List<DriftFinding> findings) {

        for (String required : requiredProperties) {
            /*
             * has() distinguishes absent from explicitly-null.
             */
            if (!root.has(required)) {
                String fieldPath = fieldPath(required);

                findings.add(finding(
                        event,
                        operation,
                        DriftType.MISSING_REQUIRED_REQUEST_FIELD,
                        DriftSeverity.BREAKING,
                        fieldPath,
                        "required property",
                        "absent",
                        "Runtime request is missing a required "
                                + "top-level property."));
            }
        }
    }

    private void detectPropertyContractViolations(
            TrafficEvent event,
            ContractOperation operation,
            ContractBaseline baseline,
            JsonNode root,
            Map<String, Schema> documentedProperties,
            List<DriftFinding> findings) {

        for (Map.Entry<String, Schema> entry
                : documentedProperties.entrySet()) {

            String fieldName = entry.getKey();

            if (!root.has(fieldName)) {
                continue;
            }

            JsonNode observed = root.get(fieldName);

            Schema<?> propertySchema =
                    resolveLocalSchema(baseline, entry.getValue());

            if (propertySchema == null) {
                /*
                 * The whole validator must not fabricate conclusions
                 * from an unresolved contract.
                 */
                continue;
            }

            String fieldPath = fieldPath(fieldName);

            Set<String> allowedTypes =
                    documentedTypes(propertySchema);

            if (!matchesAnyType(observed, allowedTypes)) {
                findings.add(finding(
                        event,
                        operation,
                        DriftType.REQUEST_TYPE_MISMATCH,
                        DriftSeverity.BREAKING,
                        fieldPath,
                        expectedTypes(allowedTypes),
                        observedType(observed),
                        "Runtime request property type does not match "
                                + "the documented type."));

                /*
                 * Do not additionally report enum mismatch when the
                 * observed value already has the wrong type.
                 */
                continue;
            }

            List<?> enumValues = propertySchema.getEnum();

            if (enumValues != null
                    && !enumValues.isEmpty()
                    && !observed.isNull()
                    && !matchesEnum(observed, enumValues)) {

                findings.add(finding(
                        event,
                        operation,
                        DriftType.REQUEST_ENUM_MISMATCH,
                        DriftSeverity.BREAKING,
                        fieldPath,
                        expectedEnum(enumValues),
                        stableObservedValue(observed),
                        "Runtime request property value is outside "
                                + "the documented enum."));
            }
        }
    }

    private Set<String> documentedTypes(Schema<?> schema) {
        Set<String> result = new LinkedHashSet<>();

        /*
         * Swagger's OpenAPI 3.1 model exposes the multi-type collection
         * through getTypes(). Keep getType() as the single-type path.
         */
        if (schema.getTypes() != null) {
            schema.getTypes().stream()
                    .filter(type -> type != null && !type.isBlank())
                    .map(type -> type.toLowerCase(Locale.ROOT))
                    .forEach(result::add);
        }

        if (schema.getType() != null
                && !schema.getType().isBlank()) {
            result.add(
                    schema.getType().toLowerCase(Locale.ROOT));
        }

        return result;
    }

    private boolean matchesAnyType(
            JsonNode observed,
            Set<String> allowedTypes) {

        /*
         * If the schema supplies no simple type, Part C has no
         * deterministic simple-type conclusion to make.
         */
        if (allowedTypes.isEmpty()) {
            return true;
        }

        for (String type : allowedTypes) {
            if (matchesType(observed, type)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesType(
            JsonNode observed,
            String documentedType) {

        return switch (documentedType) {
            case "null" ->
                    observed.isNull();

            case "string" ->
                    observed.isTextual();

            case "integer" ->
                    observed.isIntegralNumber();

            case "number" ->
                    observed.isNumber();

            case "boolean" ->
                    observed.isBoolean();

            case "object" ->
                    observed.isObject();

            case "array" ->
                    observed.isArray();

            default ->
                /*
                 * Unsupported types are not converted into false
                 * drift conclusions in this narrow Part C slice.
                 */
                    true;
        };
    }

    private boolean matchesEnum(
            JsonNode observed,
            List<?> enumValues) {

        String observedValue = stableObservedValue(observed);

        return enumValues.stream()
                .map(String::valueOf)
                .anyMatch(observedValue::equals);
    }

    private String expectedEnum(List<?> enumValues) {
        return enumValues.stream()
                .map(String::valueOf)
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private String expectedTypes(Set<String> types) {
        if (types.isEmpty()) {
            return "unspecified";
        }

        return types.stream()
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private String observedType(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isIntegralNumber()) {
            return "integer";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }

        return "unknown";
    }

    private String stableObservedValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }

        if (node.isTextual()) {
            return node.asText();
        }

        return node.toString();
    }

    private boolean isApplicationJson(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }

        String mediaType = contentType
                .split(";", 2)[0]
                .trim();

        return APPLICATION_JSON.equalsIgnoreCase(mediaType);
    }

    private String fieldPath(String fieldName) {
        return "/" + fieldName
                .replace("~", "~0")
                .replace("/", "~1");
    }

    private DriftFinding finding(
            TrafficEvent event,
            ContractOperation operation,
            DriftType type,
            DriftSeverity severity,
            String fieldPath,
            String expected,
            String observed,
            String message) {

        return new DriftFinding(
                event.eventId()
                        + ":"
                        + type.name()
                        + ":"
                        + fieldPath,
                event.eventId(),
                event.sequence(),
                type,
                severity,
                event.method(),
                event.requestPath(),
                operation.pathTemplate(),
                fieldPath,
                expected,
                observed,
                message);
    }
}