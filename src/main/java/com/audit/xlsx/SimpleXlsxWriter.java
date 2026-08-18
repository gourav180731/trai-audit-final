package com.audit.xlsx;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.util.regex.Pattern;

/**
 * Minimal .xlsx writer using only java.util.zip (no Apache POI, no external dependencies).
 * Supports multiple sheets, a bold header row, and a highlighted fill for flagged rows.
 * Numeric-looking values are written as real numeric cells (so Excel can sort/sum them);
 * everything else is written as inline text.
 */
public final class SimpleXlsxWriter {

    private final List<Sheet> sheets = new ArrayList<>();
    private static final Pattern NUMERIC = Pattern.compile("-?\\d+(\\.\\d+)?");

    public static final class Sheet {
        final String name;
        final List<String> headers;
        final List<List<String>> rows;
        final List<Boolean> flagged;
        Sheet(String name, List<String> headers, List<List<String>> rows, List<Boolean> flagged) {
            this.name = name;
            this.headers = headers;
            this.rows = rows;
            this.flagged = flagged;
        }
    }

    public void addSheet(String name, List<String> headers, List<List<String>> rows, List<Boolean> flagged) {
        sheets.add(new Sheet(sanitizeSheetName(name), headers, rows, flagged));
    }

    private static String sanitizeSheetName(String name) {
        String s = name.replaceAll("[\\[\\]:*?/\\\\]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    public void write(String path) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path))) {
            entry(zos, "[Content_Types].xml", contentTypesXml());
            entry(zos, "_rels/.rels", rootRelsXml());
            entry(zos, "xl/workbook.xml", workbookXml());
            entry(zos, "xl/_rels/workbook.xml.rels", workbookRelsXml());
            entry(zos, "xl/styles.xml", stylesXml());
            for (int i = 0; i < sheets.size(); i++) {
                entry(zos, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sheets.get(i)));
            }
        }
    }

    private void entry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    // ---------------------------------------------------------------------

    private String contentTypesXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= sheets.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
              .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        sb.append("</Types>");
        return sb.toString();
    }

    private String rootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private String workbookXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
          .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(escapeXml(sheets.get(i).name)).append("\" sheetId=\"")
              .append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        sb.append("</sheets></workbook>");
        return sb.toString();
    }

    private String workbookRelsXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
              .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
              .append(i + 1).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(sheets.size() + 1)
          .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        sb.append("</Relationships>");
        return sb.toString();
    }

    /** Style 0 = default, 1 = bold header, 2 = rose fill (flagged rows). */
    private String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFC7CE\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"3\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private String sheetXml(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");

        int r = 1;
        sb.append("<row r=\"").append(r).append("\">");
        for (int c = 0; c < sheet.headers.size(); c++) {
            appendCell(sb, r, c, sheet.headers.get(c), 1);
        }
        sb.append("</row>");
        r++;

        for (int i = 0; i < sheet.rows.size(); i++) {
            List<String> row = sheet.rows.get(i);
            int style = (sheet.flagged.get(i) != null && sheet.flagged.get(i)) ? 2 : 0;
            sb.append("<row r=\"").append(r).append("\">");
            for (int c = 0; c < row.size(); c++) {
                appendCell(sb, r, c, row.get(c), style);
            }
            sb.append("</row>");
            r++;
        }

        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private void appendCell(StringBuilder sb, int row, int col, String value, int styleIdx) {
        String ref = colLetter(col) + row;
        String v = value == null ? "" : value;
        sb.append("<c r=\"").append(ref).append("\" s=\"").append(styleIdx).append("\"");
        if (v.isEmpty()) {
            sb.append("/>");
            return;
        }
        if (NUMERIC.matcher(v).matches()) {
            sb.append("><v>").append(v).append("</v></c>");
        } else {
            sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escapeXml(v)).append("</t></is></c>");
        }
    }

    static String colLetter(int col0) {
        StringBuilder sb = new StringBuilder();
        int n = col0 + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private static String escapeXml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        // strip control chars XML can't represent
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
