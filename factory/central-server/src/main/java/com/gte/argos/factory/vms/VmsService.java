package com.gte.argos.factory.vms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.common.api.ApiException;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.ingest.LiveJudgementService;
import com.gte.argos.factory.judge.SensorReading;
import com.gte.argos.factory.judge.Stage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VMS → 중앙 열화상 값 받기 (VMS 전송 규격 IR-301, 2026-11-19 시험용).
 *
 * <pre>
 * ① 평소 값 (1초마다, JSON)  { ts, type: IR, serial: IR-301, value, unit, quality, stage }
 * ② 단계 진입 (오를 때 한 번, multipart) meta(① + roi) + snapshot(jpeg)
 *
 * - 단계는 VMS 가 판정한 값을 그대로 쓴다 (중앙은 다시 판정하지 않음, sensor_event.rule = IR)
 * - 값은 1분 추이 · 실시간 현재값에 들어간다. 1초 원시값 테이블은 없다
 * - 사진은 file_asset(THERMAL) 로 저장하고 sensor_event.image_file_id · roi, alarm.thermal_file 에 잇는다
 * - ② 가 유실돼도 ① 의 stage 로 이벤트·경보를 만든다 (사진만 빠짐)
 * - 시각은 받은 시각(서버 시계)으로 저장한다. 보낸 ts 는 형식만 확인 ('YYYY-MM-DD HH:MM:SS' 또는 +09:00)
 * </pre>
 */
@Service
public class VmsService {

