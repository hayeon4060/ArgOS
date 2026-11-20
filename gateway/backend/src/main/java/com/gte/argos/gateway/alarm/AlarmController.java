package com.gte.argos.gateway.alarm;

import com.gte.argos.gateway.common.api.ApiResponse;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알림 API (API 명세서 B1 · C0 · C1 · C4 · D1-5 · I1) */
@RestController
@RequestMapping("/api/v1")
public class AlarmController {

    private final AlarmService service;

    public AlarmController(AlarmService service) {
        this.service = service;
    }

    /**
     * 실시간 알림 목록 · 알림 이력 표.
     *
     * @param status 쉼표로 여러 개 (OPEN,ACKED)
     * @param date   today | 2026-11-12. from·to 를 주면 그 기간
     */
    @GetMapping("/alarms")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) Long processId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String minLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(siteId, factoryId, processId, date, from, to, status, minLevel,
                Math.max(0, page), Math.max(1, Math.min(500, size))));
    }

    @GetMapping("/alarms/hourly")
    public ApiResponse<Map<String, Object>> hourly(@RequestParam(required = false) Long siteId,
                                                   @RequestParam(required = false) Long factoryId,
                                                   @RequestParam(required = false) String date) {
        return ApiResponse.ok(service.hourly(siteId, factoryId, date));
    }

    @GetMapping("/alarms/{alarmId}")
    public ApiResponse<Map<String, Object>> timeline(@PathVariable long alarmId) {
        return ApiResponse.ok(service.timeline(alarmId));
    }

    @GetMapping("/alarms/{alarmId}/detail")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long alarmId) {
        return ApiResponse.ok(service.detail(alarmId));
    }

    @GetMapping("/alarms/{alarmId}/response")
    public ApiResponse<Map<String, Object>> response(@PathVariable long alarmId) {
        return ApiResponse.ok(service.response(alarmId));
    }

    @PostMapping("/alarms/{alarmId}/ack")
    public ApiResponse<Map<String, Object>> ack(@PathVariable long alarmId) {
        return ApiResponse.ok(service.ack(alarmId));
    }

    @PostMapping("/alarms/{alarmId}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable long alarmId,
                                                  @RequestBody(required = false) CloseRequest body) {
        return ApiResponse.ok(service.close(alarmId, body == null ? null : body.result(),
                body == null ? null : body.followUp()));
    }

    @GetMapping("/notifications/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(service.summary());
    }

    /**
     * @param result   조치 결과 ("배기팬 정지 후 CO 12 ppm 으로 복귀")
     * @param followUp 후속 조치 ("배기팬 베어링 교체 예정")
     */
    public record CloseRequest(String result, String followUp) {
    }
}
