package com.audit.webapp.controller;

import com.audit.webapp.entity.GeneratedReport;
import com.audit.webapp.entity.IngestionBatch;
import com.audit.webapp.entity.live.TspContactList;
import com.audit.webapp.repository.DiscrepancyRecordRepository;
import com.audit.webapp.repository.GeneratedReportRepository;
import com.audit.webapp.repository.IngestionBatchRepository;
import com.audit.webapp.service.AuditReportService;
import com.audit.webapp.service.EmailService;
import com.audit.webapp.service.LiveDiscrepancyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LiveReportController {

    private final LiveDiscrepancyService liveService;
    private final AuditReportService auditService;
    private final EmailService emailService;
    private final IngestionBatchRepository batchRepo;
    private final DiscrepancyRecordRepository discRepo;
    private final GeneratedReportRepository reportRepo;

    @GetMapping("/live")
    public String liveDashboard(Model model) {
        // Latest live batch
        var latest = batchRepo.findTopByStatusOrderByIngestionTimeDesc(IngestionBatch.ProcessingStatus.COMPLETED).orElse(null);
        model.addAttribute("latestBatch", latest);
        if (latest != null) {
            model.addAttribute("counts", discRepo.countByTypeForBatch(latest.getId()));
            model.addAttribute("tspCounts", discRepo.countByTspForBatch(latest.getId()));
            model.addAttribute("reports", reportRepo.findByBatchIdOrderByGeneratedAtDesc(latest.getId()));
        }
        model.addAttribute("recentBatches", batchRepo.findTop10ByOrderByIngestionTimeDesc());
        return "live-dashboard";
    }

    // ---- Click 1: Generate Report ----
    @PostMapping("/live/generate")
    public String generateReport(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String tspFilter,
            @RequestParam(required = false) String triggeredBy,
            RedirectAttributes ra) {
        try {
            LocalDateTime from = parseDate(dateFrom, true);
            LocalDateTime to = parseDate(dateTo, false);
            String who = (triggeredBy != null && !triggeredBy.isBlank()) ? triggeredBy : "web-ui";
            IngestionBatch batch = liveService.runLiveChecks(from, to, blankToNull(tspFilter), who);
            // Auto-audit: create a generated report tied to this batch
            LocalDateTime repFrom = from != null ? from : batch.getDateFrom() != null ? batch.getDateFrom() : batch.getIngestionTime().minusDays(7);
            LocalDateTime repTo = to != null ? to : batch.getDateTo() != null ? batch.getDateTo() : batch.getIngestionTime();
            GeneratedReport gr = auditService.generateAndAudit(batch.getId(), repFrom, repTo, blankToNull(tspFilter), who);

            ra.addFlashAttribute("success", "Report generated: " + gr.getFileName() + " (" + gr.getDiscrepancyCount() + " discrepancies)");
            ra.addFlashAttribute("generatedReportId", gr.getId());
            ra.addFlashAttribute("batchId", batch.getId());
            return "redirect:/live/preview/" + gr.getId();
        } catch (Exception e) {
            log.error("Generate failed", e);
            ra.addFlashAttribute("error", "Generate failed: " + e.getMessage());
            return "redirect:/live";
        }
    }

    @GetMapping("/live/preview/{reportId}")
    public String previewReport(@PathVariable Long reportId, Model model) {
        GeneratedReport gr = reportRepo.findById(reportId).orElseThrow();
        IngestionBatch batch = batchRepo.findById(gr.getBatchId()).orElse(null);
        model.addAttribute("report", gr);
        model.addAttribute("batch", batch);
        if (batch != null) {
            model.addAttribute("counts", discRepo.countByTypeForBatch(batch.getId()));
            model.addAttribute("tspCounts", discRepo.countByTspForBatch(batch.getId()));
            // Resolve recipients for preview
            List<String> tsps = gr.getTspFilter() != null && !gr.getTspFilter().isBlank()
                    ? List.of(gr.getTspFilter())
                    : discRepo.countByTspForBatch(batch.getId()).stream().map(r -> (String) r[0]).toList();
            if (tsps.isEmpty()) {
                // fallback to all active TSPs if batch has no discrepancies
                tsps = emailService.resolveAllActive().stream().map(TspContactList::getTspName).distinct().toList();
            }
            Map<String, List<TspContactList>> recipients = emailService.resolveRecipients(tsps);
            List<String> allEmails = recipients.values().stream().flatMap(List::stream).map(TspContactList::getEmailId).distinct().toList();
            model.addAttribute("recipientsByTsp", recipients);
            model.addAttribute("allEmails", allEmails);

            // Editable To/Cc — pre-filled from DB (both To and Cc get same list so mail is delivered and user can add more)
            String emailsCsv = String.join(", ", allEmails);
            model.addAttribute("defaultTo", emailsCsv);
            model.addAttribute("defaultCc", emailsCsv);

            // Subject is editable, pre-filled with official format
            String defaultSubject = emailService.buildOfficialSubject(gr);
            model.addAttribute("defaultSubject", defaultSubject);

            // Body is editable, pre-filled with official format using real counts
            String period = formatPeriod(gr.getDateFrom(), gr.getDateTo());
            String tspLabel = (gr.getTspFilter() != null && !gr.getTspFilter().isBlank() && !gr.getTspFilter().equalsIgnoreCase("ALL"))
                    ? gr.getTspFilter() : "Airtel";
            long delayCount = safe(batch.getCountTotalDurationBreach()) + safe(batch.getCountPrefetchDurationBreach());
            if (delayCount == 0) delayCount = safe(batch.getCountFeedbackDelayExceeds());
            long pendingCount = safe(batch.getCountStatisticsPending());
            long zeroCount = safe(batch.getCountCompleteFailure()); // complete failure is closest to zero-subscriber in live schema
            // Delta pending is part of feedback not received; show pendingCount as delta for demo if needed, else 0
            long deltaCount = 0; // no separate delta check in live DB; show 0 truthfully (example had 1)
            // Try to compute delta from discrepancy_records if needed: count where type is STATISTICS_PENDING and delta-like, but we keep 0 to avoid fabrication
            long expiryCount = safe(batch.getCountExpiredNonzero());
            // Find max delay for wording
            String maxDelay = "two day";
            // Try to find max feedback delay from discrepancies
            try {
                var maxDelayRec = discRepo.findByIngestionBatchId(batch.getId()).stream()
                        .filter(r -> r.getDiscrepancyType() == com.audit.webapp.entity.DiscrepancyRecord.DiscrepancyType.FEEDBACK_DELAY_EXCEEDS_THRESHOLD)
                        .map(r -> r.getFeedbackDelaySeconds() != null ? r.getFeedbackDelaySeconds() : 0L)
                        .max(Long::compare).orElse(0L);
                if (maxDelayRec > 0) {
                    long hrs = maxDelayRec / 3600;
                    if (hrs >= 24) maxDelay = (hrs/24) + " day" + (hrs/24 > 1 ? "s" : "");
                    else if (hrs > 0) maxDelay = hrs + " hour" + (hrs > 1 ? "s" : "");
                    else maxDelay = (maxDelayRec/60) + " minutes";
                }
            } catch (Exception ignored) {}
            String defaultBody = emailService.buildOfficialBodyWithCounts(period, tspLabel, delayCount, pendingCount, zeroCount, deltaCount, expiryCount, maxDelay);
            model.addAttribute("defaultBody", defaultBody);
        }
        return "report-preview";
    }

    private static String formatPeriod(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yy", java.util.Locale.ENGLISH);
        try {
            String a = from != null ? from.format(fmt) : "—";
            String b = to != null ? to.format(fmt) : "—";
            return a + " - " + b;
        } catch (Exception e) { return "—"; }
    }
    private static long safe(Integer v) { return v != null ? v : 0; }

    @GetMapping("/live/download/{reportId}")
    @ResponseBody
    public ResponseEntity<FileSystemResource> downloadReport(@PathVariable Long reportId) {
        try {
            GeneratedReport gr = reportRepo.findById(reportId).orElseThrow();
            var file = auditService.getReportFile(reportId);
            FileSystemResource res = new FileSystemResource(file);
            String ct = gr.getFileName().endsWith(".zip") ? "application/zip" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + gr.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(ct))
                    .contentLength(file.length())
                    .body(res);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ---- Click 2: Send Email ----
    @PostMapping("/live/send/{reportId}")
    public String sendEmail(@PathVariable Long reportId,
                            @RequestParam(required = false) String subject,
                            @RequestParam(required = false) String body,
                            @RequestParam(required = false) String to,
                            @RequestParam(required = false) String cc,
                            RedirectAttributes ra) {
        try {
            GeneratedReport gr = emailService.sendReport(reportId, blankToNull(subject), blankToNull(body), blankToNull(to), blankToNull(cc));
            ra.addFlashAttribute("success", "Email sent to: " + gr.getEmailRecipients());
            ra.addFlashAttribute("emailStatus", gr.getEmailStatus());
            return "redirect:/live/preview/" + reportId;
        } catch (Exception e) {
            log.error("Send failed for report {}", reportId, e);
            ra.addFlashAttribute("error", "Email failed: " + e.getMessage());
            // still redirect to preview so user sees who it would have gone to
            return "redirect:/live/preview/" + reportId;
        }
    }

    // ---- helpers ----
    private static LocalDateTime parseDate(String s, boolean startOfDay) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        try {
            if (s.length() == 10) return LocalDateTime.parse(s + (startOfDay ? "T00:00:00" : "T23:59:59"));
            return LocalDateTime.parse(s);
        } catch (Exception e) { return null; }
    }
    private static String blankToNull(String s) { return (s==null || s.isBlank()) ? null : s.trim(); }
}
