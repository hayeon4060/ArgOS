package com.gte.argos.gateway.dashboard;

import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.common.api.ApiResponse;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 API (API 명세서 A2 · B1 · B2 · B3 · B4, H1 선택 상자).
 * 권한·담당 범위는 DashboardService 가 매번 확인한다.
 */
@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DashboardService service;
    private final SimTime time;

    public DashboardController(DashboardService service, SimTime time) {
        this.service = service;
        this.time = time;
    }

    /** 시연 시계. 화면의 "오늘", "지금"은 브라우저 시계 대신 이 값을 쓴다 */
    @GetMapping("/system/clock")
    public ApiResponse<Map<String, Object>> clock() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("now", time.now());
        out.put("zone", SimTime.ZONE.getId());
        return ApiResponse.ok(out);
    }

    @GetMapping("/sites")
    public ApiResponse<List<Map<String, Object>>> sites() {
        return ApiResponse.ok(service.sites());
    }

    @GetMapping("/sites/{siteId}/tree")
    public ApiResponse<Map<String, Object>> siteTree(@PathVariable long siteId) {
        return ApiResponse.ok(service.siteTree(siteId));
    }

    @GetMapping("/sites/{siteId}/overview")
    public ApiResponse<Map<String, Object>> siteOverview(@PathVariable long siteId) {
        return ApiResponse.ok(service.siteOverview(siteId));
    }

    @GetMapping("/factories")
    public ApiResponse<List<Map<String, Object>>> factories(@RequestParam long siteId) {
        return ApiResponse.ok(service.factories(siteId));
    }

    @GetMapping("/processes")
    public ApiResponse<List<Map<String, Object>>> processes(@RequestParam long factoryId) {
        return ApiResponse.ok(service.processes(factoryId));
    }

    @GetMapping("/factories/{factoryId}/overview")
    public ApiResponse<Map<String, Object>> factoryOverview(@PathVariable long factoryId) {
        return ApiResponse.ok(service.factoryOverview(factoryId));
    }

    @GetMapping("/processes/{processId}/layout")
    public ApiResponse<Map<String, Object>> processLayout(@PathVariable long processId) {
        return ApiResponse.ok(service.processLayout(processId));
    }

    @GetMapping("/processes/{processId}/status")
    public ApiResponse<Map<String, Object>> processStatus(@PathVariable long processId) {
        return ApiResponse.ok(service.processStatus(processId));
    }

    @GetMapping("/equipment/{equipmentId}")
    public ApiResponse<Map<String, Object>> equipment(@PathVariable long equipmentId) {
        return ApiResponse.ok(service.equipment(equipmentId));
    }

    @GetMapping("/equipment/{equipmentId}/events")
    public ApiResponse<List<Map<String, Object>>> equipmentEvents(@PathVariable long equipmentId,
                                                                  @RequestParam(required = false) String date,
                                                                  @RequestParam(required = false) String type) {
        return ApiResponse.ok(service.equipmentEvents(equipmentId, date, type));
    }

    /**
     * @param ids 센서 id 쉼표로 여러 개 (117,115,113)
     */
    @GetMapping("/sensors/series")
    public ApiResponse<Map<String, Object>> series(
            @RequestParam List<Long> ids,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String step,
            @RequestParam(required = false) String compare) {
        OffsetDateTime t = to != null ? to : time.now();
        OffsetDateTime f = from != null ? from : t.minusHours(1);
        return ApiResponse.ok(service.series(ids, f, t, step, compare));
    }
}
