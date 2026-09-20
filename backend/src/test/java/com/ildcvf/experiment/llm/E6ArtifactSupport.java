package com.ildcvf.experiment.llm;

import java.util.List;

final class E6ArtifactSupport {

    private E6ArtifactSupport() {
    }

    static String groundTruthJson(
            List<LlmReliabilityScenario> scenarios
    ) {
        StringBuilder json = new StringBuilder();

        json.append("[\n");

        for (int i = 0; i < scenarios.size(); i++) {
            LlmReliabilityScenario scenario =
                    scenarios.get(i);

            LlmProposalGroundTruth truth =
                    scenario.groundTruth();

            json.append("  {\n");
            appendString(
                    json,
                    "scenarioId",
                    scenario.scenarioId(),
                    true);
            appendString(
                    json,
                    "evidenceFingerprint",
                    scenario.evidenceFingerprint(),
                    true);
            appendString(
                    json,
                    "sourceTrafficEventId",
                    truth.sourceTrafficEventId(),
                    true);
            appendString(
                    json,
                    "sourceFindingId",
                    truth.sourceFindingId(),
                    true);
            appendString(
                    json,
                    "driftType",
                    truth.driftType().name(),
                    true);
            appendString(
                    json,
                    "changeKind",
                    truth.changeKind().name(),
                    true);
            appendString(
                    json,
                    "method",
                    truth.method(),
                    true);
            appendString(
                    json,
                    "runtimePath",
                    truth.runtimePath(),
                    true);
            appendString(
                    json,
                    "matchedOpenApiPath",
                    truth.matchedOpenApiPath(),
                    true);
            appendString(
                    json,
                    "targetFieldPath",
                    truth.targetFieldPath(),
                    true);
            appendString(
                    json,
                    "expected",
                    truth.expected(),
                    true);
            appendString(
                    json,
                    "observed",
                    truth.observed(),
                    true);
            appendString(
                    json,
                    "proposedValue",
                    truth.proposedValue(),
                    false);

            json.append("  }");

            if (i < scenarios.size() - 1) {
                json.append(',');
            }

            json.append('\n');
        }

        json.append("]\n");

        return json.toString();
    }

    private static void appendString(
            StringBuilder json,
            String name,
            String value,
            boolean comma
    ) {
        json.append("    \"")
                .append(escape(name))
                .append("\": ");

        if (value == null) {
            json.append("null");
        } else {
            json.append('"')
                    .append(escape(value))
                    .append('"');
        }

        if (comma) {
            json.append(',');
        }

        json.append('\n');
    }

    private static String escape(String value) {
        StringBuilder escaped =
                new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);

            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");

                default -> {
                    if (character < 0x20) {
                        escaped.append(
                                String.format(
                                        "\\u%04x",
                                        (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }

        return escaped.toString();
    }
}