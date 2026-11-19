package com.gte.argos.factory.history;

import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.judge.JudgementEngine;
import com.gte.argos.factory.judge.SensorDef;
import com.gte.argos.factory.master.SensorDefinitionLoader;
import com.gte.argos.factory.retention.SensorDailyService;
import com.gte.argos.factory.store.JdbcJudgementStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 7일치 더미데이터를 한 번 처리해서 DB 에 넣는다.
 *
 * <pre>
 * CSV 폴더 → 시간 순 병합 → 판정 엔진(과거 모드: 이벤트가 끝나면 경보도 닫음)
 *          → sensor_trend, sensor_event, sensor_event_sample, alarm, alarm_event
 * </pre>
 *
 * 시연 시작 시각(simStart, 11/16 09:00)보다 앞의 값만 넣는다. 그 뒤는 실시간 몫.
 * 이미 넣은 데이터가 있으면 건너뛰고, reset 이면 과거 데이터를 지우고 다시 넣는다.
 */
@Service
public class HistoryImportService {

    private static final Logger log = LoggerFactory.getLogger(HistoryImportService.class);
    private static final int BATCH_SIZE = 2_000;

    private final JdbcTemplate jdbc;
    private final SensorDefinitionLoader sensorLoader;
    private final DemoClock clock;
    private final SensorDailyService daily;

    public HistoryImportService(JdbcTemplate jdbc, SensorDefinitionLoader sensorLoader, DemoClock clock,
                                SensorDailyService daily) {
        this.jdbc = jdbc;
        this.sensorLoader = sensorLoader;
        this.clock = clock;
        this.daily = daily;
    }

    @Transactional
    public Result importFrom(Path dir, boolean reset) {
        OffsetDateTime cutoff = clock.simStart().atZone(clock.zone()).toOffsetDateTime();

        Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM sensor_trend WHERE ts < ?", Long.class, cutoff);
        if (existing != null && existing > 0) {
            if (!reset) {
                log.info("7일치 데이터가 이미 있어 가져오기를 건너뜁니다 (sensor_trend {}줄). 다시 넣으려면 reset=true", existing);
                return Result.skipped(existing);
            }
            deleteHistory(cutoff);
        }

        List<Path> files = csvFiles(dir);
        if (files.isEmpty()) {
            throw new IllegalStateException("CSV 파일이 없습니다: " + dir.toAbsolutePath());
        }
        List<SensorDef> sensors = sensorLoader.loadActive();
        log.info("7일치 가져오기 시작: 파일 {}개, 센서 {}개, 기준 시각 {} 이전", files.size(), sensors.size(), cutoff);

        long started = System.currentTimeMillis();
        JdbcJudgementStore store = new JdbcJudgementStore(jdbc, clock.zone(), BATCH_SIZE);
        JudgementEngine engine = new JudgementEngine(sensors, store, JudgementEngine.Options.history());
        long[] afterCutoff = {0};
        long read;
        long skipped;
        try (CsvReadingSource source = new CsvReadingSource(files, clock.zone())) {
            source.forEach(r -> {
                if (r.ts().isBefore(clock.simStart())) {
                    engine.accept(r);
                } else {
                    afterCutoff[0]++;
                }
            });
            read = source.readCount();
            skipped = source.skippedLines();
        }
        engine.flushTrends();
        store.flush();
        int dailyRows = daily.aggregateAll();
        log.info("센서 일 집계 {}행", dailyRows);

        Result result = new Result(false, read, skipped, afterCutoff[0], engine.unknownTagCount(),
                store.trendCount(), store.eventCount(), store.alarmCount(), System.currentTimeMillis() - started);
        log.info("7일치 가져오기 끝: {}", result);
        return result;
    }

    /** simStart 이전의 판정 결과를 지운다 (마스터·기준값은 그대로) */
    private void deleteHistory(OffsetDateTime cutoff) {
        jdbc.update("DELETE FROM alarm WHERE raised_at < ?", cutoff);
        jdbc.update("DELETE FROM sensor_event WHERE started_at < ?", cutoff);
        jdbc.update("DELETE FROM sensor_trend WHERE ts < ?", cutoff);
        jdbc.update("DELETE FROM sensor_daily WHERE day <= ?", cutoff.atZoneSameInstant(clock.zone()).toLocalDate());
        log.info("기존 7일치 판정 결과를 지웠습니다");
    }

    private static List<Path> csvFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("더미데이터 폴더가 없습니다: " + dir.toAbsolutePath());
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param skipped        이미 데이터가 있어 건너뛰었는지
     * @param readCount      읽은 값 수
     * @param badLines       형식이 틀려 버린 줄 수
     * @param afterCutoff    시연 시작 이후라 넣지 않은 값 수
     * @param unknownTags    DB 에 없는 태그라 버린 값 수
     * @param trendRows      넣은 1분 추이 수
     * @param events         만든 센서 이벤트 수
     * @param alarms         만든 경보 수
     * @param elapsedMs      걸린 시간
     */
    public record Result(boolean skipped, long readCount, long badLines, long afterCutoff, long unknownTags,
                         long trendRows, long events, long alarms, long elapsedMs) {

        static Result skipped(long existingTrendRows) {
            return new Result(true, 0, 0, 0, 0, existingTrendRows, 0, 0, 0);
        }
    }
}
