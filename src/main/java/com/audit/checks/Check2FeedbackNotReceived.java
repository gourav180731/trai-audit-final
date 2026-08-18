package com.audit.checks;

import com.audit.model.AlertGroup;
import com.audit.model.TspRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Sir's Check #2: "SMS dissemination feedback not received".
 * Covers both ways live feedback can be missing:
 *   - Subscriber Count / SMS Count still literally "Awaited" (matches sir's manual
 *     "Statistics Pending" category - TSP hasn't sent anything, not even an estimate)
 *   - Subscriber Count / SMS Count carries a "**" marker (matches sir's manual "Delta
 *     Statistics Pending" category - TSP sent a pre-fetch estimate but live confirmation
 *     hasn't landed yet)
 * Rows already caught by Check #1 (Cell Count "--", complete failure) are excluded here
 * so a single row isn't double-counted across both checks.
 */
public final class Check2FeedbackNotReceived {

    private Check2FeedbackNotReceived() {}

    public static List<CheckResultRow> run(List<AlertGroup> groups) {
        List<CheckResultRow> out = new ArrayList<>();

        for (AlertGroup g : groups) {
            for (TspRow t : g.tspRows) {
                if (isDash(t.cellCountRaw)) continue; // that's Check #1, not this one

                boolean subAwaited = isAwaited(t.subscriberCountRaw);
                boolean smsAwaited = isAwaited(t.smsCountRaw);
                boolean preFetch = t.isPreFetch();
                if (!subAwaited && !smsAwaited && !preFetch) continue;

                CheckResultRow row = new CheckResultRow();
                row.put("Sl No.", g.slNo)
                   .put("Identifier", g.identifier)
                   .put("State", g.state)
                   .put("Alert Push Time", g.alertCreationTime)
                   .put("TSP", t.tsp)
                   .put("Cell Count", t.cellCountRaw)
                   .put("Subscriber Count", t.subscriberCountRaw)
                   .put("SMS Count", t.smsCountRaw);
                row.flagged = true;

                StringBuilder note = new StringBuilder();
                if (subAwaited || smsAwaited) {
                    note.append("Awaited (");
                    note.append(subAwaited && smsAwaited ? "Subscriber + SMS Count" : subAwaited ? "Subscriber Count" : "SMS Count");
                    note.append(")");
                }
                if (preFetch) {
                    if (note.length() > 0) note.append("; ");
                    note.append("Pre-fetch estimate only (**), live feedback pending");
                }
                row.note = note.toString();
                out.add(row);
            }
        }
        return out;
    }

    private static boolean isAwaited(String raw) {
        return raw != null && raw.trim().equalsIgnoreCase("Awaited");
    }

    private static boolean isDash(String raw) {
        return raw != null && raw.trim().equals("--");
    }
}
