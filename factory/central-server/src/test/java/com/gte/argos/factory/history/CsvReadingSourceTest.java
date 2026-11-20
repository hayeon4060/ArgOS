package com.gte.argos.factory.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte.argos.factory.judge.SensorReading;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvReadingSourceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void 여러_파일을_시간_순으로_합치고_BOM_CRLF_틀린_줄을_처리한다(@TempDir Path dir) throws Exception {
        Path te = dir.resolve("상도건조로_TE.csv");
        Path at = dir.resolve("상도버너실_AT.csv");
        Files.writeString(te, "﻿timestamp,sensor_type,sensor_serial_no,value\r\n"
                + "2026-11-16 09:00:00,TE,TE-305,140.0\r\n"
                + "2026-11-16 09:00:10,TE,TE-305,141.5\r\n", StandardCharsets.UTF_8);
        Files.writeString(at, "timestamp,sensor_type,sensor_serial_no,value\n"
                + "2026-11-16 09:00:05,AT,AT-303,6.1\n"
                + "잘못된 줄\n"
                + "\n"
                + "2026-11-16 09:00:15,AT,AT-303,\n"
                + "2026-11-16 09:00:20,AT,AT-303,6.3\n", StandardCharsets.UTF_8);

        List<SensorReading> out = new ArrayList<>();
        try (CsvReadingSource src = new CsvReadingSource(List.of(te, at), SEOUL)) {
            src.forEach(out::add);
            assertThat(src.skippedLines()).isEqualTo(2);
            assertThat(src.readCount()).isEqualTo(4);
        }

        assertThat(out).extracting(SensorReading::tag)
                .containsExactly("TE-305", "AT-303", "TE-305", "AT-303");
        // 시간대 없는 시각은 서울 시각으로 읽는다 (09:00 KST = 00:00 UTC)
        assertThat(out.get(0).ts()).isEqualTo(Instant.parse("2026-11-16T00:00:00Z"));
        assertThat(out.get(2).value()).isEqualTo(141.5);
    }
}
