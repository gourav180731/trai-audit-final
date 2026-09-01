package com.audit.webapp.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles dm.t_tsp_sms_dissemination_statistics.status values.
 * Confirmed: 'received', 'failed', 'finished'. Anything else = UNKNOWN_STATUS.
 * 'finished' does NOT imply success — must inspect counts/expiry.
 */
@Slf4j
public final class DisseminationStatus {

    public static final String RECEIVED = "received";
    public static final String FAILED   = "failed";
    public static final String FINISHED = "finished";
    public static final String UNKNOWN  = "UNKNOWN_STATUS";

    private DisseminationStatus() {}

    public static String classify(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase();
        if (v.equals(RECEIVED) || v.equals(FAILED) || v.equals(FINISHED)) return v;
        log.warn("Unknown dissemination status encountered: '{}' — flagging as {}", raw, UNKNOWN);
        return UNKNOWN;
    }

    public static boolean isKnown(String raw) {
        if (raw == null) return false;
        String v = raw.trim().toLowerCase();
        return v.equals(RECEIVED) || v.equals(FAILED) || v.equals(FINISHED);
    }
}
