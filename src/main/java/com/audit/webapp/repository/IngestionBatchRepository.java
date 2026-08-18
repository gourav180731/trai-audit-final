package com.audit.webapp.repository;

import com.audit.webapp.entity.IngestionBatch;
import com.audit.webapp.entity.IngestionBatch.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IngestionBatchRepository extends JpaRepository<IngestionBatch, Long> {

    List<IngestionBatch> findByStatus(ProcessingStatus status);

    List<IngestionBatch> findByIngestionTimeBetweenOrderByIngestionTimeDesc(
            LocalDateTime start, LocalDateTime end);

    Optional<IngestionBatch> findTopByStatusOrderByIngestionTimeDesc(ProcessingStatus status);

    List<IngestionBatch> findTop10ByOrderByIngestionTimeDesc();
}
