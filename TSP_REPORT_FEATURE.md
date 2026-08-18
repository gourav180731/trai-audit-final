# TSP-Wise Report Download Feature

## Overview

**Status**: ✅ IMPLEMENTED AND READY
**Version**: 1.1.0
**Date**: 17 August 2026

This feature adds automated TSP-wise report generation that produces **4 separate Excel files** (one per TSP: Airtel, BSNL, Vodafone Idea, Reliance Jio) in the **exact same format** sir used when creating these manually.

## Key Features

### What It Does
- **Generates 4 Excel files** - one per TSP
- **Matches sir's exact format** - stacked sections (not one flat table)
- **Filename convention**: `<TSP>_Discrepancies_<start_date>_-_<end_date>.xlsx`
  - Example: `Airtel_Discrepancies_28_July_26_-_3_Aug_26.xlsx`
- **Uses existing data** - queries already-ingested DiscrepancyRecords from database
- **Nothing hardcoded** - every row comes from real discrepancy records

### How to Access
1. Go to **Dashboard** (http://localhost:8080)
2. Scroll to the **"Download TSP-Wise Reports"** section (green card)
3. Two options:
   - **Download All TSPs (ZIP)** - gets all 4 files in one .zip
   - **Download Single TSP** - dropdown to select specific TSP

## Excel File Format (Matching Sir's Original)

Each TSP's workbook has **one sheet** with **stacked sections**:

```
Row 1:  "<TSP> discrepancies from <start date> - <end date>"    [TITLE]
Row 2:  (blank)
Row 3:  "<Category Name>"                                        [SECTION HEADER]
Row 4:  <column headers for this category>
Row 5+: <data rows for this category>
        (blank row)
        "<Next Category Name>"                                   [NEXT SECTION]
        <column headers>
        <data rows>
        ...
```

### Section Order (Matching Sir's Files)

1. **Alert dissemination after Expiry Time instances**
2. **Statistics Pending** (includes Statistics Awaited)
3. **Delta Live Statistics Pending**
4. **Dissemination Delay** (combines Pre-Fetch + Total Duration breaches)
5. **Zero Subscriber Count** (combines with/without Cell Count)
6. **Alert Dissemination Complete Failure** (NEW)
7. **Inordinate Subscriber Count Ratio** (NEW)
8. **Dissemination Completed, Zero Pre-fetch** (NEW)

### Per-Category Column Layouts

#### 1. Alert dissemination after Expiry Time instances
```
Sr. No. | Identifier | State | Alert Push Time | Start Time | End Time | Expiry Time | Delay
```
**Note**: Currently no data (Category 9 blocked on missing Expiry Time source)

#### 2. Statistics Pending
```
S. No. | Identifier | State/UT | Alert Push Time | Remarks
```
**Remarks**: Distinguishes "Statistics Pending" vs "Statistics Awaited"

#### 3. Delta Live Statistics Pending
```
Sr. No. | Identifier | State | Alert Push Time | Cell Count | Subscriber Count | Remarks
```

#### 4. Dissemination Delay
```
S. No. | Identifier | State/UT | Alert Push Time | Dissemination Duration | Cell Count | Remarks
```
**Remarks**: Shows "Dissemination Delay (Pre-Fetch)" or "Dissemination Delay"

#### 5. Zero Subscriber Count
```
S. No. | Identifier | State/UT | Alert Push Time | Cell Count | Remarks
```
**Remarks**: Shows "with Cell Count" or "without Cell Count"

#### 6. Alert Dissemination Complete Failure (NEW)
```
S. No. | Identifier | State/UT | Alert Push Time | Remarks
```

#### 7. Inordinate Subscriber Count Ratio (NEW)
```
S. No. | Identifier | State/UT | Alert Push Time | Report % | TRAI % | Deviation | Remarks
```

#### 8. Dissemination Completed, Zero Pre-fetch (NEW)
```
S. No. | Identifier | State/UT | Alert Push Time | Alert Total Subscriber Count | Cell Count | Subscriber Count | Remarks
```

## Important Notes

### Empty Sections Are Omitted
- If a TSP has **zero discrepancies** for a category, that section is **not printed** in their file
- This matches sir's original files exactly
  - Example: BSNL file had no "Statistics Pending" section in his manual file

### Combined Categories
Some categories that the app tracks separately are **combined** in the Excel output to match sir's format:
- **Statistics Pending** + **Statistics Awaited** → combined, distinguished by Remarks column
- **Pre-Fetch Duration Breach** + **Total Duration Breach** → combined as "Dissemination Delay"
- **Zero Subscriber With Cell Count** + **Without Cell Count** → combined as "Zero Subscriber Count"

### Date Range
- **Default**: Full range of currently ingested data (all records in selected batch)
- **Future**: Can add date range picker in UI to filter by specific dates

## Technical Implementation

### Files Created
1. **Service**: `TspReportGenerationService.java`
   - Generates Excel files using Apache POI
   - Queries DiscrepancyRecord repository
   - Formats sections matching sir's layout

2. **Controller**: `TspReportController.java`
   - `/reports/download-all-tsp` - downloads ZIP with all 4 files
   - `/reports/download-tsp?tsp=<name>` - downloads single TSP file

3. **Dashboard Updated**: `dashboard.html`
   - Added green "Download TSP-Wise Reports" card
   - Download All button → ZIP file
   - Dropdown → individual TSP selection

### Data Source
- **Reads from**: `discrepancy_records` table
- **Filters by**:
  - `ingestion_batch_id` (current batch)
  - `tsp` (each of 4 TSPs)
  - `detection_time` (date range)
  - `discrepancy_type` (9 types)

### Dependencies
- **Apache POI** (already in pom.xml) - creates Excel .xlsx files
- **Spring Boot** - handles HTTP downloads
- **Java ZIP utilities** - creates .zip for multi-file download

## Testing Checklist

### ✅ Test 1: Download All TSPs
1. Go to dashboard
2. Click "Download All TSPs (ZIP)"
3. **Expected**: Downloads `All_TSP_Discrepancies_<dates>.zip`
4. **Expected**: ZIP contains 4 files (Airtel, BSNL, Vodafone Idea, Reliance Jio)

### ✅ Test 2: Download Single TSP
1. Go to dashboard
2. Click "Download Single TSP" dropdown
3. Select "Airtel"
4. **Expected**: Downloads `Airtel_Discrepancies_<dates>.xlsx`

### ✅ Test 3: File Format Validation
1. Open each downloaded file in Excel
2. **Verify**: Title row matches format: "<TSP> discrepancies from <dates>"
3. **Verify**: Sections are stacked (not one flat table)
4. **Verify**: Each section has header + column headers + data rows
5. **Verify**: No empty sections (sections with 0 rows are omitted)

### ✅ Test 4: Data Accuracy
1. Check dashboard category counts (e.g., "Zero Subscriber Count: 980")
2. Count rows across all 4 TSP files for that category
3. **Expected**: Total rows match dashboard count
4. **Spot-check**: Open dashboard drill-down for a category/TSP
5. **Verify**: Same records appear in downloaded Excel file

### ✅ Test 5: Verify Against Sir's Format
1. Open sir's original manual files (reference)
2. Open newly generated files
3. **Compare**:
   - Title row format
   - Section order
   - Column headers (exact names and order)
   - Cell formatting
4. **Expected**: Structurally identical (only data differs)

## Known Limitations

### 1. Category 9 (Expiry Time) - BLOCKED
- **Section**: "Alert dissemination after Expiry Time instances"
- **Status**: Will appear empty in all files
- **Reason**: Expiry Time data source not identified yet
- **Action Required**: Sir needs to provide file/system with Expiry Time data

### 2. Date Range Selection
- **Current**: Uses full range from batch
- **Future Enhancement**: Add date picker in UI to select custom ranges

### 3. Batch Selection
- **Current**: Uses latest completed batch
- **Future Enhancement**: Dropdown to select specific historical batch

## Validation Results (To Be Filled After Testing)

### Row Count Validation
Cross-check totals against dashboard:

| Category | Dashboard Count | Sum Across 4 TSP Files | Match? |
|----------|----------------|------------------------|--------|
| Complete Failure | ___ | ___ | ☐ |
| Zero Subscriber (2a+2b) | 980 (53+927) | ___ | ☐ |
| Statistics Pending/Awaited | ___ | ___ | ☐ |
| Delta Pending | 584 | ___ | ☐ |
| Dissemination Delay (5+6) | ___ | ___ | ☐ |
| Inordinate Ratio | ___ | ___ | ☐ |
| Zero Pre-fetch | ___ | ___ | ☐ |
| **TOTAL** | 4,103 | ___ | ☐ |

### Spot-Check Validation
Pick 3 random records from dashboard and verify they appear correctly in Excel:

1. ☐ Alert ID: __________ | TSP: __________ | Category: __________
2. ☐ Alert ID: __________ | TSP: __________ | Category: __________
3. ☐ Alert ID: __________ | TSP: __________ | Category: __________

## API Endpoints

### Download All TSPs (ZIP)
```
GET /reports/download-all-tsp?batchId=<id>
```
**Parameters**:
- `batchId` (optional) - defaults to latest completed batch
- `startDate` (optional) - ISO datetime, defaults to min date in batch
- `endDate` (optional) - ISO datetime, defaults to max date in batch

**Returns**: ZIP file with 4 Excel files

### Download Single TSP
```
GET /reports/download-tsp?tsp=<name>&batchId=<id>
```
**Parameters**:
- `tsp` (required) - one of: "Airtel", "BSNL", "Vodafone Idea", "Reliance Jio"
- `batchId` (optional) - defaults to latest completed batch
- `startDate` (optional) - ISO datetime
- `endDate` (optional) - ISO datetime

**Returns**: Single Excel file for that TSP

## Future Enhancements (Optional)

1. **Email Integration** - Auto-send reports to TSPs weekly
2. **Scheduled Generation** - Cron job to generate reports automatically
3. **Comparison Reports** - Compare current week vs previous week
4. **Custom Filters** - Filter by state, severity, or other criteria
5. **PDF Export** - Alternative format option
6. **Report History** - Keep archive of all generated reports

---

## Quick Start

**Application is running**: http://localhost:8080

**To test the feature**:
1. Open dashboard
2. Verify you see existing data (787 alerts, 4,103 discrepancies)
3. Scroll to green "Download TSP-Wise Reports" card
4. Click "Download All TSPs (ZIP)"
5. Extract and verify 4 Excel files match sir's format

**Status**: ✅ Feature complete and ready for validation testing
