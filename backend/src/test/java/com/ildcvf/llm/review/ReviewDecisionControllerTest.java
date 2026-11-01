package com.ildcvf.llm.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ReviewDecisionControllerTest {

    private ReviewDecisionPersistenceService service;
    private ReviewDecisionController controller;

    private final UUID attempt = UUID.randomUUID();

    private ReviewDecisionRecord record(
            ReviewDecision decision) {

        return new ReviewDecisionRecord(
                UUID.randomUUID(),
                attempt,
                decision,
                "operator-1",
                "Reviewed evidence",
                Instant.now()
        );
    }

    @BeforeEach
    void setUp() {
        service = mock(ReviewDecisionPersistenceService.class);
        controller = new ReviewDecisionController(service);
    }

    private ReviewDecisionController.ReviewRequest request(
            String decision) {

        return new ReviewDecisionController.ReviewRequest(
                attempt.toString(),
                decision,
                "operator-1",
                "Reviewed evidence"
        );
    }

    private void status(HttpStatus expected, Runnable call) {
        assertEquals(
                expected,
                assertThrows(
                        ResponseStatusException.class,
                        call::run
                ).getStatusCode()
        );
    }

    @Test
    void approvedReturns201() {

        var saved = record(ReviewDecision.APPROVED);

        when(service.record(
                eq(attempt),
                eq(ReviewDecision.APPROVED),
                anyString(),
                anyString()
        )).thenReturn(saved);

        var response = controller.create(request("APPROVED"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(saved, response.getBody());

        assertEquals(
                "/api/llm/reviews/" + attempt,
                response.getHeaders().getLocation().toString()
        );
    }

    @Test
    void rejectedReturns201() {

        when(service.record(
                eq(attempt),
                eq(ReviewDecision.REJECTED),
                anyString(),
                anyString()
        )).thenReturn(record(ReviewDecision.REJECTED));

        assertEquals(
                201,
                controller.create(request("REJECTED"))
                        .getStatusCode().value()
        );
    }

    @Test
    void invalidDecisionAndFieldsReturn400() {

        status(HttpStatus.BAD_REQUEST,
                () -> controller.create(request("UNKNOWN")));

        status(HttpStatus.BAD_REQUEST,
                () -> controller.create(null));

        status(HttpStatus.BAD_REQUEST,
                () -> controller.create(
                        new ReviewDecisionController.ReviewRequest(
                                "bad", "APPROVED", "op", "reason")));

        status(HttpStatus.BAD_REQUEST,
                () -> controller.create(
                        new ReviewDecisionController.ReviewRequest(
                                attempt.toString(),
                                "APPROVED", " ", "reason")));

        status(HttpStatus.BAD_REQUEST,
                () -> controller.create(
                        new ReviewDecisionController.ReviewRequest(
                                attempt.toString(),
                                "APPROVED", "op", " ")));

        status(HttpStatus.BAD_REQUEST,
                () -> controller.create(
                        new ReviewDecisionController.ReviewRequest(
                                attempt.toString(),
                                "APPROVED", "x".repeat(129), "reason")));

        status(HttpStatus.BAD_REQUEST,
                () -> controller.create(
                        new ReviewDecisionController.ReviewRequest(
                                attempt.toString(),
                                "APPROVED", "op", "x".repeat(4001))));

        verifyNoInteractions(service);
    }

    @Test
    void unknownAttemptReturns404() {

        when(service.record(
                eq(attempt),
                any(),
                anyString(),
                anyString()
        )).thenThrow(new IllegalArgumentException("missing"));

        status(HttpStatus.NOT_FOUND,
                () -> controller.create(request("APPROVED")));
    }

    @Test
    void ineligibleReturns409() {

        when(service.record(
                eq(attempt),
                any(),
                anyString(),
                anyString()
        )).thenThrow(new IllegalStateException("ineligible"));

        status(HttpStatus.CONFLICT,
                () -> controller.create(request("APPROVED")));
    }

    @Test
    void duplicateReturns409() {

        when(service.record(
                eq(attempt),
                any(),
                anyString(),
                anyString()
        )).thenThrow(new IllegalStateException("already reviewed"));

        status(HttpStatus.CONFLICT,
                () -> controller.create(request("REJECTED")));
    }

    @Test
    void databaseRaceUniqueViolationReturns409() {

        when(service.record(
                eq(attempt),
                any(),
                anyString(),
                anyString()
        )).thenThrow(
                new DataIntegrityViolationException("unique constraint"));

        status(HttpStatus.CONFLICT,
                () -> controller.create(request("APPROVED")));
    }

    @Test
    void getReturnsSavedDecision() {

        var saved = record(ReviewDecision.APPROVED);

        when(service.findByAttempt(attempt))
                .thenReturn(java.util.Optional.of(saved));

        assertEquals(
                saved,
                controller.get(attempt.toString())
        );
    }

    @Test
    void getMissingReturns404() {

        when(service.findByAttempt(attempt))
                .thenReturn(java.util.Optional.empty());

        status(HttpStatus.NOT_FOUND,
                () -> controller.get(attempt.toString()));
    }

    @Test
    void historyIsDelegatedWithPagination() {

        var page = new PageImpl<>(
                java.util.List.of(record(ReviewDecision.APPROVED)));

        when(service.history(0, 20)).thenReturn(page);

        assertSame(page, controller.list(0, 20));

        status(HttpStatus.BAD_REQUEST,
                () -> controller.list(-1, 20));

        status(HttpStatus.BAD_REQUEST,
                () -> controller.list(0, 101));
    }
}
