package com.audit.io;

import com.audit.checks.CheckResultRow;
import com.audit.xlsx.SimpleXlsxWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReportWriter {

    private ReportWriter() {}

    /** sheets: ordered map of sheet name -> rows, e.g. "Check1_CompleteFailure" -> rows. */
    public static void write(String outputPath, Map<String, List<CheckResultRow>> sheets) throws IOException {
        SimpleXlsxWriter writer = new SimpleXlsxWriter();

        for (Map.Entry<String, List<CheckResultRow>> e : sheets.entrySet()) {
            String sheetName = e.getKey();
            List<CheckResultRow> rows = e.getValue();

            if (rows.isEmpty()) {
                writer.addSheet(sheetName, List.of("No rows"), List.of(), List.of());
                continue;
            }

            Set<String> columns = new LinkedHashSet<>();
            for (CheckResultRow r : rows) columns.addAll(r.fields.keySet());

            List<String> headers = new ArrayList<>(columns);
            headers.add("Flagged");
            headers.add("Note");

            List<List<String>> outRows = new ArrayList<>();
            List<Boolean> flaggedList = new ArrayList<>();
            for (CheckResultRow r : rows) {
                List<String> outRow = new ArrayList<>();
                for (String col : columns) {
                    Object v = r.fields.get(col);
                    outRow.add(v == null ? "" : String.valueOf(v));
                }
                outRow.add(r.flagged ? "YES" : "");
                outRow.add(r.note == null ? "" : r.note);
                outRows.add(outRow);
                flaggedList.add(r.flagged);
            }

            writer.addSheet(sheetName, headers, outRows, flaggedList);
        }

        writer.write(outputPath);
    }
}
