package com.audit.webapp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MRAD matrix driven by AuditProperties — no hardcoded bands elsewhere.
 */
@Component
@RequiredArgsConstructor
public class MradConfig {

    private final AuditProperties props;

    public int thresholdMinutes(long cellCount) {
        if (cellCount <= 5_000) return props.getMrad().getBand05kMinutes();
        if (cellCount <= 15_000) return props.getMrad().getBand515kMinutes();
        return props.getMrad().getBand1530kMinutes();
    }

    public long thresholdSeconds(long cellCount) {
        return (long) thresholdMinutes(cellCount) * 60L;
    }

    public boolean isBeyondMatrix(long cellCount) {
        return cellCount > 30_000;
    }

    public String bucketLabel(long cellCount) {
        if (cellCount <= 5_000) return "0-5k";
        if (cellCount <= 15_000) return "5k-15k";
        if (cellCount <= 30_000) return "15k-30k";
        return ">30k (beyond matrix)";
    }
}
