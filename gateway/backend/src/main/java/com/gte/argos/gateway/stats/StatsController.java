package com.gte.argos.gateway.stats;

import com.gte.argos.gateway.common.api.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계 · 이력 관리 (API 명세서 D1). 모든 API 가 같은 조건 파라미터를 받는다.
 * from · to (필수, 날짜) · factoryId · processId · sensorType(CURRENT|TEMP|CO|IR) · level(WARN|ALARM) · shift(DAY|NIGHT)
 */
@RestController
@RequestMapping("/api/v1")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/stats/summary")
    public ApiResponse<Map<String, Object>> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long factoryId, @RequestParam(required = false) Long processId,
            @RequestParam(required = false) String sensorType, @RequestParam(required = false) String level,
            @RequestParam(required = false) String shift) {
        return ApiResponse.ok(service.summary(new AlarmCriteria(from, to, factoryId, processId, sensorType, level, shift)));
    }

    @GetMapping("/stats/trend")
    public ApiResponse<Map<String, Object>> trend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long factoryId, @RequestParam(required = false) Long processId,
            @RequestParam(required = false) String sensorType, @RequestParam(required = false) String level,
            @RequestParam(required = false) String shift, @RequestParam(required = false) String unit) {
        return ApiResponse.ok(service.trend(new AlarmCriteria(from, to, factoryId, processId, sensorType, level, shift), unit));
    }

    @GetMapping("/stats/by-process")
    public ApiResponse<List<Map<String, Object>>> byProcess(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long factoryId, @RequestParam(required = false) Long processId,
            @RequestParam(required = false) String sensorType, @RequestParam(required = false) String level,
            @RequestParam(required = false) String shift) {
        return ApiResponse.ok(service.byProcess(new AlarmCriteria(from, to, factoryId, processId, sensorType, level, shift)));
    }

    @GetMapping("/stats/heatmap")
    public ApiResponse<Map<String, Object>> heatmap(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long factoryId, @RequestParam(required = false) Long processId,
            @RequestParam(required = false) String sensorType, @RequestParam(required = false) String level,
            @RequestParam(required = false) String shift) {
        return ApiResponse.ok(service.heatmap(new AlarmCriteria(from, to, factoryId, processId, sensorType, level, shift)));
    }

    /**
     * 알림 이력 표. /alarms 와 같은 줄 모양에 통계 조건(sensorType · level · shift)과 정렬을 더했다.
     *
     * @param sort raisedAt,desc (기본) | actionMinutes,desc
     */
    @GetMapping("/stats/alarms")
    public ApiResponse<Map<String, Object>> history(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long factoryId, @RequestParam(required = false) Long processId,
            @RequestParam(required = false) String sensorType, @RequestParam(required = false) String level,
            @RequestParam(required = false) String shift, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String hazard, @RequestParam(required = false) String status,
            @RequestParam(required = false) Long siteId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.history(new AlarmCriteria(from, to, factoryId, processId, sensorType, level, shift,
                        hazard, status, siteId),
                sort, Math.max(0, page), Math.max(1, Math.min(500, size))));
    }

    /** 알림 상세 패널: 센서 그래프 · 열화상 · 조치 이력 · 체크리스트 / 셧다운 단계 */
    @GetMapping("/stats/alarms/{alarmId}")
    public ApiResponse<Map<String, Object>> alarmDetail(@org.springframework.web.bind.annotation.PathVariable long alarmId) {
        return ApiResponse.ok(service.alarmDetail(alarmId));
    }

    /** CSV (기본) 또는 format=xlsx 엑셀 파일 */
    @GetMapping("/stats/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long factoryId, @RequestParam(required = false) Long processId,
            @RequestParam(required = false) String sensorType, @RequestParam(required = false) String level,
            @RequestParam(required = false) String shift, @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String hazard, @RequestParam(required = false) String status,
            @RequestParam(required = false) Long siteId) {
        StatsService.Export file = service.export(
                new AlarmCriteria(from, to, factoryId, processId, sensorType, level, shift, hazard, status, siteId), format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.body());
    }
}
