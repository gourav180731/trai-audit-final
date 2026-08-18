# TRAI SMS Dissemination Audit - Web Application

Full-stack web application for detecting and monitoring TSP SMS dissemination discrepancies across 9 official categories, per the attached problem statement.

## Architecture

- **Backend**: Spring Boot 3.2 (Java 17+)
- **Database**: H2 (embedded, file-based at `./data/trai_audit_db`)
- **Frontend**: Thymeleaf server-rendered templates
- **Detection Engine**: Reuses 100% of the validated CLI engine logic from the existing codebase
- **Zero external infrastructure**: Runs standalone, no separate database server needed

## Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Build and Run

```bash
# Build the application
mvn clean package

# Run the application
java -jar target/trai-audit-webapp-1.0.0.jar

# Or use Maven directly
mvn spring-boot:run
```

Application will start at: `http://localhost:8080`

H2 Console (for debugging): `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/trai_audit_db`
- Username: `sa`
- Password: (leave empty)

## Uploading Data Files

1. Navigate to the **Upload** page from the dashboard
2. Select the `WarningDetailedReport_*.xlsx` file
3. Select the `TRAI_Wireless_Subscriber_Base.xlsx` file
4. Click **Process Files**
5. System will parse files, run all 9 discrepancy categories, and persist results
6. Dashboard will update with new discrepancy counts and records

## Dashboard Features (Problem Statement Section 4)

The main dashboard displays:

1. **Total Alerts Processed** - count of unique alerts in the latest batch
2. **Total Alerts with Discrepancies** - alerts that have at least one flagged discrepancy
3. **Total Discrepancy Instances** - sum across all 9 categories
4. **TSP-wise Discrepancies** - breakdown by Airtel, BSNL, MTNL, Reliance Jio, Vodafone Idea
5. **Discrepancy Category-wise Count** - 9-row table (see Section below)
6. **Date-wise Discrepancy Trend** - chart showing daily counts over last 30 days
7. **Recent Ingestion Batches** - history of file uploads and processing runs

### 9 Discrepancy Categories (Dashboard Table)

Each row is clickable to drill down:

| # | Category | Sub-categories | Description |
|---|----------|----------------|-------------|
| 1 | Alert Dissemination Complete Failure | - | TSP sent nothing (Cell Count + Dissemination Duration both "--") |
| 2 | Zero Subscriber Count | 2a: WITH Cell Count<br>2b: WITHOUT Cell Count | Subscriber Count missing/zero; split by whether Cell Count present |
| 3 | SMS Dissemination Feedback Not Received | 3a: Statistics Pending<br>3b: Statistics Awaited<br>3c: Delta Pending | "Awaited" (split by recency) or "**" pre-fetch marker |
| 4 | Feedback Delay Greater Than Threshold | - | Feedback Delay > 10 minutes (600s) |
| 5 | Pre-Fetch Duration Not Following DoT Matrix | - | Pre-fetch rows ("**") exceeding MRAD thresholds |
| 6 | Total Duration Too High as per Cell Count | - | All rows exceeding MRAD matrix thresholds |
| 7 | Inordinate Subscriber Count Ratio | - | TSP % deviates from TRAI baseline by > 15 percentage points |
| 8 | Dissemination Completed but Pre-Fetch Zero | - | Alert total > 0 but TSP's pre-fetch value is zero/missing |
| 9 | Alerts Disseminated After Expiry Time | - | **BLOCKED** - data source not available (see below) |

## Drill-Down Navigation (Problem Statement Section 5)

Dashboard → Discrepancy Type → Alert → TSP → Detailed Record

- **Dashboard**: Click any category row to see all flagged records for that type
- **Category View**: Click an alert ID to see all discrepancies for that alert
- **Alert View**: Click a TSP name to see that TSP's specific records
- **Detailed Record**: Shows full context:
  - All relevant raw data (state, event, timestamps, area description)
  - Calculation performed (actual value, expected value, deviation)
  - Plain-language reason why flagged
  - Threshold used and how it was exceeded
  - Notes on data quality or ambiguity

## Search and Filters (Problem Statement Section 6)

All filters can be combined simultaneously on the **Search** page:

