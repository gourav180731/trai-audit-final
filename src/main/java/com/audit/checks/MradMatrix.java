package com.audit.checks;

/**
 * Minimum Requirement for Alert Dissemination (MRAD) compliance table:
 *   Cell Count 0     - 5,000   -> 60 min
 *   Cell Count 5,000 - 15,000  -> 90 min
 *   Cell Count 15,000- 30,000  -> 120 min
 *
 * ASSUMPTION (unconfirmed - flag rows above 30,000 cells as OUT_OF_DEFINED_RANGE rather
 * than silently trusting the 120-min tier; the source image only defines up to 30k):
 * cellCount > 30,000 currently falls back to the strictest defined tier (120 min) so the
 * checker still produces a number, but ReportWriter marks these rows for manual review.
 */
public final class MradMatrix {

    private MradMatrix() {}

    public static int thresholdMinutes(int cellCount) {
        if (cellCount <= 5_000) return 60;
        if (cellCount <= 15_000) return 90;
        return 120; // covers 15k-30k, and the unconfirmed >30k fallback
    }

    public static long thresholdSeconds(int cellCount) {
        return thresholdMinutes(cellCount) * 60L;
    }

    public static boolean isOutOfDefinedRange(int cellCount) {
        return cellCount > 30_000;
    }

    public static String bucketLabel(int cellCount) {
        if (cellCount <= 5_000) return "0-5k";
        if (cellCount <= 15_000) return "5k-15k";
        if (cellCount <= 30_000) return "15k-30k";
        return ">30k (undefined)";
    }
}
