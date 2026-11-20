package com.gte.argos.factory.history;

import com.gte.argos.factory.judge.SensorReading;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/**
 * 더미데이터 CSV 여러 개를 시간 순서로 합쳐 읽는다.
 *
 * <pre>
 * 형식: timestamp,sensor_type,sensor_serial_no,value
 *       2026-11-09 09:00:00,TE,TE-305,138.91
 * - 파일마다 시간 순으로 정렬돼 있다고 보고, 파일들을 섞어 전체를 시간 순으로 내보낸다
 *   (관제 단위 경보는 여러 센서를 같은 시각 기준으로 봐야 하므로)
 * - 시간에는 시간대가 없으므로 zone(Asia/Seoul)으로 읽는다
 * - BOM, CRLF, 빈 줄은 무시. 형식이 틀린 줄은 건너뛰고 수를 센다
 * </pre>
 */
public final class CsvReadingSource implements Closeable {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZoneId zone;
    private final List<FileCursor> cursors = new ArrayList<>();
    private final PriorityQueue<FileCursor> queue =
            new PriorityQueue<>(Comparator.comparing((FileCursor c) -> c.head.ts()).thenComparing(c -> c.head.tag()));
    private long skippedLines;
    private long readCount;

    public CsvReadingSource(List<Path> files, ZoneId zone) {
        this.zone = zone;
        try {
            for (Path file : files) {
                FileCursor cursor = new FileCursor(file, Files.newBufferedReader(file, StandardCharsets.UTF_8));
                cursors.add(cursor);
                cursor.readHeader();
                if (cursor.advance()) {
                    queue.add(cursor);
                }
            }
        } catch (IOException e) {
            close();
            throw new UncheckedIOException(e);
        }
    }

    /** 전체 값을 시간 순으로 하나씩 넘긴다 */
    public void forEach(Consumer<SensorReading> consumer) {
        while (!queue.isEmpty()) {
            FileCursor cursor = queue.poll();
            consumer.accept(cursor.head);
            readCount++;
            if (cursor.advance()) {
                queue.add(cursor);
            }
        }
    }

    public long skippedLines() {
        return skippedLines;
    }

    public long readCount() {
        return readCount;
    }

    @Override
    public void close() {
        for (FileCursor c : cursors) {
            try {
                c.reader.close();
            } catch (IOException ignored) {
                // 닫기 실패는 무시
            }
        }
    }

    /** 한 줄을 SensorReading 으로. 형식이 틀리면 null */
    static SensorReading parseLine(String line, ZoneId zone) {
        String s = stripBom(line).trim();
        if (s.isEmpty()) {
            return null;
        }
        String[] cols = s.split(",", -1);
        if (cols.length < 4) {
            return null;
        }
        try {
            LocalDateTime local = LocalDateTime.parse(cols[0].trim(), TS);
            String tag = cols[2].trim();
            String raw = cols[3].trim();
            if (tag.isEmpty() || raw.isEmpty()) {
                return null;
            }
            double value = Double.parseDouble(raw);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return new SensorReading(tag, local.atZone(zone).toInstant(), value);
        } catch (DateTimeParseException | NumberFormatException e) {
            return null;
        }
    }

    private static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
    }

    private final class FileCursor {
        final Path file;
        final BufferedReader reader;
        SensorReading head;

        FileCursor(Path file, BufferedReader reader) {
            this.file = file;
            this.reader = reader;
        }

        void readHeader() throws IOException {
            reader.readLine();
        }

        /** 다음 정상 줄로 이동. 파일 끝이면 false */
        boolean advance() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    SensorReading r = parseLine(line, zone);
                    if (r != null) {
                        head = r;
                        return true;
                    }
                    if (!line.isBlank()) {
                        skippedLines++;
                    }
                }
                head = null;
                return false;
            } catch (IOException e) {
                throw new UncheckedIOException("CSV 읽기 실패: " + file, e);
            }
        }
    }
}
