package com.audit.webapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_reports")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class GeneratedReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    private String generatedBy;

    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;

    private String tspFilter;

    private String fileName;
    private Long fileSizeBytes;
    private Integer discrepancyCount;
    private String checksumSha256;

    @Builder.Default
    private Boolean emailSent = false;
    private LocalDateTime emailSentAt;
    @Column(columnDefinition = "TEXT")
    private String emailRecipients;
    private String emailSubject;
    private String emailStatus;
    @Column(columnDefinition = "TEXT")
    private String emailError;
}
