package com.ildcvf.llm.review;

import java.net.URI;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Prototype reviewerId is supplied by the operator,
 * not authenticated.
 */
@RestController
@RequestMapping("/api/llm/reviews")
public class ReviewDecisionController {

    private final ReviewDecisionPersistenceService service;

    public ReviewDecisionController(
            ReviewDecisionPersistenceService service) {
        this.service = service;
    }

    public record ReviewRequest(
            String proposalAttemptId,
            String decision,
            String reviewerId,
            String justification
    ) {}

    @PostMapping
    public ResponseEntity<ReviewDecisionRecord> create(
            @RequestBody(required = false) ReviewRequest request) {

        if (request == null) {
            throw badRequest("Request body required");
        }

        UUID attemptId = parseId(request.proposalAttemptId());

        if (request.decision() == null) {
            throw badRequest("decision is required");
        }

        final ReviewDecision decision;

        try {
            decision = ReviewDecision.valueOf(request.decision());
        } catch (IllegalArgumentException ex) {
            throw badRequest(
                    "decision must be APPROVED or REJECTED");
        }

        if (request.reviewerId() == null
                || request.reviewerId().isBlank()
                || request.reviewerId().length() > 128) {
            throw badRequest(
                    "reviewerId must be nonblank and at most 128 characters");
        }

        if (request.justification() == null
                || request.justification().isBlank()
                || request.justification().length() > 4000) {
            throw badRequest(
                    "justification must be nonblank and at most 4000 characters");
        }

        try {
            ReviewDecisionRecord saved = service.record(
                    attemptId,
                    decision,
                    request.reviewerId(),
                    request.justification()
            );

            return ResponseEntity
                    .created(URI.create(
                            "/api/llm/reviews/" + attemptId))
                    .body(saved);

        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Proposal attempt not found");

        } catch (IllegalStateException
                 | DataIntegrityViolationException
                 | PessimisticLockingFailureException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Proposal is ineligible, already reviewed, "
                            + "or being reviewed concurrently");
        }
    }

    @GetMapping("/{attemptId}")
    public ReviewDecisionRecord get(
            @PathVariable String attemptId) {

        return service.findByAttempt(parseId(attemptId))
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Review not found"));
    }

    @GetMapping
    public Page<ReviewDecisionRecord> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw badRequest(
                    "page must be >= 0; size must be 1..100");
        }

        return service.history(page, size);
    }

    private static UUID parseId(String text) {

        if (text == null || text.isBlank()) {
            throw badRequest(
                    "Valid proposalAttemptId required");
        }

        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw badRequest(
                    "Invalid proposal attempt ID");
        }
    }

    private static ResponseStatusException badRequest(
            String reason) {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                reason);
    }
}
