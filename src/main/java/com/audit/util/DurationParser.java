package com.audit.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses durations like "9h 17m 43s", "1m 13s", "0s", "16s". Returns -1 for "--" / blank / unparsable. */
public final class DurationParser {

    private static final Pattern PATTERN = Pattern.compile(
            "(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+)s)?", Pattern.CASE_INSENSITIVE);

    private DurationParser() {}

    public static long toSeconds(String raw) {
        if (raw == null) return -1;
        String s = raw.trim();
        if (s.isEmpty() || s.equals("--") || s.equalsIgnoreCase("Awaited")) return -1;

        Matcher m = PATTERN.matcher(s);
        if (!m.matches()) return -1;

        String h = m.group(1);
        String mi = m.group(2);
        String se = m.group(3);
        if (h == null && mi == null && se == null) return -1;

        long hours = h == null ? 0 : Long.parseLong(h);
        long mins = mi == null ? 0 : Long.parseLong(mi);
        long secs = se == null ? 0 : Long.parseLong(se);
        return hours * 3600 + mins * 60 + secs;
    }

    /** Formats seconds back to "Xh Ym Zs" for report readability. */
    public static String toHms(long totalSeconds) {
        if (totalSeconds < 0) return "--";
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (h > 0 || m > 0) sb.append(m).append("m ");
        sb.append(s).append("s");
        return sb.toString();
    }
}
