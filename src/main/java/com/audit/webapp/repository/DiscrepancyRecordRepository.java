package com.audit.webapp.repository;

import com.audit.webapp.entity.DiscrepancyRecord;
import com.audit.webapp.entity.DiscrepancyRecord.DiscrepancyStatus;
import com.audit.webapp.entity.DiscrepancyRecord.DiscrepancyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface DiscrepancyRecordRepository extends JpaRepository<DiscrepancyRecord, Long>, 
        JpaSpecificationExecutor<DiscrepancyRecord> {

    List<DiscrepancyRecord> findByIngestionBatchId(Long batchId);

    List<DiscrepancyRecord> findByDiscrepancyType(DiscrepancyType type);

    List<DiscrepancyRecord> findByAlertId(String alertId);

    List<DiscrepancyRecord> findByTsp(String tsp);

    List<DiscrepancyRecord> findByState(String state);

    List<DiscrepancyRecord> findByStatus(DiscrepancyStatus status);

    @Query("SELECT COUNT(DISTINCT d.alertId) FROM DiscrepancyRecord d WHERE d.ingestionBatchId = :batchId")
    Long countDistinctAlertsByBatchId(@Param("batchId") Long batchId);

    @Query("SELECT d.tsp, COUNT(d) FROM DiscrepancyRecord d WHERE d.ingestionBatchId = :batchId GROUP BY d.tsp")
    List<Object[]> countByTspForBatch(@Param("batchId") Long batchId);

    @Query("SELECT d.discrepancyType, COUNT(d) FROM DiscrepancyRecord d WHERE d.ingestionBatchId = :batchId GROUP BY d.discrepancyType")
    List<Object[]> countByTypeForBatch(@Param("batchId") Long batchId);

    @Query("SELECT CAST(d.detectionTime AS date), COUNT(d) FROM DiscrepancyRecord d WHERE d.detectionTime >= :startDate GROUP BY CAST(d.detectionTime AS date) ORDER BY CAST(d.detectionTime AS date)")
    List<Object[]> countByDateSince(@Param("startDate") LocalDateTime startDate);

    long countByIngestionBatchId(Long batchId);
}
