package com.audit.webapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for all thresholds.
 * Every value here is externalized to application.properties and
 * must be flagged in NEEDS_SIGN_OFF.md if still placeholder.
 */
@Data
@Component
@ConfigurationProperties(prefix = "audit")
public class AuditProperties {

    private Threshold threshold = new Threshold();
    private Mrad mrad = new Mrad();
    private Mail mail = new Mail();
    private Report report = new Report();

    @Data
    public static class Threshold {
        /** default 600s = 10 min (spec header says 5, body says 10) */
        private long feedbackDelaySeconds = 600;
        /** placeholder 15 percentage points pending DoT sign-off */
        private double subscriberRatioDeviationPct = 15.0;
        /** recency window for Statistics Pending vs Awaited split */
        private long recencyWindowHours = 24;
    }

    @Data
    public static class Mrad {
        private int band05kMinutes = 60;
        private int band515kMinutes = 90;
        private int band1530kMinutes = 120;
    }

    @Data
    public static class Mail {
        private String from = "noreply@cap-sachet.gov.in";
        private String fromName = "CAP Sachet Audit System";
    }

    @Data
    public static class Report {
        private int previewRowLimit = 100;
    }
}
