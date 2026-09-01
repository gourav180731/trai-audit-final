package com.audit.webapp.service;

import com.audit.webapp.config.AuditProperties;
import com.audit.webapp.entity.GeneratedReport;
import com.audit.webapp.entity.IngestionBatch;
import com.audit.webapp.repository.GeneratedReportRepository;
import com.audit.webapp.repository.IngestionBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

/**
 * Auditable report lifecycle: persists a GeneratedReport per Generate click,
 * stores file bytes, and links to the IngestionBatch that produced the discrepancies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditReportService {

    private final TspReportGenerationService reportGen;
    private final GeneratedReportRepository reportRepo;
    private final IngestionBatchRepository batchRepo;
    private final AuditProperties props;

    @Transactional
    public GeneratedReport generateAndAudit(Long batchId, LocalDateTime from, LocalDateTime to, String tspFilter, String triggeredBy) throws Exception {
        IngestionBatch batch = batchRepo.findById(batchId).orElseThrow();
        // Build report bytes: if tspFilter specified, single TSP; else all TSPs ZIP
        byte[] bytes;
        String fileName;
        if (tspFilter != null && !tspFilter.isBlank() && !tspFilter.equalsIgnoreCase("ALL")) {
            bytes = reportGen.generateSingleTspReport(batchId, tspFilter, from, to);
            fileName = reportGen.generateFilename(tspFilter, from, to);
        } else {
            // Single combined report for all discrepancies (use first TSP's file as combined)
            // For simplicity generate a single XLSX containing all TSP data via existing method with a combined sheet
            // We reuse single-report generation with max range: filter in generator does per-TSP, so we generate ZIP and take it
            Map<String, byte[]> all = reportGen.generateAllTspReports(batchId, from, to);
            // For audit we store the ZIP; file name reflects all TSPs
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (var zos = new java.util.zip.ZipOutputStream(baos)) {
                for (var e : all.entrySet()) {
                    String fn = reportGen.generateFilename(e.getKey(), from, to);
                    var entry = new java.util.zip.ZipEntry(fn);
                    zos.putNextEntry(entry);
                    zos.write(e.getValue());
                    zos.closeEntry();
                }
            }
            bytes = baos.toByteArray();
            fileName = "All_TSP_Discrepancies_" + from.toLocalDate() + "_-_" + to.toLocalDate() + ".zip";
        }

        String sha = sha256(bytes);
        GeneratedReport gr = GeneratedReport.builder()
                .batchId(batchId)
                .generatedAt(LocalDateTime.now())
                .generatedBy(triggeredBy)
                .dateFrom(from)
                .dateTo(to)
                .tspFilter(tspFilter)
                .fileName(fileName)
                .fileSizeBytes((long) bytes.length)
                .discrepancyCount(batch.getTotalDiscrepancyInstances())
                .checksumSha256(sha)
                .build();
        gr = reportRepo.save(gr);

        // Persist bytes to a temp auditable file path for email attachment retrieval
        // (In production replace with S3/object store; for now local file under ./data/reports)
        File dir = new File("./data/reports");
        dir.mkdirs();
        File out = new File(dir, gr.getId() + "_" + fileName);
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); }
        log.info("Audited report id={} batch={} file={} sha={}", gr.getId(), batchId, fileName, sha);
        return gr;
    }

    public File getReportFile(Long reportId) {
        GeneratedReport gr = reportRepo.findById(reportId).orElseThrow();
        File f = new File("./data/reports", gr.getId() + "_" + gr.getFileName());
        if (!f.exists()) throw new RuntimeException("Report file not found on disk for id " + reportId);
        return f;
    }

    public byte[] getReportBytes(Long reportId) throws Exception {
        return Files.readAllBytes(getReportFile(reportId).toPath());
    }

    private static String sha256(byte[] b) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(b));
    }
}
