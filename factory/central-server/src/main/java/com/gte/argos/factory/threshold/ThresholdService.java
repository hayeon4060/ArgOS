package com.gte.argos.factory.threshold;

import com.gte.argos.factory.common.api.ApiException;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.common.time.Seasons;
import com.gte.argos.factory.ingest.LiveJudgementService;
import com.gte.argos.factory.judge.SensorDef;
import com.gte.argos.factory.judge.Stage;
import com.gte.argos.factory.judge.ThresholdRule;
import com.gte.argos.factory.live.LiveFeed;
import com.gte.argos.factory.master.SensorDefinitionLoader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 센서 기준값 관리 (연계 서버 F1 · H2 화면이 쓴다).
 *
 * <pre>
 * - 저장: 이전 줄은 지우지 않고 valid_to 만 채운 뒤 새 줄을 만든다 (= 변경 이력)
 * - version: 설비 센서들의 지금 기준값 줄 중 가장 큰 threshold_id. 다른 사람이 먼저 바꾸면 달라져 409
 * - 저장하면 실시간 판정에 바로 반영 (진행 중 이벤트는 그대로)
 * - CO 는 법정 기준(산업안전보건기준에 관한 규칙 적정공기 30 ppm 미만)보다 느슨하게 못 바꾼다
 * - 미리보기: 최근 N일 1분 추이(최대·최소)로 "이 기준이었다면 몇 건"을 센다 (저장 안 함)
 * </pre>
 */
@Service
public class ThresholdService {

    public static final double CO_LEGAL_MAX = 30.0;

    private final JdbcTemplate jdbc;
    private final DemoClock clock;
    private final LiveJudgementService live;
    private final SensorDefinitionLoader loader;
    private final LiveFeed feed;

