package com.audit.io;

import com.audit.model.AlertGroup;
import com.audit.model.TspRow;
import com.audit.util.DurationParser;
import com.audit.util.NumberParser;
import com.audit.xlsx.SimpleXlsxReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the "CAP Sachet Report" sheet of WarningDetailedReport_*.xlsx.
 * Column layout (0-indexed) - confirmed against the actual file's raw XML:
 *   0  Sl No.                    - present only on the first row of each 5-row TSP block
 *   1  Organization (state)      - present only on first row
 *   2  Event                     - present only on first row
 *   3  Message                   - present only on first row (not needed here)
 *   4  Alert Creation Time       - present only on first row
 *   5  Area Description          - present only on first row
 *   6  TSP                       - present on EVERY row
 *   7  CAP XML Receive Time
 *   8  Start Time from TSP
 *   9  End Time from TSP
 *  10  Feedback Time of Dissemination
 *  11  Dissemination Duration    - per-TSP
 *  12  Feedback Delay            - per-TSP
 *  13  Cell Count                - per-TSP
 *  14  Subscriber Count          - per-TSP, may carry "**" pre-fetch marker
 *  15  Total Subscriber Count    - ALERT-level total, present only on first row
 *  16  SMS Count                 - per-TSP, may carry "**" pre-fetch marker
 *  17  Total SMS Count           - ALERT-level total, present only on first row
 *  18  Identifier                - present only on first row
 */
public final class WarningReportReader {

    private static final String SHEET_NAME = "CAP Sachet Report";

    private WarningReportReader() {}

    public static List<AlertGroup> read(String path) throws IOException {
        SimpleXlsxReader reader = new SimpleXlsxReader(path);
        List<String[]> grid = reader.readSheet(SHEET_NAME);
        // grid.get(0) is a dummy placeholder (xlsx rows are 1-indexed); grid.get(1) is the header row.

        List<AlertGroup> groups = new ArrayList<>();
        AlertGroup current = null;

        for (int r = 2; r < grid.size(); r++) { // row 1 = header
            String[] row = grid.get(r);
            String tspName = cell(row, 6);
            if (tspName.isEmpty()) continue; // fully blank row

            String slNoStr = cell(row, 0);
            boolean isNewAlert = !slNoStr.isEmpty();

            if (isNewAlert) {
                current = new AlertGroup();
                current.slNo = parseSlNo(slNoStr);
                current.state = cell(row, 1);
                current.event = cell(row, 2);
                current.alertCreationTime = cell(row, 4);
                current.areaDescription = cell(row, 5);
                current.totalSubscriberCount = NumberParser.parse(cell(row, 15)).value;
                current.totalSmsCount = NumberParser.parse(cell(row, 17)).value;
                current.identifier = cell(row, 18);
                groups.add(current);
            }

            if (current == null) continue; // malformed leading rows with no Sl No. yet

            TspRow t = new TspRow();
            t.tsp = tspName;

            t.disseminationDurationRaw = cell(row, 11);
            t.disseminationSeconds = DurationParser.toSeconds(t.disseminationDurationRaw);

            t.feedbackDelayRaw = cell(row, 12);
            t.feedbackDelaySeconds = DurationParser.toSeconds(t.feedbackDelayRaw);

            t.cellCountRaw = cell(row, 13);
            t.cellCount = NumberParser.parseInt(t.cellCountRaw);

            t.subscriberCountRaw = cell(row, 14);
            NumberParser.Result sub = NumberParser.parse(t.subscriberCountRaw);
            t.subscriberCount = sub.value;
            t.subscriberPreFetch = sub.preFetch;

            t.smsCountRaw = cell(row, 16);
            NumberParser.Result sms = NumberParser.parse(t.smsCountRaw);
            t.smsCount = sms.value;
            t.smsPreFetch = sms.preFetch;

            current.tspRows.add(t);
        }
        return groups;
    }

    private static String cell(String[] row, int idx) {
        if (idx >= row.length) return "";
        String v = row[idx];
        return v == null ? "" : v.trim();
    }

    private static int parseSlNo(String raw) {
        try {
            return (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
