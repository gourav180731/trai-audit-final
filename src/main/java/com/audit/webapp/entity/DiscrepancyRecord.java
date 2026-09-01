package com.audit.webapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Core entity persisting every detected discrepancy across all 9 categories.
 * Schema per problem statement Section E.
 */
@Entity
@Table(name = "discrepancy_records", indexes = {
    @Index(name = "idx_alert_id", columnList = "alertId"),
    @Index(name = "idx_tsp", columnList = "tsp"),
    @Index(name = "idx_discrepancy_type", columnList = "discrepancyType"),
    @Index(name = "idx_detection_time", columnList = "detectionTime"),
    @Index(name = "idx_state", columnList = "state"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscrepancyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Core identification fields
    @Column(nullable = false, length = 500)
    private String alertId;

    @Column(nullable = false, length = 100)
    private String tsp;

    @Column(nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private DiscrepancyType discrepancyType;

    @Column(nullable = false)
    private LocalDateTime detectionTime;

    // Supporting data for drill-down (problem statement Section E)
    @Column(length = 200)
    private String state;

    @Column(length = 200)
    private String event;

    @Column(length = 500)
    private String alertCreationTime;

    @Column(length = 1000)
    private String areaDescription;

    // Relevant parameters that drove the detection
    @Column(columnDefinition = "TEXT")
    private String relevantParameters;

    @Column(length = 200)
    private String actualValue;

    @Column(length = 200)
    private String expectedValue;

    @Column(length = 200)
    private String deviation;

    // Status and processing
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DiscrepancyStatus status = DiscrepancyStatus.OPEN;

    // Additional context
    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String note;

    // Metadata for filtering (some may be null until data source provides them)
    private Integer slNo;

    @Column(length = 200)
    private String district; // Not in current input files - placeholder

    @Column(length = 200)
    private String alertAuthorizingAgency; // Not in current input files - placeholder

    @Column(length = 50)
    private String severity; // Not in current input files - placeholder

    @Column(length = 50)
    private String priority; // Not in current input files - placeholder

    private Long cellCount;
    private Long subscriberCount;
    private Long smsCount;
    private Long disseminationDurationSeconds;
    private Long feedbackDelaySeconds;

    // For drill-down navigation
    @Column(nullable = false)
    private Long ingestionBatchId;

    // Traceability back to live dm row
    private Long sourceRowId;

    public enum DiscrepancyStatus {
        OPEN,
        ACKNOWLEDGED,
        UNDER_REVIEW,
        RESOLVED,
        FALSE_POSITIVE
    }

    public enum DiscrepancyType {
        // Live 7 checks (current)
        COMPLETE_FAILURE,
        STATISTICS_PENDING, // used for feedback_not_received (generic)
        FEEDBACK_DELAY_EXCEEDS_THRESHOLD,
        PREFETCH_DURATION_MATRIX_BREACH,
        TOTAL_DURATION_MATRIX_BREACH,
        INORDINATE_SUBSCRIBER_RATIO,
        EXPIRED_NONZERO,
        ARITHMETIC_MISMATCH,
        // Legacy Excel-era sub-categories (kept for backward compat / dashboard)
        ZERO_SUBSCRIBER_WITH_CELL_COUNT,
        ZERO_SUBSCRIBER_WITHOUT_CELL_COUNT,
        STATISTICS_AWAITED,
        DELTA_PENDING,
        DISSEMINATION_COMPLETED_ZERO_PREFETCH,
        DISSEMINATED_AFTER_EXPIRY
    }
}
