package com.audit.webapp.repository;

import com.audit.webapp.entity.GeneratedReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, Long> {
    List<GeneratedReport> findByBatchIdOrderByGeneratedAtDesc(Long batchId);
    List<GeneratedReport> findTop10ByOrderByGeneratedAtDesc();
}
