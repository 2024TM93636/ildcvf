package com.ildcvf.llm.review;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.persistence.*;
import com.ildcvf.llm.proposal.*;
import com.ildcvf.llm.validation.ProposalValidationResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewDecisionConcurrencyIntegrationTest {

    @Autowired
    ProposalAttemptPersistenceService attempts;

    @Autowired
    ProposalAttemptRepository attemptRepository;

    @Autowired
    ReviewDecisionPersistenceService reviews;

    @Autowired
    ReviewDecisionRepository reviewRepository;

    @Test
    void concurrentFinalDecisionsCannotBothSucceed() throws Exception {

        String eventId = "m142-" +
                UUID.randomUUID().toString().substring(0, 12);

        String findingId =
                eventId + ":RESPONSE_TYPE_MISMATCH:id";

        UUID attemptId = attempts.start(eventId, findingId);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            ContractChangeProposal proposal =
                    new ContractChangeProposal(
                            "m142-concurrency",
                            eventId,
                            findingId,
                            DriftType.RESPONSE_TYPE_MISMATCH,
                            ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                            "GET",
                            "/api/residents/1",
                            "/api/residents/{id}",
                            "id",
                            "integer",
                            "string",
                            "string",
                            "Concurrency verification"
                    );

            attempts.completeValidated(
                    attemptId,
                    proposal,
                    ProposalValidationResult.validResult()
            );

            var before = attempts.find(attemptId).orElseThrow();

            String proposalBefore = before.proposalJson.toString();
            String validationBefore = before.validationJson.toString();

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Callable<Boolean> approve = () -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);

                try {
                    reviews.record(
                            attemptId,
                            ReviewDecision.APPROVED,
                            "operator-a",
                            "Approved after reviewing evidence"
                    );
                    return true;
                } catch (IllegalStateException
                         | DataIntegrityViolationException
                         | PessimisticLockingFailureException ex) {
                    return false;
                }
            };

            Callable<Boolean> reject = () -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);

                try {
                    reviews.record(
                            attemptId,
                            ReviewDecision.REJECTED,
                            "operator-b",
                            "Rejected after reviewing evidence"
                    );
                    return true;
                } catch (IllegalStateException
                         | DataIntegrityViolationException
                         | PessimisticLockingFailureException ex) {
                    return false;
                }
            };

            Future<Boolean> first = executor.submit(approve);
            Future<Boolean> second = executor.submit(reject);

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .isTrue();

            start.countDown();

            boolean firstSucceeded =
                    first.get(20, TimeUnit.SECONDS);

            boolean secondSucceeded =
                    second.get(20, TimeUnit.SECONDS);

            assertThat(
                    (firstSucceeded ? 1 : 0)
                            + (secondSucceeded ? 1 : 0)
            ).isEqualTo(1);

            var saved =
                    reviews.findByAttempt(attemptId).orElseThrow();

            assertThat(saved.decision())
                    .isIn(
                            ReviewDecision.APPROVED,
                            ReviewDecision.REJECTED
                    );

            assertThat(
                    reviewRepository.findByProposalAttempt_Id(attemptId)
            ).isPresent();

            var after = attempts.find(attemptId).orElseThrow();

            assertThat(after.status)
                    .isEqualTo(ProposalAttemptStatus.VALIDATED);

            assertThat(after.proposalJson.toString())
                    .isEqualTo(proposalBefore);

            assertThat(after.validationJson.toString())
                    .isEqualTo(validationBefore);

        } finally {
            executor.shutdownNow();

            reviewRepository
                    .findByProposalAttempt_Id(attemptId)
                    .ifPresent(reviewRepository::delete);

            attemptRepository.deleteById(attemptId);
        }
    }
}
