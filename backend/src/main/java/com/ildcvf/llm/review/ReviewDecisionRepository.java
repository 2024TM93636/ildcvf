package com.ildcvf.llm.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ReviewDecisionRepository extends JpaRepository<ReviewDecisionEntity, UUID> {
    Optional<ReviewDecisionEntity> findByProposalAttempt_Id(UUID proposalAttemptId);
    boolean existsByProposalAttempt_Id(UUID proposalAttemptId);
    Page<ReviewDecisionEntity> findByProposalAttempt_Id(UUID proposalAttemptId, Pageable pageable);
}
