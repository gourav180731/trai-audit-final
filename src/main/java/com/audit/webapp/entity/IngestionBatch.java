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

    // Live-DB run filters (nullable for legacy file runs)
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    @Column(length = 200)
    private String tspFilter;
    @Column(length = 200)
    private String triggeredBy;

    @Builder.Default
    @Column(nullable = false)
    private Integer totalAlertsProcessed = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer totalTspRowsProcessed = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer totalAlertsWithDiscrepancies = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer totalDiscrepancyInstances = 0;

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
    // Check 7 (new, live-schema)
    private Integer countExpiredNonzero;
    private Integer countArithmeticMismatch;

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
