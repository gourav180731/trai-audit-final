package com.audit.webapp.service;

import com.audit.webapp.config.AuditProperties;
import com.audit.webapp.entity.GeneratedReport;
import com.audit.webapp.entity.live.TspContactList;
import com.audit.webapp.repository.GeneratedReportRepository;
import com.audit.webapp.repository.live.TspContactRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Email sender — Click 2 of the 2-click workflow.
 * Resolves recipients from dm.t_tsp_contact_list and sends the audited report.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final AuditProperties props;
    private final TspContactRepository contactRepo;
    private final GeneratedReportRepository reportRepo;

    /**
     * Resolve active email-enabled contacts for TSPs in the batch.
     * Returns map: tspCanonical -> List<email>
     */
    public Map<String, List<TspContactList>> resolveRecipients(List<String> tspsInReport) {
        Map<String, List<TspContactList>> map = new LinkedHashMap<>();
        for (String tsp : tspsInReport) {
            List<TspContactList> contacts = contactRepo.findActiveEmailContacts(tsp);
            map.put(tsp, contacts);
            if (contacts.isEmpty()) {
                log.warn("TSP '{}' has zero active email-enabled contacts — surface in UI, don't fail silently", tsp);
            }
        }
        return map;
    }

    public List<TspContactList> resolveAllActive() {
        return contactRepo.findAllActiveEmailContacts();
    }

    /**
     * Send report id with attachment. Returns the updated GeneratedReport.
     */
    public GeneratedReport sendReport(Long reportId, String subjectOverride, String bodyOverride) throws Exception {
        GeneratedReport gr = reportRepo.findById(reportId).orElseThrow(() -> new RuntimeException("Report not found: " + reportId));
        File reportFile = new File("./data/reports", gr.getId() + "_" + gr.getFileName());
        if (!reportFile.exists()) throw new RuntimeException("Report file missing for id " + reportId);
        byte[] bytes = Files.readAllBytes(reportFile.toPath());

        // Resolve TSPs — from tspFilter or from batch's distinct TSPs (fallback: all contacts)
        List<String> tsps = new ArrayList<>();
        if (gr.getTspFilter() != null && !gr.getTspFilter().isBlank() && !gr.getTspFilter().equalsIgnoreCase("ALL")) {
            tsps.add(gr.getTspFilter());
        } else {
            // Send to all active contacts if no filter
            var all = contactRepo.findAllActiveEmailContacts();
            tsps = all.stream().map(c -> c.getTspName()).distinct().toList();
        }

        Map<String, List<TspContactList>> byTsp = resolveRecipients(tsps);
        List<String> allEmails = byTsp.values().stream().flatMap(List::stream).map(TspContactList::getEmailId).distinct().toList();

        if (allEmails.isEmpty()) {
            gr.setEmailStatus("NO_RECIPIENTS");
            gr.setEmailError("Zero active email-enabled contacts for TSPs: " + tsps);
            reportRepo.save(gr);
            throw new RuntimeException("No active email-enabled contacts found for TSPs: " + tsps + " — add contacts in dm.t_tsp_contact_list with email_notifications=true and deactivated_on IS NULL");
        }

        String subject = subjectOverride != null ? subjectOverride
                : "CAP Sachet TSP Discrepancy Report — " + gr.getFileName() + " (" + gr.getGeneratedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + ")";
        String body = bodyOverride != null ? bodyOverride : buildDefaultBody(gr, byTsp);

        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true);
        helper.setFrom(props.getMail().getFrom(), props.getMail().getFromName());
        helper.setTo(allEmails.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(body, false);
        helper.addAttachment(gr.getFileName(), new ByteArrayResource(bytes));

        try {
            mailSender.send(msg);
            gr.setEmailSent(true);
            gr.setEmailSentAt(LocalDateTime.now());
            gr.setEmailRecipients(String.join(", ", allEmails));
            gr.setEmailSubject(subject);
            gr.setEmailStatus("SENT");
            reportRepo.save(gr);
            log.info("Email SENT for report {} to {} recipients: {}", reportId, allEmails.size(), allEmails);
            return gr;
        } catch (Exception e) {
            log.error("Email FAILED for report {}: {}", reportId, e.getMessage(), e);
            gr.setEmailStatus("FAILED");
            gr.setEmailError(e.getMessage());
            gr.setEmailRecipients(String.join(", ", allEmails));
            reportRepo.save(gr);
            throw e;
        }
    }

    private String buildDefaultBody(GeneratedReport gr, Map<String, List<TspContactList>> byTsp) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear TSP Team,\n\n");
        sb.append("Please find attached the CAP Sachet TSP SMS Dissemination Compliance Report.\n\n");
        sb.append("Report: ").append(gr.getFileName()).append("\n");
        sb.append("Generated: ").append(gr.getGeneratedAt()).append("\n");
        sb.append("Date range: ").append(gr.getDateFrom()).append(" to ").append(gr.getDateTo()).append("\n");
        sb.append("TSP filter: ").append(gr.getTspFilter() != null ? gr.getTspFilter() : "ALL").append("\n");
        sb.append("Discrepancies included: ").append(gr.getDiscrepancyCount()).append("\n\n");
        sb.append("Recipients resolved from dm.t_tsp_contact_list:\n");
        for (var e : byTsp.entrySet()) {
            sb.append("  - ").append(e.getKey()).append(": ");
            if (e.getValue().isEmpty()) sb.append("(NO active email-enabled contacts!)");
            else sb.append(e.getValue().stream().map(TspContactList::getEmailId).collect(Collectors.joining(", ")));
            if (!e.getValue().isEmpty()) {
                String brs = e.getValue().stream().map(c -> c.getBoundaryRestriction()).filter(Objects::nonNull).distinct().collect(Collectors.joining(", "));
                if (!brs.isEmpty()) sb.append(" [boundary: ").append(brs).append("]");
            }
            sb.append("\n");
        }
        sb.append("\nThis is an automated report from the TSP SMS Dissemination Compliance Monitor.\n");
        sb.append("Thresholds: feedback delay ").append(props.getThreshold().getFeedbackDelaySeconds()/60).append(" min, ratio deviation ")
          .append(props.getThreshold().getSubscriberRatioDeviationPct()).append("pp, MRAD bands ")
          .append(props.getMrad().getBand05kMinutes()).append("/").append(props.getMrad().getBand515kMinutes()).append("/").append(props.getMrad().getBand1530kMinutes()).append(" min.\n");
        return sb.toString();
    }
}
