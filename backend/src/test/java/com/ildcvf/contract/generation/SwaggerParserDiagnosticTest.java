package com.ildcvf.contract.generation;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SwaggerParserDiagnosticTest {

    @Test
    void reportsUnresolvedInternalReference() throws Exception {
        String baseline = Files.readString(Path.of(
                "../datasets/openapi/baseline/resident-api-v1.yaml"));

        String broken = baseline.replace(
                "#/components/schemas/ResidentResponse",
                "#/components/schemas/NonexistentResidentResponse");

        assertNotEquals(baseline, broken,
                "The diagnostic must actually modify a reference");

        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);

        SwaggerParseResult result =
                new OpenAPIV3Parser().readContents(broken, null, options);

        System.out.println("OpenAPI object present: "
                + (result != null && result.getOpenAPI() != null));

        System.out.println("Parser diagnostics: "
                + (result == null ? "null result" : result.getMessages()));

        assertNotNull(result);
        assertNotNull(result.getMessages());

        assertFalse(
                result.getMessages().isEmpty(),
                "Unresolved internal schema references must produce parser diagnostics"
        );

        assertTrue(
                result.getMessages().stream()
                        .anyMatch(message ->
                                message.contains("NonexistentResidentResponse")),
                "Diagnostics must identify the unresolved schema reference"
        );
    }
}
