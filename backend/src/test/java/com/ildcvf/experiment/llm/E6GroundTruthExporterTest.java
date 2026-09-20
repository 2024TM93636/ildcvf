package com.ildcvf.experiment.llm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class E6GroundTruthExporterTest {

    private static final Path OUTPUT_PATH =
            Path.of(
                    "..",
                    "experiments",
                    "e6-llm-reliability",
                    "scenarios",
                    "ground-truth.json");

    @Test
    void exportsCanonicalGroundTruthArtifact()
            throws IOException {

        List<LlmReliabilityScenario> scenarios =
                new LlmScenarioCatalog()
                        .scenarios();

        String json =
                E6ArtifactSupport
                        .groundTruthJson(scenarios);

        Files.createDirectories(
                OUTPUT_PATH.getParent());

        Files.writeString(
                OUTPUT_PATH,
                json,
                StandardCharsets.UTF_8);

        assertTrue(
                Files.isRegularFile(OUTPUT_PATH));

        assertEquals(
                json,
                Files.readString(
                        OUTPUT_PATH,
                        StandardCharsets.UTF_8));
    }
}