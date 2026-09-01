package com.audit.webapp.service;

import com.audit.webapp.entity.DiscrepancyRecord;
import com.audit.webapp.entity.DiscrepancyRecord.DiscrepancyType;
import com.audit.webapp.repository.DiscrepancyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates TSP-wise Excel reports matching sir's exact manual format.
 * Cell-by-cell styling verified from real reference files (28 Jul-3 Aug & 4-13 Aug 2026).
 * Each TSP gets one .xlsx file with stacked sections (one per category with data).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TspReportGenerationService {

    private final DiscrepancyRecordRepository discrepancyRepository;

    private static final List<String> TSP_NAMES = Arrays.asList("Airtel", "BSNL", "Vodafone Idea", "Reliance Jio");
    
    // Date formats - using newer ISO-style format per 4-13 Aug files (newer convention)
    private static final DateTimeFormatter TITLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter FILENAME_DATE_FORMATTER = DateTimeFormatter.ofPattern("d_MMMM_yy", Locale.ENGLISH);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Generate all 4 TSP reports for the given batch and date range.
     * Returns a map of TSP name -> Excel file bytes.
     */
    public Map<String, byte[]> generateAllTspReports(Long batchId, LocalDateTime startDate, LocalDateTime endDate) throws IOException {
        Map<String, byte[]> reports = new LinkedHashMap<>();
        
        for (String tsp : TSP_NAMES) {
            byte[] report = generateSingleTspReport(batchId, tsp, startDate, endDate);
            reports.put(tsp, report);
        }
        
        return reports;
    }

    /**
     * Generate a single TSP report matching sir's exact format with verified styling.
     */
    public byte[] generateSingleTspReport(Long batchId, String tsp, LocalDateTime startDate, LocalDateTime endDate) throws IOException {
        // Fetch all discrepancy records for this TSP and date range
        List<DiscrepancyRecord> allRecords = discrepancyRepository.findByIngestionBatchId(batchId).stream()
                .filter(r -> r.getTsp().equalsIgnoreCase(tsp))
                .filter(r -> !startDate.isAfter(r.getDetectionTime()) && !endDate.isBefore(r.getDetectionTime()))
                .collect(Collectors.toList());

        log.info("Generating report for TSP: {}, records found: {}", tsp, allRecords.size());

        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Sheet name = TSP short name (Airtel, BSNL, "Vodafone Idea", Jio)
        String sheetName = tsp.equals("Reliance Jio") ? "Jio" : tsp;
        Sheet sheet = workbook.createSheet(sheetName);

        // Create cell styles matching sir's files exactly
        XSSFCellStyle titleStyle = createTitleStyle(workbook);
        XSSFCellStyle sectionHeaderStyle = createSectionHeaderStyle(workbook);
        XSSFCellStyle columnHeaderStyle = createColumnHeaderStyle(workbook);
        XSSFCellStyle dataStyle = createDataStyle(workbook);
        XSSFCellStyle remarksStyle = createRemarksStyle(workbook);

        int currentRow = 0;

        // Row 1: Title - "<TSP> discrepancies from DD Month YYYY - DD Month YYYY"
        Row titleRow = sheet.createRow(currentRow++);
        Cell titleCell = titleRow.createCell(0);
        
        // TSP name in title: Airtel, BSNL, VodafoneIdea (no space!), Reliance Jio
        String titleTspName = tsp.equals("Vodafone Idea") ? "VodafoneIdea" : tsp;
        String dateRange = startDate.format(TITLE_DATE_FORMATTER) + " - " + endDate.format(TITLE_DATE_FORMATTER);
        titleCell.setCellValue(titleTspName + " discrepancies from " + dateRange);
        titleCell.setCellStyle(titleStyle);
        
        // Merge title across all columns (up to H for widest section)
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        // Row 2: Blank
        currentRow++;

        // Generate sections in consistent order — live 7 checks + legacy compat
        currentRow = addExpirySection(sheet, allRecords, currentRow, sectionHeaderStyle, columnHeaderStyle, dataStyle);
        currentRow = addCompleteFailureSection(sheet, allRecords, currentRow, sectionHeaderStyle, columnHeaderStyle, dataStyle, remarksStyle);
        currentRow = addZeroSubscriberSection(sheet, allRecords, currentRow, sectionHeaderStyle, columnHeaderStyle, dataStyle, remarksStyle);
        currentRow = addStatisticsPendingSection(sheet, allRecords, currentRow, sectionHeaderStyle, columnHeaderStyle, dataStyle, remarksStyle);
        currentRow = addDeltaPendingSection(sheet, allRecords, currentRow, sectionHeaderStyle, columnHeaderStyle, dataStyle, remarksStyle);
        currentRow = addDisseminationDelaySection(sheet, allRecords, currentRow, sectionHeaderStyle, columnHeaderStyle, dataStyle, remarksStyle);
        currentRow = addInordinateRatioSection(sheet, allRecords, currentRow, sectionHeaderStyle, columnHeaderStyle, dataStyle);
        currentRow = addZeroPrefetchSection(sheet, allRecords, currentRow, sectionHeaderStyle, columnHeaderStyle, dataStyle, remarksStyle);
        currentRow = addExpiredNonZeroSection(sheet, allRecords, currentRow, sectionHeaderStyle, columnHeaderStyle, dataStyle);
        currentRow = addArithmeticMismatchSection(sheet, allRecords, currentRow, sectionHeaderStyle, columnHeaderStyle, dataStyle);

        // Set column widths (approximate from sir's files)
        setColumnWidths(sheet);

        // Write to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream.toByteArray();
    }

    /**
     * Section: Alert dissemination after Expiry Time instances
     * Columns: S. No. | Identifier | State | Alert Push Time | Start Time | End Time | Expiry Time | Delay
     * (Standardized to S. No. across all sections - fixing sir's inconsistency)
     */
    private int addExpirySection(Sheet sheet, List<DiscrepancyRecord> allRecords, int startRow, 
                                  XSSFCellStyle sectionHeaderStyle, XSSFCellStyle columnHeaderStyle,
                                  XSSFCellStyle dataStyle) {
        List<DiscrepancyRecord> records = allRecords.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.DISSEMINATED_AFTER_EXPIRY)
                .collect(Collectors.toList());

        if (records.isEmpty()) return startRow; // Omit empty sections

        int currentRow = startRow;

        // Section header (yellow fill, bold 14, centered, merged across all columns)
        Row sectionHeader = sheet.createRow(currentRow++);
        Cell headerCell = sectionHeader.createCell(0);
        headerCell.setCellValue("Alert dissemination after Expiry Time instances");
        headerCell.setCellStyle(sectionHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 7));

        // Column headers (light blue fill, bold 14, centered, bordered)
        Row colHeaders = sheet.createRow(currentRow++);
        String[] headers = {"S. No.", "Identifier", "State", "Alert Push Time", "Start Time", "End Time", "Expiry Time", "Delay"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = colHeaders.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(columnHeaderStyle);
        }

        // Data rows (regular font 12, centered, bordered, no fill)
        int srNo = 1;
        for (DiscrepancyRecord record : records) {
            Row dataRow = sheet.createRow(currentRow++);
            
            createDataCell(dataRow, 0, srNo++, dataStyle);
            createDataCell(dataRow, 1, record.getAlertId(), dataStyle);
            createDataCell(dataRow, 2, record.getState(), dataStyle);
            createDataCell(dataRow, 3, formatDateTime(record.getAlertCreationTime()), dataStyle);
            createDataCell(dataRow, 4, extractFromRelevantParams(record, "startTime"), dataStyle);
            createDataCell(dataRow, 5, extractFromRelevantParams(record, "endTime"), dataStyle);
            createDataCell(dataRow, 6, extractFromRelevantParams(record, "expiryTime"), dataStyle);
            createDataCell(dataRow, 7, record.getDeviation(), dataStyle); // Delay as HH:MM:SS
        }

        // Blank row after section
        currentRow++;

        return currentRow;
    }

    /**
     * Section: Statistics Pending (includes Statistics Awaited)
     * Columns: S. No. | Identifier | State/UT | Alert Push Time | Remarks
     * Remarks = single merged cell spanning all data rows with "Statistics Pending"
     */
    private int addStatisticsPendingSection(Sheet sheet, List<DiscrepancyRecord> allRecords, int startRow,
                                            XSSFCellStyle sectionHeaderStyle, XSSFCellStyle columnHeaderStyle,
                                            XSSFCellStyle dataStyle, XSSFCellStyle remarksStyle) {
        List<DiscrepancyRecord> records = allRecords.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.STATISTICS_PENDING ||
                            r.getDiscrepancyType() == DiscrepancyType.STATISTICS_AWAITED)
                .collect(Collectors.toList());

        if (records.isEmpty()) return startRow;

        int currentRow = startRow;
        int firstDataRow = currentRow + 2;

        // Section header
        Row sectionHeader = sheet.createRow(currentRow++);
        Cell headerCell = sectionHeader.createCell(0);
        headerCell.setCellValue("Statistics Pending");
        headerCell.setCellStyle(sectionHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 4));

        // Column headers
        Row colHeaders = sheet.createRow(currentRow++);
        String[] headers = {"S. No.", "Identifier", "State/UT", "Alert Push Time", "Remarks"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = colHeaders.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(columnHeaderStyle);
        }

        // Data rows
        int srNo = 1;
        for (DiscrepancyRecord record : records) {
            Row dataRow = sheet.createRow(currentRow++);
            
            createDataCell(dataRow, 0, srNo++, dataStyle);
            createDataCell(dataRow, 1, record.getAlertId(), dataStyle);
            createDataCell(dataRow, 2, record.getState(), dataStyle);
            createDataCell(dataRow, 3, formatDateTime(record.getAlertCreationTime()), dataStyle);
        }

        // Create merged Remarks cell spanning all data rows
        int lastDataRow = currentRow - 1;
        if (lastDataRow > firstDataRow) { // Only merge if 2+ rows
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(4);
            remarksCell.setCellValue("Statistics Pending");
            remarksCell.setCellStyle(remarksStyle);
            sheet.addMergedRegion(new CellRangeAddress(firstDataRow, lastDataRow, 4, 4));
        } else if (lastDataRow == firstDataRow) { // Single row - no merge needed
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(4);
            remarksCell.setCellValue("Statistics Pending");
            remarksCell.setCellStyle(remarksStyle);
        }

        currentRow++;
        return currentRow;
    }

    /**
     * Section: Delta Statistics Pending (standardized name - was inconsistent in sir's files)
     * Columns: S. No. | Identifier | State | Alert Push Time | Cell Count | Subscriber Count | Remarks
     */
    private int addDeltaPendingSection(Sheet sheet, List<DiscrepancyRecord> allRecords, int startRow,
                                       XSSFCellStyle sectionHeaderStyle, XSSFCellStyle columnHeaderStyle,
                                       XSSFCellStyle dataStyle, XSSFCellStyle remarksStyle) {
        List<DiscrepancyRecord> records = allRecords.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.DELTA_PENDING)
                .collect(Collectors.toList());

        if (records.isEmpty()) return startRow;

        int currentRow = startRow;
        int firstDataRow = currentRow + 2;

        // Section header - standardized name
        Row sectionHeader = sheet.createRow(currentRow++);
        Cell headerCell = sectionHeader.createCell(0);
        headerCell.setCellValue("Delta Statistics Pending");
        headerCell.setCellStyle(sectionHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 6));

        // Column headers
        Row colHeaders = sheet.createRow(currentRow++);
        String[] headers = {"S. No.", "Identifier", "State", "Alert Push Time", "Cell Count", "Subscriber Count", "Remarks"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = colHeaders.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(columnHeaderStyle);
        }

        // Data rows
        int srNo = 1;
        for (DiscrepancyRecord record : records) {
            Row dataRow = sheet.createRow(currentRow++);
            
            createDataCell(dataRow, 0, srNo++, dataStyle);
            createDataCell(dataRow, 1, record.getAlertId(), dataStyle);
            createDataCell(dataRow, 2, record.getState(), dataStyle);
            createDataCell(dataRow, 3, formatDateTime(record.getAlertCreationTime()), dataStyle);
            createDataCell(dataRow, 4, record.getCellCount(), dataStyle);
            createDataCell(dataRow, 5, record.getSubscriberCount(), dataStyle);
        }

        // Create merged Remarks cell
        int lastDataRow = currentRow - 1;
        if (lastDataRow > firstDataRow) { // Only merge if 2+ rows
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(6);
            remarksCell.setCellValue("Delta Statistics Pending");
            remarksCell.setCellStyle(remarksStyle);
            sheet.addMergedRegion(new CellRangeAddress(firstDataRow, lastDataRow, 6, 6));
        } else if (lastDataRow == firstDataRow) { // Single row - no merge needed
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(6);
            remarksCell.setCellValue("Delta Statistics Pending");
            remarksCell.setCellStyle(remarksStyle);
        }

        currentRow++;
        return currentRow;
    }

    /**
     * Section: Dissemination Delay
     * Combines PREFETCH_DURATION_MATRIX_BREACH + TOTAL_DURATION_MATRIX_BREACH
     * Columns: S. No. | Identifier | State/UT | Alert Push Time | Dissemination Duration | Cell Count | Remarks
     */
    private int addDisseminationDelaySection(Sheet sheet, List<DiscrepancyRecord> allRecords, int startRow,
                                             XSSFCellStyle sectionHeaderStyle, XSSFCellStyle columnHeaderStyle,
                                             XSSFCellStyle dataStyle, XSSFCellStyle remarksStyle) {
        List<DiscrepancyRecord> records = allRecords.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.PREFETCH_DURATION_MATRIX_BREACH ||
                            r.getDiscrepancyType() == DiscrepancyType.TOTAL_DURATION_MATRIX_BREACH)
                .collect(Collectors.toList());

        if (records.isEmpty()) return startRow;

        int currentRow = startRow;
        int firstDataRow = currentRow + 2;

        // Section header
        Row sectionHeader = sheet.createRow(currentRow++);
        Cell headerCell = sectionHeader.createCell(0);
        headerCell.setCellValue("Dissemination Delay");
        headerCell.setCellStyle(sectionHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 6));

        // Column headers
        Row colHeaders = sheet.createRow(currentRow++);
        String[] headers = {"S. No.", "Identifier", "State/UT", "Alert Push Time", "Dissemination Duration", "Cell Count", "Remarks"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = colHeaders.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(columnHeaderStyle);
        }

        // Data rows
        int srNo = 1;
        for (DiscrepancyRecord record : records) {
            Row dataRow = sheet.createRow(currentRow++);
            
            createDataCell(dataRow, 0, srNo++, dataStyle);
            createDataCell(dataRow, 1, record.getAlertId(), dataStyle);
            createDataCell(dataRow, 2, record.getState(), dataStyle);
            createDataCell(dataRow, 3, formatDateTime(record.getAlertCreationTime()), dataStyle);
            createDataCell(dataRow, 4, formatDuration(record.getDisseminationDurationSeconds()), dataStyle);
            createDataCell(dataRow, 5, record.getCellCount(), dataStyle);
        }

        // Create merged Remarks cell
        int lastDataRow = currentRow - 1;
        if (lastDataRow > firstDataRow) { // Only merge if 2+ rows
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(6);
            remarksCell.setCellValue("Dissemination Delay");
            remarksCell.setCellStyle(remarksStyle);
            sheet.addMergedRegion(new CellRangeAddress(firstDataRow, lastDataRow, 6, 6));
        } else if (lastDataRow == firstDataRow) { // Single row - no merge needed
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(6);
            remarksCell.setCellValue("Dissemination Delay");
            remarksCell.setCellStyle(remarksStyle);
        }

        currentRow++;
        return currentRow;
    }

    /**
     * Section: Zero Subscriber Count
     * Combines ZERO_SUBSCRIBER_WITH_CELL_COUNT + ZERO_SUBSCRIBER_WITHOUT_CELL_COUNT
     * Columns: S. No. | Identifier | State/UT | Alert Push Time | Cell Count | Remarks
     */
    private int addZeroSubscriberSection(Sheet sheet, List<DiscrepancyRecord> allRecords, int startRow,
                                         XSSFCellStyle sectionHeaderStyle, XSSFCellStyle columnHeaderStyle,
                                         XSSFCellStyle dataStyle, XSSFCellStyle remarksStyle) {
        List<DiscrepancyRecord> records = allRecords.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.ZERO_SUBSCRIBER_WITH_CELL_COUNT ||
                            r.getDiscrepancyType() == DiscrepancyType.ZERO_SUBSCRIBER_WITHOUT_CELL_COUNT)
                .collect(Collectors.toList());

        if (records.isEmpty()) return startRow;

        int currentRow = startRow;
        int firstDataRow = currentRow + 2;

        // Section header
        Row sectionHeader = sheet.createRow(currentRow++);
        Cell headerCell = sectionHeader.createCell(0);
        headerCell.setCellValue("Zero Subscriber Count");
        headerCell.setCellStyle(sectionHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 5));

        // Column headers
        Row colHeaders = sheet.createRow(currentRow++);
        String[] headers = {"S. No.", "Identifier", "State/UT", "Alert Push Time", "Cell Count", "Remarks"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = colHeaders.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(columnHeaderStyle);
        }

        // Data rows
        int srNo = 1;
        for (DiscrepancyRecord record : records) {
            Row dataRow = sheet.createRow(currentRow++);
            
            createDataCell(dataRow, 0, srNo++, dataStyle);
            createDataCell(dataRow, 1, record.getAlertId(), dataStyle);
            createDataCell(dataRow, 2, record.getState(), dataStyle);
            createDataCell(dataRow, 3, formatDateTime(record.getAlertCreationTime()), dataStyle);
            createDataCell(dataRow, 4, record.getCellCount(), dataStyle);
        }

        // Create merged Remarks cell
        int lastDataRow = currentRow - 1;
        if (lastDataRow > firstDataRow) { // Only merge if 2+ rows
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(5);
            remarksCell.setCellValue("Zero Subscriber Count");
            remarksCell.setCellStyle(remarksStyle);
            sheet.addMergedRegion(new CellRangeAddress(firstDataRow, lastDataRow, 5, 5));
        } else if (lastDataRow == firstDataRow) { // Single row - no merge needed
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(5);
            remarksCell.setCellValue("Zero Subscriber Count");
            remarksCell.setCellStyle(remarksStyle);
        }

        currentRow++;
        return currentRow;
    }

    /**
     * Section: Alert Dissemination Complete Failure (NEW)
     * Columns: S. No. | Identifier | State/UT | Alert Push Time | Remarks
     */
    private int addCompleteFailureSection(Sheet sheet, List<DiscrepancyRecord> allRecords, int startRow,
                                          XSSFCellStyle sectionHeaderStyle, XSSFCellStyle columnHeaderStyle,
                                          XSSFCellStyle dataStyle, XSSFCellStyle remarksStyle) {
        List<DiscrepancyRecord> records = allRecords.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.COMPLETE_FAILURE)
                .collect(Collectors.toList());

        if (records.isEmpty()) return startRow;

        int currentRow = startRow;
        int firstDataRow = currentRow + 2;

        // Section header
        Row sectionHeader = sheet.createRow(currentRow++);
        Cell headerCell = sectionHeader.createCell(0);
        headerCell.setCellValue("Alert Dissemination Complete Failure");
        headerCell.setCellStyle(sectionHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 4));

        // Column headers
        Row colHeaders = sheet.createRow(currentRow++);
        String[] headers = {"S. No.", "Identifier", "State/UT", "Alert Push Time", "Remarks"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = colHeaders.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(columnHeaderStyle);
        }

        // Data rows
        int srNo = 1;
        for (DiscrepancyRecord record : records) {
            Row dataRow = sheet.createRow(currentRow++);
            
            createDataCell(dataRow, 0, srNo++, dataStyle);
            createDataCell(dataRow, 1, record.getAlertId(), dataStyle);
            createDataCell(dataRow, 2, record.getState(), dataStyle);
            createDataCell(dataRow, 3, formatDateTime(record.getAlertCreationTime()), dataStyle);
        }

        // Create merged Remarks cell
        int lastDataRow = currentRow - 1;
        if (lastDataRow > firstDataRow) { // Only merge if 2+ rows
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(4);
            remarksCell.setCellValue("Complete Failure");
            remarksCell.setCellStyle(remarksStyle);
            sheet.addMergedRegion(new CellRangeAddress(firstDataRow, lastDataRow, 4, 4));
        } else if (lastDataRow == firstDataRow) { // Single row - no merge needed
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(4);
            remarksCell.setCellValue("Complete Failure");
            remarksCell.setCellStyle(remarksStyle);
        }

        currentRow++;
        return currentRow;
    }

    /**
     * Section: Inordinate Subscriber Count Ratio (NEW)
     * Columns: S. No. | Identifier | State/UT | Alert Push Time | Report % | TRAI % | Deviation | Remarks
     */
    private int addInordinateRatioSection(Sheet sheet, List<DiscrepancyRecord> allRecords, int startRow,
                                          XSSFCellStyle sectionHeaderStyle, XSSFCellStyle columnHeaderStyle,
                                          XSSFCellStyle dataStyle) {
        List<DiscrepancyRecord> records = allRecords.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.INORDINATE_SUBSCRIBER_RATIO)
                .collect(Collectors.toList());

        if (records.isEmpty()) return startRow;

        int currentRow = startRow;

        // Section header
        Row sectionHeader = sheet.createRow(currentRow++);
        Cell headerCell = sectionHeader.createCell(0);
        headerCell.setCellValue("Inordinate Subscriber Count Ratio");
        headerCell.setCellStyle(sectionHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 7));

        // Column headers
        Row colHeaders = sheet.createRow(currentRow++);
        String[] headers = {"S. No.", "Identifier", "State/UT", "Alert Push Time", "Report %", "TRAI %", "Deviation", "Remarks"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = colHeaders.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(columnHeaderStyle);
        }

        // Data rows
        int srNo = 1;
        for (DiscrepancyRecord record : records) {
            Row dataRow = sheet.createRow(currentRow++);
            
            createDataCell(dataRow, 0, srNo++, dataStyle);
            createDataCell(dataRow, 1, record.getAlertId(), dataStyle);
            createDataCell(dataRow, 2, record.getState(), dataStyle);
            createDataCell(dataRow, 3, formatDateTime(record.getAlertCreationTime()), dataStyle);
            createDataCell(dataRow, 4, record.getActualValue(), dataStyle);
            createDataCell(dataRow, 5, record.getExpectedValue(), dataStyle);
            createDataCell(dataRow, 6, record.getDeviation(), dataStyle);
            createDataCell(dataRow, 7, "Inordinate Ratio", dataStyle);
        }

        currentRow++;
        return currentRow;
    }

    /**
     * Section: Dissemination Completed, Zero Pre-fetch (NEW)
     * Columns: S. No. | Identifier | State/UT | Alert Push Time | Alert Total Subscriber Count | Cell Count | Subscriber Count | Remarks
     */
    private int addZeroPrefetchSection(Sheet sheet, List<DiscrepancyRecord> allRecords, int startRow,
                                       XSSFCellStyle sectionHeaderStyle, XSSFCellStyle columnHeaderStyle,
                                       XSSFCellStyle dataStyle, XSSFCellStyle remarksStyle) {
        List<DiscrepancyRecord> records = allRecords.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.DISSEMINATION_COMPLETED_ZERO_PREFETCH)
                .collect(Collectors.toList());

        if (records.isEmpty()) return startRow;

        int currentRow = startRow;
        int firstDataRow = currentRow + 2;

        // Section header
        Row sectionHeader = sheet.createRow(currentRow++);
        Cell headerCell = sectionHeader.createCell(0);
        headerCell.setCellValue("Dissemination Completed, Zero Pre-fetch");
        headerCell.setCellStyle(sectionHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 7));

        // Column headers
        Row colHeaders = sheet.createRow(currentRow++);
        String[] headers = {"S. No.", "Identifier", "State/UT", "Alert Push Time", "Alert Total Subscriber Count", "Cell Count", "Subscriber Count", "Remarks"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = colHeaders.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(columnHeaderStyle);
        }

        // Data rows
        int srNo = 1;
        for (DiscrepancyRecord record : records) {
            Row dataRow = sheet.createRow(currentRow++);
            
            createDataCell(dataRow, 0, srNo++, dataStyle);
            createDataCell(dataRow, 1, record.getAlertId(), dataStyle);
            createDataCell(dataRow, 2, record.getState(), dataStyle);
            createDataCell(dataRow, 3, formatDateTime(record.getAlertCreationTime()), dataStyle);
            createDataCell(dataRow, 4, extractFromRelevantParams(record, "alertTotalSubscribers"), dataStyle);
            createDataCell(dataRow, 5, record.getCellCount(), dataStyle);
            createDataCell(dataRow, 6, record.getSubscriberCount(), dataStyle);
        }

        // Create merged Remarks cell
        int lastDataRow = currentRow - 1;
        if (lastDataRow > firstDataRow) { // Only merge if 2+ rows
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(7);
            remarksCell.setCellValue("Zero Pre-fetch");
            remarksCell.setCellStyle(remarksStyle);
            sheet.addMergedRegion(new CellRangeAddress(firstDataRow, lastDataRow, 7, 7));
        } else if (lastDataRow == firstDataRow) { // Single row - no merge needed
            Row firstRow = sheet.getRow(firstDataRow);
            Cell remarksCell = firstRow.createCell(7);
            remarksCell.setCellValue("Zero Pre-fetch");
            remarksCell.setCellStyle(remarksStyle);
        }

        currentRow++;
        return currentRow;
    }

    // Style creation methods - matching sir's exact styling

    /**
     * Title row style: Bold 14, centered, light blue fill (#B4C6E7)
     */
    private XSSFCellStyle createTitleStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Light blue fill - Excel theme "Blue, Accent 1, Lighter 40%" or RGB #B4C6E7
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xB4, (byte)0xC6, (byte)0xE7}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        return style;
    }

    /**
     * Section header style: Bold 14, centered, solid yellow (#FFFF00)
     */
    private XSSFCellStyle createSectionHeaderStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Solid yellow fill
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xFF, (byte)0xFF, (byte)0x00}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        return style;
    }

    /**
     * Column header style: Bold 14, centered, light blue fill, thin borders
     */
    private XSSFCellStyle createColumnHeaderStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Light blue fill (same as title)
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xB4, (byte)0xC6, (byte)0xE7}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Thin black borders on all sides
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }

    /**
     * Data row style: Regular font 12, centered, thin borders, no fill
     */
    private XSSFCellStyle createDataStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Thin black borders on all sides
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }

    /**
     * Remarks column style: Regular font 12, centered, thin borders, no fill
     * Used for merged Remarks cells
     */
    private XSSFCellStyle createRemarksStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Thin black borders on all sides
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }

    // Helper methods

    private void createDataCell(Row row, int column, Object value, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else if (value instanceof Long) {
            cell.setCellValue((Long) value);
        } else {
            cell.setCellValue(value.toString());
        }
        cell.setCellStyle(style);
    }

    private String formatDuration(Long seconds) {
        if (seconds == null) return "";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private String formatDateTime(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) return "";
        try {
            // Try to parse and reformat to ISO style with milliseconds
            LocalDateTime dt = LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return dt.format(DATETIME_FORMATTER);
        } catch (Exception e) {
            // Return as-is if parsing fails
            return dateTime;
        }
    }

    private String extractFromRelevantParams(DiscrepancyRecord record, String key) {
        if (record.getRelevantParameters() == null) return "";
        // Parse relevant parameters string
        // Format expected: "key1=value1, key2=value2"
        String params = record.getRelevantParameters();
        String searchKey = key + "=";
        int startIdx = params.indexOf(searchKey);
        if (startIdx == -1) return "";
        startIdx += searchKey.length();
        int endIdx = params.indexOf(",", startIdx);
        if (endIdx == -1) endIdx = params.length();
        return params.substring(startIdx, endIdx).trim();
    }

    private void setColumnWidths(Sheet sheet) {
        // Approximate column widths from sir's files (in Excel width units)
        // or auto-fit to content
        int[] widths = {9, 24, 30, 26, 29, 30, 36, 24}; // A through H
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256); // Excel width units * 256
        }
    }

    // --- New live-schema sections ---

    private int addExpiredNonZeroSection(Sheet sheet, List<DiscrepancyRecord> allRecords, int startRow,
                                         XSSFCellStyle sectionHeaderStyle, XSSFCellStyle columnHeaderStyle,
                                         XSSFCellStyle dataStyle) {
        List<DiscrepancyRecord> records = allRecords.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.EXPIRED_NONZERO)
                .collect(Collectors.toList());
        if (records.isEmpty()) return startRow;
        int currentRow = startRow;
        Row sectionHeader = sheet.createRow(currentRow++);
        Cell headerCell = sectionHeader.createCell(0);
        headerCell.setCellValue("Expired Before Completion (total_expired / sms_count_expired > 0)");
        headerCell.setCellStyle(sectionHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 6));
        Row colHeaders = sheet.createRow(currentRow++);
        String[] headers = {"S. No.", "Identifier", "TSP", "Start Time", "End Time", "Expired Count", "capPlatform Remarks"};
        for (int i = 0; i < headers.length; i++) { Cell c = colHeaders.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(columnHeaderStyle); }
        int srNo = 1;
        for (DiscrepancyRecord record : records) {
            Row dataRow = sheet.createRow(currentRow++);
            createDataCell(dataRow, 0, srNo++, dataStyle);
            createDataCell(dataRow, 1, record.getAlertId(), dataStyle);
            createDataCell(dataRow, 2, record.getTsp(), dataStyle);
            createDataCell(dataRow, 3, formatDateTime(record.getAlertCreationTime()), dataStyle);
            createDataCell(dataRow, 4, formatDateTime(extractFromRelevantParams(record, "end_time")), dataStyle);
            createDataCell(dataRow, 5, record.getActualValue(), dataStyle);
            createDataCell(dataRow, 6, record.getNote(), dataStyle);
        }
        currentRow++;
        return currentRow;
    }

    private int addArithmeticMismatchSection(Sheet sheet, List<DiscrepancyRecord> allRecords, int startRow,
                                             XSSFCellStyle sectionHeaderStyle, XSSFCellStyle columnHeaderStyle,
                                             XSSFCellStyle dataStyle) {
        List<DiscrepancyRecord> records = allRecords.stream()
                .filter(r -> r.getDiscrepancyType() == DiscrepancyType.ARITHMETIC_MISMATCH)
                .collect(Collectors.toList());
        if (records.isEmpty()) return startRow;
        int currentRow = startRow;
        Row sectionHeader = sheet.createRow(currentRow++);
        Cell headerCell = sectionHeader.createCell(0);
        headerCell.setCellValue("Data Integrity — Arithmetic Mismatch (success+failure+expired != total_subscribers)");
        headerCell.setCellStyle(sectionHeaderStyle);
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 6));
        Row colHeaders = sheet.createRow(currentRow++);
        String[] headers = {"S. No.", "Identifier", "TSP", "Actual (success+failure+expired)", "Expected (total_subscribers)", "Deviation", "Reason"};
        for (int i = 0; i < headers.length; i++) { Cell c = colHeaders.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(columnHeaderStyle); }
        int srNo = 1;
        for (DiscrepancyRecord record : records) {
            Row dataRow = sheet.createRow(currentRow++);
            createDataCell(dataRow, 0, srNo++, dataStyle);
            createDataCell(dataRow, 1, record.getAlertId(), dataStyle);
            createDataCell(dataRow, 2, record.getTsp(), dataStyle);
            createDataCell(dataRow, 3, record.getActualValue(), dataStyle);
            createDataCell(dataRow, 4, record.getExpectedValue(), dataStyle);
            createDataCell(dataRow, 5, record.getDeviation(), dataStyle);
            createDataCell(dataRow, 6, record.getReason(), dataStyle);
        }
        currentRow++;
        return currentRow;
    }

    public String generateFilename(String tsp, LocalDateTime startDate, LocalDateTime endDate) {
        String start = startDate.format(FILENAME_DATE_FORMATTER).replace("_", " ");
        String end = endDate.format(FILENAME_DATE_FORMATTER).replace("_", " ");
        return tsp.replace(" ", "_") + "_Discrepancies_" + start + "_-_" + end + ".xlsx";
    }
}
