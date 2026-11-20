package com.gte.argos.factory.ingest;

import com.gte.argos.factory.common.api.ApiResponse;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.config.ArgosProperties;
import com.gte.argos.factory.history.HistoryImportService;
import com.gte.argos.factory.judge.SensorReading;
import com.gte.argos.factory.retention.RetentionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발·시연용 API. argos.dev-api=true 일 때만 켜진다. 운영에서는 끈다.
 *
 * <pre>
 * POST /api/v1/dev/readings        값 직접 넣기 (Redis 봇이 붙기 전 테스트용)
 * POST /api/v1/dev/history-import  7일치 가져오기 (reset=true 면 지우고 다시)
 * POST /api/v1/dev/sim/reset       시연 리셋 (시계 09:00 으로, 09:00 이후 데이터 삭제)
 * POST /api/v1/dev/retention/run   보관 기간(기본 7일) 지난 센서 값 지금 지우기
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(prefix = "argos", name = "dev-api", havingValue = "true")
public class DevController {

    private final LiveJudgementService live;
    private final HistoryImportService history;
    private final DemoClock clock;
    private final ArgosProperties props;

    private final RetentionService retention;

    public DevController(LiveJudgementService live, HistoryImportService history, DemoClock clock, ArgosProperties props,
                         RetentionService retention) {
        this.live = live;
        this.history = history;
        this.clock = clock;
        this.props = props;
        this.retention = retention;
    }

    /** 보관 기간 지난 센서 값 지금 바로 지우기 (평소에는 1시간마다 자동) */
    @PostMapping("/retention/run")
    public ApiResponse<RetentionService.Result> retention() {
        return ApiResponse.ok(retention.purge());
    }

    /**
     * 예: [{ "tag": "AT-301", "value": 18.5 }, { "tag": "IT-303", "value": 21.0, "ts": "2026-11-16T09:01:00+09:00" }]
     * ts 를 비우면 서버 시각의 지금으로 넣는다.
     */
    @PostMapping("/readings")
    public ApiResponse<IngestResponse> readings(@RequestBody @NotEmpty List<@Valid ReadingRequest> body) {
        List<SensorReading> readings = body.stream()
                .map(r -> new SensorReading(r.tag(), r.ts() != null ? r.ts().toInstant() : clock.now(), r.value()))
                .sorted((a, b) -> a.ts().compareTo(b.ts()))
                .toList();
        int accepted = live.ingest(readings);
        return ApiResponse.ok(new IngestResponse(accepted, readings.size() - accepted));
    }

    @PostMapping("/history-import")
    public ApiResponse<HistoryImportService.Result> historyImport(@RequestParam(defaultValue = "false") boolean reset) {
        return ApiResponse.ok(history.importFrom(Path.of(props.historyImport().path()), reset));
    }

    @PostMapping("/sim/reset")
    public ApiResponse<OffsetDateTime> reset() {
        live.reset();
        return ApiResponse.ok(clock.nowOffset());
    }

    public record ReadingRequest(@NotBlank String tag, @NotNull Double value, OffsetDateTime ts) {
    }

    /**
     * @param accepted 판정에 넣은 값 수
     * @param ignored  시연 시작 전 시각이라 버린 값 수
     */
    public record IngestResponse(int accepted, int ignored) {
    }
}
