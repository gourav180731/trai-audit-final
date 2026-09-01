package com.audit.webapp.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Projection returned by live SQL checks — contains all context needed to
 * generate a report row and a DB DiscrepancyRecord.
 */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DiscrepancyRow {

    // source row identity
    private Long sourceRowId;
    private String identifier;
    private String tspName;
    private String tspNameCanonical;

    // common context
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime entryTime;
    private Long totalSubscribers;
    private Long totalCellCount;
    private String remarksByCapplatform;
    private String remarksByTsp;

    // timestamps for feedback checks
    private LocalDateTime response1ReceivedTimestamp;
    private LocalDateTime response2ReceivedTimestamp;
    private String deltaReceived;

    // prefetch
    private LocalDateTime prefetchStartTime;
    private LocalDateTime prefetchEndTime;

    // counts for integrity
    private Long totalDeliverySuccess;
    private Long totalDeliveryFailure;
    private Long totalExpired;
    private Long smsCountSuccess;
    private Long smsCountExpired;

    // computed for report
    private String checkType; // e.g. COMPLETE_FAILURE, FEEDBACK_DELAY etc.
    private String actualValue;
    private String expectedValue;
    private String deviation;
    private String reason;
    private String note;
}
