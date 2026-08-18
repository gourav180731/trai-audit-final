package com.audit.webapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tracks each ingestion/processing run of input files, allowing drill-down by batch
 * and historical trend analysis.
 */
@Entity
@Table(name = "ingestion_batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime ingestionTime;

    @Column(nullable = false, length = 500)
    private String warningReportFilename;

    @Column(nullable = false, length = 500)
    private String traiBaselineFilename;

    @Column(nullable = false)
    private Integer totalAlertsProcessed;

    @Column(nullable = false)
    private Integer totalTspRowsProcessed;

    @Column(nullable = false)
    private Integer totalAlertsWithDiscrepancies;

    @Column(nullable = false)
    private Integer totalDiscrepancyInstances;

    // Category-wise counts for dashboard
    private Integer countCompleteFailure;
    private Integer countZeroSubscriberWithCellCount;
    private Integer countZeroSubscriberWithoutCellCount;
    private Integer countStatisticsPending;
    private Integer countStatisticsAwaited;
    private Integer countDeltaPending;
    private Integer countFeedbackDelayExceeds;
    private Integer countPrefetchDurationBreach;
    private Integer countTotalDurationBreach;
    private Integer countInordinateRatio;
    private Integer countDisseminationCompletedZeroPrefetch;
    private Integer countDisseminatedAfterExpiry;

    @Column(length = 50)
    @Enumerated(EnumType.STRING)
    private ProcessingStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public enum ProcessingStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
