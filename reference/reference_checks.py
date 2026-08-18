"""
Python reference implementation of sir's 6-point checklist.
Mirrors the exact logic that will go into the Java tool - used purely to produce
verified expected counts to test the Java build against, since this sandbox has
no JDK to compile Java directly.
"""
import pandas as pd
import re

WARNING_REPORT = "/mnt/user-data/uploads/WarningDetailedReport_2026-08-11_9_56.xlsx"
TRAI_FILE = "/mnt/user-data/uploads/TRAI_Wireless_Subscriber_Base.xlsx"

# ---------- parsing helpers (mirror DurationParser / NumberParser in Java) ----------

def duration_to_seconds(raw):
    if raw is None:
        return -1
    s = str(raw).strip()
    if s == "" or s == "--" or s.lower() == "awaited":
        return -1
    m = re.match(r"(?:(\d+)h)?\s*(?:(\d+)m)?\s*(?:(\d+)s)?", s, re.IGNORECASE)
    if not m or (m.group(1) is None and m.group(2) is None and m.group(3) is None):
        return -1
    h = int(m.group(1) or 0)
    mi = int(m.group(2) or 0)
    se = int(m.group(3) or 0)
    return h*3600 + mi*60 + se

def parse_number(raw):
    """Returns (value_or_None, pre_fetch_bool)."""
    if raw is None:
        return None, False
    s = str(raw).strip()
    if s == "" or s == "--" or s.lower() == "awaited":
        return None, False
    pre_fetch = "**" in s
    digits = s.replace("**", "").replace(",", "").strip()
    if digits == "":
        return None, pre_fetch
    try:
        return int(float(digits)), pre_fetch
    except ValueError:
        return None, pre_fetch

# ---------- read + group into alerts (mirror WarningReportReader) ----------

def load_groups():
    df = pd.read_excel(WARNING_REPORT, header=None, dtype=str)
    groups = []
    current = None
    for i in range(1, len(df)):  # row 0 = header
        row = df.iloc[i]
        tsp = (row[6] or "").strip() if pd.notna(row[6]) else ""
        if tsp == "":
            continue
        sl_no_raw = row[0]
        is_new = pd.notna(sl_no_raw) and str(sl_no_raw).strip() != ""
        if is_new:
            total_sub, _ = parse_number(row[15])
            total_sms, _ = parse_number(row[17])
            current = {
                "slNo": str(sl_no_raw).strip(),
                "state": (row[1] or "").strip() if pd.notna(row[1]) else "",
                "event": (row[2] or "").strip() if pd.notna(row[2]) else "",
                "alertTime": (row[4] or "").strip() if pd.notna(row[4]) else "",
                "identifier": (row[18] or "").strip() if pd.notna(row[18]) else "",
                "totalSub": total_sub,
                "totalSms": total_sms,
                "rows": [],
            }
            groups.append(current)
        if current is None:
            continue

        cell_count_raw = row[13] if pd.notna(row[13]) else "--"
        cell_count, _ = parse_number(cell_count_raw)
        sub_raw = row[14] if pd.notna(row[14]) else "--"
        sub_val, sub_pf = parse_number(sub_raw)
        sms_raw = row[16] if pd.notna(row[16]) else "--"
        sms_val, sms_pf = parse_number(sms_raw)
        dur_raw = row[11] if pd.notna(row[11]) else "--"
        dur_sec = duration_to_seconds(dur_raw)
        fbdelay_raw = row[12] if pd.notna(row[12]) else "--"
        fbdelay_sec = duration_to_seconds(fbdelay_raw)

        current["rows"].append({
            "tsp": tsp,
            "cellCountRaw": str(cell_count_raw).strip(),
            "cellCount": cell_count,
            "subRaw": str(sub_raw).strip(),
            "sub": sub_val,
            "subPreFetch": sub_pf,
            "smsRaw": str(sms_raw).strip(),
            "sms": sms_val,
            "smsPreFetch": sms_pf,
            "durRaw": str(dur_raw).strip(),
            "durSec": dur_sec,
            "fbDelayRaw": str(fbdelay_raw).strip(),
            "fbDelaySec": fbdelay_sec,
        })
    return groups

