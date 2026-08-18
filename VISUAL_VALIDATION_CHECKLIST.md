# 📋 Visual Validation Checklist - TSP Reports

**Purpose**: Verify generated Excel files match sir's manual files exactly

---

## 🎯 Quick Visual Test (5 minutes)

### Step 1: Download Files
1. Go to http://localhost:8080
2. Click **"Download All TSPs (ZIP)"**
3. Extract the 4 files

### Step 2: Open Side-by-Side
Open **Airtel** file (generated) next to sir's **Airtel** reference file

### Step 3: Visual Scan (Should be identical at a glance)

**Row 1 (Title)**:
- [ ] ✅ Bold, large font (14pt)
- [ ] ✅ **Light blue background** (#B4C6E7)
- [ ] ✅ Text: "Airtel discrepancies from DD Month YYYY - DD Month YYYY"
- [ ] ✅ Merged across columns A-H

**Section Headers** (e.g., "Statistics Pending"):
- [ ] ✅ Bold, large font (14pt)
- [ ] ✅ **Bright YELLOW background** (#FFFF00)
- [ ] ✅ Merged across all columns

**Column Headers** (e.g., "S. No. | Identifier | ..."):
- [ ] ✅ Bold, large font (14pt)
- [ ] ✅ **Light blue background** (same as title)
- [ ] ✅ **Thin black borders** on all sides

**Data Rows**:
- [ ] ✅ Regular font (12pt, not bold)
- [ ] ✅ White background (no fill)
- [ ] ✅ **Thin black borders** on all sides
- [ ] ✅ Centered text

**Remarks Column** (CRITICAL - most common mistake):
- [ ] ✅ **ONE MERGED CELL** spanning all data rows
- [ ] ✅ NOT separate cells with repeated text
- [ ] ✅ Contains single caption like "Statistics Pending"

---

## 📊 Detailed Validation

### A. Title Row

**Generated File**:
```
Row 1: [Bold 14, Light Blue Fill, Merged A:H]
"Airtel discrepancies from 04 August 2026 - 13 August 2026"
```

**Reference File**:
```
Row 1: [Bold 14, Light Blue Fill, Merged A:H]
"Airtel discrepancies from 28 July 2026 - 3 August 2026"
```

✅ **Check**: Styling matches (only dates differ = correct!)

---

### B. Section Headers

**Generated File - Statistics Pending**:
```
Row N: [Bold 14, YELLOW Fill, Merged across columns, Centered]
"Statistics Pending"
```

**Reference File**:
```
[Bold 14, YELLOW Fill, Merged across columns, Centered]
"Statistics Pending"
```

✅ **Check**: Exact match

---

### C. Column Headers

**Generated File - Statistics Pending**:
```
Row N+1: [Bold 14, Light Blue Fill, Bordered, Centered]
S. No. | Identifier | State/UT | Alert Push Time | Remarks
```

**Reference File**:
```
[Bold 14, Light Blue Fill, Bordered, Centered]
S. No. | Identifier | State/UT | Alert Push Time | Remarks
```

✅ **Check**: 
- Column names match
- "S. No." (not "Sr. No.") ← standardized
- Styling matches

---

### D. Data Rows

**Generated File**:
```
Row N+2: [Regular 12, No Fill, Bordered, Centered]
1 | ABC123 | Delhi | 2026-08-04 00:46:18.687 | [merged cell →]
2 | DEF456 | Mumbai | 2026-08-04 01:23:45.123 | [merged cell →]
3 | GHI789 | Kolkata | 2026-08-04 02:15:30.456 | [merged cell →]
                                                    ↑
                          ONE MERGED CELL: "Statistics Pending"
```

**Reference File**:
```
[Regular 12, No Fill, Bordered, Centered]
Similar structure with merged Remarks cell
```

✅ **Check**:
- Data styling matches
- Remarks is ONE MERGED CELL (not per-row repeated value)
- Timestamps in ISO format with milliseconds

---

## 🔍 Critical Details to Verify

### 1. Remarks Column Structure

**CORRECT** (What we implemented):
```excel
| S. No. | Identifier | ... | Remarks                |
|--------|------------|-----|------------------------|
| 1      | ABC123     | ... | ║ Statistics Pending  ║
| 2      | DEF456     | ... | ║  (ONE MERGED CELL)  ║
| 3      | GHI789     | ... | ║                      ║
```

**WRONG** (Common mistake):
```excel
| S. No. | Identifier | ... | Remarks            |
|--------|------------|-----|--------------------|
| 1      | ABC123     | ... | Statistics Pending |  ← Repeated
| 2      | DEF456     | ... | Statistics Pending |  ← Repeated
| 3      | GHI789     | ... | Statistics Pending |  ← Repeated
```

### 2. Colors

**Light Blue (Title & Column Headers)**:
- Excel appearance: Soft, pale blue
- RGB: #B4C6E7
- NOT bright blue, NOT dark blue

**Yellow (Section Headers)**:
- Excel appearance: Bright, pure yellow
- RGB: #FFFF00
- NOT pale yellow, NOT gold

### 3. Fonts

**Title & Headers**: Bold, 14pt  
**Data**: Regular (not bold), 12pt

### 4. Borders

- **All data cells**: Thin black borders on ALL 4 sides
- **Column headers**: Thin black borders on ALL 4 sides
- Creates grid appearance

---

## 📐 Section Order Check

**Generated Files Order**:
1. Alert dissemination after Expiry Time instances
2. Alert Dissemination Complete Failure
3. Zero Subscriber Count
4. Statistics Pending
5. Delta Statistics Pending
6. Dissemination Delay
7. Inordinate Subscriber Count Ratio
8. Dissemination Completed, Zero Pre-fetch

**Reference Files**: Variable order (inconsistent)

✅ **Check**: Our order is consistent across all 4 TSPs (better than sir's inconsistent ordering)

---

## 🔢 Data Accuracy Validation

### Dashboard Reference:
- Total Alerts: **787**
- Total Discrepancies: **4,103**
- Zero Subscriber (2a): **53**
- Zero Subscriber (2b): **927**
- Delta Pending: **584**
- [Other categories...]

### File Validation:
1. Open all 4 TSP files
2. Count rows in "Zero Subscriber Count" section across all 4 files
3. **Expected sum**: 53 + 927 = **980 rows**
4. Repeat for each category
5. **Grand total**: **4,103 rows** across all sections and all TSPs

---

## ✅ Pass/Fail Criteria

### ✅ PASS if:
1. Colors match (yellow section headers, light blue title/columns)
2. Fonts match (bold 14 for headers, regular 12 for data)
3. Borders present on all data/column header cells
4. **Remarks column is single merged cell per section**
5. Section headers are merged across columns
6. Title row is merged A:H
7. Empty sections omitted (no section with 0 rows)
8. Total row count = 4,103 across all 4 files
9. Timestamps in ISO format: "2026-08-04 00:46:18.687"
10. Serial numbers: "S. No." (not "Sr. No.")

### ❌ FAIL if:
1. Colors wrong (no yellow, wrong blue shade)
2. Remarks column not merged (repeated per row instead)
3. Section headers not merged
4. No borders on cells
5. Wrong fonts (not bold where should be, or bold where shouldn't)
6. Row counts don't match dashboard
7. Empty sections printed (section with 0 rows shown)

---

## 🎨 Visual Examples

### Title Row - What You Should See:
```
┌─────────────────────────────────────────────────────────────────┐
│ [LIGHT BLUE FILL]                                               │
│ Airtel discrepancies from 04 August 2026 - 13 August 2026      │
│ [BOLD, 14pt, CENTERED, MERGED A:H]                             │
└─────────────────────────────────────────────────────────────────┘
```

### Section Header - What You Should See:
```
┌─────────────────────────────────────────────┐
│ [BRIGHT YELLOW FILL]                        │
│ Statistics Pending                          │
│ [BOLD, 14pt, CENTERED, MERGED across cols] │
└─────────────────────────────────────────────┘
```

### Column Headers - What You Should See:
```
┌──────────┬─────────────┬──────────┬──────────────────┬──────────┐
│ [LIGHT   │ [LIGHT      │ [LIGHT   │ [LIGHT BLUE]     │ [LIGHT   │
│  BLUE]   │  BLUE]      │  BLUE]   │                  │  BLUE]   │
│ S. No.   │ Identifier  │ State/UT │ Alert Push Time  │ Remarks  │
│ [BOLD    │ [BORDERED]  │ [CENTER] │ [BOLD 14pt]      │ [BORDER] │
│  14pt]   │             │          │                  │          │
└──────────┴─────────────┴──────────┴──────────────────┴──────────┘
```

### Data Rows with Merged Remarks - What You Should See:
```
┌────┬─────────┬─────────┬──────────────────────┬────────────────────┐
│ 1  │ ABC123  │ Delhi   │ 2026-08-04 00:46:... │ ║                  ║
├────┼─────────┼─────────┼──────────────────────┤ ║  Statistics      ║
│ 2  │ DEF456  │ Mumbai  │ 2026-08-04 01:23:... │ ║  Pending         ║
├────┼─────────┼─────────┼──────────────────────┤ ║  (ONE MERGED)    ║
│ 3  │ GHI789  │ Kolkata │ 2026-08-04 02:15:... │ ║  CELL)           ║
└────┴─────────┴─────────┴──────────────────────┴────────────────────┘
    ↑                                               ↑
  BORDERED                                    SINGLE MERGED CELL
                                              spanning all 3 rows
```

---

## 🚀 Quick Test Now

**5-Minute Test**:
1. Download: http://localhost:8080 → "Download All TSPs (ZIP)"
2. Extract ZIP
3. Open **Airtel_Discrepancies_....xlsx**
4. Visual scan:
   - Row 1: Light blue? ✅
   - Section headers: Yellow? ✅
   - Column headers: Light blue? ✅
   - Data: Bordered? ✅
   - **Remarks: ONE merged cell?** ✅
5. If all ✅ → **PASS**

---

**Ready to test!** Open http://localhost:8080 and download the reports now.
