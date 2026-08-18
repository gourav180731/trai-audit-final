package com.audit.checks;

import com.audit.model.AlertGroup;
import com.audit.model.TspRow;
import com.audit.util.DurationParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Check #5: for every TSP row with a known Cell Count and a known Dissemination Duration,
 * flag it if the duration exceeds the MRAD threshold for its Cell Count bucket.
 * Rows with "--" (no data) or missing Cell Count are skipped, not treated as pass/fail.
 */
public final class Check5Dissemination {

    private Check5Dissemination() {}

    public static List<CheckResultRow> run(List<AlertGroup> groups) {
        List<CheckResultRow> out = new ArrayList<>();

        for (AlertGroup g : groups) {
            for (TspRow t : g.tspRows) {
                if (t.cellCount == null || t.disseminationSeconds < 0) continue;

                long thresholdSec = MradMatrix.thresholdSeconds(t.cellCount);
                boolean breach = t.disseminationSeconds > thresholdSec;

                CheckResultRow row = new CheckResultRow();
                row.put("Sl No.", g.slNo)
                   .put("State", g.state)
                   .put("Event", g.event)
                   .put("Alert Creation Time", g.alertCreationTime)
                   .put("TSP", t.tsp)
                   .put("Cell Count", t.cellCount)
                   .put("Cell Count Bucket", MradMatrix.bucketLabel(t.cellCount))
                   .put("Dissemination Duration", t.disseminationDurationRaw)
                   .put("MRAD Threshold", MradMatrix.thresholdMinutes(t.cellCount) + " min")
                   .put("Excess Over Threshold", breach ? DurationParser.toHms(t.disseminationSeconds - thresholdSec) : "");

                row.flagged = breach;
                if (MradMatrix.isOutOfDefinedRange(t.cellCount)) {
                    row.note = "Cell Count > 30,000 - outside the defined MRAD matrix, verify threshold manually";
                }
                out.add(row);
            }
        }
        return out;
    }
}