# ---------- MRAD matrix ----------

def mrad_threshold_seconds(cell_count):
    if cell_count <= 5000:
        return 60*60
    if cell_count <= 15000:
        return 90*60
    return 120*60  # covers 15k-30k and the unconfirmed >30k fallback

# ---------- 6 checks ----------

def check1_complete_failure(groups):
    out = []
    for g in groups:
        for t in g["rows"]:
            if t["cellCountRaw"] == "--":
                out.append((g["slNo"], g["state"], t["tsp"]))
    return out

def check2_feedback_not_received(groups):
    out = []
    for g in groups:
        for t in g["rows"]:
            if t["cellCountRaw"] == "--":
                continue  # covered by check 1
            sub_awaited = t["subRaw"].lower() == "awaited"
            sms_awaited = t["smsRaw"].lower() == "awaited"
            pre_fetch = t["subPreFetch"] or t["smsPreFetch"]
            if sub_awaited or sms_awaited or pre_fetch:
                out.append((g["slNo"], g["state"], t["tsp"]))
    return out

def check3_feedback_delay(groups, threshold_sec=600):
    out = []
    for g in groups:
        for t in g["rows"]:
            if t["fbDelaySec"] < 0:
                continue
            if t["fbDelaySec"] > threshold_sec:
                out.append((g["slNo"], g["state"], t["tsp"], t["fbDelayRaw"]))
    return out

def check4_prefetch_breach(groups):
    out = []
    for g in groups:
        for t in g["rows"]:
            if not (t["subPreFetch"] or t["smsPreFetch"]):
                continue
            if t["cellCount"] is None or t["durSec"] < 0:
                continue
            thr = mrad_threshold_seconds(t["cellCount"])
            if t["durSec"] > thr:
                out.append((g["slNo"], g["state"], t["tsp"], t["cellCount"], t["durRaw"]))
    return out

def check5_duration_breach(groups):
    out = []
    for g in groups:
        for t in g["rows"]:
            if t["cellCount"] is None or t["durSec"] < 0:
                continue
            thr = mrad_threshold_seconds(t["cellCount"])
            if t["durSec"] > thr:
                out.append((g["slNo"], g["state"], t["tsp"], t["cellCount"], t["durRaw"]))
    return out

# ---------- TRAI baseline + Check 6 ----------

STATE_TO_CIRCLE = {
    "Andhra Pradesh": ("Andhra Pradesh", "DIRECT"),
    "Assam": ("Assam", "DIRECT"),
    "Bihar": ("Bihar", "DIRECT"),
    "Haryana": ("Haryana", "DIRECT"),
    "Himachal Pradesh": ("Himachal Pradesh", "DIRECT"),
    "Kerala": ("Kerala", "DIRECT"),
    "Madhya Pradesh": ("Madhya Pradesh", "DIRECT"),
    "Odisha": ("Odisha", "DIRECT"),
    "Punjab": ("Punjab", "DIRECT"),
    "Rajasthan": ("Rajasthan", "DIRECT"),
    "West Bengal": ("West Bengal", "DIRECT"),
    "Chhattisgarh": ("Madhya Pradesh", "CIRCLE_GROUPED"),
    "Jharkhand": ("Bihar", "CIRCLE_GROUPED"),
    "Telangana": ("Andhra Pradesh", "CIRCLE_GROUPED"),
    "Uttarakhand": ("U.P.(W)", "CIRCLE_GROUPED"),
    "Meghalaya": ("North East", "CIRCLE_GROUPED"),
    "Mizoram": ("North East", "CIRCLE_GROUPED"),
    "Nagaland": ("North East", "CIRCLE_GROUPED"),
    "Tripura": ("North East", "CIRCLE_GROUPED"),
    "Uttar Pradesh": (None, "AMBIGUOUS"),
    "Andaman and Nicobar Islands": (None, "NO_BASELINE"),
}

