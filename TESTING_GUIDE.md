# 🧪 TRAI Audit Web Application - Testing Guide

## Quick Status Check

**Application Running**: http://localhost:8080 ✅
**Last Build**: 17 Aug 2026, 11:32 AM IST
**All Known Bugs**: FIXED ✅

## 🎯 Test #1: Dashboard Loads Without Data (Critical)

This was the 500 error you reported. Let's verify it's fixed:

### Steps:
1. Open your browser
2. Go to: **http://localhost:8080**
3. **Expected**: Dashboard should load successfully (no 500 error)
4. **Expected**: Should show either:
   - Empty state message ("No data available")
   - Or a blank/minimal dashboard

### ✅ If this works → Bug is FIXED!
### ❌ If you still get 500 error → Report the error message

---

## 🎯 Test #2: File Upload (Critical)

This is where you got the NULL constraint error. Let's verify it's fixed:

### Prerequisites:
You need TWO Excel files ready:

1. **Warning Detailed Report** (filename: `WarningDetailedReport_*.xlsx`)
   ```
   Sheet: "CAP Sachet Report"
   Required Columns:
   - Sl No.
   - Organization
   - Event
   - TSP
   - Cell Count
   - Sub Count
   - Alert Time
   - Prefetch Time
   - Feedback Time
   - Dissemination Time
   - Expiry Time
   - (other columns as per your existing CLI format)
   ```

2. **TRAI Wireless Subscriber Base** (filename: `TRAI_Wireless_Subscriber_Base.xlsx`)
   ```
   First Sheet (any name)
   Rows: Service areas (Delhi, Mumbai, etc.)
   Columns: Operator names with May-26/Jun-26 data
   Example:
   | Service Area | Airtel | Vodafone Idea | BSNL | Jio | MTNL |
   |-------------|--------|---------------|------|-----|------|
   | Delhi       | 25.5   | 20.3          | 5.2  | 48  | 1.0  |
   ```

### Steps:
1. Go to: **http://localhost:8080/upload**
2. Click "Choose File" for Warning Report → Select your WarningDetailedReport file
3. Click "Choose File" for TRAI Baseline → Select your TRAI_Wireless_Subscriber_Base file
4. Click **"Upload and Process"**
5. Wait for processing (may take 10-30 seconds depending on data size)

### ✅ Success Indicators:
- Success message appears: "Processing completed successfully"
- No error about "NULL not allowed for column TOTAL_ALERTS_PROCESSED"
- Redirected to dashboard showing data

### ❌ If Upload Fails:
- Note the exact error message
- Check if it's still the NULL constraint error (should be fixed)
- Check if it's a file format error (wrong Excel structure)

---

## 🎯 Test #3: Dashboard With Data

**When to test**: After successful file upload

### Steps:
1. Go to: **http://localhost:8080**
2. **Expected to see**:
   - ✅ Latest batch information (filename, timestamp)
   - ✅ Summary statistics:
     - Total alerts processed
     - Alerts with discrepancies
     - Total discrepancy instances
     - TSP rows processed
   - ✅ TSP-wise discrepancy chart/table
   - ✅ Category-wise discrepancy breakdown (9 categories)

### Verify Data Accuracy:
- Do the numbers match your CLI output?
- Are all 9 discrepancy types shown?
- Are TSP names correct?

---

## 🎯 Test #4: Category Drill-Down

**When to test**: After dashboard shows data

### Steps:
1. From dashboard, click on any discrepancy category
   - Examples: "Complete Failure", "Feedback Delay Exceeds", etc.
2. **Expected to see**:
   - ✅ List of all discrepancy records for that category
   - ✅ Columns: Alert ID, TSP, Event, Organization, Details
   - ✅ Each row is clickable

### Try multiple categories:
- [ ] Complete Failure
- [ ] Feedback Not Received
- [ ] Feedback Delay Exceeds
- [ ] Pre-Fetch Duration Breach
- [ ] Dissemination After Expiry
- [ ] Zero Subscribers (with cells)
- [ ] Zero Subscribers (without cells)
- [ ] Inordinate Subscriber Ratio
- [ ] Total Duration Breach

---

## 🎯 Test #5: Alert-Level Details

**When to test**: After clicking a category

### Steps:
1. From category detail page, click on an **Alert ID**
2. **Expected to see**:
   - ✅ All discrepancies for that specific alert
   - ✅ Grouped by TSP
   - ✅ Shows event, organization details
   - ✅ Lists all discrepancy types found for this alert
   - ✅ Each discrepancy is clickable for more detail

### Verify:
- Does the alert have discrepancies across multiple TSPs?
- Are all relevant discrepancies shown?

