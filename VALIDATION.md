# VALIDATION — expected output for `WarningDetailedReport_2026-08-11_9_56.xlsx`

**Why this file exists:** this sandbox has a JRE but no JDK (`javac` doesn't exist) and no
internet access, so the Java code below could not be compiled here. Instead, every check's
exact logic was independently re-implemented in Python and run against your real files —
the numbers below are real, not estimated. Compile the Java tool on a machine with a JDK,
run it on the same input file, and these are the numbers your output should match exactly.
If they don't match, something in the port is wrong — tell me the mismatch and I'll fix it.

## Run against WarningDetailedReport_2026-08-11_9_56.xlsx

```
Total alerts parsed: 787
Total TSP rows parsed: 3935

Check #1 Complete failure:            927 rows flagged
Check #2 Feedback not received:        627 rows flagged
Check #3 Feedback delay > 10min:       289 rows flagged
Check #4 Pre-fetch MRAD breach:        147 rows flagged
Check #5 Duration MRAD breach:         571 rows flagged
Check #6 Subscriber ratio breach:      479 rows flagged
Check #6 alerts with no TRAI mapping:  169  (Uttar Pradesh: 160, Andaman & Nicobar: 9)
```

TRAI circles parsed from `TRAI_Wireless_Subscriber_Base.xlsx`: 22 circles found —
Andhra Pradesh, Assam, Bihar, Delhi, Gujarat, Haryana, Himachal Pradesh, J & K, Karnataka,
Kerala, Kolkata, Madhya Pradesh, Maharashtra, Mumbai, North East, Odisha, Punjab, Rajasthan,
Tamil Nadu, U.P.(E), U.P.(W), West Bengal.

## Sample flagged rows (Sl No., State, TSP, ...) — spot-check these specifically

```
Check1 sample: ('1','Andhra Pradesh','MTNL'), ('1','Andhra Pradesh','Vodafone Idea'), ('2','Uttarakhand','MTNL')
Check2 sample: ('1','Andhra Pradesh','Airtel'), ('2','Uttarakhand','Airtel'), ('2','Uttarakhand','Vodafone Idea')
Check3 sample: ('5','Assam','Reliance Jio','13m 7s'), ('6','Assam','Reliance Jio','12m 25s')
Check4 sample: ('5','Assam','Vodafone Idea',18 cells,'2h 18m 1s')
Check5 sample: ('1','Andhra Pradesh','Airtel',75 cells,'9h 17m 43s')
Check6 sample: ('1','Andhra Pradesh','Reliance Jio', report%=71.91, trai%=36.90, dev=35.01)
```

## How to build and run the Java tool

No Maven, no internet needed — pure JDK, standard library only:

```bash
cd trai-audit-final
find src -name "*.java" > sources.txt
javac -d out @sources.txt
java -cp out com.audit.Main WarningDetailedReport_2026-08-11_9_56.xlsx TRAI_Wireless_Subscriber_Base.xlsx TSP_Issue_Flagging_Report.xlsx
```

The console output prints "N rows evaluated, M flagged" for each of the 6 checks as it
runs — compare those M numbers directly against the table above.

## What was actually verified in this sandbox (even without a compiler)

1. **The check logic itself** — reimplemented identically in Python
   (`reference/reference_checks.py`) and run against your real files. The numbers above are
   from that real run, not a guess.
2. **The XLSX reading assumptions** — your actual file's raw XML was unzipped and inspected
   directly; confirmed shared-string cell format (`t="s"`), empty-cell format, and — for the
   TRAI file — every single column index used in the code (`COL_AIRTEL_JUN=2`,
   `COL_VI_JUN=6`, `COL_BSNL_JUN=8`, `COL_MTNL_JUN=10`, `COL_JIO_JUN=12`, `COL_TOTAL_JUN=14`)
   against the literal raw row for Andhra Pradesh.
3. **The XLSX writing format** — the exact same OOXML-building logic as `SimpleXlsxWriter`
   was ported to Python, used to write a real test file, then opened with `openpyxl` and
   confirmed: correct values, numbers stay numeric, bold headers, and the rose fill lands on
   flagged rows only.

What was **not** verified: the Java source compiling cleanly (no `javac` here) — build it
first thing and paste back any compiler error, they're normally quick to fix.
