package com.ildcvf.contract.service;

import com.ildcvf.contract.model.ContractOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContractOperationMatcherTest {

    private ContractOperationMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new ContractOperationMatcher();
    }

    @Test
    void matchesExactStaticOperation() {
        var result = matcher.match(
                "GET",
                "/api/residents",
                residentOperations());

        assertEquals(
                ContractOperationMatcher.MatchStatus.MATCHED,
                result.status());

        assertNotNull(result.operation());
        assertEquals("/api/residents", result.operation().pathTemplate());
        assertEquals("GET", result.operation().method());
    }

    @Test
    void matchesTemplatedGetOperation() {
        var result = matcher.match(
                "GET",
                "/api/residents/123",
                residentOperations());

        assertEquals(
                ContractOperationMatcher.MatchStatus.MATCHED,
                result.status());

        assertNotNull(result.operation());
        assertEquals(
                "/api/residents/{id}",
                result.operation().pathTemplate());
    }

    @Test
    void matchesTemplatedPutOperation() {
        var result = matcher.match(
                "PUT",
                "/api/residents/123",
                residentOperations());

        assertEquals(
                ContractOperationMatcher.MatchStatus.MATCHED,
                result.status());

        assertNotNull(result.operation());
        assertEquals(
                "/api/residents/{id}",
                result.operation().pathTemplate());
    }

    @Test
    void matchesTemplatedDeleteOperation() {
        var result = matcher.match(
                "DELETE",
                "/api/residents/123",
                residentOperations());

        assertEquals(
                ContractOperationMatcher.MatchStatus.MATCHED,
                result.status());

        assertNotNull(result.operation());
        assertEquals(
                "/api/residents/{id}",
                result.operation().pathTemplate());
    }

    @Test
    void reportsUndocumentedMethod() {
        var result = matcher.match(
                "PATCH",
                "/api/residents",
                residentOperations());

        assertEquals(
                ContractOperationMatcher.MatchStatus.UNDOCUMENTED,
                result.status());

        assertNull(result.operation());
    }

    @Test
    void reportsUndocumentedPath() {
        var result = matcher.match(
                "GET",
                "/api/unknown",
                residentOperations());

        assertEquals(
                ContractOperationMatcher.MatchStatus.UNDOCUMENTED,
                result.status());

        assertNull(result.operation());
    }

    @Test
    void reportsAmbiguousEquallySpecificTemplates() {
        List<ContractOperation> operations = List.of(
                operation("/api/residents/{id}", "GET"),
                operation("/api/residents/{residentId}", "GET"));

        var result = matcher.match(
                "GET",
                "/api/residents/123",
                operations);

        assertEquals(
                ContractOperationMatcher.MatchStatus.AMBIGUOUS,
                result.status());

        assertNull(result.operation());
    }

    @Test
    void prefersExactStaticOperationOverTemplate() {
        List<ContractOperation> operations = List.of(
                operation("/api/residents/special", "GET"),
                operation("/api/residents/{id}", "GET"));

        var result = matcher.match(
                "GET",
                "/api/residents/special",
                operations);

        assertEquals(
                ContractOperationMatcher.MatchStatus.MATCHED,
                result.status());

        assertNotNull(result.operation());
        assertEquals(
                "/api/residents/special",
                result.operation().pathTemplate());
    }

    private List<ContractOperation> residentOperations() {
        return List.of(
                operation("/api/residents", "POST"),
                operation("/api/residents", "GET"),
                operation("/api/residents/{id}", "GET"),
                operation("/api/residents/{id}", "PUT"),
                operation("/api/residents/{id}", "DELETE"));
    }

    private ContractOperation operation(String path, String method) {
        return new ContractOperation(path, method, null);
    }
}