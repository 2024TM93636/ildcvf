package com.ildcvf.contract.version;

import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.persistence.*;
import com.ildcvf.llm.proposal.*;
import com.ildcvf.llm.review.*;
import com.ildcvf.llm.validation.ProposalValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContractVersionPersistenceIntegrationTest {
    private static final String YAML = "openapi: 3.1.0\ninfo:\n  title: Candidate\n  version: 1.0.0\n";
    @Autowired ProposalAttemptPersistenceService attempts;
    @Autowired ReviewDecisionPersistenceService reviews;
    @Autowired ContractVersionPersistenceService versions;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transactions;

    private static String sha(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private UUID validated() {
        String event = "m151-" + UUID.randomUUID().toString().substring(0, 12);
        String finding = event + ":RESPONSE_TYPE_MISMATCH:id";
        UUID id = attempts.start(event, finding);
        attempts.completeValidated(id, new ContractChangeProposal("m151-" + UUID.randomUUID(), event, finding,
            DriftType.RESPONSE_TYPE_MISMATCH, ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
            "GET", "/api/residents/1", "/api/residents/{id}", "id", "integer", "string", "string", "Fixture"),
            ProposalValidationResult.validResult());
        return id;
    }

    private ValidatedContractCandidate candidate(UUID attempt, UUID review, String yaml) {
        return new ValidatedContractCandidate("resident-api-v1.yaml", sha("baseline"),
            attempt, review, yaml, sha(yaml));
    }

    @Test
    void persistsExactYamlMetadataAndLeavesEvidenceUntouched() {
        UUID attempt = validated();
        var before = attempts.find(attempt).orElseThrow();
        String proposalBefore = before.proposalJson.toString();
        String validationBefore = before.validationJson.toString();
        var review = reviews.record(attempt, ReviewDecision.APPROVED, "m151-reviewer", "Disposable fixture");
        var saved = versions.saveValidated(candidate(attempt, review.reviewId(), YAML));
        assertThat(saved.validationStatus()).isEqualTo("VALID");
        assertThat(saved.openapiYaml()).isEqualTo(YAML);
        assertThat(saved.documentSha256()).isEqualTo(sha(YAML));
        assertThat(saved.baselineSha256()).isEqualTo(sha("baseline"));
        var persistedById = versions.find(saved.id()).orElseThrow();
        assertThat(persistedById.id()).isEqualTo(saved.id());
        assertThat(persistedById.baselineIdentifier()).isEqualTo(saved.baselineIdentifier());
        assertThat(persistedById.baselineSha256()).isEqualTo(saved.baselineSha256());
        assertThat(persistedById.proposalAttemptId()).isEqualTo(saved.proposalAttemptId());
        assertThat(persistedById.reviewDecisionId()).isEqualTo(saved.reviewDecisionId());
        assertThat(persistedById.openapiYaml()).isEqualTo(saved.openapiYaml());
        assertThat(persistedById.documentSha256()).isEqualTo(saved.documentSha256());
        assertThat(persistedById.validationStatus()).isEqualTo(saved.validationStatus());

        var persistedByAttempt = versions.findByProposalAttempt(attempt).orElseThrow();
        assertThat(persistedByAttempt.id()).isEqualTo(saved.id());
        assertThat(persistedByAttempt.proposalAttemptId()).isEqualTo(attempt);
        assertThat(attempts.find(attempt).orElseThrow().proposalJson.toString()).isEqualTo(proposalBefore);
        assertThat(attempts.find(attempt).orElseThrow().validationJson.toString()).isEqualTo(validationBefore);
        assertThat(reviews.findByAttempt(attempt).orElseThrow().reviewId()).isEqualTo(review.reviewId());
        assertThatThrownBy(() -> versions.saveValidated(candidate(attempt, review.reviewId(), YAML)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnapprovedMissingAndMismatchedReview() {
        UUID attempt = validated();
        UUID other = validated();
        var rejected = reviews.record(attempt, ReviewDecision.REJECTED, "m151-reviewer", "Fixture rejection");
        var approved = reviews.record(other, ReviewDecision.APPROVED, "m151-reviewer", "Fixture approval");
        assertThatThrownBy(() -> versions.saveValidated(candidate(attempt, rejected.reviewId(), YAML)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> versions.saveValidated(candidate(attempt, approved.reviewId(), YAML)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> versions.saveValidated(candidate(attempt, UUID.randomUUID(), YAML)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> versions.saveValidated(candidate(UUID.randomUUID(), approved.reviewId(), YAML)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInProgressAttemptAndInvalidMetadata() {
        String event = "m151-" + UUID.randomUUID().toString().substring(0, 12);
        UUID pending = attempts.start(event, event + ":finding");
        assertThatThrownBy(() -> versions.saveValidated(candidate(pending, UUID.randomUUID(), YAML)))
            .isInstanceOf(IllegalStateException.class);
        attempts.completeFailure(pending, ProposalAttemptStatus.TIMEOUT, "TIMEOUT", null, "fixture");
        assertThatThrownBy(() -> versions.saveValidated(candidate(pending, UUID.randomUUID(), YAML)))
            .isInstanceOf(IllegalStateException.class);
        UUID attempt = validated();
        var review = reviews.record(attempt, ReviewDecision.APPROVED, "m151-reviewer", "Fixture approval");
        var invalid = new ValidatedContractCandidate("resident-api-v1.yaml", "invalid", attempt,
            review.reviewId(), YAML, sha(YAML));
        assertThatThrownBy(() -> versions.saveValidated(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> versions.saveValidated(candidate(attempt, review.reviewId(), "  ")))
            .isInstanceOf(IllegalArgumentException.class);
        var wrongHash = new ValidatedContractCandidate("resident-api-v1.yaml", sha("baseline"),
            attempt, review.reviewId(), YAML, sha("different"));
        assertThatThrownBy(() -> versions.saveValidated(wrongHash)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historyIsBoundedNewestFirst() {
        UUID a = validated();
        var ra = reviews.record(a, ReviewDecision.APPROVED, "m151-reviewer", "Fixture");
        var first = versions.saveValidated(candidate(a, ra.reviewId(), YAML));
        UUID b = validated();
        var rb = reviews.record(b, ReviewDecision.APPROVED, "m151-reviewer", "Fixture");
        var second = versions.saveValidated(candidate(b, rb.reviewId(), YAML));
        Page<ContractVersionRecord> page = versions.history(0, 1);
        assertThat(page.getContent()).hasSize(1);
        var history = versions.history(0, 100).getContent();

        assertThat(history)
                .extracting(ContractVersionRecord::id)
                .contains(first.id(), second.id());
        assertThat(versions.history(0, 100).getContent()).isSortedAccordingTo(
            (x, y) -> { int t = y.createdAt().compareTo(x.createdAt());
                return t != 0 ? t : y.id().compareTo(x.id()); });
        assertThatThrownBy(() -> versions.history(0, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void databasePreventsDuplicateReviewAndAttempt() {
        UUID attempt = validated();
        var review = reviews.record(attempt, ReviewDecision.APPROVED, "m151-reviewer", "Fixture");
        versions.saveValidated(candidate(attempt, review.reviewId(), YAML));
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> em.createNativeQuery(
            "INSERT INTO contract_versions (id,baseline_identifier,baseline_sha256,proposal_attempt_id," +
            "review_decision_id,openapi_yaml,document_sha256,validation_status,created_at) " +
            "VALUES (:id,'resident-api-v1.yaml',:hash,:attempt,:review,'yaml',:hash,'VALID',CURRENT_TIMESTAMP)")
            .setParameter("id", UUID.randomUUID()).setParameter("hash", sha("baseline"))
            .setParameter("attempt", attempt).setParameter("review", review.reviewId()).executeUpdate()))
            .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    void concurrentCreationHasExactlyOneWinner() throws Exception {
        UUID attempt = validated();
        var review = reviews.record(attempt, ReviewDecision.APPROVED, "m151-reviewer", "Fixture");
        var input = candidate(attempt, review.reviewId(), YAML);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> task = () -> {
                start.await();
                try { versions.saveValidated(input); return true; }
                catch (RuntimeException expectedDuplicate) { return false; }
            };
            Future<Boolean> one = pool.submit(task);
            Future<Boolean> two = pool.submit(task);
            start.countDown();
            assertThat((one.get(30, TimeUnit.SECONDS) ? 1 : 0) +
                (two.get(30, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
            assertThat(versions.findByProposalAttempt(attempt)).isPresent();
        } finally { pool.shutdownNow(); }
    }
}
