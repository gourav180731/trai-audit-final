package com.audit.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One alert (one CAP Sachet Report block) = alert-level fields + up to 5 TSP rows.
 * In the source sheet, the alert-level fields (Sl No., Organization/state, Event,
 * Alert Creation Time, Area Description, Total Subscriber Count, Total SMS Count,
 * Identifier) are populated only on the FIRST row of the block and blank on the
 * other 4 - the reader forward-fills them onto this object once per group.
 */
public class AlertGroup {

    public int slNo;
    public String state;              // "Organization" column
    public String event;
    public String alertCreationTime;
    public String areaDescription;
    public String identifier;

    /** Alert-level total subscribers reached, summed across all TSPs by the source system. */
    public Long totalSubscriberCount;
    /** Alert-level total SMS sent, summed across all TSPs by the source system. */
    public Long totalSmsCount;

    public List<TspRow> tspRows = new ArrayList<>();
}
