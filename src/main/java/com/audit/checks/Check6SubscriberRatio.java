package com.audit.checks;

import com.audit.io.TraiBaselineReader;
import com.audit.io.TraiBaselineReader.CircleMapping;
import com.audit.io.TraiBaselineReader.MappingStatus;
import com.audit.model.AlertGroup;
import com.audit.model.TspRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Check #6 (inordinate subscriber count ratio):
 *   report % = this TSP's Subscriber Count / alert's Total Subscriber Count * 100
 *   TRAI %   = this TSP's Jun-26 subscriber share of its TRAI circle
 *   flag if |report % - TRAI %| > DEVIATION_THRESHOLD_PCT
 *
 * No explicit threshold was given by DoT/sir in the source material - 15 percentage points
 * is a placeholder default (between "would flag" and the 80-vs-50 example given). Adjust
 * DEVIATION_THRESHOLD_PCT once a real number is confirmed.
 */
public final class Check6SubscriberRatio {

    public static double DEVIATION_THRESHOLD_PCT = 15.0;

    private Check6SubscriberRatio() {}

    public static List<CheckResultRow> run(List<AlertGroup> groups, Map<String, Map<String, Double>> traiBaseline) {
        List<CheckResultRow> out = new ArrayList<>();

        for (AlertGroup g : groups) {
            CircleMapping mapping = TraiBaselineReader.mapStateToCircle(g.state);

            if (mapping.status == MappingStatus.NO_BASELINE || mapping.status == MappingStatus.AMBIGUOUS) {
                CheckResultRow row = new CheckResultRow();
                row.put("Sl No.", g.slNo).put("State", g.state).put("TSP", "-")
                   .put("Report %", "-").put("TRAI %", "-").put("Deviation", "-");
                row.flagged = false;
                row.note = mapping.status == MappingStatus.AMBIGUOUS
                        ? "AMBIGUOUS: Uttar Pradesh needs a manual U.P.(E)/U.P.(W) decision - not computed"
                        : "NO_BASELINE: no matching TRAI circle for this state in the supplied file - not computed";
                out.add(row);
                continue;
            }

            if (g.totalSubscriberCount == null || g.totalSubscriberCount <= 0) {
                CheckResultRow row = new CheckResultRow();
                row.put("Sl No.", g.slNo).put("State", g.state).put("TSP", "-")
                   .put("Report %", "-").put("TRAI %", "-").put("Deviation", "-");
                row.flagged = false;
                row.note = "NO_ALERT_TOTAL: alert's Total Subscriber Count is missing/zero - cannot compute %";
                out.add(row);
                continue;
            }

            Map<String, Double> circlePct = traiBaseline.get(mapping.circleName);
            if (circlePct == null) {
                CheckResultRow row = new CheckResultRow();
                row.put("Sl No.", g.slNo).put("State", g.state).put("TSP", "-")
                   .put("Report %", "-").put("TRAI %", "-").put("Deviation", "-");
                row.flagged = false;
                row.note = "NO_BASELINE: circle '" + mapping.circleName + "' not found in TRAI file";
                out.add(row);
                continue;
            }

            for (TspRow t : g.tspRows) {
                if (t.subscriberCount == null) continue; // "--" or "Awaited" - nothing to compare

                double reportPct = (t.subscriberCount * 100.0) / g.totalSubscriberCount;
                Double traiPct = circlePct.get(t.tsp);

                CheckResultRow row = new CheckResultRow();
                row.put("Sl No.", g.slNo)
                   .put("State", g.state)
                   .put("Event", g.event)
                   .put("Alert Creation Time", g.alertCreationTime)
                   .put("TSP", t.tsp)
                   .put("TRAI Circle Used", mapping.circleName)
                   .put("Mapping Type", mapping.status)
                   .put("TSP Subscriber Count", t.subscriberCount)
                   .put("Alert Total Subscriber Count", g.totalSubscriberCount)
                   .put("Report %", round2(reportPct));

                if (traiPct == null) {
                    row.put("TRAI %", "-").put("Deviation", "-");
                    row.flagged = false;
                    row.note = "NO_OPERATOR_DATA: TRAI has no figure for " + t.tsp + " in " + mapping.circleName;
                } else {
                    double deviation = reportPct - traiPct;
                    row.put("TRAI %", round2(traiPct)).put("Deviation", round2(deviation));
                    row.flagged = Math.abs(deviation) > DEVIATION_THRESHOLD_PCT;
                    if (t.subscriberPreFetch) {
                        row.note = "Subscriber Count is pre-fetch/provisional (**)";
                    }
                }
                out.add(row);
            }
        }
        return out;
    }

    private static double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