**Fully Implemented** (data available in current input files):
- Alert ID
- Date / Date Range
- TSP (Airtel, BSNL, MTNL, Reliance Jio, Vodafone Idea)
- Discrepancy Type (1-9, including sub-categories)
- State
- Cell Count (range)
- Subscriber Count (range)
- Discrepancy Status (OPEN, ACKNOWLEDGED, UNDER_REVIEW, RESOLVED, FALSE_POSITIVE)

**UI-Only / Placeholder** (not in current input files):
- Alert Authorizing Agency - filter UI present but no data to filter on
- District - filter UI present but no data to filter on
- Severity - filter UI present but no data to filter on
- Priority - filter UI present but no data to filter on
- Dissemination Status - partially inferred, no explicit field in source data
- Feedback Status - partially inferred from Awaited/"--" markers

These placeholder filters will activate automatically once the input files or database schema is extended with the missing fields. No code changes needed, just supply the data.

## Reports (Problem Statement Section 7)

Downloadable from the **Reports** page in multiple formats:

### Formats
- **Excel (.xlsx)** - full formatting, ready to forward to TSPs
- **CSV (.csv)** - for further analysis/scripting
- **PDF (.pdf)** - planned for future release

### Report Types
1. **Complete Report** - all discrepancies across all categories, all batches
2. **Per-Category Report** - one file per discrepancy type (1-9)
3. **Date-wise Report** - filter by date/date range
4. **TSP-wise Report** - one report per TSP
5. **Alert-wise Report** - all discrepancies for a specific alert ID
6. **Filtered Report** - export current search/filter results

### Report Content
Every downloaded report includes enough fields to act on the discrepancy and raise the issue directly with the TSP:
- Alert ID, State, Event, Alert Creation Time, Area Description
- TSP name
- Discrepancy type and plain-language reason
- Actual value, Expected value, Deviation
- Cell Count, Subscriber Count, SMS Count (where relevant)
- Dissemination Duration, Feedback Delay (where relevant)
- MRAD threshold and bucket (for duration checks)
- TRAI circle and market share baseline (for ratio checks)
- Detection timestamp and batch ID for audit trail

---

## NEEDS SIGN-OFF FROM SIR

The following 6 items require confirmation/resolution with the DoT/NDMA official who owns the official problem statement. Everything else is fully implemented and operational.

### 1. Feedback Delay Threshold: 5 minutes or 10 minutes?

**Issue**: Problem statement Section 3.4 heading says "**5 Minutes**" but the body text of that same section says "**10 minutes**", and the dashboard table (Section 4) also says "**Feedback Delay Greater Than 5 Minutes**".

**Current Implementation**: Uses **10 minutes (600 seconds)** to match:
- The validated CLI engine's confirmed baseline (Check #3: 289 flagged using 10 min)
- The threshold constant was always named `THRESHOLD_SECONDS = 600`
- Matches the original "sir's 6-point checklist" memo

**Configuration**: `audit.threshold.feedback-delay-seconds=600` in `application.properties`

**Action Required**: Confirm which threshold is official. If 5 minutes, change to `300` and re-run against the real files to get new validated baseline count.

---

### 2. Statistics Pending vs Statistics Awaited Split: What is the recency window?

**Issue**: Category 3 (SMS Dissemination Feedback Not Received) must be split into:
- **3a: Statistics Pending** - "Awaited" and alert is recent (expected/normal shortly after alert)
- **3b: Statistics Awaited** - "Awaited" but alert is old (overdue, more serious)

Problem statement does not provide the cutoff hours/days that separates "recent" from "overdue".

**Current Implementation**: Uses **24 hours** as the recency window. Both 3a and 3b check for "Awaited" in Subscriber Count or SMS Count, but split based on whether Alert Creation Time is within 24 hours of the current detection run.

**Configuration**: `audit.threshold.recency-window-hours=24` in `application.properties`

**Action Required**: Confirm the official recency window. Common choices: 12h, 24h, 48h, or "same calendar day". Adjust config and re-run.

---

### 3. Subscriber Ratio Deviation Threshold: Is 15 percentage points correct?

**Issue**: Category 7 (Inordinate Subscriber Count Ratio) flags if a TSP's reported % deviates from its TRAI market share by more than a threshold. The original CLI engine used **15 percentage points** as a placeholder because no explicit DoT/NDMA number was ever provided in the source material.

