package com.ildcvf.experiment.llm.groq;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroqConfigurationAndSecretTest {

    @Test
    void frozenConfigurationHasExactPreliminaryValues() {
        GroqConfiguration configuration =
                GroqConfiguration.frozenPreliminary();

        assertEquals(
                URI.create(
                        "https://api.groq.com/openai/v1/chat/completions"),
                configuration.endpoint());

        assertEquals(
                "openai/gpt-oss-20b",
                configuration.model());

        assertEquals(
                0.0d,
                configuration.temperature());

        assertEquals(
                1.0d,
                configuration.topP());

        assertEquals(
                20260919L,
                configuration.seed());

        assertEquals(
                512,
                configuration.maxCompletionTokens());

        assertEquals(
                "on_demand",
                configuration.serviceTier());

        assertEquals(
                Duration.ofSeconds(30),
                configuration.timeout());
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GroqConfiguration(
                                URI.create("http://example.test"),
                                "model",
                                0.0d,
                                1.0d,
                                1L,
                                512,
                                "on_demand",
                                Duration.ofSeconds(30)));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GroqConfiguration(
                                URI.create("https://example.test"),
                                "model",
                                0.0d,
                                0.0d,
                                1L,
                                512,
                                "on_demand",
                                Duration.ofSeconds(30)));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GroqConfiguration(
                                URI.create("https://example.test"),
                                "model",
                                0.0d,
                                1.0d,
                                1L,
                                0,
                                "on_demand",
                                Duration.ofSeconds(30)));
    }

    @Test
    void absentSecretFailsWithoutLeakingSecretMaterial() {
        GroqApiKeyResolver resolver =
                new GroqApiKeyResolver(
                        Map::<String, String>of);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        resolver::resolve);

        assertFalse(
                exception.getMessage()
                        .contains(
                                GroqApiKeyResolver
                                        .ENVIRONMENT_VARIABLE));
    }

    @Test
    void suppliedFakeSecretCanBeResolved() {
        GroqApiKeyResolver resolver =
                new GroqApiKeyResolver(
                        () ->
                                Map.of(
                                        GroqApiKeyResolver
                                                .ENVIRONMENT_VARIABLE,
                                        GroqTestFixtures.FAKE_SECRET));

        assertEquals(
                GroqTestFixtures.FAKE_SECRET,
                resolver.resolve());
    }

    @Test
    void configurationNeverContainsSecret() {
        String configuration =
                GroqConfiguration
                        .frozenPreliminary()
                        .toString();

        assertFalse(
                configuration.contains(
                        GroqTestFixtures.FAKE_SECRET));

        assertFalse(
                configuration.contains(
                        GroqApiKeyResolver
                                .ENVIRONMENT_VARIABLE));

        assertTrue(
                configuration.contains(
                        "openai/gpt-oss-20b"));
    }
}