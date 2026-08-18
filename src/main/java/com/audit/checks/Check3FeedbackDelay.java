package com.audit.checks;

import com.audit.model.AlertGroup;
import com.audit.model.TspRow;
import com.audit.util.DurationParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Sir's Check #3: "Feedback delay > 10 mins".
 * Uses the "Feedback Delay" column directly from the Warning Detailed Report - flags any
 * TSP row where that delay exceeds 10 minutes. Rows with "--" (no feedback at all) are
 * skipped here since those are already covered by Checks #1/#2.
 */
public final class Check3FeedbackDelay {

    public static long THRESHOLD_SECONDS = 10 * 60; // 10 minutes - per sir's stated rule

    private Check3FeedbackDelay() {}

    public static List<CheckResultRow> run(List<AlertGroup> groups) {
        List<CheckResultRow> out = new ArrayList<>();

        for (AlertGroup g : groups) {
            for (TspRow t : g.tspRows) {
                if (t.feedbackDelaySeconds < 0) continue; // "--", nothing to evaluate

                boolean breach = t.feedbackDelaySeconds > THRESHOLD_SECONDS;

                CheckResultRow row = new CheckResultRow();
                row.put("Sl No.", g.slNo)
                   .put("Identifier", g.identifier)
                   .put("State", g.state)
                   .put("Alert Push Time", g.alertCreationTime)
                   .put("TSP", t.tsp)
                   .put("Feedback Delay", t.feedbackDelayRaw)
                   .put("Excess Over 10min", breach ? DurationParser.toHms(t.feedbackDelaySeconds - THRESHOLD_SECONDS) : "");
                row.flagged = breach;
                out.add(row);
            }
        }
        return out;
    }
}
