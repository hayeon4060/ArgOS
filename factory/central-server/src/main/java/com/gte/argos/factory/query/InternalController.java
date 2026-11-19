package com.gte.argos.factory.query;

import com.gte.argos.factory.common.api.ApiException;
import com.gte.argos.factory.common.api.ApiResponse;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.live.LiveFeed;
import com.gte.argos.factory.query.InternalDto.Alarm;
import com.gte.argos.factory.query.InternalQueryRepository.AlarmFilter;
import com.gte.argos.factory.query.InternalQueryRepository.EventFilter;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연계 서버 전용 API. 화면이 직접 부르지 않는다. 연계 서버가 HTTP 로 불러 화면용 모양으로 바꾼다.
 * argos.internal-api-key 를 설정하면 X-Argos-Internal-Key 헤더가 맞아야 한다 (InternalApiKeyFilter).
 *
 * <pre>
 * GET  /api/v1/internal/master                     사업장·공장·공정·설비·센서·기준값·관제 단위 (동기화용)
 * GET  /api/v1/internal/status?processId=          지금 값 · 단계 (센서, 설비, 범위별)
 * GET  /api/v1/internal/series?sensorIds=&from=&to=&step=   추이 그래프
 * GET  /api/v1/internal/events?from=&to=&...       센서 이벤트
 * GET  /api/v1/internal/events/{id}/samples        이벤트 구간 5초 값
 * GET  /api/v1/internal/alarms?from=&to=&...       경보 목록 (원인 이벤트 포함)
 * GET  /api/v1/internal/alarms/{id}                경보 한 건
 * POST /api/v1/internal/alarms/{id}/ack            확인
 * POST /api/v1/internal/alarms/{id}/acting         조치 중
 * POST /api/v1/internal/alarms/{id}/close          종료 (값이 아직 기준 위면 409 VALUE_STILL_OVER)
 * GET  /api/v1/internal/feed?after=&limit=         실시간 변화 (연계 서버가 1초마다 가져가 SSE 로 전달)
 * </pre>
 *
 * 시각 파라미터는 ISO 8601 (2026-11-16T09:00:00+09:00). 비우면 시연 시계 기준 오늘 0시 ~ 지금.
 */
@RestController
@RequestMapping("/api/v1/internal")
public class InternalController {

    private final InternalQueryService query;
    private final InternalQueryRepository repo;
    private final AlarmStatusService alarmStatus;
    private final LiveFeed feed;
    private final DemoClock clock;

    public InternalController(InternalQueryService query, InternalQueryRepository repo,
                              AlarmStatusService alarmStatus, LiveFeed feed, DemoClock clock) {
        this.query = query;
        this.repo = repo;
        this.alarmStatus = alarmStatus;
        this.feed = feed;
        this.clock = clock;
    }

    @GetMapping("/master")
    public ApiResponse<InternalDto.Master> master() {
        return ApiResponse.ok(query.master());
    }

    @GetMapping("/status")
    public ApiResponse<InternalDto.Status> status(@RequestParam(required = false) Long processId) {
        return ApiResponse.ok(query.status(processId));
    }

    @GetMapping("/series")
    public ApiResponse<InternalDto.Series> series(
            @RequestParam List<Long> sensorIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String step) {
        Instant t = to != null ? to.toInstant() : clock.now();
        Instant f = from != null ? from.toInstant() : t.minus(Duration.ofHours(1));
        return ApiResponse.ok(query.series(sensorIds, f, t, step));
    }

    @GetMapping("/events")
    public ApiResponse<List<InternalDto.Event>> events(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) Long sensorId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long processId,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(defaultValue = "200") int limit) {
        Range r = range(from, to);
        return ApiResponse.ok(repo.events(new EventFilter(r.from(), r.to(), sensorId, equipmentId, processId,
                openOnly, clamp(limit, 1, 1000))));
    }

    @GetMapping("/events/{eventId}/samples")
    public ApiResponse<List<InternalDto.Sample>> samples(@PathVariable long eventId) {
        return ApiResponse.ok(repo.samples(eventId));
    }

    @GetMapping("/alarms")
    public ApiResponse<InternalDto.Page<Alarm>> alarms(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) Integer minStage,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Long processId,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) Long plantId,
            @RequestParam(required = false) String hazard,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Range r = range(from, to);
        return ApiResponse.ok(repo.alarms(new AlarmFilter(r.from(), r.to(), status, minStage, unitId, processId,
                factoryId, plantId, Math.max(0, page), clamp(size, 1, 500), hazard)));
    }

    @GetMapping("/alarms/{alarmId}")
    public ApiResponse<Alarm> alarm(@PathVariable long alarmId) {
        Alarm alarm = repo.alarm(alarmId);
        if (alarm == null) {
            throw ApiException.notFound("ALARM_NOT_FOUND", "없는 알림입니다.");
        }
        return ApiResponse.ok(alarm);
    }

    /** 알림 상세 그래프: 관제 단위 센서 전부의 값 (30분 이하 5초, 넘으면 1분 평균) */
    @GetMapping("/alarms/{alarmId}/series")
    public ApiResponse<InternalDto.AlarmSeries> alarmSeries(@PathVariable long alarmId) {
        Alarm alarm = repo.alarm(alarmId);
        if (alarm == null) {
            throw ApiException.notFound("ALARM_NOT_FOUND", "없는 알림입니다.");
        }
        return ApiResponse.ok(repo.alarmSeries(alarm));
    }

    @PostMapping("/alarms/{alarmId}/ack")
    public ApiResponse<Alarm> ack(@PathVariable long alarmId, @RequestBody(required = false) ActorRequest body) {
        return ApiResponse.ok(alarmStatus.ack(alarmId, userId(body)));
    }

    @PostMapping("/alarms/{alarmId}/acting")
    public ApiResponse<Alarm> acting(@PathVariable long alarmId, @RequestBody(required = false) ActorRequest body) {
        return ApiResponse.ok(alarmStatus.acting(alarmId, userId(body)));
    }

    @PostMapping("/alarms/{alarmId}/close")
    public ApiResponse<Alarm> close(@PathVariable long alarmId, @RequestBody(required = false) ActorRequest body) {
        return ApiResponse.ok(alarmStatus.close(alarmId, userId(body)));
    }

    @GetMapping("/feed")
    public ApiResponse<LiveFeed.Page> feed(@RequestParam(defaultValue = "0") long after,
                                           @RequestParam(defaultValue = "500") int limit) {
        return ApiResponse.ok(feed.after(after, clamp(limit, 1, 2000)));
    }

    /** 누가 했는지 (연계 DB app_user.user_id) */
    public record ActorRequest(Long userId) {
    }

    private Long userId(ActorRequest body) {
        return body == null ? null : body.userId();
    }

    /** 비우면 시연 시계 기준 오늘 0시 ~ 지금(+1초) */
    private Range range(OffsetDateTime from, OffsetDateTime to) {
        Instant t = to != null ? to.toInstant() : clock.now().plusSeconds(1);
        Instant f = from != null ? from.toInstant()
                : clock.now().atZone(clock.zone()).truncatedTo(ChronoUnit.DAYS).toInstant();
        if (!f.isBefore(t)) {
            throw new IllegalArgumentException("from 은 to 보다 앞이어야 합니다");
        }
        return new Range(f, t);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private record Range(Instant from, Instant to) {
    }
}