    private static final Logger log = LoggerFactory.getLogger(VmsService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbc;
    private final LiveJudgementService live;
    private final DemoClock clock;
    private final ObjectMapper json;
    private final Path filesDir;

    public VmsService(JdbcTemplate jdbc, LiveJudgementService live, DemoClock clock, ObjectMapper json,
                      @Value("${argos.files-dir:./data/files}") String filesDir) {
        this.jdbc = jdbc;
        this.live = live;
        this.clock = clock;
        this.json = json;
        this.filesDir = Path.of(filesDir);
    }

    /** ① 평소 값 · ② 단계 진입. snapshot 이 없으면 null */
    @Transactional
    public Map<String, Object> receive(Map<String, Object> meta, byte[] snapshot, String fileName, String mime) {
        String serial = str(meta.get("serial"));
        if (serial == null) {
            throw bad("serial 이 없습니다.");
        }
        if (meta.get("value") == null) {
            throw bad("value 가 없습니다.");
        }
        double value = ((Number) meta.get("value")).doubleValue();
        Stage stage = stage(meta.get("stage"));
        // 시각은 받은 시각(서버 시계). VMS 가 보낸 ts 는 형식만 확인하고 쓰지 않는다 (2026-11-20 결정: 봇 CSV 시각 반복)
        parseTs(str(meta.get("ts")), clock);
        Instant ts = clock.now();
        List<Long> ids = jdbc.queryForList("SELECT sensor_id FROM sensor WHERE tag = ? AND sensor_type = 'IR'",
                Long.class, serial);
        if (ids.isEmpty()) {
            throw ApiException.notFound("SENSOR_NOT_FOUND", "없는 열화상 센서입니다: " + serial);
        }
        long sensorId = ids.get(0);
        boolean accepted = live.ingestExternal(new SensorReading(serial, ts, value), stage);

        Long fileId = null;
        if (snapshot != null && snapshot.length > 0) {
            fileId = saveSnapshot(sensorId, serial, ts, snapshot, fileName, mime, meta.get("roi"));
        }
        if (accepted && stage != Stage.NORMAL && (fileId != null || meta.get("roi") != null)) {
            attach(sensorId, fileId, meta.get("roi"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accepted", accepted);
        out.put("sensorId", sensorId);
        out.put("stage", stage.code());
        out.put("fileId", fileId);
        return out;
    }

    /** 사진 → data/files/thermal/yyyyMMdd/IR-301_….jpg, file_asset(THERMAL) */
    private Long saveSnapshot(long sensorId, String serial, Instant ts, byte[] bytes, String fileName, String mime,
                              Object roi) {
        String day = DAY.format(ts.atZone(clock.zone()));
        String name = (fileName == null || fileName.isBlank()
                ? serial + "_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(ts.atZone(clock.zone())) + ".jpg"
                : Path.of(fileName).getFileName().toString()).replaceAll("[^A-Za-z0-9._-]", "_");
        String rel = "thermal/" + day + "/" + System.nanoTime() + "_" + name;
        try {
            Path target = filesDir.resolve(rel);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            log.warn("열화상 사진 저장 실패: {}", e.getMessage());
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("serial", serial);
        if (roi != null) {
            m.put("roi", roi);
        }
        return jdbc.queryForObject("""
                INSERT INTO file_asset (kind, path, mime, size_bytes, sensor_id, captured_at, meta)
                VALUES ('THERMAL', ?, ?, ?, ?, ?, ?::jsonb) RETURNING file_id
                """, Long.class, rel, mime == null || mime.isBlank() ? "image/jpeg" : mime, (long) bytes.length,
                sensorId, ts.atZone(clock.zone()).toOffsetDateTime(), write(m));
    }

    /** 진행 중인 IR 이벤트에 ROI · 사진, 그 이벤트로 생긴 경보에 사진(아직 없을 때만) */
    private void attach(long sensorId, Long fileId, Object roi) {
        List<Long> events = jdbc.queryForList("""
                SELECT event_id FROM sensor_event WHERE sensor_id = ? AND ended_at IS NULL
                ORDER BY started_at DESC LIMIT 1
                """, Long.class, sensorId);
        if (events.isEmpty()) {
            return;
        }
        long eventId = events.get(0);
        jdbc.update("""
                UPDATE sensor_event SET roi = COALESCE(?::jsonb, roi), image_file_id = COALESCE(?, image_file_id)
                WHERE event_id = ?
                """, roi == null ? null : write(roi), fileId, eventId);
        if (fileId != null) {
            jdbc.update("""
                    UPDATE alarm SET thermal_file = ? WHERE thermal_file IS NULL
                      AND alarm_id IN (SELECT alarm_id FROM alarm_event WHERE event_id = ?)
                    """, fileId, eventId);
        }
    }

    /** 'YYYY-MM-DD HH:MM:SS'(한국 시각) 또는 ISO(+09:00). 비우면 지금(서버 시계) */
    static Instant parseTs(String ts, DemoClock clock) {
        if (ts == null) {
            return clock.now();
        }
        try {
            return LocalDateTime.parse(ts.trim(), TS).atZone(clock.zone()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(ts.trim()).toInstant();
            } catch (DateTimeParseException e2) {
                throw bad("ts 형식은 'YYYY-MM-DD HH:MM:SS' 입니다.");
            }
        }
    }

    static Stage stage(Object v) {
        int s = v == null ? 1 : ((Number) v).intValue();
        return switch (s) {
            case 1 -> Stage.NORMAL;
            case 2 -> Stage.WARN;
            case 3 -> Stage.ALARM;
            default -> throw bad("stage 는 1 · 2 · 3 입니다.");
        };
    }

    /** 파일 읽기 (연계 서버가 사진을 보여 줄 때) */
    public Stored file(long fileId) {
        List<Stored> rows = jdbc.query("SELECT path, mime FROM file_asset WHERE file_id = ?",
                (rs, i) -> new Stored(filesDir.resolve(rs.getString("path")), rs.getString("mime")), fileId);
        if (rows.isEmpty() || !Files.isRegularFile(rows.get(0).path())) {
            throw ApiException.notFound("FILE_NOT_FOUND", "파일이 없습니다.");
        }
        return rows.get(0);
    }

    public record Stored(Path path, String mime) {
    }

    private String write(Object v) {
        try {
            return json.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String str(Object v) {
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
}
