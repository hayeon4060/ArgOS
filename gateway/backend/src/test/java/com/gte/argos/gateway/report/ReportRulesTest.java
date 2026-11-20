package com.gte.argos.gateway.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ReportRulesTest {

    @Test
    void 안전_등급() {
        assertThat(ReportService.grade(0, 5, 0).grade()).isEqualTo('A');
        assertThat(ReportService.grade(2, 5, 1).grade()).isEqualTo('B');
        assertThat(ReportService.grade(5, 10, 0).grade()).isEqualTo('C');
        assertThat(ReportService.grade(50, 0, 0).score()).isEqualTo(0.0);
    }

    @Test
    void 엑셀_열_이름과_파일_구성() throws Exception {
        assertThat(SimpleXlsx.col(0)).isEqualTo("A");
        assertThat(SimpleXlsx.col(25)).isEqualTo("Z");
        assertThat(SimpleXlsx.col(26)).isEqualTo("AA");
        byte[] x = SimpleXlsx.write("알림", List.of("시각", "값"), List.of(Arrays.asList("2026-11-16", 3.5)));
        List<String> names = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(x))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                names.add(e.getName());
            }
        }
        assertThat(names.contains("xl/worksheets/sheet1.xml")).isTrue();
        assertThat(names.contains("[Content_Types].xml")).isTrue();
    }
}