    public ThresholdService(JdbcTemplate jdbc, DemoClock clock, LiveJudgementService live,
                            SensorDefinitionLoader loader, LiveFeed feed) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.live = live;
        this.loader = loader;
        this.feed = feed;
    }

    // ---------------------------------------------------------------
    // 조회
    // ---------------------------------------------------------------

    /** 설비의 센서별 계절 기준값 */
    public Map<String, Object> equipmentThresholds(long equipmentId) {
        List<Map<String, Object>> sensors = jdbc.query("""
                SELECT sensor_id, tag, sensor_type, unit, sample_sec, setpoint
                FROM sensor WHERE equipment_id = ? AND is_active ORDER BY sensor_id
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sensorId", rs.getLong("sensor_id"));
            m.put("tag", rs.getString("tag"));
            m.put("type", rs.getString("sensor_type"));
            m.put("unit", rs.getString("unit"));
            m.put("sampleSec", rs.getInt("sample_sec"));
            m.put("setpoint", rs.getObject("setpoint", Double.class));
            m.put("seasons", new LinkedHashMap<String, Object>());
            m.put("legal", "AT".equals(rs.getString("sensor_type"))
                    ? Map.of("max", CO_LEGAL_MAX, "text", "산업안전보건기준에 관한 규칙 적정공기 CO 30 ppm 미만") : null);
            return m;
        }, equipmentId);
        if (sensors.isEmpty() && !equipmentExists(equipmentId)) {
            throw ApiException.notFound("EQUIPMENT_NOT_FOUND", "없는 설비입니다.");
        }
        Map<Long, Map<String, Object>> bySensor = new HashMap<>();
        sensors.forEach(s -> bySensor.put((Long) s.get("sensorId"), s));
        jdbc.query("""
                SELECT th.* FROM sensor_threshold th JOIN sensor s ON s.sensor_id = th.sensor_id
                WHERE s.equipment_id = ? AND th.valid_to IS NULL ORDER BY th.sensor_id, th.season
                """, rs -> {
            Map<String, Object> s = bySensor.get(rs.getLong("sensor_id"));
            if (s != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> seasons = (Map<String, Object>) s.get("seasons");
                seasons.put(rs.getString("season"), row(rs));
            }
        }, equipmentId);
        for (Map<String, Object> s : sensors) {
            @SuppressWarnings("unchecked")
            Map<String, Object> seasons = (Map<String, Object>) s.get("seasons");
            fillSeasons(seasons);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipmentId", equipmentId);
        out.put("activeSeason", Seasons.current(clock));
        out.put("version", version(equipmentId));
        out.put("sensors", sensors);
        return out;
    }

    /** 변경 이력 (앞 줄 값과 함께). 처음 넣은 기준은 before 가 null */
    public List<Map<String, Object>> history(Long equipmentId, Long sensorId, String season,
                                             OffsetDateTime from, OffsetDateTime to) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM (
                  SELECT th.*, s.tag, s.equipment_id,
                         LAG(th.warn_value)  OVER w AS b_warn,  LAG(th.alarm_value) OVER w AS b_alarm,
                         LAG(th.low_warn)    OVER w AS b_low_warn, LAG(th.low_alarm) OVER w AS b_low_alarm,
                         LAG(th.threshold_id) OVER w AS b_id
                  FROM sensor_threshold th JOIN sensor s ON s.sensor_id = th.sensor_id
                  WINDOW w AS (PARTITION BY th.sensor_id, th.season ORDER BY th.valid_from, th.threshold_id)
                ) x WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (equipmentId != null) {
            sql.append(" AND x.equipment_id = ?");
            args.add(equipmentId);
        }
        if (sensorId != null) {
            sql.append(" AND x.sensor_id = ?");
            args.add(sensorId);
        }
        if (season != null && !season.isBlank()) {
            sql.append(" AND x.season = ?");
            args.add(season);
        }
        if (from != null) {
            sql.append(" AND x.valid_from >= ?");
            args.add(from);
        }
        if (to != null) {
            sql.append(" AND x.valid_from < ?");
            args.add(to);
        }
        sql.append(" ORDER BY x.valid_from DESC, x.threshold_id DESC LIMIT 500");
        return jdbc.query(sql.toString(), (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("threshold_id"));
            m.put("sensorId", rs.getLong("sensor_id"));
            m.put("sensorTag", rs.getString("tag"));
            m.put("equipmentId", rs.getLong("equipment_id"));
            m.put("season", rs.getString("season"));
            if (rs.getObject("b_id") != null) {
                m.put("before", values(rs.getObject("b_warn", Double.class), rs.getObject("b_alarm", Double.class),
                        rs.getObject("b_low_warn", Double.class), rs.getObject("b_low_alarm", Double.class)));
            } else {
                m.put("before", null);
            }
            m.put("after", values(rs.getObject("warn_value", Double.class), rs.getObject("alarm_value", Double.class),
                    rs.getObject("low_warn", Double.class), rs.getObject("low_alarm", Double.class)));
            m.put("changedBy", rs.getObject("changed_by", Long.class));
            m.put("changedAt", odt(rs, "valid_from"));
            m.put("validTo", odt(rs, "valid_to"));
            m.put("reason", rs.getString("note"));
            m.put("basis", rs.getString("basis"));
            return m;
        }, args.toArray());
    }

    // ---------------------------------------------------------------
    // 저장
    // ---------------------------------------------------------------

    /**
     * @param season  ALL | SPRING_FALL | SUMMER | WINTER
     * @param version 조회 때 받은 version (다르면 409 THRESHOLD_CONFLICT)
     */
    @Transactional
    public Map<String, Object> save(long equipmentId, String season, Long version, String reason, Long userId,
                                    List<SensorThreshold> sensors) {
        if (!Seasons.isValid(season)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "계절 값이 올바르지 않습니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "변경 사유를 입력해 주세요.");
        }
        if (!equipmentExists(equipmentId)) {
            throw ApiException.notFound("EQUIPMENT_NOT_FOUND", "없는 설비입니다.");
        }
        // 같은 설비를 동시에 저장하지 않도록 잠근다
        jdbc.query("SELECT equipment_id FROM equipment WHERE equipment_id = ? FOR UPDATE", rs -> { }, equipmentId);
        long current = version(equipmentId);
        if (version != null && version != current) {
            throw ApiException.conflict("THRESHOLD_CONFLICT", "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
        }
        Map<Long, String> typeById = new HashMap<>();
        jdbc.query("SELECT sensor_id, sensor_type FROM sensor WHERE equipment_id = ?",
                rs -> {
                    typeById.put(rs.getLong("sensor_id"), rs.getString("sensor_type"));
                }, equipmentId);

        OffsetDateTime now = clock.nowOffset();
        List<Map<String, Object>> changes = new ArrayList<>();
        for (SensorThreshold t : sensors) {
            String type = typeById.get(t.sensorId());
            if (type == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SENSOR_NOT_IN_EQUIPMENT", "이 설비의 센서가 아닙니다: " + t.sensorId());
            }
            validate(t, type);
            Map<String, Object> before = currentRow(t.sensorId(), season);
            Map<String, Object> base = before != null ? before : currentRowAny(t.sensorId());
            int warnSamples = t.warnSamples() != null ? t.warnSamples() : intOr(base, "warnSamples", 24);
            int alarmSamples = t.alarmSamples() != null ? t.alarmSamples() : intOr(base, "alarmSamples", 6);
            if (warnSamples < 1 || alarmSamples < 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "지속 조건은 1샘플 이상이어야 합니다.");
            }
            if (before != null) {
                jdbc.update("""
                        UPDATE sensor_threshold SET valid_to = GREATEST(?, valid_from + INTERVAL '1 millisecond')
                        WHERE threshold_id = ?
                        """, now, before.get("thresholdId"));
            }
            jdbc.update("""
                    INSERT INTO sensor_threshold
                        (sensor_id, warn_value, alarm_value, low_warn, low_alarm, warn_samples, alarm_samples,
                         release_samples, release_margin, alarm_avg_sec, immediate_value,
                         basis, note, valid_from, changed_by, season)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PROPOSED', ?, GREATEST(?, ?), ?, ?)
                    """, t.sensorId(), t.warn(), t.alarm(), t.lowWarn(), t.lowAlarm(), warnSamples, alarmSamples,
                    // 복귀 횟수·여유·평균 시간·즉시값은 화면에서 고치지 않으므로 이전 기준을 그대로 잇는다 (없으면 v5 기본값)
                    intOr(base, "releaseSamples", 6), base == null ? 0.05 : ((Number) base.get("releaseMargin")).doubleValue(),
                    base == null ? null : base.get("alarmAvgSec"), base == null ? null : base.get("immediate"),
                    reason, now, before == null ? now : ((OffsetDateTime) before.get("validFrom")).plusNanos(1_000_000),
                    userId, season);
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("sensorId", t.sensorId());
            change.put("before", before == null ? null
                    : values((Double) before.get("warn"), (Double) before.get("alarm"),
                    (Double) before.get("lowWarn"), (Double) before.get("lowAlarm")));
            change.put("after", values(t.warn(), t.alarm(), t.lowWarn(), t.lowAlarm()));
            changes.add(change);
        }
        live.reloadDefinitions();
        boolean appliedNow = Seasons.ALL.equals(season) || season.equals(Seasons.current(clock));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("equipmentId", equipmentId);
        data.put("season", season);
        data.put("by", userId);
        data.put("changes", changes);
        feed.append("THRESHOLD_CHANGED", now, data);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipmentId", equipmentId);
        out.put("version", version(equipmentId));
        out.put("appliedNow", appliedNow);
        out.put("changes", changes);
        return out;
    }

    /** 수집 주기 · 사용 여부 */
    @Transactional
    public Map<String, Object> updateSensor(long sensorId, Integer sampleSec, Boolean active) {
        return updateSensor(sensorId, sampleSec, active, null);
    }

    public Map<String, Object> updateSensor(long sensorId, Integer sampleSec, Boolean active, String streamUrl) {
        if (sampleSec != null && (sampleSec < 1 || sampleSec > 3600)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "수집 주기는 1 ~ 3600초입니다.");
        }
        int n = jdbc.update("""
                UPDATE sensor SET sample_sec = COALESCE(?, sample_sec), is_active = COALESCE(?, is_active)
                WHERE sensor_id = ?
                """, sampleSec, active, sensorId);
        if (n == 0) {
            throw ApiException.notFound("SENSOR_NOT_FOUND", "없는 센서입니다.");
        }
        if (streamUrl != null) {
            jdbc.update("UPDATE sensor SET stream_url = ? WHERE sensor_id = ?", streamUrl.isBlank() ? null : streamUrl.trim(),
                    sensorId);
            // 연계 서버 마스터 캐시를 바로 비우게 한다
            feed.append("MASTER_CHANGED", clock.nowOffset(), Map.of("what", "SENSOR", "id", sensorId));
        }
        live.reloadDefinitions();
        return jdbc.queryForObject("SELECT sensor_id, sample_sec, is_active, stream_url FROM sensor WHERE sensor_id = ?",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("sensor_id"));
                    m.put("sampleSec", rs.getInt("sample_sec"));
                    m.put("active", rs.getBoolean("is_active"));
                    m.put("streamUrl", rs.getString("stream_url"));
                    return m;
                }, sensorId);
    }

    // ---------------------------------------------------------------
    // 미리보기
    // ---------------------------------------------------------------

    /**
     * 최근 days 일의 1분 추이로 지금 기준과 바꿀 기준의 알림 수를 비교한다.
     * 1분 안의 최대(상한)·최소(하한)가 기준을 넘은 분이 지속 시간만큼 이어지면 1건.
     */
    public Map<String, Object> simulate(long sensorId, Double warn, Double alarm, Double lowWarn, Double lowAlarm,
                                        Integer warnSamples, Integer alarmSamples, int days) {
        SensorDef def = loader.loadActive().stream().filter(s -> s.sensorId() == sensorId).findFirst()
                .orElseThrow(() -> ApiException.notFound("SENSOR_NOT_FOUND", "없는 센서입니다."));
        ThresholdRule cur = def.rule();
        int ws = warnSamples != null ? warnSamples : cur == null ? 24 : cur.warnSamples();
        int as = alarmSamples != null ? alarmSamples : cur == null ? 6 : cur.alarmSamples();
        ThresholdRule proposed = new ThresholdRule(0, warn, alarm, lowWarn, lowAlarm, Math.max(1, ws), Math.max(1, as));

        OffsetDateTime to = clock.nowOffset();
        OffsetDateTime from = to.minusDays(Math.max(1, Math.min(31, days)));
        List<Minute> minutes = jdbc.query("""
                SELECT ts, min_v, max_v FROM sensor_trend WHERE sensor_id = ? AND ts >= ? AND ts < ? ORDER BY ts
                """, (rs, i) -> new Minute(odt(rs, "ts"), rs.getDouble("min_v"), rs.getDouble("max_v")), sensorId, from, to);

        Count c = cur == null ? new Count(0, 0, List.of()) : count(minutes, cur, def.sampleSec());
        Count p = count(minutes, proposed, def.sampleSec());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("current", Map.of("warnCount", c.warn(), "alarmCount", c.alarm()));
        out.put("proposed", Map.of("warnCount", p.warn(), "alarmCount", p.alarm()));
        out.put("diff", (p.warn() + p.alarm()) - (c.warn() + c.alarm()));
        out.put("hits", p.hits());
        out.put("peaks", p.peaks());
        out.put("days", Math.max(1, Math.min(31, days)));
        return out;
    }

    static Count count(List<Minute> minutes, ThresholdRule rule, int sampleSec) {
        int warnMin = (int) Math.ceil(rule.warnSamples() * (double) sampleSec / 60.0);
        int alarmMin = (int) Math.ceil(rule.alarmSamples() * (double) sampleSec / 60.0);
        int warnCount = 0;
        int alarmCount = 0;
        List<OffsetDateTime> hits = new ArrayList<>();
        List<Map<String, Object>> peaks = new ArrayList<>();
        OffsetDateTime peakAt = null;
        double peakValue = 0;
        int peakStage = 0;
        int run = 0;
        int alarmRun = 0;
        int bestAlarmRun = 0;
        OffsetDateTime runStart = null;
        OffsetDateTime prev = null;
        for (int i = 0; i <= minutes.size(); i++) {
            Minute m = i < minutes.size() ? minutes.get(i) : null;
            boolean gap = m != null && prev != null && m.ts().isAfter(prev.plusMinutes(1));
            Stage stage = m == null ? Stage.NORMAL : Stage.max(rule.classify(m.max()), rule.classify(m.min()));
            if (m == null || gap || stage == Stage.NORMAL) {
                if (run > 0) {
                    if (bestAlarmRun >= alarmMin) {
                        alarmCount++;
                        hits.add(runStart);
                        peaks.add(peak(peakAt, peakValue));
                    } else if (run >= warnMin) {
                        warnCount++;
                        hits.add(runStart);
                        peaks.add(peak(peakAt, peakValue));
                    }
                    peakAt = null;
                    peakStage = 0;
                }
                run = 0;
                alarmRun = 0;
                bestAlarmRun = 0;
                runStart = null;
            }
            if (m != null && stage != Stage.NORMAL) {
                if (run == 0) {
                    runStart = m.ts();
                }
                run++;
                // 그래프에 찍을 점: 이 구간에서 단계가 가장 높은 분의 기준을 넘은 쪽 값 (같으면 더 벗어난 값)
                boolean high = rule.classify(m.max()) != Stage.NORMAL;
                double v = high ? m.max() : m.min();
                if (stage.code() > peakStage || (stage.code() == peakStage && (high ? v > peakValue : v < peakValue))) {
                    peakStage = stage.code();
                    peakValue = v;
                    peakAt = m.ts();
                }
                alarmRun = stage == Stage.ALARM ? alarmRun + 1 : 0;
                bestAlarmRun = Math.max(bestAlarmRun, alarmRun);
            }
            if (m != null) {
                prev = m.ts();
            }
        }
        return new Count(warnCount, alarmCount, hits, peaks);
    }

    private static Map<String, Object> peak(OffsetDateTime at, double value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("at", at);
        m.put("value", Math.round(value * 100) / 100.0);
        return m;
    }

    // ---------------------------------------------------------------

    private void validate(SensorThreshold t, String type) {
        if (t.warn() != null && t.alarm() != null && t.warn() >= t.alarm()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WARN_NOT_BELOW_ALARM", "주의 기준이 경보 기준보다 작아야 합니다.");
        }
        if (t.lowWarn() != null && t.lowAlarm() != null && t.lowWarn() <= t.lowAlarm()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WARN_NOT_BELOW_ALARM", "하한 주의 기준이 하한 경보 기준보다 커야 합니다.");
        }
        if (t.warn() == null && t.alarm() == null && t.lowWarn() == null && t.lowAlarm() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "기준값을 하나 이상 입력해 주세요.");
        }
        if ("AT".equals(type) && (t.alarm() == null || t.alarm() > CO_LEGAL_MAX || (t.warn() != null && t.warn() > CO_LEGAL_MAX))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOOSER_THAN_LEGAL", "CO 기준은 법정 기준 30 ppm 을 넘을 수 없습니다.");
        }
    }

    private Map<String, Object> currentRow(long sensorId, String season) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT * FROM sensor_threshold WHERE sensor_id = ? AND season = ? AND valid_to IS NULL ORDER BY valid_from DESC LIMIT 1",
                (rs, i) -> row(rs), sensorId, season);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> currentRowAny(long sensorId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT * FROM sensor_threshold WHERE sensor_id = ? AND valid_to IS NULL ORDER BY (season = 'ALL') DESC, valid_from DESC LIMIT 1",
                (rs, i) -> row(rs), sensorId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long version(long equipmentId) {
        Long v = jdbc.queryForObject("""
                SELECT COALESCE(MAX(th.threshold_id), 0) FROM sensor_threshold th JOIN sensor s ON s.sensor_id = th.sensor_id
                WHERE s.equipment_id = ? AND th.valid_to IS NULL
                """, Long.class, equipmentId);
        return v == null ? 0 : v;
    }

    private boolean equipmentExists(long equipmentId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM equipment WHERE equipment_id = ?", Long.class, equipmentId);
        return n != null && n > 0;
    }

    /**
     * 계절 줄(SPRING_FALL · SUMMER · WINTER)이 없으면 ALL 줄 값을 복사해 채운다 (inherited = true).
     * 판정 규칙(지금 계절 줄이 먼저, 없으면 ALL)과 같은 값을 화면에 보여 주기 위해서. ALL 도 없으면 채우지 않는다.
     */
    static void fillSeasons(Map<String, Object> seasons) {
        Object all = seasons.get(Seasons.ALL);
        if (!(all instanceof Map<?, ?> allRow)) {
            return;
        }
        for (String season : Seasons.SEASONS) {
            if (!seasons.containsKey(season)) {
                Map<String, Object> copy = new LinkedHashMap<>();
                allRow.forEach((k, v) -> copy.put(String.valueOf(k), v));
                copy.put("inherited", true);
                seasons.put(season, copy);
            }
        }
    }

    private Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("thresholdId", rs.getLong("threshold_id"));
        m.put("warn", rs.getObject("warn_value", Double.class));
        m.put("alarm", rs.getObject("alarm_value", Double.class));
        m.put("lowWarn", rs.getObject("low_warn", Double.class));
        m.put("lowAlarm", rs.getObject("low_alarm", Double.class));
        m.put("warnSamples", rs.getInt("warn_samples"));
        m.put("alarmSamples", rs.getInt("alarm_samples"));
        m.put("releaseSamples", rs.getInt("release_samples"));
        m.put("releaseMargin", rs.getDouble("release_margin"));
        m.put("alarmAvgSec", rs.getObject("alarm_avg_sec", Integer.class));
        m.put("immediate", rs.getObject("immediate_value", Double.class));
        m.put("basis", rs.getString("basis"));
        m.put("note", rs.getString("note"));
        m.put("validFrom", odt(rs, "valid_from"));
        m.put("changedBy", rs.getObject("changed_by", Long.class));
        return m;
    }

    private static Map<String, Object> values(Double warn, Double alarm, Double lowWarn, Double lowAlarm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("warn", warn);
        m.put("alarm", alarm);
        m.put("lowWarn", lowWarn);
        m.put("lowAlarm", lowAlarm);
        return m;
    }

    private static int intOr(Map<String, Object> m, String key, int dflt) {
        return m == null || m.get(key) == null ? dflt : ((Number) m.get(key)).intValue();
    }

    private OffsetDateTime odt(ResultSet rs, String col) throws SQLException {
        OffsetDateTime v = rs.getObject(col, OffsetDateTime.class);
        return v == null ? null : v.atZoneSameInstant(clock.zone()).toOffsetDateTime();
    }

    /** 저장 요청 한 줄. 지속 조건을 비우면 지금 값을 그대로 쓴다 */
    public record SensorThreshold(long sensorId, Double warn, Double alarm, Double lowWarn, Double lowAlarm,
                                  Integer warnSamples, Integer alarmSamples) {
    }

    record Minute(OffsetDateTime ts, double min, double max) {
    }

    /** peaks: 알림 하나마다 그래프에 찍을 점 { at, value } (hits 와 같은 순서) */
    record Count(int warn, int alarm, List<OffsetDateTime> hits, List<Map<String, Object>> peaks) {
        Count(int warn, int alarm, List<OffsetDateTime> hits) {
            this(warn, alarm, hits, List.of());
        }
    }
}
