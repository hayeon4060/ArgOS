package com.gte.argos.gateway.log;

import com.gte.argos.gateway.common.api.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

/** 이용 로그 (명세서 G2) */
@RestController
@RequestMapping("/api/v1")
public class LogController {

    private final LogService service;

    public LogController(LogService service) {
        this.service = service;
    }

    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long factoryId, @RequestParam(required = false) Long processId,
            @RequestParam(required = false) Long userId, @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(new LogService.Criteria(from, to, factoryId, processId, userId, type, q),
                Math.max(0, page), Math.max(1, Math.min(500, size))));
    }

    @GetMapping("/logs/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long factoryId, @RequestParam(required = false) Long processId,
            @RequestParam(required = false) Long userId, @RequestParam(required = false) String type,
            @RequestParam(required = false) String q) {
        LogService.Export file = service.export(
                new LogService.Criteria(from, to, factoryId, processId, userId, type, q));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(file.body());
    }
}
