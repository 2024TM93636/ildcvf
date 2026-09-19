package com.ildcvf.contract.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import com.ildcvf.contract.model.ResponseBodyValidationResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.traffic.domain.TrafficEvent;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ResponseBodyContractValidator {

    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String ROOT_PATH = "$";

    private final ObjectMapper objectMapper;

    public ResponseBodyContractValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResponseBodyValidationResult validate(
            TrafficEvent event,
            ContractOperation matchedOperation,
            ContractBaseline baseline) {

        Operation operation =
                resolveOperation(baseline, matchedOperation);

        if (operation == null) {
            return partial(
                    "Matched OpenAPI operation could not be resolved "
                            + "for response-body validation.");
        }

        ApiResponse selectedResponse =
                selectResponse(operation, event.responseStatus());

        /*
         * No exact/default response means Part D owns the deterministic
         * conclusion: UNDOCUMENTED_RESPONSE_STATUS.
         *
         * Do not validate the body against another documented status.
         */
        if (selectedResponse == null) {
            return ResponseBodyValidationResult.evaluated(List.of());
        }

        Schema<?> schema = jsonResponseSchema(selectedResponse);

        /*
         * No applicable JSON schema is not response-body drift.
         * This check intentionally comes before runtime evidence checks
         * so bodyless 204 responses remain fully evaluated.
         */
        if (schema == null) {
            return ResponseBodyValidationResult.evaluated(List.of());
        }

        if (event.responseBodyTruncated()) {
            return partial(
                    "Response body was truncated; response schema "
                            + "validation was not safely performed.");
        }

        if (!isJsonContentType(event.responseContentType())) {
            return partial(
                    "Runtime response content type is not JSON; "
                            + "response schema validation was not safely performed.");
        }

        if (event.responseBody() == null
                || event.responseBody().isBlank()) {
            return partial(
                    "Runtime response body is absent; response schema "
                            + "validation was not safely performed.");
        }

        JsonNode runtimeBody;

        try {
            runtimeBody = objectMapper.readTree(event.responseBody());
        } catch (Exception ex) {
            return partial(
                    "Runtime response body is malformed JSON; response "
                            + "schema validation was not safely performed.");
        }

        List<DriftFinding> findings = new ArrayList<>();

        try {
            Schema<?> resolvedSchema =
                    resolveSchema(schema, baseline.openApi());

            validateRoot(
                    event,
                    runtimeBody,
                    resolvedSchema,
                    baseline.openApi(),
                    findings);

            findings.sort(
                    Comparator.comparing(
                                    (DriftFinding finding) ->
                                            finding.type().name())
                            .thenComparing(
                                    finding ->
                                            finding.fieldPath() == null
                                                    ? ""
                                                    : finding.fieldPath())
                            .thenComparing(
                                    DriftFinding::findingId));

            return ResponseBodyValidationResult.evaluated(findings);

        } catch (UnresolvedSchemaException ex) {
            return partial(ex.getMessage());
        }
    }

    private void validateRoot(
            TrafficEvent event,
            JsonNode runtime,
            Schema<?> schema,
            OpenAPI openApi,
            List<DriftFinding> findings) {

        if (!matchesType(runtime, schema)) {
            findings.add(typeMismatch(
                    event,
                    ROOT_PATH,
                    documentedType(schema),
                    runtimeType(runtime)));

            return;
        }

        String type = primaryNonNullType(schema);

        if ("object".equals(type)) {
            validateObject(
                    event,
                    runtime,
                    schema,
                    openApi,
                    "",
                    findings);

            return;
        }

        if ("array".equals(type)) {
            validateArray(
                    event,
                    runtime,
                    schema,
                    openApi,
                    findings);
        }
    }

    private void validateArray(
            TrafficEvent event,
            JsonNode runtime,
            Schema<?> arraySchema,
            OpenAPI openApi,
            List<DriftFinding> findings) {

        if (!runtime.isArray()) {
            return;
        }

        Schema<?> itemSchema = arraySchema.getItems();

        if (itemSchema == null) {
            return;
        }

        Schema<?> resolvedItemSchema =
                resolveSchema(itemSchema, openApi);

        for (int index = 0; index < runtime.size(); index++) {
            JsonNode item = runtime.get(index);
            String itemPath = "[" + index + "]";

            if (!matchesType(item, resolvedItemSchema)) {
                findings.add(typeMismatch(
                        event,
                        itemPath,
                        documentedType(resolvedItemSchema),
                        runtimeType(item)));

                continue;
            }

            if ("object".equals(
                    primaryNonNullType(resolvedItemSchema))) {

                validateObject(
                        event,
                        item,
                        resolvedItemSchema,
                        openApi,
                        itemPath,
                        findings);
            }
        }
    }

    private void validateObject(
            TrafficEvent event,
            JsonNode runtime,
            Schema<?> objectSchema,
            OpenAPI openApi,
            String prefix,
            List<DriftFinding> findings) {

        if (!runtime.isObject()) {
            return;
        }

        Map<String, Schema> properties =
                objectSchema.getProperties();

        if (properties == null) {
            properties = Map.of();
        }

        Set<String> runtimeFields =
                toFieldSet(runtime.propertyNames());

        List<String> required =
                objectSchema.getRequired() == null
                        ? List.of()
                        : objectSchema.getRequired();

        /*
         * Required-field presence is different from nullability.
         * A required nullable property is valid when present with JSON null.
         */
        for (String requiredField :
                required.stream().sorted().toList()) {

            if (!runtime.has(requiredField)) {
                String fieldPath =
                        fieldPath(prefix, requiredField);

                findings.add(new DriftFinding(
                        findingId(
                                event,
                                DriftType.MISSING_REQUIRED_RESPONSE_FIELD,
                                fieldPath),
                        event.eventId(),
                        event.sequence(),
                        DriftType.MISSING_REQUIRED_RESPONSE_FIELD,
                        DriftSeverity.BREAKING,
                        event.method(),
                        event.requestPath(),
                        null,
                        fieldPath,
                        "required",
                        "missing",
                        "Required response field is missing."));
            }
        }

        /*
         * JSON Schema/OpenAPI permits additional properties by default.
         * Emit unexpected-field drift only when explicitly false.
         */
        if (Boolean.FALSE.equals(
                objectSchema.getAdditionalProperties())) {

            String expected =
                    properties.keySet().stream()
                            .sorted()
                            .collect(Collectors.joining("|"));

            Map<String, Schema> finalProperties = properties;
            runtimeFields.stream()
                    .filter(field -> !finalProperties.containsKey(field))
                    .sorted()
                    .forEach(field -> {
                        String fieldPath =
                                fieldPath(prefix, field);

                        findings.add(new DriftFinding(
                                findingId(
                                        event,
                                        DriftType.UNEXPECTED_RESPONSE_FIELD,
                                        fieldPath),
                                event.eventId(),
                                event.sequence(),
                                DriftType.UNEXPECTED_RESPONSE_FIELD,
                                DriftSeverity.WARNING,
                                event.method(),
                                event.requestPath(),
                                null,
                                fieldPath,
                                expected,
                                field,
                                "Runtime response contains a field "
                                        + "not permitted by the documented schema."));
                    });
        }

        for (String propertyName :
                properties.keySet().stream().sorted().toList()) {

            if (!runtime.has(propertyName)) {
                continue;
            }

            JsonNode runtimeValue =
                    runtime.get(propertyName);

            Schema<?> propertySchema =
                    resolveSchema(
                            properties.get(propertyName),
                            openApi);

            String fieldPath =
                    fieldPath(prefix, propertyName);

            /*
             * Only shallow supported validation.
             * Nested object children are intentionally not traversed.
             */
            if (!matchesType(runtimeValue, propertySchema)) {
                findings.add(typeMismatch(
                        event,
                        fieldPath,
                        documentedType(propertySchema),
                        runtimeType(runtimeValue)));

                continue;
            }

            validateEnum(
                    event,
                    runtimeValue,
                    propertySchema,
                    fieldPath,
                    findings);
        }
    }

    private void validateEnum(
            TrafficEvent event,
            JsonNode runtime,
            Schema<?> schema,
            String fieldPath,
            List<DriftFinding> findings) {

        List<?> allowedValues = schema.getEnum();

        if (allowedValues == null
                || allowedValues.isEmpty()
                || runtime == null
                || runtime.isNull()) {
            return;
        }

        String observed = scalarValue(runtime);

        boolean allowed =
                allowedValues.stream()
                        .map(String::valueOf)
                        .anyMatch(observed::equals);

        if (allowed) {
            return;
        }

        String expected =
                allowedValues.stream()
                        .map(String::valueOf)
                        .sorted()
                        .collect(Collectors.joining("|"));

        findings.add(new DriftFinding(
                findingId(
                        event,
                        DriftType.RESPONSE_ENUM_MISMATCH,
                        fieldPath),
                event.eventId(),
                event.sequence(),
                DriftType.RESPONSE_ENUM_MISMATCH,
                DriftSeverity.BREAKING,
                event.method(),
                event.requestPath(),
                null,
                fieldPath,
                expected,
                observed,
                "Runtime response value is not in the documented enum."));
    }

    private DriftFinding typeMismatch(
            TrafficEvent event,
            String fieldPath,
            String expected,
            String observed) {

        return new DriftFinding(
                findingId(
                        event,
                        DriftType.RESPONSE_TYPE_MISMATCH,
                        fieldPath),
                event.eventId(),
                event.sequence(),
                DriftType.RESPONSE_TYPE_MISMATCH,
                DriftSeverity.BREAKING,
                event.method(),
                event.requestPath(),
                null,
                fieldPath,
                expected,
                observed,
                "Runtime response value does not match "
                        + "the documented type.");
    }

    private String findingId(
            TrafficEvent event,
            DriftType type,
            String fieldPath) {

        return event.eventId()
                + ":"
                + type.name()
                + ":"
                + fieldPath;
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

        try {
            PathItem.HttpMethod method =
                    PathItem.HttpMethod.valueOf(
                            matchedOperation.method()
                                    .toUpperCase(Locale.ROOT));

            return pathItem.readOperationsMap().get(method);

        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ApiResponse selectResponse(
            Operation operation,
            int runtimeStatus) {

        ApiResponses responses = operation.getResponses();

        if (responses == null || responses.isEmpty()) {
            return null;
        }

        String exact = Integer.toString(runtimeStatus);

        if (responses.containsKey(exact)) {
            return responses.get(exact);
        }

        return responses.get("default");
    }

    private Schema<?> jsonResponseSchema(
            ApiResponse response) {

        Content content = response.getContent();

        if (content == null || content.isEmpty()) {
            return null;
        }

        if (content.get(JSON_MEDIA_TYPE) == null) {
            return null;
        }

        return content.get(JSON_MEDIA_TYPE).getSchema();
    }

    private Schema<?> resolveSchema(
            Schema<?> schema,
            OpenAPI openApi) {

        if (schema == null) {
            throw new UnresolvedSchemaException(
                    "Response schema could not be resolved.");
        }

        if (schema.get$ref() == null) {
            return schema;
        }

        String prefix = "#/components/schemas/";

        if (!schema.get$ref().startsWith(prefix)) {
            throw new UnresolvedSchemaException(
                    "Only local OpenAPI component schema references "
                            + "are supported for response validation.");
        }

        if (openApi == null
                || openApi.getComponents() == null
                || openApi.getComponents().getSchemas() == null) {

            throw new UnresolvedSchemaException(
                    "OpenAPI component schemas are unavailable "
                            + "for response validation.");
        }

        String schemaName =
                schema.get$ref().substring(prefix.length());

        Schema<?> resolved =
                openApi.getComponents()
                        .getSchemas()
                        .get(schemaName);

        if (resolved == null) {
            throw new UnresolvedSchemaException(
                    "Local response schema reference could not be resolved: "
                            + schema.get$ref());
        }

        return resolved;
    }

    private boolean matchesType(
            JsonNode runtime,
            Schema<?> schema) {

        if (runtime == null) {
            return false;
        }

        List<String> allowedTypes =
                allowedTypes(schema);

        if (runtime.isNull()) {
            return allowedTypes.contains("null");
        }

        if (allowedTypes.isEmpty()) {
            /*
             * A referenced enum such as ResidentStatus may not expose
             * its type through getType() depending on parser model shape.
             * If there is an enum but no explicit type, enum validation
             * can still deterministically proceed.
             */
            return schema.getEnum() != null;
        }

        for (String type : allowedTypes) {
            if (matchesSingleType(runtime, type)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesSingleType(
            JsonNode runtime,
            String type) {

        return switch (type) {
            case "object" -> runtime.isObject();
            case "array" -> runtime.isArray();
            case "string" -> runtime.isTextual();
            case "integer" -> runtime.isIntegralNumber();
            case "number" -> runtime.isNumber();
            case "boolean" -> runtime.isBoolean();
            case "null" -> runtime.isNull();
            default -> false;
        };
    }

    private List<String> allowedTypes(
            Schema<?> schema) {

        Collection<String> types = schema.getTypes();

        if (types != null && !types.isEmpty()) {
            return types.stream()
                    .map(String::valueOf)
                    .sorted()
                    .toList();
        }

        if (schema.getType() != null
                && !schema.getType().isBlank()) {
            return List.of(schema.getType());
        }

        return List.of();
    }

    private String primaryNonNullType(
            Schema<?> schema) {

        return allowedTypes(schema).stream()
                .filter(type -> !"null".equals(type))
                .findFirst()
                .orElse(null);
    }

    private String documentedType(
            Schema<?> schema) {

        List<String> types = allowedTypes(schema);

        if (!types.isEmpty()) {
            return String.join("|", types);
        }

        return "unspecified";
    }

    private String runtimeType(JsonNode node) {

        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isIntegralNumber()) {
            return "integer";
        }
        if (node.isFloatingPointNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }

        return "unknown";
    }

    private String scalarValue(JsonNode node) {

        if (node.isTextual()) {
            return node.textValue();
        }

        return node.asText();
    }

    private boolean isJsonContentType(
            String contentType) {

        if (contentType == null || contentType.isBlank()) {
            return false;
        }

        String mediaType =
                contentType.split(";", 2)[0]
                        .trim()
                        .toLowerCase(Locale.ROOT);

        return JSON_MEDIA_TYPE.equals(mediaType)
                || mediaType.endsWith("+json");
    }

    private Set<String> toFieldSet(
            Collection<String> fields) {

        return new java.util.TreeSet<>(fields);
    }

    private String fieldPath(
            String prefix,
            String property) {

        return prefix == null || prefix.isBlank()
                ? property
                : prefix + "." + property;
    }

    private ResponseBodyValidationResult partial(
            String message) {

        return ResponseBodyValidationResult.partiallyEvaluated(
                message);
    }

    private static final class UnresolvedSchemaException
            extends RuntimeException {

        private UnresolvedSchemaException(String message) {
            super(message);
        }
    }
}