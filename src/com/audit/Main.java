package com.audit;

import com.audit.checks.Check1CompleteFailure;
import com.audit.checks.Check2FeedbackNotReceived;
import com.audit.checks.Check3FeedbackDelay;
import com.audit.checks.Check4PreFetch;
import com.audit.checks.Check5Dissemination;
import com.audit.checks.Check6SubscriberRatio;
import com.audit.checks.CheckResultRow;
import com.audit.io.ReportWriter;
import com.audit.io.TraiBaselineReader;
import com.audit.io.WarningReportReader;
import com.audit.model.AlertGroup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Usage:
 *   java -jar trai-mrad-audit.jar <WarningDetailedReport.xlsx> <TRAI_Wireless_Subscriber_Base.xlsx> <output.xlsx>
 *
 * Runs sir's official 6-point checklist for raising SMS dissemination issues to TSPs:
 *   1. Alert dissemination complete failure
 *   2. SMS dissemination feedback not received
 *   3. Feedback delay > 10 mins
 *   4. Pre-fetch dissemination duration not following DoT-defined matrix
 *   5. Total dissemination duration too high as per cell count
 *   6. Inordinate subscriber count ratio compared to other TSPs
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String warningReportPath = args.length > 0 ? args[0] : "WarningDetailedReport.xlsx";
        String traiPath = args.length > 1 ? args[1] : "TRAI_Wireless_Subscriber_Base.xlsx";
        String outputPath = args.length > 2 ? args[2] : "TSP_Issue_Flagging_Report.xlsx";

        System.out.println("Reading Warning Detailed Report: " + warningReportPath);
        List<AlertGroup> groups = WarningReportReader.read(warningReportPath);
        int totalTspRows = 0;
        for (AlertGroup g : groups) totalTspRows += g.tspRows.size();
        System.out.println("  Total alerts parsed: " + groups.size());
        System.out.println("  Total TSP rows parsed: " + totalTspRows);

        System.out.println("Reading TRAI baseline: " + traiPath);
        Map<String, Map<String, Double>> traiBaseline = TraiBaselineReader.read(traiPath);
        System.out.println("  Parsed " + traiBaseline.size() + " circles.");

        Map<String, List<CheckResultRow>> sheets = new LinkedHashMap<>();

        System.out.println("Check #1 - Alert dissemination complete failure...");
        List<CheckResultRow> c1 = Check1CompleteFailure.run(groups);
        report(c1);
        sheets.put("Check1_CompleteFailure", c1);

        System.out.println("Check #2 - SMS dissemination feedback not received...");
        List<CheckResultRow> c2 = Check2FeedbackNotReceived.run(groups);
        report(c2);
        sheets.put("Check2_FeedbackNotReceived", c2);

        System.out.println("Check #3 - Feedback delay > 10 mins...");
        List<CheckResultRow> c3 = Check3FeedbackDelay.run(groups);
        report(c3);
        sheets.put("Check3_FeedbackDelay", c3);

        System.out.println("Check #4 - Pre-fetch dissemination duration vs DoT matrix...");
        List<CheckResultRow> c4 = Check4PreFetch.run(groups);
        report(c4);
        sheets.put("Check4_PreFetchDuration", c4);

        System.out.println("Check #5 - Total dissemination duration vs cell count...");
        List<CheckResultRow> c5 = Check5Dissemination.run(groups);
        report(c5);
        sheets.put("Check5_DisseminationDuration", c5);

        System.out.println("Check #6 - Inordinate subscriber count ratio...");
        List<CheckResultRow> c6 = Check6SubscriberRatio.run(groups, traiBaseline);
        report(c6);
        sheets.put("Check6_SubscriberRatio", c6);

        System.out.println("Writing report: " + outputPath);
        ReportWriter.write(outputPath, sheets);
        System.out.println("Done.");
    }

    private static void report(List<CheckResultRow> rows) {
        long flagged = rows.stream().filter(r -> r.flagged).count();
        System.out.println("  " + rows.size() + " rows evaluated, " + flagged + " flagged.");
    }
}
