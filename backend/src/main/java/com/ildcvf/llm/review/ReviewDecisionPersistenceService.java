package com.ildcvf.llm.review;

import com.ildcvf.llm.persistence.ProposalAttemptEntity;
import com.ildcvf.llm.persistence.ProposalAttemptStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** Persistence-only foundation. Caller-supplied reviewer ID is NOT authenticated. */
@Service
public class ReviewDecisionPersistenceService {
    private final ReviewDecisionRepository reviews;
    private final EntityManager em;
    private final Clock clock;

    public ReviewDecisionPersistenceService(ReviewDecisionRepository reviews, EntityManager em) {
        this.reviews = reviews;
        this.em = em;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public ReviewDecisionRecord record(UUID attemptId, ReviewDecision decision,
                                       String reviewerId, String justification) {
        if (attemptId == null || decision == null) throw new IllegalArgumentException("Attempt and decision are required");
        String reviewer = requiredText(reviewerId, 128, "reviewerId");
        String reason = requiredText(justification, 4000, "justification");
        // Lock the parent row: competing review writers serialize even when no review row exists yet.
        ProposalAttemptEntity attempt = em.find(ProposalAttemptEntity.class, attemptId, LockModeType.PESSIMISTIC_WRITE);
        if (attempt == null) throw new IllegalArgumentException("Proposal attempt does not exist");
        if (attempt.status != ProposalAttemptStatus.VALIDATED)
            throw new IllegalStateException("Only VALIDATED proposal attempts can be reviewed");
        if (reviews.existsByProposalAttempt_Id(attemptId))
            throw new IllegalStateException("A final review decision already exists");
        ReviewDecisionEntity entity = new ReviewDecisionEntity(UUID.randomUUID(), attempt,
            decision, reviewer, reason, clock.instant());
        reviews.saveAndFlush(entity);
        if (entity.proposalAttempt == null || entity.proposalAttempt.id == null) {
            throw new IllegalStateException(
                    "Review decision has no proposal attempt ID"
            );
        }
        return ReviewDecisionRecord.from(entity);
    }

    @Transactional(readOnly = true)
    public Optional<ReviewDecisionRecord> findByAttempt(UUID attemptId) {
        if (attemptId == null) throw new IllegalArgumentException("attemptId is required");
        return reviews.findByProposalAttempt_Id(attemptId).map(ReviewDecisionRecord::from);
    }

    @Transactional(readOnly = true)
    public Page<ReviewDecisionRecord> history(int page, int size) {
        return reviews.findAll(pageRequest(page, size)).map(ReviewDecisionRecord::from);
    }

    @Transactional(readOnly = true)
    public Page<ReviewDecisionRecord> historyForAttempt(UUID attemptId, int page, int size) {
        if (attemptId == null) throw new IllegalArgumentException("attemptId is required");
        return reviews.findByProposalAttempt_Id(attemptId, pageRequest(page, size)).map(ReviewDecisionRecord::from);
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("decidedAt"), Sort.Order.desc("id")));
    }

    private static String requiredText(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException(field + " must be nonblank and at most " + max + " characters");
        return value.trim();
    }
}
