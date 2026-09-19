package com.ildcvf.llm.evidence;

/**
 * Minimal deterministic baseline-contract context associated with one
 * grounded drift finding.
 *
 * <p>This record deliberately avoids carrying the complete OpenAPI model
 * across the future LLM trust boundary.</p>
 *
 * <p>For an undocumented operation, {@code pathTemplate} and
 * {@code operationId} may legitimately be {@code null} because no matching
 * baseline operation exists.</p>
 */
public record RelevantContractContext(
        String openApiVersion,
        String method,
        String pathTemplate,
        String operationId
) {
}
