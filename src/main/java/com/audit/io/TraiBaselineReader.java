package com.audit.io;

import com.audit.xlsx.SimpleXlsxReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads TRAI_Wireless_Subscriber_Base.xlsx and exposes each circle's per-operator
 * % share of subscribers for the latest month column (Jun-26 in this file).
 *
 * Raw grid layout (0-indexed rows/cols) - confirmed against the actual file:
 *   row 2: headers   -> col0 "Service Area", col1 "Bharti Airtel", col3 "Reliance Com.",
 *                        col5 "Vodafone Idea", col7 "BSNL", col9 "MTNL", col11 "Reliance Jio",
 *                        col13 "Total", col15 "Net Addition"
 *   row 3: sub-header-> May-26 / Jun-26 under each paired operator column
 *   rows 4..: one row per circle
 *
 * We use the *_Jun-26 columns (2nd column of each operator pair) as the latest baseline.
 */
public final class TraiBaselineReader {

    public static final String OP_AIRTEL = "Airtel";
    public static final String OP_VI = "Vodafone Idea";
    public static final String OP_BSNL = "BSNL";
    public static final String OP_MTNL = "MTNL";
    public static final String OP_JIO = "Reliance Jio";

    private static final int COL_SERVICE_AREA = 0;
    private static final int COL_AIRTEL_JUN = 2;
    private static final int COL_VI_JUN = 6;
    private static final int COL_BSNL_JUN = 8;
    private static final int COL_MTNL_JUN = 10;
    private static final int COL_JIO_JUN = 12;
    private static final int COL_TOTAL_JUN = 14;

    private static final int DATA_START_ROW_0INDEXED = 4; // grid rows are 1-indexed via SimpleXlsxReader (grid.get(0) unused)

    private TraiBaselineReader() {}

    /** circleName -> (operatorName -> percent share of that circle's Jun-26 total). */
    public static Map<String, Map<String, Double>> read(String path) throws IOException {
        SimpleXlsxReader reader = new SimpleXlsxReader(path);
        java.util.List<String[]> grid = reader.readSheet(reader.sheetNames().get(0));

        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        int startRow = DATA_START_ROW_0INDEXED + 1; // +1 because grid index 0 is the dummy placeholder

        for (int r = startRow; r < grid.size(); r++) {
            String[] row = grid.get(r);
            String circle = cell(row, COL_SERVICE_AREA);
            if (circle.isEmpty()) continue;
            String lower = circle.toLowerCase();
            if (lower.equals("total") || lower.contains("net addition") || lower.contains("subscribers") || lower.contains("source")) {
                continue;
            }

            double total = numeric(row, COL_TOTAL_JUN);
            if (total <= 0) continue;

            Map<String, Double> pct = new HashMap<>();
            pct.put(OP_AIRTEL, numeric(row, COL_AIRTEL_JUN) / total * 100.0);
            pct.put(OP_VI, numeric(row, COL_VI_JUN) / total * 100.0);
            pct.put(OP_BSNL, numeric(row, COL_BSNL_JUN) / total * 100.0);
            pct.put(OP_MTNL, numeric(row, COL_MTNL_JUN) / total * 100.0);
            pct.put(OP_JIO, numeric(row, COL_JIO_JUN) / total * 100.0);

            result.put(circle, pct);
        }
        return result;
    }

    private static String cell(String[] row, int idx) {
        if (idx >= row.length) return "";
        String v = row[idx];
        return v == null ? "" : v.trim();
    }

    private static double numeric(String[] row, int idx) {
        String s = cell(row, idx).replace(",", "");
        if (s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ---------------------------------------------------------------------------------------
    // State (as used in the Warning Report "Organization" column) -> TRAI telecom circle.
    // TRAI reports by licensed telecom circle, not by administrative state, so this is NOT
    // a 1:1 lookup. Groupings below reflect actual TRAI circle structure:
    //   - Chhattisgarh is part of the Madhya Pradesh circle
    //   - Jharkhand is part of the Bihar circle
    //   - Telangana is part of the Andhra Pradesh circle
    //   - Uttarakhand is part of the U.P.(West) circle
    //   - Meghalaya / Mizoram / Nagaland / Tripura are part of the "North East" circle
    // Two states have NO safe automatic mapping and are marked accordingly:
    //   - Uttar Pradesh: TRAI splits this into U.P.(East) and U.P.(West) - the Warning
    //     Report doesn't say which, so this needs a manual decision.
    //   - Andaman & Nicobar Islands: not present as a separate circle in the TRAI file at all.
    // Edit STATE_TO_CIRCLE below once these are resolved with sir.
    // ---------------------------------------------------------------------------------------
    public enum MappingStatus { DIRECT, CIRCLE_GROUPED, AMBIGUOUS, NO_BASELINE }

    public static final class CircleMapping {
        public final String circleName; // null if NO_BASELINE or AMBIGUOUS
        public final MappingStatus status;
        public CircleMapping(String circleName, MappingStatus status) {
            this.circleName = circleName;
            this.status = status;
        }
    }

    private static final Map<String, CircleMapping> STATE_TO_CIRCLE = new HashMap<>();
    static {
        direct("Andhra Pradesh");
        direct("Assam");
        direct("Bihar");
        direct("Haryana");
        direct("Himachal Pradesh");
        direct("Kerala");
        direct("Madhya Pradesh");
        direct("Odisha");
        direct("Punjab");
        direct("Rajasthan");
        direct("West Bengal");

        grouped("Chhattisgarh", "Madhya Pradesh");
        grouped("Jharkhand", "Bihar");
        grouped("Telangana", "Andhra Pradesh");
        grouped("Uttarakhand", "U.P.(W)");
        grouped("Meghalaya", "North East");
        grouped("Mizoram", "North East");
        grouped("Nagaland", "North East");
        grouped("Tripura", "North East");

        STATE_TO_CIRCLE.put("Uttar Pradesh", new CircleMapping(null, MappingStatus.AMBIGUOUS));
        STATE_TO_CIRCLE.put("Andaman and Nicobar Islands", new CircleMapping(null, MappingStatus.NO_BASELINE));
    }

    private static void direct(String state) {
        STATE_TO_CIRCLE.put(state, new CircleMapping(state, MappingStatus.DIRECT));
    }

    private static void grouped(String state, String circle) {
        STATE_TO_CIRCLE.put(state, new CircleMapping(circle, MappingStatus.CIRCLE_GROUPED));
    }

    public static CircleMapping mapStateToCircle(String state) {
        CircleMapping m = STATE_TO_CIRCLE.get(state);
        return m != null ? m : new CircleMapping(null, MappingStatus.NO_BASELINE);
    }
}
