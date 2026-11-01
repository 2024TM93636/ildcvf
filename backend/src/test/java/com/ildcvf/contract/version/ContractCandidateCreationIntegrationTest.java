package com.ildcvf.contract.version;

import com.ildcvf.contract.generation.ContractCandidateGenerator;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.persistence.*;
import com.ildcvf.llm.proposal.*;
import com.ildcvf.llm.review.*;
import com.ildcvf.llm.validation.ProposalValidationResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.*;
import com.ildcvf.contract.generation.CandidateGenerationException;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContractCandidateCreationIntegrationTest {

    @Autowired ContractCandidateCreationService creation;
    @Autowired ContractCandidateGenerator generator;
    @Autowired ContractVersionPersistenceService versions;
    @Autowired ProposalAttemptPersistenceService attempts;
    @Autowired ReviewDecisionPersistenceService reviews;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transactions;
    @Autowired ObjectMapper mapper;

    private final List<UUID> createdAttemptIds = new ArrayList<>();

    private UUID startTrackedAttempt(String event, String finding) {
        UUID id = attempts.start(event, finding);
        createdAttemptIds.add(id);
        return id;
    }

    private ContractChangeProposal proposal(String event, String finding) {
        return new ContractChangeProposal(
                "m152b-" + UUID.randomUUID(),
                event,
                finding,
                DriftType.RESPONSE_TYPE_MISMATCH,
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "GET",
                "/api/residents/1",
                "/api/residents/{id}",
                "id",
                "integer",
                "string",
                "string",
                "Disposable M15.2B integration fixture"
        );
    }

    private UUID validatedAttempt() {
        String event = "m152b-" + UUID.randomUUID().toString().substring(0, 12);
        String finding = event + ":RESPONSE_TYPE_MISMATCH:id";

        UUID id = startTrackedAttempt(event, finding);

        attempts.completeValidated(
                id,
                proposal(event, finding),
                ProposalValidationResult.validResult()
        );

        return id;
    }

    @Test
    void approvedValidatedProposalCreatesPersistedCandidate() throws Exception {
        UUID attemptId = validatedAttempt();

        var review = reviews.record(
                attemptId,
                ReviewDecision.APPROVED,
                "m152b-test-reviewer",
                "Disposable approved integration fixture"
        );

        var storedProposal = attempts.find(attemptId).orElseThrow();
        String proposalBefore = storedProposal.proposalJson.toString();
        String validationBefore = storedProposal.validationJson.toString();

        var generated = generator.generate(
                new tools.jackson.databind.ObjectMapper().treeToValue(
                        storedProposal.proposalJson,
                        ContractChangeProposal.class
                )
        );

        var created = creation.create(attemptId);

        assertThat(created.id()).isNotNull();
        assertThat(created.proposalAttemptId()).isEqualTo(attemptId);
        assertThat(created.reviewDecisionId()).isEqualTo(review.reviewId());
        assertThat(created.validationStatus()).isEqualTo("VALID");

        assertThat(created.openapiYaml()).isEqualTo(generated.openapiYaml());
        assertThat(created.baselineSha256()).isEqualTo(generated.baselineSha256());
        assertThat(created.documentSha256()).isEqualTo(generated.documentSha256());

        var recovered = versions.find(created.id()).orElseThrow();

// Verify persistence without relying on exact timestamp precision.
        assertThat(recovered.id()).isEqualTo(created.id());
        assertThat(recovered.baselineIdentifier())
                .isEqualTo(created.baselineIdentifier());
        assertThat(recovered.baselineSha256())
                .isEqualTo(created.baselineSha256());
        assertThat(recovered.proposalAttemptId())
                .isEqualTo(created.proposalAttemptId());
        assertThat(recovered.reviewDecisionId())
                .isEqualTo(created.reviewDecisionId());
        assertThat(recovered.openapiYaml()).isEqualTo(created.openapiYaml());
        assertThat(recovered.documentSha256())
                .isEqualTo(created.documentSha256());
        assertThat(recovered.validationStatus())
                .isEqualTo(created.validationStatus());

// Verify the database timestamp is preserved to its supported precision.
        assertThat(recovered.createdAt())
                .isCloseTo(created.createdAt(),
                        within(1, java.time.temporal.ChronoUnit.MICROS));

        assertThat(versions.findByProposalAttempt(attemptId))
                .contains(recovered);

        assertThat(attempts.find(attemptId).orElseThrow()
                .proposalJson.toString()).isEqualTo(proposalBefore);

        assertThat(attempts.find(attemptId).orElseThrow()
                .validationJson.toString()).isEqualTo(validationBefore);

        var recoveredReview =
                reviews.findByAttempt(attemptId).orElseThrow();

        assertThat(recoveredReview.reviewId())
                .isEqualTo(review.reviewId());
        assertThat(recoveredReview.proposalAttemptId())
                .isEqualTo(review.proposalAttemptId());
        assertThat(recoveredReview.decision())
                .isEqualTo(review.decision());
        assertThat(recoveredReview.reviewerId())
                .isEqualTo(review.reviewerId());
        assertThat(recoveredReview.justification())
                .isEqualTo(review.justification());

        assertThat(recoveredReview.decidedAt())
                .isCloseTo(
                        review.decidedAt(),
                        within(1, java.time.temporal.ChronoUnit.MICROS)
                );
    }

    @Test
    void missingReviewCannotCreateCandidate() {
        UUID attemptId = validatedAttempt();

        assertThatThrownBy(() -> creation.create(attemptId))
                .isInstanceOf(ContractCandidateCreationException.class)
                .satisfies(error -> {
                    var failure =
                            (ContractCandidateCreationException) error;

                    assertThat(failure.code()).isEqualTo(
                            ContractCandidateCreationException.Code.REVIEW_MISSING
                    );
                });

        assertThat(versions.findByProposalAttempt(attemptId))
                .isEmpty();
    }

    @Test
    void rejectedReviewCannotCreateCandidate() {
        UUID attemptId = validatedAttempt();

        reviews.record(
                attemptId,
                ReviewDecision.REJECTED,
                "m152b-test-reviewer",
                "Disposable rejected integration fixture"
        );

        assertThatThrownBy(() -> creation.create(attemptId))
                .isInstanceOf(ContractCandidateCreationException.class)
                .satisfies(error -> {
                    var failure =
                            (ContractCandidateCreationException) error;

                    assertThat(failure.code()).isEqualTo(
                            ContractCandidateCreationException.Code.REVIEW_REJECTED
                    );
                });

        assertThat(versions.findByProposalAttempt(attemptId))
                .isEmpty();
    }

    @Test
    void inProgressProposalCannotCreateCandidate() {
        String event = "m152b-" +
                UUID.randomUUID().toString().substring(0, 12);

        UUID attemptId = startTrackedAttempt(
                event,
                event + ":RESPONSE_TYPE_MISMATCH:id"
        );

        assertThatThrownBy(() -> creation.create(attemptId))
                .isInstanceOf(ContractCandidateCreationException.class)
                .satisfies(error -> {
                    var failure =
                            (ContractCandidateCreationException) error;

                    assertThat(failure.code()).isEqualTo(
                            ContractCandidateCreationException.Code.PROPOSAL_NOT_VALIDATED
                    );
                });

        assertThat(versions.findByProposalAttempt(attemptId))
                .isEmpty();

        // Finish the disposable attempt so it does not remain IN_PROGRESS.
        attempts.completeFailure(
                attemptId,
                ProposalAttemptStatus.TIMEOUT,
                "TEST_FIXTURE",
                null,
                "Disposable integration fixture completed"
        );
    }

    @Test
    void duplicateCreationIsRejectedWithoutCreatingAnotherVersion() {
        UUID attemptId = validatedAttempt();

        var review = reviews.record(
                attemptId,
                ReviewDecision.APPROVED,
                "m152b-test-reviewer",
                "Disposable duplicate-creation fixture"
        );

        var first = creation.create(attemptId);

        assertThat(first.id()).isNotNull();
        assertThat(first.reviewDecisionId())
                .isEqualTo(review.reviewId());

        assertThatThrownBy(() -> creation.create(attemptId))
                .isInstanceOf(ContractCandidateCreationException.class)
                .satisfies(error -> {
                    var failure =
                            (ContractCandidateCreationException) error;

                    assertThat(failure.code()).isEqualTo(
                            ContractCandidateCreationException.Code.VERSION_EXISTS
                    );
                });

        var persisted = versions.findByProposalAttempt(attemptId)
                .orElseThrow();

        assertThat(persisted.id()).isEqualTo(first.id());
        assertThat(persisted.reviewDecisionId())
                .isEqualTo(review.reviewId());
    }

    @Test
    void concurrentCreationProducesExactlyOneVersion() throws Exception {
        UUID attemptId = validatedAttempt();

        var review = reviews.record(
                attemptId,
                ReviewDecision.APPROVED,
                "m152b-test-reviewer",
                "Disposable concurrency fixture"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Boolean> task = () -> {
                start.await();

                try {
                    creation.create(attemptId);
                    return true;

                } catch (ContractCandidateCreationException ex) {
                    assertThat(ex.code()).isIn(
                            ContractCandidateCreationException.Code.VERSION_EXISTS,
                            ContractCandidateCreationException.Code.PERSISTENCE_CONFLICT
                    );
                    return false;
                }
            };

            Future<Boolean> first = executor.submit(task);
            Future<Boolean> second = executor.submit(task);

            start.countDown();

            boolean firstSucceeded = first.get(30, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(30, TimeUnit.SECONDS);

            assertThat(
                    (firstSucceeded ? 1 : 0)
                            + (secondSucceeded ? 1 : 0)
            ).isEqualTo(1);

            var persisted = versions.findByProposalAttempt(attemptId)
                    .orElseThrow();

            assertThat(persisted.proposalAttemptId())
                    .isEqualTo(attemptId);

            assertThat(persisted.reviewDecisionId())
                    .isEqualTo(review.reviewId());

            assertThat(persisted.validationStatus())
                    .isEqualTo("VALID");

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void nonexistentProposalCannotCreateCandidate() {
        UUID unknownAttemptId = UUID.randomUUID();

        assertThatThrownBy(() -> creation.create(unknownAttemptId))
                .isInstanceOf(ContractCandidateCreationException.class)
                .satisfies(error -> {
                    var failure =
                            (ContractCandidateCreationException) error;

                    assertThat(failure.code()).isEqualTo(
                            ContractCandidateCreationException.Code.PROPOSAL_NOT_FOUND
                    );
                });

        assertThat(versions.findByProposalAttempt(unknownAttemptId))
                .isEmpty();
    }

    private void assertCreationFailure(
            UUID attemptId,
            ContractCandidateCreationException.Code expectedCode) {

        assertThatThrownBy(() -> creation.create(attemptId))
                .isInstanceOf(ContractCandidateCreationException.class)
                .satisfies(error -> assertThat(
                        ((ContractCandidateCreationException) error).code()
                ).isEqualTo(expectedCode));

        // A null attempt ID cannot be used for a persistence lookup.
        if (attemptId != null) {
            assertThat(versions.findByProposalAttempt(attemptId)).isEmpty();
        }
    }

    @Test
    void nullProposalAttemptIsRejected() {
        assertCreationFailure(
                null,
                ContractCandidateCreationException.Code.PROPOSAL_NOT_FOUND
        );
    }

    @Test
    void unsupportedProposalCannotCreateCandidate() {
        String event = "m152b-" + UUID.randomUUID().toString().substring(0, 12);
        String finding = event + ":RESPONSE_TYPE_MISMATCH:id";

        UUID attemptId = startTrackedAttempt(event, finding);

        var unsupported = new ContractChangeProposal(
                "m152b-" + UUID.randomUUID(),
                event,
                finding,
                DriftType.RESPONSE_TYPE_MISMATCH,
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                "GET",
                "/api/residents/1",
                "/api/residents/{id}",
                "name",
                "integer",
                "string",
                "string",
                "Disposable unsupported-target fixture"
        );

        attempts.completeValidated(
                attemptId,
                unsupported,
                ProposalValidationResult.validResult()
        );

        reviews.record(
                attemptId,
                ReviewDecision.APPROVED,
                "m152b-test-reviewer",
                "Disposable unsupported-target review"
        );

        assertCreationFailure(
                attemptId,
                ContractCandidateCreationException.Code.UNSUPPORTED_PROPOSAL
        );
    }

    @Test
    void malformedPersistedProposalCannotCreateCandidate() {
        UUID attemptId = validatedAttempt();

        reviews.record(
                attemptId,
                ReviewDecision.APPROVED,
                "m152b-test-reviewer",
                "Disposable malformed-JSON fixture"
        );

        // Change only the disposable fixture created by this test.
        transactions.executeWithoutResult(tx -> {
            em.createNativeQuery(
                            "UPDATE llm_proposal_attempts " +
                                    "SET proposal_json = CAST(:json AS jsonb) " +
                                    "WHERE id = :id"
                    )
                    .setParameter("json", "[]")
                    .setParameter("id", attemptId)
                    .executeUpdate();
        });

        assertCreationFailure(
                attemptId,
                ContractCandidateCreationException.Code.INVALID_PROPOSAL_JSON
        );
    }

    @Test
    void generatedCandidateMatchesBaselineAndItsChecksums() throws Exception {
        UUID attemptId = validatedAttempt();

        reviews.record(
                attemptId,
                ReviewDecision.APPROVED,
                "m152b-test-reviewer",
                "Disposable checksum verification fixture"
        );

        byte[] baselineBefore = Files.readAllBytes(
                Path.of("../datasets/openapi/baseline/resident-api-v1.yaml")
        );

        var created = creation.create(attemptId);

        byte[] baselineAfter = Files.readAllBytes(
                Path.of("../datasets/openapi/baseline/resident-api-v1.yaml")
        );

        assertThat(baselineAfter).isEqualTo(baselineBefore);

        String baselineHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(baselineBefore)
        );

        String documentHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                        created.openapiYaml().getBytes(StandardCharsets.UTF_8)
                )
        );

        assertThat(created.baselineSha256()).isEqualTo(baselineHash);
        assertThat(created.documentSha256()).isEqualTo(documentHash);
        assertThat(created.baselineIdentifier())
                .isEqualTo("resident-api-v1.yaml");
    }

    @AfterEach
    void cleanupDisposableFixtures() {
        if (createdAttemptIds.isEmpty()) {
            return;
        }

        List<UUID> ids = List.copyOf(createdAttemptIds);

        transactions.executeWithoutResult(status -> {

            // Delete only contract versions associated with this test's
            // explicitly tracked proposal-attempt UUIDs.
            em.createNativeQuery(
                            "DELETE FROM contract_versions " +
                                    "WHERE proposal_attempt_id IN (:ids)"
                    )
                    .setParameter("ids", ids)
                    .executeUpdate();

            // Delete the corresponding human-review decisions.
            em.createNativeQuery(
                            "DELETE FROM llm_review_decisions " +
                                    "WHERE proposal_attempt_id IN (:ids)"
                    )
                    .setParameter("ids", ids)
                    .executeUpdate();

            // Delete only the proposal attempts created by this test.
            em.createNativeQuery(
                            "DELETE FROM llm_proposal_attempts " +
                                    "WHERE id IN (:ids)"
                    )
                    .setParameter("ids", ids)
                    .executeUpdate();
        });

        createdAttemptIds.clear();
    }

}
