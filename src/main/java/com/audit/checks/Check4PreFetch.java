package com.audit.checks;

import com.audit.model.AlertGroup;
import com.audit.model.TspRow;
import com.audit.util.DurationParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Check #4: same MRAD matrix as Check #5, but restricted to rows where the "**" marker
 * shows the TSP's Subscriber Count / SMS Count is pre-fetched (provisional/cached), not
 * live feedback. A breach here is a stronger red flag - the TSP's own provisional numbers
 * are already failing SLA before live confirmation even arrives.
 */
public final class Check4PreFetch {

    private Check4PreFetch() {}

    public static List<CheckResultRow> run(List<AlertGroup> groups) {
        List<CheckResultRow> out = new ArrayList<>();

        for (AlertGroup g : groups) {
            for (TspRow t : g.tspRows) {
                if (!t.isPreFetch()) continue;
                if (t.cellCount == null || t.disseminationSeconds < 0) continue;

                long thresholdSec = MradMatrix.thresholdSeconds(t.cellCount);
                boolean breach = t.disseminationSeconds > thresholdSec;

                CheckResultRow row = new CheckResultRow();
                row.put("Sl No.", g.slNo)
                   .put("State", g.state)
                   .put("Event", g.event)
                   .put("Alert Creation Time", g.alertCreationTime)
                   .put("TSP", t.tsp)
                   .put("Pre-fetch marker on", markerSource(t))
                   .put("Cell Count", t.cellCount)
                   .put("Cell Count Bucket", MradMatrix.bucketLabel(t.cellCount))
                   .put("Dissemination Duration", t.disseminationDurationRaw)
                   .put("MRAD Threshold", MradMatrix.thresholdMinutes(t.cellCount) + " min")
                   .put("Excess Over Threshold", breach ? DurationParser.toHms(t.disseminationSeconds - thresholdSec) : "");

                row.flagged = breach;
                row.note = "PRE-FETCH: provisional data, TSP has not sent live confirmation yet"
                        + (MradMatrix.isOutOfDefinedRange(t.cellCount) ? "; also Cell Count > 30,000 (outside defined matrix)" : "");
                out.add(row);
            }
        }
        return out;
    }

    private static String markerSource(TspRow t) {
        if (t.subscriberPreFetch && t.smsPreFetch) return "Subscriber Count + SMS Count";
        if (t.subscriberPreFetch) return "Subscriber Count";
        return "SMS Count";
    }
}
