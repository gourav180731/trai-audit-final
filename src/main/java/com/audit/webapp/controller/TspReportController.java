package com.audit.webapp.controller;

import com.audit.webapp.entity.IngestionBatch;
import com.audit.webapp.repository.DiscrepancyRecordRepository;
import com.audit.webapp.repository.IngestionBatchRepository;
import com.audit.webapp.service.TspReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Controller for TSP-wise report downloads matching sir's manual format.
 */
@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
@Slf4j
public class TspReportController {

    private final TspReportGenerationService reportService;
    private final IngestionBatchRepository batchRepository;
    private final DiscrepancyRecordRepository discrepancyRepository;

    /**
     * Download all 4 TSP reports as a ZIP file
     */
    @GetMapping("/download-all-tsp")
    public ResponseEntity<Resource> downloadAllTspReports(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        try {
            // Get batch or use latest
            IngestionBatch batch = getBatch(batchId);
            if (batch == null) {
                return ResponseEntity.badRequest().build();
            }

            // Determine date range
            LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : getMinDate(batch.getId());
            LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : getMaxDate(batch.getId());

            log.info("Generating all TSP reports for batch {}, date range: {} to {}", batch.getId(), start, end);

            // Generate all 4 reports
            Map<String, byte[]> reports = reportService.generateAllTspReports(batch.getId(), start, end);

            // Create ZIP file
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (Map.Entry<String, byte[]> entry : reports.entrySet()) {
                    String filename = reportService.generateFilename(entry.getKey(), start, end);
                    ZipEntry zipEntry = new ZipEntry(filename);
                    zos.putNextEntry(zipEntry);
                    zos.write(entry.getValue());
                    zos.closeEntry();
                }
            }

            byte[] zipBytes = baos.toByteArray();
            ByteArrayResource resource = new ByteArrayResource(zipBytes);

            String zipFilename = "All_TSP_Discrepancies_" + 
                    start.format(java.time.format.DateTimeFormatter.ofPattern("d_MMMM_yy")) + "_-_" +
                    end.format(java.time.format.DateTimeFormatter.ofPattern("d_MMMM_yy")) + ".zip";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFilename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(zipBytes.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Error generating TSP reports", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download a single TSP report
     */
    @GetMapping("/download-tsp")
    public ResponseEntity<Resource> downloadSingleTspReport(
            @RequestParam String tsp,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        try {
            // Get batch or use latest
            IngestionBatch batch = getBatch(batchId);
            if (batch == null) {
                return ResponseEntity.badRequest().build();
            }

            // Determine date range
            LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : getMinDate(batch.getId());
            LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : getMaxDate(batch.getId());

            log.info("Generating TSP report for {}, batch {}, date range: {} to {}", tsp, batch.getId(), start, end);

            // Generate report
            byte[] reportBytes = reportService.generateSingleTspReport(batch.getId(), tsp, start, end);
            ByteArrayResource resource = new ByteArrayResource(reportBytes);

            String filename = reportService.generateFilename(tsp, start, end);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(reportBytes.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Error generating TSP report for " + tsp, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private IngestionBatch getBatch(Long batchId) {
        if (batchId != null) {
            return batchRepository.findById(batchId).orElse(null);
        }
        // Use latest completed batch
        return batchRepository.findTopByStatusOrderByIngestionTimeDesc(
                IngestionBatch.ProcessingStatus.COMPLETED).orElse(null);
    }

    private LocalDateTime getMinDate(Long batchId) {
        return discrepancyRepository.findByIngestionBatchId(batchId).stream()
                .map(r -> r.getDetectionTime())
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusDays(7));
    }

    private LocalDateTime getMaxDate(Long batchId) {
        return discrepancyRepository.findByIngestionBatchId(batchId).stream()
                .map(r -> r.getDetectionTime())
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
    }
}
