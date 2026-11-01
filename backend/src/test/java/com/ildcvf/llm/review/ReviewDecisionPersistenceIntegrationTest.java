package com.ildcvf.llm.review;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.persistence.*;
import com.ildcvf.llm.proposal.*;
import com.ildcvf.llm.validation.ProposalValidationResult;
import com.ildcvf.llm.validation.ProposalValidationError;
import com.ildcvf.llm.validation.ProposalValidationCode;

import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReviewDecisionPersistenceIntegrationTest {
    private static final String EVENT = "m141-" + UUID.randomUUID().toString().substring(0, 12);
    private static final String FINDING = EVENT + ":RESPONSE_TYPE_MISMATCH:id";
    private static UUID approvedAttempt;
    private static UUID rejectedAttempt;
    private static UUID approvedReview;

    @Autowired ProposalAttemptPersistenceService attempts;
    @Autowired ProposalAttemptRepository attemptRepository;
    @Autowired ReviewDecisionPersistenceService reviews;
    @Autowired ReviewDecisionRepository reviewRepository;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transactions;

    private UUID validated(String label) {
        UUID id = attempts.start(EVENT, FINDING);
        attempts.completeValidated(id, new ContractChangeProposal(label, EVENT, FINDING,
            DriftType.RESPONSE_TYPE_MISMATCH, ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
            "GET", "/api/residents/1", "/api/residents/{id}", "id", "integer", "string", "string", "Reason"),
            ProposalValidationResult.validResult());
        return id;
    }

    @Test @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void approvedDecisionPersistsWithReviewerJustificationAndOriginalEvidenceUnchanged() {
        approvedAttempt = validated("m141-approved");
        var before = attempts.find(approvedAttempt).orElseThrow();
        String proposal = before.proposalJson.toString();
        String validation = before.validationJson.toString();
        var decision = reviews.record(approvedAttempt, ReviewDecision.APPROVED, " operator-01 ", " Verified against captured traffic ");
        approvedReview = decision.reviewId();
        assertThat(decision.decision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(decision.reviewerId()).isEqualTo("operator-01");
        assertThat(decision.justification()).isEqualTo("Verified against captured traffic");
        var persisted = reviews.findByAttempt(approvedAttempt).orElseThrow();

        assertThat(persisted.reviewId()).isEqualTo(decision.reviewId());
        assertThat(persisted.proposalAttemptId())
                .isEqualTo(decision.proposalAttemptId());
        assertThat(persisted.decision()).isEqualTo(decision.decision());
        assertThat(persisted.reviewerId()).isEqualTo(decision.reviewerId());
        assertThat(persisted.justification()).isEqualTo(decision.justification());

        assertThat(persisted.decidedAt()).isNotNull();
        assertThat(persisted.decidedAt())
                .isCloseTo(decision.decidedAt(), within(1, ChronoUnit.MICROS));

        assertThat(attempts.find(approvedAttempt).orElseThrow()
                .proposalJson.toString()).isEqualTo(proposal);

        assertThat(attempts.find(approvedAttempt).orElseThrow()
                .validationJson.toString()).isEqualTo(validation);
    }

    @Test @Order(2)
    void reviewSurvivesFreshApplicationContext() {
        var recovered = reviews.findByAttempt(approvedAttempt).orElseThrow();
        assertThat(recovered.reviewId()).isEqualTo(approvedReview);
        assertThat(recovered.decision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(recovered.reviewerId()).isEqualTo("operator-01");
        assertThat(attempts.find(approvedAttempt).orElseThrow().status).isEqualTo(ProposalAttemptStatus.VALIDATED);
    }


    @Test
    @Order(3)
    void rejectedDecisionAndBoundedHistoryPersist() {
        rejectedAttempt = validated("m141-rejected");

        var rejected = reviews.record(
                rejectedAttempt,
                ReviewDecision.REJECTED,
                "operator-02",
                "Not appropriate"
        );

        assertThat(rejected.decision()).isEqualTo(ReviewDecision.REJECTED);

        var persisted = reviews.findByAttempt(rejectedAttempt).orElseThrow();

        assertThat(persisted.reviewId()).isEqualTo(rejected.reviewId());
        assertThat(persisted.proposalAttemptId()).isEqualTo(rejected.proposalAttemptId());
        assertThat(persisted.decision()).isEqualTo(rejected.decision());
        assertThat(persisted.reviewerId()).isEqualTo(rejected.reviewerId());
        assertThat(persisted.justification()).isEqualTo(rejected.justification());
        assertThat(persisted.decidedAt()).isCloseTo(
                rejected.decidedAt(), within(1, ChronoUnit.MICROS)
        );

        var history = reviews.historyForAttempt(rejectedAttempt, 0, 10)
                .getContent();

        assertThat(history).hasSize(1);

        var historyRecord = history.get(0);

        assertThat(historyRecord.reviewId()).isEqualTo(rejected.reviewId());
        assertThat(historyRecord.proposalAttemptId()).isEqualTo(rejected.proposalAttemptId());
        assertThat(historyRecord.decision()).isEqualTo(rejected.decision());
        assertThat(historyRecord.reviewerId()).isEqualTo(rejected.reviewerId());
        assertThat(historyRecord.justification()).isEqualTo(rejected.justification());
        assertThat(historyRecord.decidedAt()).isCloseTo(
                rejected.decidedAt(), within(1, ChronoUnit.MICROS)
        );

        assertThat(reviews.history(0, 1).getContent()).hasSize(1);

        assertThatThrownBy(() -> reviews.history(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @Order(4)
    void duplicateDecisionRejected() {
        assertThatThrownBy(() -> reviews.record(approvedAttempt, ReviewDecision.REJECTED, "operator-03", "Change mind"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(reviews.findByAttempt(approvedAttempt).orElseThrow().decision()).isEqualTo(ReviewDecision.APPROVED);
    }

    @Test @Order(5)
    void allIneligibleStatusesRejected() {
        for (ProposalAttemptStatus status : new ProposalAttemptStatus[] {
            ProposalAttemptStatus.IN_PROGRESS, ProposalAttemptStatus.REJECTED,
            ProposalAttemptStatus.PROVIDER_FAILURE, ProposalAttemptStatus.PARSE_FAILURE, ProposalAttemptStatus.TIMEOUT}) {
            UUID id = attempts.start(EVENT, FINDING);
            if (status == ProposalAttemptStatus.REJECTED) {
                // A rejected attempt is a completed attempt with an invalid validation result.
                attempts.completeRejected(id, new ContractChangeProposal("m141-invalid", EVENT, FINDING,
                    DriftType.RESPONSE_TYPE_MISMATCH, ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                    "GET", "/api/residents/1", "/api/residents/{id}", "id", "integer", "string", "string", "Reason"),
                    ProposalValidationResult.invalid(List.of(new ProposalValidationError(
                        ProposalValidationCode.PROPOSED_VALUE_NOT_GROUNDED, "test"))));
            } else if (status != ProposalAttemptStatus.IN_PROGRESS) {
                attempts.completeFailure(id, status, "ERROR", 500, "test");
            }
            assertThatThrownBy(() -> reviews.record(id, ReviewDecision.APPROVED, "operator", "reason"))
                .isInstanceOf(IllegalStateException.class);
            if (status == ProposalAttemptStatus.IN_PROGRESS)
                attempts.completeFailure(id, ProposalAttemptStatus.TIMEOUT, "TIMEOUT", null, "test");
        }
    }

    @Test @Order(6)
    void foreignKeyAndUniquenessAreDatabaseEnforced() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> em.createNativeQuery(
            "INSERT INTO llm_review_decisions(id,proposal_attempt_id,decision,reviewer_id,justification,decided_at) " +
            "VALUES (:id,:attempt,'APPROVED','operator','reason',CURRENT_TIMESTAMP)")
            .setParameter("id", UUID.randomUUID()).setParameter("attempt", UUID.randomUUID()).executeUpdate()))
            .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> em.createNativeQuery(
            "INSERT INTO llm_review_decisions(id,proposal_attempt_id,decision,reviewer_id,justification,decided_at) " +
            "VALUES (:id,:attempt,'REJECTED','operator','reason',CURRENT_TIMESTAMP)")
            .setParameter("id", UUID.randomUUID()).setParameter("attempt", approvedAttempt).executeUpdate()))
            .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test @Order(7)
    void rejectsInvalidReviewerAndJustification() {
        UUID id = validated("m141-validation");
        assertThatThrownBy(() -> reviews.record(id, ReviewDecision.APPROVED, " ", "reason"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reviews.record(id, ReviewDecision.APPROVED, "operator", " "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @AfterAll
    static void cleanup(@Autowired ReviewDecisionRepository reviews,
                        @Autowired ProposalAttemptRepository attempts) {
        var source = attempts.findByTrafficEventIdOrderByCreatedAtDescIdDesc(EVENT,
            org.springframework.data.domain.PageRequest.of(0, 100));
        for (var attempt : source) reviews.findByProposalAttempt_Id(attempt.id).ifPresent(reviews::delete);
        attempts.deleteAll(source);
    }
}
