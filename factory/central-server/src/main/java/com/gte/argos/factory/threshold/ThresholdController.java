package com.gte.argos.factory.threshold;

import com.gte.argos.factory.common.api.ApiResponse;
import com.gte.argos.factory.threshold.ThresholdService.SensorThreshold;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연계 서버 전용 기준값 API.
 *
 * <pre>
 * GET  /api/v1/internal/equipment/{id}/thresholds     센서별 계절 기준값 + version + activeSeason
 * PUT  /api/v1/internal/equipment/{id}/thresholds     저장 (version 이 다르면 409 THRESHOLD_CONFLICT)
 * POST /api/v1/internal/thresholds/simulate           최근 N일 알림 수 미리보기 (저장 안 함)
 * GET  /api/v1/internal/thresholds/history            변경 이력
 * PUT  /api/v1/internal/sensors/{id}                  수집 주기 · 사용 여부
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/internal")
public class ThresholdController {

    private final ThresholdService service;

    public ThresholdController(ThresholdService service) {
        this.service = service;
    }

    @GetMapping("/equipment/{equipmentId}/thresholds")
    public ApiResponse<Map<String, Object>> get(@PathVariable long equipmentId) {
        return ApiResponse.ok(service.equipmentThresholds(equipmentId));
    }

    @PutMapping("/equipment/{equipmentId}/thresholds")
    public ApiResponse<Map<String, Object>> save(@PathVariable long equipmentId, @RequestBody @Valid SaveRequest body) {
        return ApiResponse.ok(service.save(equipmentId, body.season(), body.version(), body.reason(), body.userId(),
                body.sensors()));
    }

    @PostMapping("/thresholds/simulate")
    public ApiResponse<Map<String, Object>> simulate(@RequestBody @Valid SimulateRequest body) {
        return ApiResponse.ok(service.simulate(body.sensorId(), body.warn(), body.alarm(), body.lowWarn(),
                body.lowAlarm(), body.warnSamples(), body.alarmSamples(), body.days() == null ? 7 : body.days()));
    }

    @GetMapping("/thresholds/history")
    public ApiResponse<List<Map<String, Object>>> history(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long sensorId,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ApiResponse.ok(service.history(equipmentId, sensorId, season, from, to));
    }

    @PutMapping("/sensors/{sensorId}")
    public ApiResponse<Map<String, Object>> updateSensor(@PathVariable long sensorId, @RequestBody SensorRequest body) {
        return ApiResponse.ok(service.updateSensor(sensorId, body.sampleSec(), body.active(), body.streamUrl()));
    }

    public record SaveRequest(@NotBlank String season, Long version, String reason, Long userId,
                              @NotEmpty List<SensorThreshold> sensors) {
    }

    public record SimulateRequest(@NotNull Long sensorId, Double warn, Double alarm, Double lowWarn, Double lowAlarm,
                                  Integer warnSamples, Integer alarmSamples, Integer days) {
    }

    /** streamUrl: VMS 영상 주소. null 이면 그대로, 빈 글자면 지움 */
    public record SensorRequest(Integer sampleSec, Boolean active, String streamUrl) {
    }
}
