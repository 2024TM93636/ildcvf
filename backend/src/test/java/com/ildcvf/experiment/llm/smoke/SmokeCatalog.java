package com.ildcvf.experiment.llm.smoke;

import com.ildcvf.experiment.llm.LlmReliabilityScenario;
import com.ildcvf.experiment.llm.LlmScenarioCatalog;

import java.util.List;
import java.util.Objects;

public final class SmokeCatalog {

    public static final String AUTHORIZED_SMOKE_ID =
            "smoke-001-llm-001-undocumented-operation";

    public static final String AUTHORIZED_SCENARIO_ID =
            "llm-001-undocumented-operation";

    private final List<LlmReliabilityScenario> scenarios;

    public SmokeCatalog() {
        this(
                new LlmScenarioCatalog()
                        .scenarios());
    }

    SmokeCatalog(
            List<LlmReliabilityScenario> scenarios
    ) {
        this.scenarios =
                List.copyOf(
                        Objects.requireNonNull(
                                scenarios,
                                "scenarios must not be null"));
    }

    public SmokeDefinition resolveDefinition(
            String smokeId
    ) {
        if (!AUTHORIZED_SMOKE_ID.equals(smokeId)) {
            throw new IllegalArgumentException(
                    "Unauthorized E6-D2 smoke ID: "
                            + smokeId);
        }

        return new SmokeDefinition(
                AUTHORIZED_SMOKE_ID,
                AUTHORIZED_SCENARIO_ID);
    }

    public LlmReliabilityScenario resolveScenario(
            SmokeDefinition definition
    ) {
        Objects.requireNonNull(
                definition,
                "definition must not be null");

        if (!AUTHORIZED_SMOKE_ID.equals(
                definition.smokeId())
                || !AUTHORIZED_SCENARIO_ID.equals(
                definition.scenarioId())) {

            throw new IllegalArgumentException(
                    "Smoke definition is outside "
                            + "the frozen E6-D2 authorization.");
        }

        return scenarios.stream()
                .filter(scenario ->
                        AUTHORIZED_SCENARIO_ID.equals(
                                scenario.scenarioId()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authorized frozen scenario "
                                        + "is unavailable."));
    }
}
