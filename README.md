# TSP SMS Dissemination Issue-Flagging Tool

Pure Java 17, **zero external dependencies** — no Maven, no Apache POI, no internet needed
to build or run. Reads `.xlsx` files by unzipping and parsing the OOXML XML directly, and
writes `.xlsx` files the same way. Chosen deliberately: your office network may be
restricted, and this way there's nothing to download to build it, ever.

## Sir's 6-point checklist → implementation

| # | Check | Class | Output sheet |
|---|-------|-------|--------------|
| 1 | Alert dissemination complete failure | `Check1CompleteFailure` | `Check1_CompleteFailure` |
| 2 | SMS dissemination feedback not received | `Check2FeedbackNotReceived` | `Check2_FeedbackNotReceived` |
| 3 | Feedback delay > 10 mins | `Check3FeedbackDelay` | `Check3_FeedbackDelay` |
| 4 | Pre-fetch dissemination duration vs DoT matrix | `Check4PreFetch` | `Check4_PreFetchDuration` |
| 5 | Total dissemination duration too high per cell count | `Check5Dissemination` | `Check5_DisseminationDuration` |
| 6 | Inordinate subscriber count ratio vs other TSPs | `Check6SubscriberRatio` | `Check6_SubscriberRatio` |

## Build & run

```bash
cd trai-audit-final
find src -name "*.java" > sources.txt
javac -d out @sources.txt
java -cp out com.audit.Main WarningDetailedReport.xlsx TRAI_Wireless_Subscriber_Base.xlsx Output.xlsx
```

One command produces one workbook with 6 flagged/highlighted sheets, ready to send to sir.

## Before you trust it — read `VALIDATION.md`

That file has the exact row counts and sample flagged rows from a real run against your
actual `WarningDetailedReport_2026-08-11_9_56.xlsx`, produced with an independently written
Python reference implementation (this sandbox has no `javac`, so the Java couldn't be
compiled here — the Python run is the proof the logic is correct; you're the one who
compiles and runs the Java, and it should match those numbers exactly).

## Check-by-check logic

1. **Complete failure** — Cell Count is `--` for that TSP on that alert.
2. **Feedback not received** — Subscriber/SMS Count is `Awaited`, or carries a `**`
   pre-fetch marker (live confirmation not yet in). Rows already caught by #1 are excluded.
3. **Feedback delay > 10 min** — reads `Feedback Delay` directly, flags > 600s
   (`Check3FeedbackDelay.THRESHOLD_SECONDS`).
4. **Pre-fetch duration vs matrix** — MRAD matrix (0–5k cells→60min, 5k–15k→90min,
   15k–30k→120min), restricted to `**`-marked rows.
5. **Duration vs cell count** — same MRAD matrix, all rows.
6. **Subscriber ratio** — `TSP Subscriber Count / Alert Total Subscriber Count` vs that
   TSP's TRAI market share in the matching circle; flags deviation over
   `Check6SubscriberRatio.DEVIATION_THRESHOLD_PCT` (15 pts, placeholder).

## Still open — confirm with sir before relying on the output

1. **Check #6 threshold (15 pts)** — placeholder, get sir's real number.
2. **Check #6 state→circle mapping** — Uttar Pradesh (TRAI splits U.P.(E)/U.P.(W), unclear
   which) and Andaman & Nicobar Islands (no circle in the TRAI file at all) come out as
   `AMBIGUOUS`/`NO_BASELINE` rather than guessed — 169 of 787 alerts affected. Edit
   `TraiBaselineReader.STATE_TO_CIRCLE` once resolved.
3. **Cell Count > 30,000** — outside the MRAD image's defined range; currently falls back to
   the 120-min tier, tagged for manual review in the `Note` column.
4. **Bucket boundaries** are inclusive on the upper edge (exactly 5,000 cells → 60-min tier).
