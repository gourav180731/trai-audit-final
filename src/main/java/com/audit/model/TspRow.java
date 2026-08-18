package com.audit.model;

/**
 * One TSP's line inside an alert block (Airtel / BSNL / MTNL / Reliance Jio / Vodafone Idea).
 * Numeric fields are null/-1 when the source cell was "--" (TSP sent nothing) or "Awaited"
 * (TSP hasn't confirmed yet) - those rows are excluded from threshold checks, not treated as 0.
 */
public class TspRow {

    public String tsp;

    public String disseminationDurationRaw;
    /** Total dissemination duration in seconds, or -1 if unknown/"--". */
    public long disseminationSeconds = -1;

    public String cellCountRaw;
    /** null if "--" (no data for this TSP on this alert). */
    public Integer cellCount;

    public String subscriberCountRaw;
    /** null if "--" or "Awaited". */
    public Long subscriberCount;
    /** true if the raw value carried a "**" marker (pre-fetched / provisional, not live). */
    public boolean subscriberPreFetch;

    public String smsCountRaw;
    public Long smsCount;
    public boolean smsPreFetch;

    public String feedbackDelayRaw;
    /** Feedback Delay in seconds, or -1 if unknown/"--". */
    public long feedbackDelaySeconds = -1;

    public boolean isPreFetch() {
        return subscriberPreFetch || smsPreFetch;
    }
}
