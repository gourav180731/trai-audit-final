package com.audit.util;

/**
 * Parses counts like "37,988", "1,15,476 **", "--", "Awaited".
 * "--" means the TSP sent nothing for this field; "Awaited" means still pending -
 * both are treated as "no usable value" (null), never as zero.
 */
public final class NumberParser {

    private NumberParser() {}

    public static final class Result {
        public final Long value;
        public final boolean preFetch;
        Result(Long value, boolean preFetch) { this.value = value; this.preFetch = preFetch; }
    }

    public static Result parse(String raw) {
        if (raw == null) return new Result(null, false);
        String s = raw.trim();
        if (s.isEmpty() || s.equals("--") || s.equalsIgnoreCase("Awaited")) {
            return new Result(null, false);
        }
        boolean preFetch = s.contains("**");
        String digits = s.replace("**", "").replace(",", "").trim();
        if (digits.isEmpty()) return new Result(null, preFetch);
        try {
            return new Result(Long.parseLong(digits), preFetch);
        } catch (NumberFormatException e) {
            return new Result(null, preFetch);
        }
    }

    /** For plain integer cells (e.g. Cell Count) with no ** marker expected. */
    public static Integer parseInt(String raw) {
        Result r = parse(raw);
        return r.value == null ? null : r.value.intValue();
    }
}
