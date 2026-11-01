package com.ildcvf.llm.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProposalAttemptRepository extends JpaRepository<ProposalAttemptEntity,UUID> {
    @Query("""
        select a from ProposalAttemptEntity a
        where (:eventId is null or a.trafficEventId = :eventId)
          and (:findingId is null or a.findingId = :findingId)
          and (:status is null or a.status = :status)
        """)
    Page<ProposalAttemptEntity> search(@Param("eventId") String eventId,
        @Param("findingId") String findingId,
        @Param("status") ProposalAttemptStatus status, Pageable pageable);

    List<ProposalAttemptEntity> findByOrderByCreatedAtDescIdDesc(Pageable pageable);
    List<ProposalAttemptEntity> findByTrafficEventIdOrderByCreatedAtDescIdDesc(String trafficEventId, Pageable pageable);
    List<ProposalAttemptEntity> findByTrafficEventIdAndFindingIdOrderByCreatedAtDescIdDesc(String trafficEventId,String findingId,Pageable pageable);
    List<ProposalAttemptEntity> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(ProposalAttemptStatus status,Instant cutoff,Pageable pageable);
}