**Current Implementation**: `audit.threshold.subscriber-ratio-deviation-pct=15.0`

**Action Required**: Confirm the official threshold. The validated CLI baseline (Check #6: 479 flagged) was computed with 15%. If the real threshold differs, the count will change.

---

### 4. Uttar Pradesh and Andaman & Nicobar Islands: State→Circle Mapping

**Issue**: TRAI reports by telecom circle, not administrative state. Two states have no safe automatic mapping:

- **Uttar Pradesh**: TRAI splits into `U.P.(East)` and `U.P.(West)` circles. Warning Report's "Organization" column just says "Uttar Pradesh" with no indication which half. Mapping is **AMBIGUOUS**.
- **Andaman and Nicobar Islands**: Not present as a separate circle in the TRAI baseline file at all. Mapping is **NO_BASELINE**.

**Current Implementation**: These alerts still surface in Category 7 (Subscriber Ratio) output with the ratio fields blank and a clear note explaining the issue. They are never silently dropped. Detection count: **169 alerts affected** (160 Uttar Pradesh, 9 Andaman & Nicobar).

**Location in Code**: `TraiBaselineReader.STATE_TO_CIRCLE` static map in `TraiBaselineReader.java`

**Action Required**:
- For Uttar Pradesh: obtain a rule (district-based? lat/long-based?) to split into U.P.(E) vs U.P.(W), or get a separate CAP field that disambiguates. Update the map once resolved.
- For Andaman & Nicobar: confirm if there's a circle for it in a newer/different TRAI file, or if it's genuinely out-of-scope for this check.

---

### 5. Category 8 (Dissemination Completed but Pre-Fetch Zero): Inferred Definition

**Issue**: Category 8 is defined as "Dissemination Completed but Pre-Fetch Shows Zero Subscriber Count". This implies the underlying system maintains a distinct **pre-fetch snapshot** value separate from the final value.

**Problem**: The two input files (`WarningDetailedReport_*.xlsx` and `TRAI_Wireless_Subscriber_Base.xlsx`) only show each TSP row's **current** Subscriber Count value, marked with "**" if still provisional. There is no separate "pre-fetch stage value" column or history table.

**Current Implementation (Best-Effort)**: Flags an alert where:
- Alert Total Subscriber Count > 0 (overall dissemination completed with real subscribers reached by at least one TSP), AND
- At least one TSP row in that same alert has a "**"-marked Subscriber Count that is 0, missing, or "--"

This is an **inference**, not a guarantee of correctness.

**Action Required**:
1. Confirm if the real CAP Sachet system logs/database exposes an actual "pre-fetch snapshot" table or history — e.g., a separate `PRE_FETCH_SUBSCRIBER_COUNT` column or a timestamped history of Subscriber Count updates per TSP per alert.
2. If yes, rebuild Category 8 detection against that real data source.
3. If no such data exists, confirm whether this category can be detected at all with current data, or if it should remain as a placeholder until the data gap is resolved.

**Note**: This is flagged in every Category 8 discrepancy record's `note` field and in the UI.

---

### 6. Category 9 (Alerts Disseminated After Expiry Time): Data Source Missing

**Issue**: Category 9 requires an **Alert Expiry Time** for each alert (the `<expires>` timestamp from the original CAP alert XML, or an equivalent field).

**Problem**: Neither `WarningDetailedReport_*.xlsx` nor `TRAI_Wireless_Subscriber_Base.xlsx` contains this field. The problem statement references it but it's not in the actual column layout.

**Current Implementation**: Category 9's schema, UI hooks, and detection engine method are all present and ready to wire up, but the method returns **0 evaluated** because there's no Expiry Time data to compare against. The dashboard shows "0" for this category with a tooltip: "Expiry Time data source not available."

**DO NOT** fabricate or infer expiry times (e.g., don't guess "alert creation time + 4 hours" without confirmation).

**Action Required**:
1. Obtain a file export or database table from the CAP Sachet system that includes Alert Expiry Time (ideally keyed by the same `Identifier` column).
2. Once available, wire it into the `WarningReportReader` or create a separate reader, and update `detectCategory9AfterExpiry()` to compare dissemination timestamps against expiry.
3. If Expiry Time will never be available in a file export, confirm whether this category should be dropped from the requirements entirely or implemented via direct database access to the CAP Sachet production DB.

---

## Regression Baseline (Section G)

The validated CLI engine processed real production data:
- **787 alerts**
- **3,935 TSP rows**

Against the same files, the **unchanged** categories must still produce these exact counts:

| Category | Expected Flagged Count | Notes |
|----------|------------------------|-------|
| 5 (Pre-fetch Duration) | **147** | Old Check #4, logic unchanged |
| 6 (Total Duration) | **571** | Old Check #5, logic unchanged |
| 7 (Subscriber Ratio) | **479** | Old Check #6, logic unchanged, threshold 15% |
| 7 (Unmapped States) | **169** | Uttar Pradesh: 160, Andaman & Nicobar: 9 |

The **changed/new** categories will report different counts:
- Category 1 (Complete Failure): subset of old Check #1
- Category 2a/2b (Zero Subscriber): the rest of old Check #1 (was 927 total combined)
- Category 3a/3b/3c (Feedback Not Received): split of old Check #2 (was 627 total combined)
- Category 4 (Feedback Delay): should match old Check #3 (**289 flagged**) once the 5-min vs 10-min threshold is confirmed
- Category 8: NEW, no baseline yet
- Category 9: BLOCKED, will be 0 until data source provided

The application logs these counts to the console on every ingestion run for easy validation.

---

## Configuration Reference

All thresholds are externalizable in `application.properties`:

```properties
# Feedback delay threshold (seconds) - default 600 (10 minutes)
# NEEDS_SIGN_OFF: confirm 5 min (300) or 10 min (600)
audit.threshold.feedback-delay-seconds=600

# Subscriber ratio deviation threshold (percentage points) - default 15.0
# NEEDS_SIGN_OFF: confirm official DoT/NDMA threshold
audit.threshold.subscriber-ratio-deviation-pct=15.0

# Recency window for Statistics Pending vs Awaited (hours) - default 24
# NEEDS_SIGN_OFF: confirm cutoff for "recent" alerts
audit.threshold.recency-window-hours=24
```

Change these values and restart the application to reprocess with new thresholds. Historical data in the database is not retroactively recalculated — upload the files again to create a new batch with the new thresholds.

---

## Database Schema

### `discrepancy_records` table
Persists every detected discrepancy per problem statement Section E:

- `id` (PK)
- `alert_id` - from source file "Identifier" column
- `tsp` - Airtel / BSNL / MTNL / Reliance Jio / Vodafone Idea
- `discrepancy_type` - one of the 9 category enum values (includes sub-categories)
- `detection_time` - when the engine ran (not the alert time)
- `relevant_parameters` - specific input values that drove the decision
- `actual_value` - what was found
- `expected_value` - what the threshold/baseline was
- `deviation` - numeric/percentage deviation where applicable
- `status` - OPEN / ACKNOWLEDGED / UNDER_REVIEW / RESOLVED / FALSE_POSITIVE
- Supporting data: `state`, `event`, `alert_creation_time`, `area_description`, `sl_no`
- Metadata for filtering: `district`, `alert_authorizing_agency`, `severity`, `priority` (placeholders until data available)
- Numeric fields: `cell_count`, `subscriber_count`, `sms_count`, `dissemination_duration_seconds`, `feedback_delay_seconds`
- `ingestion_batch_id` (FK) - links to the batch that created this record
- `reason` - plain-language explanation
- `note` - additional context, data quality notes, NEEDS_SIGN_OFF flags

### `ingestion_batches` table
Tracks each file upload/processing run:

- `id` (PK)
- `ingestion_time`
- `warning_report_filename`
- `trai_baseline_filename`
- `total_alerts_processed`
- `total_tsp_rows_processed`
- `total_alerts_with_discrepancies`
- `total_discrepancy_instances`
- Category-wise counts: `count_complete_failure`, `count_zero_subscriber_with_cell_count`, etc. (12 columns, one per category/sub-category)
- `status` - PROCESSING / COMPLETED / FAILED
- `error_message` - if FAILED

---

## Reuse of Validated CLI Logic

**100% of the parsing and detection logic from the original CLI engine is reused**, ensuring the web application behaves identically to the validated baseline:

### Reused Classes (unchanged):
- `com.audit.io.WarningReportReader` - parses `WarningDetailedReport_*.xlsx`
- `com.audit.io.TraiBaselineReader` - parses `TRAI_Wireless_Subscriber_Base.xlsx`, includes state→circle mapping
- `com.audit.model.AlertGroup` - alert-level data model
- `com.audit.model.TspRow` - per-TSP row data model
- `com.audit.checks.MradMatrix` - MRAD compliance thresholds (0-5k→60min, 5k-15k→90min, 15k-30k→120min)
- `com.audit.util.DurationParser` - parses "XhYmZs" format
- `com.audit.util.NumberParser` - handles Indian comma grouping, "--", "Awaited", "**" markers
- `com.audit.xlsx.SimpleXlsxReader` / `SimpleXlsxWriter` - zero-dependency OOXML parsing

### Extended Classes:
- `DiscrepancyDetectionService` - wraps the original Check1-6 logic, adds Category 8/9, splits Categories 1/2/3 into sub-categories, persists to DB
- Original `Check1CompleteFailure` through `Check6SubscriberRatio` logic is called directly or replicated method-for-method

### New Classes (web layer only):
- Entity classes (`DiscrepancyRecord`, `IngestionBatch`)
- Repository interfaces (Spring Data JPA)
- Service classes (`DashboardService`, `DiscrepancySearchService`, `ReportGenerationService`)
- Controller classes (`DashboardController`, `UploadController`, `SearchController`, `ReportController`)
- Thymeleaf templates (HTML frontend)

**No business logic was changed.** The web application is a wrapper around the validated engine, not a rewrite.

---

## Acceptance Test Procedure

1. Place the validated input files in a known directory:
   - `WarningDetailedReport_2026-08-11_9_56.xlsx`
   - `TRAI_Wireless_Subscriber_Base.xlsx`

2. Start the application: `mvn spring-boot:run`

3. Navigate to the Upload page and process these files

4. Check the console log output for the regression baseline report (logged automatically)

5. Verify these counts in the dashboard and database:
   - Total alerts: **787**
   - Total TSP rows: **3,935**
   - Category 5 flagged: **147**
   - Category 6 flagged: **571**
   - Category 7 flagged: **479**
   - Category 7 unmapped: **169** (Uttar Pradesh: 160, Andaman & Nicobar: 9)

6. For the new/changed categories (1, 2, 3, 4, 8):
   - Check the dashboard counts
   - Drill down into a few sample records and verify the reason field matches the logic described above
   - Confirm the sum across all sub-categories matches the old combined counts where applicable

7. Test drill-down navigation: Dashboard → Category → Alert → TSP → Detailed Record

8. Test filters: combine multiple filters simultaneously, verify results

9. Test report downloads: Excel and CSV, verify they contain all required fields

10. Category 9 should show **0 evaluated** with the note about missing data source — this is correct/expected

---

## Deployment Notes

### Production Checklist
- [ ] Disable H2 Console: `spring.h2.console.enabled=false`
- [ ] Change database location to a persistent volume: `spring.datasource.url=jdbc:h2:file:/var/data/trai_audit_db`
- [ ] Set `spring.jpa.hibernate.ddl-auto=validate` (not `update`) after initial schema creation
- [ ] Configure backup strategy for `./data/trai_audit_db.mv.db` file
- [ ] Set `logging.level.com.audit=INFO` (not DEBUG)
- [ ] Review `server.port` if 8080 conflicts with other services
- [ ] Set up file upload size limits per organizational policy
- [ ] Configure reverse proxy (nginx/Apache) if needed
- [ ] Set up monitoring/alerting for failed ingestion batches

### Scaling Considerations
- H2 is fine for single-user/office use with thousands of alerts
- For tens of thousands of alerts or multi-user concurrent access, migrate to PostgreSQL/MySQL:
  - Change `spring.datasource.url` to Postgres/MySQL JDBC URL
  - Change `spring.jpa.database-platform` to match
  - Add JDBC driver dependency to `pom.xml`
  - No code changes needed — Spring Data JPA handles it

---

## Support and Maintenance

### Adding New Discrepancy Categories
1. Add new enum value to `DiscrepancyRecord.DiscrepancyType`
2. Add count field to `IngestionBatch` entity
3. Write detection method in `DiscrepancyDetectionService`
4. Call it from `processFiles()` and update batch counts
5. Add dashboard card/row in `dashboard.html` template

### Adjusting MRAD Matrix
Edit `MradMatrix.java`:
```java
public static int thresholdMinutes(int cellCount) {
    if (cellCount <= 5_000) return 60;
    if (cellCount <= 15_000) return 90;
    if (cellCount <= 50_000) return 120; // example: extend range
    return 150; // example: new tier
}
```

### Adding New Filters
1. Add field to `DiscrepancyRecord` entity if not present
2. Add filter parameter to `DiscrepancySearchService.search()` method
3. Add filter input to `search.html` template
4. No database migration needed if field already exists as placeholder

---

## Differences from CLI Tool

| Aspect | CLI Tool | Web Application |
|--------|----------|----------------|
| Interface | Command-line, one-shot | Web dashboard, persistent history |
| Output | Single Excel file, 6 sheets | Database records + downloadable reports |
| Categories | 6 checks | 9 categories (12 sub-categories total) |
| Dependencies | Zero (pure Java) | Spring Boot + H2 + Apache POI |
| Usage | `java -jar ... file1 file2 output` | Upload files via web UI |
| Drill-down | Manual Excel navigation | Clickable dashboard → alert → TSP → record |
| Filtering | Manual Excel filters | Combined multi-field search |
| Historical data | None (each run overwrites) | All batches persisted, trend analysis |
| Validation | External Python reference | Self-contained with regression logging |

---

## License and Attribution

This application reuses the validated detection engine logic developed and confirmed against real production data (787 alerts, 3,935 TSP rows). The original CLI tool was built with zero external dependencies for restricted network environments; the web application adds Spring Boot and a database for persistence and multi-user access, but preserves 100% of the parsing/detection behavior.

---

## Quick Reference: Problem Statement Section Mapping

| Section | Requirement | Implementation |
|---------|-------------|----------------|
| 3 | 9 discrepancy categories | `DiscrepancyDetectionService` + 12 sub-category enums |
| 4 | Dashboard with summary cards | `DashboardController` + `dashboard.html` |
| 5 | Drill-down navigation | `DashboardController` + category/alert/discrepancy detail pages |
| 6 | Search and filters | `DiscrepancySearchService` + `SearchController` + `search.html` |
| 7 | Reports (Excel/CSV/PDF) | `ReportGenerationService` + `ReportController` |
| 8 | Discrepancy record schema | `DiscrepancyRecord` entity |
| E | Structured persistence | `discrepancy_records` table |
| G | Regression baseline | Logged on every ingestion, validated against 787/3935 baseline |

---

## Contact and Escalation

For questions on the 6 "NEEDS_SIGN_OFF" items above, contact the DoT/NDMA official who provided the original problem statement ("sir" in the source documentation).

For technical issues with the application itself, check:
1. Console logs for exception stack traces
2. H2 Console to inspect database state
3. `application.properties` for configuration errors
4. This README's troubleshooting section (below)

---

## Troubleshooting

**Problem**: Application won't start, "port 8080 already in use"
**Solution**: Change `server.port=8081` in `application.properties`, or stop the conflicting service

**Problem**: File upload fails with "Maximum upload size exceeded"
**Solution**: Increase `spring.servlet.multipart.max-file-size` in properties

**Problem**: Dashboard shows 0 discrepancies but files uploaded successfully
**Solution**: Check console logs for parsing errors. Verify column layout in Excel files matches Section A (0-indexed columns). Check that Alert Creation Time format is parseable.

**Problem**: Category 7 shows many "AMBIGUOUS" records
**Solution**: This is expected for Uttar Pradesh alerts (160 of 787). See NEEDS_SIGN_OFF #4 above.

**Problem**: Regression baseline counts don't match (Categories 5/6/7)
**Solution**: Verify you're using the exact same input files (`WarningDetailedReport_2026-08-11_9_56.xlsx`). Verify thresholds in `application.properties` match the validated baseline (15% for ratio, 10min for delay). If still mismatched, check for version skew in reused classes — diff against the original CLI tool's `.java` files.

**Problem**: H2 database file is locked
**Solution**: Ensure only one instance of the application is running. H2's `AUTO_SERVER=TRUE` mode allows multiple connections but requires the first instance to stay running.

---

**End of README**
