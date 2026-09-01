package com.audit.webapp.entity.live;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_tsp_sms_dissemination_statistics", schema = "dm")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TspSmsDisseminationStatistics {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "identifier")
    private String identifier;

    @Column(name = "tsp_name")
    private String tspName;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "total_subscribers")
    private Long totalSubscribers;

    @Column(name = "total_delivery_success")
    private Long totalDeliverySuccess;

    @Column(name = "total_delivery_failure")
    private Long totalDeliveryFailure;

    @Column(name = "total_cell_count")
    private Long totalCellCount;

    @Column(name = "status")
    private String status;

    @Column(name = "entry_time")
    private LocalDateTime entryTime;

    @Column(name = "remarks_by_tsp")
    private String remarksByTsp;

    @Column(name = "tsp_remarks_received_timestamp")
    private LocalDateTime tspRemarksReceivedTimestamp;

    @Column(name = "response1_received_timestamp")
    private LocalDateTime response1ReceivedTimestamp;

    @Column(name = "response2_received_timestamp")
    private LocalDateTime response2ReceivedTimestamp;

    @Column(name = "remarks_by_capplatform")
    private String remarksByCapplatform;

    @Column(name = "internal_testing_remarks")
    private String internalTestingRemarks;

    @Column(name = "sms_count_success")
    private Long smsCountSuccess;

    @Column(name = "prefetch_start_time")
    private LocalDateTime prefetchStartTime;

    @Column(name = "prefetch_end_time")
    private LocalDateTime prefetchEndTime;

    @Column(name = "prefetch_total_subscribers")
    private Long prefetchTotalSubscribers;

    @Column(name = "prefetch_total_delivery_success")
    private Long prefetchTotalDeliverySuccess;

    @Column(name = "prefetch_total_delivery_failure")
    private Long prefetchTotalDeliveryFailure;

    @Column(name = "prefetch_response2_received_timestamp")
    private LocalDateTime prefetchResponse2ReceivedTimestamp;

    @Column(name = "prefetch_sms_count_success")
    private Long prefetchSmsCountSuccess;

    @Column(name = "delta_received")
    private String deltaReceived;

    @Column(name = "charges")
    private String charges;

    @Column(name = "total_expired")
    private Long totalExpired;

    @Column(name = "sms_count_expired")
    private Long smsCountExpired;
}
