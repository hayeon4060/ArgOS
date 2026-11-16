package com.gte.argos.gateway.threshold;

import com.gte.argos.gateway.common.api.ApiResponse;
import com.gte.argos.gateway.threshold.ThresholdAdminService.SaveRequest;
import com.gte.argos.gateway.threshold.ThresholdAdminService.SensorUpdateRequest;
import com.gte.argos.gateway.threshold.ThresholdAdminService.SimulateRequest;
import java.time.LocalDate;
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

/** 설비 기준값 관리 (API 명세서 F1) · 센서 설정 창 (H2) */
@RestController
@RequestMapping("/api/v1")
public class ThresholdController {

    private final ThresholdAdminService service;

    public ThresholdController(ThresholdAdminService service) {
        this.service = service;
    }

    @GetMapping("/equipment/tree")
    public ApiResponse<List<Map<String, Object>>> tree(@RequestParam long factoryId) {
        return ApiResponse.ok(service.tree(factoryId));
    }

    @GetMapping("/equipment/{equipmentId}/thresholds")
    public ApiResponse<Map<String, Object>> get(@PathVariable long equipmentId) {
        return ApiResponse.ok(service.get(equipmentId));
    }

    @PostMapping("/thresholds/simulate")
    public ApiResponse<Map<String, Object>> simulate(@RequestBody SimulateRequest body) {
        return ApiResponse.ok(service.simulate(body));
    }

    @PutMapping("/equipment/{equipmentId}/thresholds")
    public ApiResponse<Map<String, Object>> save(@PathVariable long equipmentId, @RequestBody SaveRequest body) {
        if (body.sensors() == null || body.sensors().isEmpty()) {
            throw new IllegalArgumentException("sensors");
        }
        return ApiResponse.ok(service.save(equipmentId, body));
    }

    @GetMapping("/thresholds/history")
    public ApiResponse<List<Map<String, Object>>> history(
            @RequestParam long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long sensorId,
            @RequestParam(required = false) String season) {
        return ApiResponse.ok(service.history(equipmentId, from, to, sensorId, season));
    }

    @GetMapping("/sensors/{sensorId}")
    public ApiResponse<Map<String, Object>> sensor(@PathVariable long sensorId) {
        return ApiResponse.ok(service.sensor(sensorId));
    }

    @PutMapping("/sensors/{sensorId}")
    public ApiResponse<Map<String, Object>> updateSensor(@PathVariable long sensorId,
                                                         @RequestBody SensorUpdateRequest body) {
        return ApiResponse.ok(service.updateSensor(sensorId, body));
    }
}
