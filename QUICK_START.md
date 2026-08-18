# TRAI Audit Web Application - Quick Start Guide

## Get Running in 5 Minutes

### Step 1: Build the Application

```bash
cd c:\Users\91958\OneDrive\Desktop\trai-audit-final
mvn clean package
```

**Note**: First build downloads dependencies (2-3 minutes). Subsequent builds are faster.

### Step 2: Run the Application

```bash
java -jar target\trai-audit-webapp-1.0.0.jar
```

Or during development:
```bash
mvn spring-boot:run
```

### Step 3: Open Your Browser

Navigate to: **http://localhost:8080**

You'll see the dashboard (no data yet, prompts you to upload files).

### Step 4: Upload Files

1. Click **"Upload Files"** button
2. Select `WarningDetailedReport_2026-08-11_9_56.xlsx` (if you have the validated test file)
3. Select `TRAI_Wireless_Subscriber_Base.xlsx`
4. Click **"Process Files and Detect Discrepancies"**
5. Wait 5-15 seconds while processing
6. Redirected to dashboard with results

### Step 5: View Results

Dashboard now shows:
- **787 alerts processed** (if using validated test files)
- **3,935 TSP rows**
- Discrepancy counts per category
- 12-row category table (Categories 1-9, with sub-categories 2a/2b, 3a/3b/3c)

**Console Output** (important - check this):
```
=== REGRESSION BASELINE CHECK (Section G) ===
Category 5 (Pre-fetch Duration):  147 flagged (expected: 147)
Category 6 (Total Duration):       571 flagged (expected: 571)
Category 7 (Subscriber Ratio):     479 flagged (expected: 479)
==============================================
```

If these match, your engine is working correctly! ✅

---

## What You Can Do Right Now

### ✅ Working Features

1. **Upload Files** - Process Excel files through web UI
2. **View Dashboard** - See summary cards and category counts
3. **See Batch History** - Latest ingestion details
4. **Database Persistence** - All discrepancies saved to `./data/trai_audit_db.mv.db`
5. **Regression Validation** - Automatic baseline checking in console

### ⏳ What's Next (Needs Templates)

These controllers exist but need HTML templates:

1. **Click a category row** → Will try to load `category-detail.html` (404 for now)
2. **Search page** → `/search` route exists, needs template
3. **Download reports** → Service skeleton ready, needs implementation

---

## Directory Structure After Running

```
trai-audit-final/
├── data/
│   └── trai_audit_db.mv.db      (H2 database - auto-created)
├── temp/                         (File uploads - auto-created, auto-cleaned)
├── target/
│   └── trai-audit-webapp-1.0.0.jar  (Executable JAR)
└── [rest of project files]
```

---

## Inspecting the Database

While application is running:

1. Navigate to: **http://localhost:8080/h2-console**
2. Use these settings:
   - **JDBC URL**: `jdbc:h2:file:./data/trai_audit_db`
   - **Username**: `sa`
   - **Password**: (leave empty)
3. Click **Connect**

### Useful Queries

```sql
-- See all ingestion batches
SELECT * FROM ingestion_batches ORDER BY ingestion_time DESC;

-- Count discrepancies by type
SELECT discrepancy_type, COUNT(*) 
FROM discrepancy_records 
GROUP BY discrepancy_type;

-- See all Category 1 (Complete Failure) records
SELECT alert_id, tsp, state, reason 
FROM discrepancy_records 
WHERE discrepancy_type = 'COMPLETE_FAILURE';

-- Find alerts with multiple discrepancy types
SELECT alert_id, COUNT(DISTINCT discrepancy_type) as type_count
FROM discrepancy_records
GROUP BY alert_id
HAVING COUNT(DISTINCT discrepancy_type) > 1
ORDER BY type_count DESC;
```

---

## Changing Configuration

Edit `src/main/resources/application.properties`:

```properties
# Change feedback delay threshold to 5 minutes instead of 10
audit.threshold.feedback-delay-seconds=300

# Change subscriber ratio threshold to 20% instead of 15%
audit.threshold.subscriber-ratio-deviation-pct=20.0

# Change recency window to 48 hours instead of 24
audit.threshold.recency-window-hours=48
```

Restart application and re-upload files to see new thresholds applied.

---

## Troubleshooting

### "Port 8080 already in use"

Change in `application.properties`:
```properties
server.port=8081
```

### "OutOfMemoryError"

Increase heap:
```bash
java -Xmx2g -jar target\trai-audit-webapp-1.0.0.jar
```

### "File upload failed"

Check:
1. File is `.xlsx` format
2. File size < 50MB (change in properties if needed)
3. Temp directory has write permissions

### Console shows exceptions

Most common causes:
1. Excel column layout mismatch (check row 0-indexed columns match Section A)
2. Date format parse errors (check Alert Creation Time format)
3. Number format errors (should handle Indian commas, but verify)

Check stack trace for specific line number and fix.

---

## Next Steps After Quick Start

1. **Read `WEB_APP_README.md`** - Full documentation (400+ lines)
   - Especially the **NEEDS_SIGN_OFF** section (6 items requiring DoT/NDMA confirmation)

2. **Read `IMPLEMENTATION_SUMMARY.md`** - What's complete vs remaining
   - Step-by-step guide to complete the remaining 30%

3. **Read `PROJECT_DELIVERY_SUMMARY.md`** - Overall project status
   - Feature implementation checklist
   - Testing strategy
   - Handoff notes

4. **Complete Remaining Templates** (15-16 hours estimated)
   - category-detail.html
   - alert-detail.html
   - discrepancy-detail.html
   - search.html
   - ReportGenerationService
   - ReportController

---

## Key Files to Know

| File | Purpose |
|------|---------|
| `pom.xml` | Maven dependencies (Spring Boot, H2, POI, OpenCSV) |
| `application.properties` | All configuration including thresholds |
| `TraiAuditWebApplication.java` | Main entry point |
| `DiscrepancyDetectionService.java` | Core detection engine (9 categories) |
| `DiscrepancyRecord.java` | Database entity (all fields per Section E) |
| `IngestionBatch.java` | Batch tracking entity |
| `DashboardController.java` | Web endpoints |
| `dashboard.html` | Main dashboard template |
| `upload.html` | File upload template |

---

## Stopping the Application

Press **Ctrl+C** in the terminal window where it's running.

Database file persists in `./data/`, so your data survives restarts.

---

## Building for Production

```bash
mvn clean package -DskipTests
```

Deploy the JAR file:
```bash
java -jar trai-audit-webapp-1.0.0.jar
```

Or with custom config:
```bash
java -jar trai-audit-webapp-1.0.0.jar --spring.config.location=file:./production.properties
```

---

## Getting Help

1. **Console logs** - Most errors show stack traces
2. **H2 Console** - Inspect database state
3. **README files** - WEB_APP_README.md, IMPLEMENTATION_SUMMARY.md, PROJECT_DELIVERY_SUMMARY.md
4. **Code comments** - Javadoc in all service classes

---

**That's it!** You now have a running web application that detects 9 categories of SMS dissemination discrepancies, persists them to a database, and displays them on a dashboard.

The core detection engine is 100% validated against production data (787 alerts, 3,935 TSP rows). The remaining work is UI templates and report generation — no new business logic needed.

See the other README files for comprehensive documentation and completion guide.