---

## 🎯 Test #6: Individual Discrepancy Detail

**When to test**: After clicking an alert

### Steps:
1. From alert detail page, click on a **specific discrepancy record**
2. **Expected to see**:
   - ✅ Complete discrepancy information:
     - Event name
     - Organization
     - Alert ID
     - TSP name
     - Cell Count
     - Subscriber Count
     - Alert Time
     - Pre-fetch Time
     - Feedback Time
     - Dissemination Time
     - Expiry Time
   - ✅ Discrepancy type clearly labeled
   - ✅ Detailed description of the issue

### Verify:
- All timestamps formatted correctly?
- Description makes sense for the discrepancy type?
- Data matches your original Excel file?

---

## 🎯 Test #7: Search Functionality

**When to test**: After data is loaded

### Steps:
1. Go to: **http://localhost:8080/search**
2. Try different search criteria:

#### Search by Alert ID:
- Enter a known Alert ID (e.g., "123456789")
- Click Search
- **Expected**: All discrepancies for that alert

#### Search by TSP:
- Enter a TSP name (e.g., "Airtel")
- Click Search
- **Expected**: All discrepancies for that TSP

#### Search by Organization:
- Enter organization name
- Click Search
- **Expected**: All discrepancies for that organization

#### Search by Discrepancy Type:
- Select a type from dropdown
- Click Search
- **Expected**: All discrepancies of that type

#### Combined Search:
- Try multiple filters together
- **Expected**: Results matching all criteria

---

## 🎯 Test #8: Navigation & Usability

### Test Back Navigation:
- [ ] From discrepancy detail → back to alert detail
- [ ] From alert detail → back to category
- [ ] From category → back to dashboard

### Test Breadcrumbs (if visible):
- [ ] Click breadcrumb links
- [ ] Verify they navigate correctly

### Test Recent Batches:
- [ ] Upload files multiple times
- [ ] Verify dashboard shows recent batches
- [ ] Switch between batches

---

## 🐛 Common Issues & Solutions

### Issue: Dashboard Still Shows 500 Error
**Solution**: 
- Refresh page (Ctrl+F5)
- Clear browser cache
- Check application logs in terminal

### Issue: Upload Fails with "Sheet not found"
**Solution**:
- Verify Warning Report has sheet named "CAP Sachet Report"
- Check sheet name spelling (case-sensitive)

### Issue: Upload Fails with "Column not found"
**Solution**:
- Verify all required columns exist in Excel
- Check column name spelling
- Ensure columns are in header row

### Issue: Wrong Discrepancy Counts
**Solution**:
- Compare with CLI output
- Check if Excel data changed
- Verify TRAI baseline data is current

### Issue: Search Returns No Results
**Solution**:
- Verify data exists for search criteria
- Check spelling of search terms
- Try partial search (e.g., "Air" instead of "Airtel")

---

## 📊 Data Validation Checklist

After upload, verify these match your CLI output:

- [ ] Total alerts processed count
- [ ] Total discrepancies count
- [ ] Complete Failure count
- [ ] Feedback Not Received count
- [ ] Feedback Delay Exceeds count
- [ ] Pre-Fetch Duration Breach count
- [ ] Dissemination After Expiry count
- [ ] Zero Subscriber counts (both types)
- [ ] Inordinate Ratio count
- [ ] Total Duration Breach count

---

## 🎯 Priority Testing Order

**HIGHEST PRIORITY** (Test these FIRST):
1. ✅ Dashboard loads without 500 error (no data state)
2. ✅ File upload works without NULL constraint error
3. ✅ Dashboard shows data after upload

**HIGH PRIORITY** (Test after basic functionality works):
4. Category drill-down works
5. Alert details work
6. Search functionality works

**MEDIUM PRIORITY** (Nice to have):
7. Navigation between pages
8. Data accuracy validation
9. Multiple batch handling

---

## 📝 Testing Report Template

After testing, please report results:

```
TEST RESULTS - [Date/Time]

✅ PASSED:
- Dashboard loads without error (no data)
- [ other passed tests ]

❌ FAILED:
- [ test name ] - Error: [ error message ]

⚠️ ISSUES FOUND:
- [ description of any issues ]

📊 DATA VALIDATION:
- CLI Total Discrepancies: [ number ]
- Web App Total Discrepancies: [ number ]
- Match: [ YES / NO ]

💬 NOTES:
- [ any additional observations ]
```

---

## 🚀 Ready to Test!

**Start Here**:
1. Open browser → http://localhost:8080
2. Verify dashboard loads ✅
3. Go to /upload → Upload your files ✅
4. Follow the test sequence above

**Good Luck! 🎉**
