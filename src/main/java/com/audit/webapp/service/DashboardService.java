package com.audit.webapp.service;

import com.audit.webapp.entity.DiscrepancyRecord;
import com.audit.webapp.entity.DiscrepancyRecord.DiscrepancyType;
import com.audit.webapp.entity.IngestionBatch;
import com.audit.webapp.entity.IngestionBatch.ProcessingStatus;
import com.audit.webapp.repository.DiscrepancyRecordRepository;
import com.audit.webapp.repository.IngestionBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for dashboard data aggregation and summary statistics
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final IngestionBatchRepository batchRepository;
    private final DiscrepancyRecordRepository discrepancyRepository;

    public IngestionBatch getLatestCompletedBatch() {
        return batchRepository.findTopByStatusOrderByIngestionTimeDesc(ProcessingStatus.COMPLETED)
                .orElse(null);
    }

    public List<IngestionBatch> getRecentBatches(int limit) {
        return batchRepository.findTop10ByOrderByIngestionTimeDesc();
    }

    public Map<String, Object> getDashboardSummary(Long batchId) {
        IngestionBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) return Collections.emptyMap();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalAlertsProcessed", batch.getTotalAlertsProcessed());
        summary.put("totalAlertsWithDiscrepancies", batch.getTotalAlertsWithDiscrepancies());
        summary.put("totalDiscrepancyInstances", batch.getTotalDiscrepancyInstances());
        summary.put("totalTspRowsProcessed", batch.getTotalTspRowsProcessed());
        summary.put("ingestionTime", batch.getIngestionTime());
        summary.put("warningReportFilename", batch.getWarningReportFilename());
        summary.put("traiBaselineFilename", batch.getTraiBaselineFilename());

        return summary;
    }

    public Map<String, Long> getTspWiseCount(Long batchId) {
        List<Object[]> results = discrepancyRepository.countByTspForBatch(batchId);
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1],
                        Long::sum,
                        LinkedHashMap::new
                ));
    }

    public Map<DiscrepancyType, Long> getCategoryWiseCount(Long batchId) {
        List<Object[]> results = discrepancyRepository.countByTypeForBatch(batchId);
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (DiscrepancyType) r[0],
                        r -> (Long) r[1],
                        Long::sum,
                        LinkedHashMap::new
                ));
    }

    public List<Map<String, Object>> getDateWiseTrend(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Object[]> results = discrepancyRepository.countByDateSince(startDate);
        
        return results.stream()
                .map(r -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("date", r[0]);
                    entry.put("count", r[1]);
                    return entry;
                })
                .collect(Collectors.toList());
    }

    public Map<String, List<DiscrepancyRecord>> groupByTsp(List<DiscrepancyRecord> records) {
        return records.stream()
                .collect(Collectors.groupingBy(
                        DiscrepancyRecord::getTsp,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    public Map<DiscrepancyType, List<DiscrepancyRecord>> groupByType(List<DiscrepancyRecord> records) {
        return records.stream()
                .collect(Collectors.groupingBy(
                        DiscrepancyRecord::getDiscrepancyType,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }
}
