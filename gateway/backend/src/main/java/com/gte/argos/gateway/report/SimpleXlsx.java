package com.gte.argos.gateway.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 표 하나짜리 .xlsx 를 라이브러리 없이 만든다 (OOXML 최소 구성: 글자는 inlineStr, 숫자는 숫자 칸, 첫 줄 굵게).
 * 엑셀·한셀·구글 시트에서 열린다.
 */
public final class SimpleXlsx {

    private SimpleXlsx() {
    }

    public static byte[] write(String sheetName, List<String> header, List<List<Object>> rows) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>""");
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""");
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>""".formatted(esc(sheetName)));
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>""");
            put(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <fonts count="2"><font><sz val="11"/><name val="맑은 고딕"/></font><font><b/><sz val="11"/><name val="맑은 고딕"/></font></fonts>
                    <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                    <borders count="1"><border/></borders>
                    <cellStyleXfs count="1"><xf/></cellStyleXfs>
                    <cellXfs count="2"><xf fontId="0"/><xf fontId="1" applyFont="1"/></cellXfs>
                    </styleSheet>""");
            StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            row(sb, 1, List.copyOf(header), true);
            int r = 2;
            for (List<Object> cells : rows) {
                row(sb, r++, cells, false);
            }
            sb.append("</sheetData></worksheet>");
            put(zip, "xl/worksheets/sheet1.xml", sb.toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private static void row(StringBuilder sb, int r, List<?> cells, boolean bold) {
        sb.append("<row r=\"").append(r).append("\">");
        for (int c = 0; c < cells.size(); c++) {
            Object v = cells.get(c);
            String ref = col(c) + r;
            String style = bold ? " s=\"1\"" : "";
            if (v == null || "".equals(v)) {
                continue;
            }
            if (v instanceof Number n && !bold) {
                sb.append("<c r=\"").append(ref).append('"').append(style).append("><v>").append(n).append("</v></c>");
            } else {
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(style).append("><is><t xml:space=\"preserve\">")
                        .append(esc(String.valueOf(v))).append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    /** 0 → A, 25 → Z, 26 → AA */
    static String col(int i) {
        StringBuilder s = new StringBuilder();
        int n = i + 1;
        while (n > 0) {
            int m = (n - 1) % 26;
            s.insert(0, (char) ('A' + m));
            n = (n - 1) / 26;
        }
        return s.toString();
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                default -> {
                    if (ch >= 0x20 || ch == '\t' || ch == '\n') {
                        b.append(ch);
                    }
                }
            }
        }
        return b.toString();
    }

    private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.strip().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
