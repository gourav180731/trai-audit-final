package com.audit.checks;

import java.util.LinkedHashMap;
import java.util.Map;

/** One output row for any check. Uses an ordered map so ReportWriter can render generic columns. */
public class CheckResultRow {
    public final Map<String, Object> fields = new LinkedHashMap<>();
    public boolean flagged;
    public String note = "";

    public CheckResultRow put(String key, Object value) {
        fields.put(key, value);
        return this;
    }
}