def load_trai_baseline():
    df = pd.read_excel(TRAI_FILE, header=None, dtype=str)
    result = {}
    for i in range(4, len(df)):
        circle = df.iloc[i, 0]
        if pd.isna(circle):
            continue
        circle = str(circle).strip()
        if circle.lower() in ("total",) or "net addition" in circle.lower() or "subscribers" in circle.lower() or "source" in circle.lower():
            continue
        def num(col):
            v = df.iloc[i, col]
            if pd.isna(v):
                return 0.0
            try:
                return float(str(v).replace(",", ""))
            except ValueError:
                return 0.0
        total = num(14)
        if total <= 0:
            continue
        result[circle] = {
            "Airtel": num(2) / total * 100.0,
            "Vodafone Idea": num(6) / total * 100.0,
            "BSNL": num(8) / total * 100.0,
            "MTNL": num(10) / total * 100.0,
            "Reliance Jio": num(12) / total * 100.0,
        }
    return result

def check6_subscriber_ratio(groups, trai, threshold_pct=15.0):
    out = []
    unmapped = []
    for g in groups:
        mapping = STATE_TO_CIRCLE.get(g["state"], (None, "NO_BASELINE"))
        circle, status = mapping
        if status in ("AMBIGUOUS", "NO_BASELINE"):
            unmapped.append((g["slNo"], g["state"], status))
            continue
        if g["totalSub"] is None or g["totalSub"] <= 0:
            continue
        circle_pct = trai.get(circle)
        if circle_pct is None:
            continue
        for t in g["rows"]:
            if t["sub"] is None:
                continue
            report_pct = t["sub"] * 100.0 / g["totalSub"]
            trai_pct = circle_pct.get(t["tsp"])
            if trai_pct is None:
                continue
            deviation = report_pct - trai_pct
            if abs(deviation) > threshold_pct:
                out.append((g["slNo"], g["state"], t["tsp"], round(report_pct, 2), round(trai_pct, 2), round(deviation, 2)))
    return out, unmapped

# ---------- run ----------

if __name__ == "__main__":
    groups = load_groups()
    print(f"Total alerts parsed: {len(groups)}")
    total_tsp_rows = sum(len(g["rows"]) for g in groups)
    print(f"Total TSP rows parsed: {total_tsp_rows}")
    print()

    c1 = check1_complete_failure(groups)
    print(f"Check #1 Complete failure: {len(c1)} rows flagged")

    c2 = check2_feedback_not_received(groups)
    print(f"Check #2 Feedback not received: {len(c2)} rows flagged")

    c3 = check3_feedback_delay(groups)
    print(f"Check #3 Feedback delay > 10min: {len(c3)} rows flagged")

    c4 = check4_prefetch_breach(groups)
    print(f"Check #4 Pre-fetch MRAD breach: {len(c4)} rows flagged")

    c5 = check5_duration_breach(groups)
    print(f"Check #5 Duration MRAD breach: {len(c5)} rows flagged")

    trai = load_trai_baseline()
    print(f"\nTRAI circles parsed: {len(trai)} -> {sorted(trai.keys())}")
    c6, unmapped = check6_subscriber_ratio(groups, trai)
    print(f"Check #6 Subscriber ratio breach: {len(c6)} rows flagged")
    print(f"Check #6 alerts with no usable state->circle mapping: {len(unmapped)}")
    from collections import Counter
    print("  breakdown:", Counter(s for _, s, _ in unmapped))

    print("\n--- Sample flagged rows ---")
    print("Check1 sample:", c1[:3])
    print("Check2 sample:", c2[:3])
    print("Check3 sample:", c3[:3])
    print("Check4 sample:", c4[:3])
    print("Check5 sample:", c5[:3])
    print("Check6 sample:", c6[:3])
