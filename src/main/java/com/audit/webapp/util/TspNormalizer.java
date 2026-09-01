package com.audit.webapp.util;

import java.util.Locale;
import java.util.Map;

/**
 * Canonicalizes TSP names across the ecosystem.
 * dm.t_tsp_sms_dissemination_statistics uses hyphenated "Vodafone-Idea",
 * older Excel exports used spaced "Vodafone Idea", TRAI file uses "Vodafone Idea" etc.
 */
public final class TspNormalizer {

    private static final Map<String, String> CANONICAL = Map.ofEntries(
            Map.entry("vodafone-idea", "Vodafone Idea"),
            Map.entry("vodafone idea", "Vodafone Idea"),
            Map.entry("vodafoneidea",  "Vodafone Idea"),
            Map.entry("vi",            "Vodafone Idea"),
            Map.entry("reliance jio",  "Reliance Jio"),
            Map.entry("jio",           "Reliance Jio"),
            Map.entry("reliance-jio",  "Reliance Jio"),
            Map.entry("airtel",        "Airtel"),
            Map.entry("bharti airtel", "Airtel"),
            Map.entry("bsnl",          "BSNL"),
            Map.entry("mtnl",          "MTNL")
    );

    private TspNormalizer() {}

    /** Lower-trim-hyphen normalize then map to canonical display form; unknown values returned trimmed as-is. */
    public static String canonical(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        // collapse internal spaces/hyphens handled via map entries
        if (CANONICAL.containsKey(key)) return CANONICAL.get(key);
        // normalize hyphen/space variant for vodafone
        String collapsed = key.replace("-", " ").replaceAll("\\s+", " ").trim();
        if (CANONICAL.containsKey(collapsed)) return CANONICAL.get(collapsed);
        return raw.trim();
    }

    /** For SQL WHERE — lower both sides. Use in JPQL/native LIKE when needed. */
    public static String normalizedKey(String raw) {
        if (raw == null) return null;
        return raw.trim().toLowerCase(Locale.ROOT).replace("-", " ").replaceAll("\\s+", " ");
    }
}
