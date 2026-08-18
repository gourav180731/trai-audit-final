package com.audit.checks;

import com.audit.model.AlertGroup;
import com.audit.model.TspRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Sir's Check #1: "Alert dissemination complete failure".
 * A TSP that sent literally nothing for the alert - Cell Count is "--" (matches sir's
 * own "Zero Subscriber Count" manual category: this is the MTNL-style no-response pattern,
 * not merely a delayed/pending response).
 */
public final class Check1CompleteFailure {

    private Check1CompleteFailure() {}

    public static List<CheckResultRow> run(List<AlertGroup> groups) {
        List<CheckResultRow> out = new ArrayList<>();

        for (AlertGroup g : groups) {
            for (TspRow t : g.tspRows) {
                if (!isDash(t.cellCountRaw)) continue;

                CheckResultRow row = new CheckResultRow();
                row.put("Sl No.", g.slNo)
                   .put("Identifier", g.identifier)
                   .put("State", g.state)
                   .put("Alert Push Time", g.alertCreationTime)
                   .put("TSP", t.tsp)
                   .put("Cell Count", t.cellCountRaw)
                   .put("Subscriber Count", t.subscriberCountRaw);
                row.flagged = true;
                row.note = "Complete dissemination failure - no cell count sent";
                out.add(row);
            }
        }
        return out;
    }

    private static boolean isDash(String raw) {
        return raw != null && raw.trim().equals("--");
    }
}
