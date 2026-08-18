package com.audit.xlsx;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Minimal .xlsx reader using only java.util.zip and javax.xml (no Apache POI, no external
 * dependencies at all). Reads shared strings + one worksheet's raw grid as display strings,
 * matching what a spreadsheet UI would show (numbers are printed without ".0" where whole).
 */
public final class SimpleXlsxReader {

    private final List<String> sharedStrings = new ArrayList<>();
    private final Map<String, String> sheetNameToTarget = new LinkedHashMap<>(); // sheet name -> "worksheets/sheetN.xml"
    private final byte[] zipBytes;

    public SimpleXlsxReader(String path) throws IOException {
        this.zipBytes = readAllBytes(path);
        loadSharedStrings();
        loadSheetMap();
    }

    /** Returns the raw grid for the given sheet name (1-indexed row/col semantics preserved as 0-indexed here). */
    public List<String[]> readSheet(String sheetName) throws IOException {
        String target = sheetNameToTarget.get(sheetName);
        if (target == null) {
            // fall back to first sheet if the exact name isn't found
            target = sheetNameToTarget.values().iterator().next();
        }
        return parseSheetXml("xl/" + target);
    }

    public List<String> sheetNames() {
        return new ArrayList<>(sheetNameToTarget.keySet());
    }

    // ---------------------------------------------------------------------

    private void loadSharedStrings() throws IOException {
        byte[] entry = readZipEntry("xl/sharedStrings.xml");
        if (entry == null) return; // no shared strings table (all inline/numeric) - fine
        Document doc = parseXml(entry);
        NodeList siList = doc.getElementsByTagName("si");
        for (int i = 0; i < siList.getLength(); i++) {
            Element si = (Element) siList.item(i);
            sharedStrings.add(collectText(si));
        }
    }

    private String collectText(Element si) {
        // <si> holds either a single <t> or multiple <r><t>...</t></r> runs - concatenate all <t>.
        StringBuilder sb = new StringBuilder();
        NodeList tNodes = si.getElementsByTagName("t");
        for (int i = 0; i < tNodes.getLength(); i++) {
            sb.append(tNodes.item(i).getTextContent());
        }
        return sb.toString();
    }

    private void loadSheetMap() throws IOException {
        byte[] wbBytes = readZipEntry("xl/workbook.xml");
        Document wbDoc = parseXml(wbBytes);
        Map<String, String> nameToRid = new LinkedHashMap<>();
        NodeList sheets = wbDoc.getElementsByTagName("sheet");
        for (int i = 0; i < sheets.getLength(); i++) {
            Element s = (Element) sheets.item(i);
            String name = s.getAttribute("name");
            String rid = s.getAttribute("r:id");
            if (rid.isEmpty()) rid = s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            nameToRid.put(name, rid);
        }

        byte[] relsBytes = readZipEntry("xl/_rels/workbook.xml.rels");
        Document relsDoc = parseXml(relsBytes);
        Map<String, String> ridToTarget = new HashMap<>();
        NodeList rels = relsDoc.getElementsByTagName("Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element r = (Element) rels.item(i);
            ridToTarget.put(r.getAttribute("Id"), r.getAttribute("Target"));
        }

        for (Map.Entry<String, String> e : nameToRid.entrySet()) {
            String target = ridToTarget.get(e.getValue());
            if (target != null) {
                if (target.startsWith("/")) target = target.substring(1);
                sheetNameToTarget.put(e.getKey(), target);
            }
        }
    }

    private List<String[]> parseSheetXml(String entryPath) throws IOException {
        byte[] bytes = readZipEntry(entryPath);
        Document doc = parseXml(bytes);

        Map<Integer, Map<Integer, String>> rows = new TreeMap<>();
        int maxCol = 0;

        NodeList rowNodes = doc.getElementsByTagName("row");
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element rowEl = (Element) rowNodes.item(i);
            int rowNum = Integer.parseInt(rowEl.getAttribute("r"));
            Map<Integer, String> rowMap = rows.computeIfAbsent(rowNum, k -> new TreeMap<>());

            NodeList cellNodes = rowEl.getElementsByTagName("c");
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Element c = (Element) cellNodes.item(j);
                String ref = c.getAttribute("r"); // e.g. "B5"
                int col = colIndexFromRef(ref);
                String type = c.getAttribute("t");
                String value = extractCellValue(c, type);
                rowMap.put(col, value);
                if (col > maxCol) maxCol = col;
            }
        }

        int maxRow = rows.isEmpty() ? 0 : Collections.max(rows.keySet());
        List<String[]> grid = new ArrayList<>();
        grid.add(new String[maxCol + 1]); // index 0 unused (rows are 1-indexed in xlsx); keeps row numbers aligned
        for (int r = 1; r <= maxRow; r++) {
            String[] arr = new String[maxCol + 1];
            Arrays.fill(arr, "");
            Map<Integer, String> rowMap = rows.get(r);
            if (rowMap != null) {
                for (Map.Entry<Integer, String> e : rowMap.entrySet()) {
                    arr[e.getKey()] = e.getValue();
                }
            }
            grid.add(arr);
        }
        return grid;
    }

    private String extractCellValue(Element c, String type) {
        if ("s".equals(type)) {
            String idxStr = firstChildText(c, "v");
            if (idxStr == null || idxStr.isEmpty()) return "";
            int idx = Integer.parseInt(idxStr);
            return idx >= 0 && idx < sharedStrings.size() ? sharedStrings.get(idx) : "";
        }
        if ("str".equals(type)) {
            String v = firstChildText(c, "v");
            return v == null ? "" : v;
        }
        if ("inlineStr".equals(type)) {
            NodeList isList = c.getElementsByTagName("is");
            if (isList.getLength() == 0) return "";
            return collectText((Element) isList.item(0));
        }
        // numeric (t missing or "n")
        String v = firstChildText(c, "v");
        if (v == null || v.isEmpty()) return "";
        try {
            double d = Double.parseDouble(v);
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        } catch (NumberFormatException e) {
            return v;
        }
    }

    private String firstChildText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    /** "B5" -> 1 (0-indexed column: A=0, B=1, ...). Ignores the row-number suffix. */
    static int colIndexFromRef(String ref) {
        int i = 0;
        int col = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) {
            col = col * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1);
            i++;
        }
        return col - 1;
    }

    // ---------------------------------------------------------------------

    private byte[] readZipEntry(String name) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return zis.readAllBytes();
                }
            }
        }
        return null;
    }

    private static byte[] readAllBytes(String path) throws IOException {
        try (InputStream is = new FileInputStream(path)) {
            return is.readAllBytes();
        }
    }

    private static Document parseXml(byte[] bytes) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IOException("Failed to parse XML", e);
        }
    }
}
