package com.gte.argos.gateway.inspection;

import com.gte.argos.gateway.common.api.ApiResponse;
import com.gte.argos.gateway.file.FileStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 점검 관리 · 수기 서류 (명세서 E1), 오늘 점검 (B3) */
@RestController
@RequestMapping("/api/v1")
public class InspectionController {

    private final InspectionService service;

    public InspectionController(InspectionService service) {
        this.service = service;
    }

    @GetMapping("/inspections/today")
    public ApiResponse<Map<String, Object>> today(@RequestParam long processId) {
        return ApiResponse.ok(service.today(processId));
    }

    @GetMapping("/inspections/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(required = false) String month,
                                                    @RequestParam(required = false) Long factoryId) {
        return ApiResponse.ok(service.summary(month, factoryId));
    }

    /** 그날 점검 목록 (달력 날짜 → 작은 창). date = YYYY-MM-DD */
    @GetMapping("/inspections/schedule")
    public ApiResponse<List<Map<String, Object>>> schedule(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @RequestParam(required = false) Long factoryId) {
        return ApiResponse.ok(service.daySchedule(date, factoryId));
    }

    @GetMapping("/doc-forms")
    public ApiResponse<List<Map<String, Object>>> forms() {
        return ApiResponse.ok(service.forms());
    }

    @PostMapping("/inspections")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestParam("file") MultipartFile file, @RequestParam(required = false) String formType,
            @RequestParam(required = false) Long scheduleId, @RequestParam(required = false) Long processId)
            throws IOException {
        Map<String, Object> out = service.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType(),
                formType, scheduleId, processId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(out));
    }

    @GetMapping("/inspections")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String formType, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(from, to, formType, status, Math.max(0, page), Math.max(1, Math.min(200, size))));
    }

    @GetMapping("/inspections/{inspectionId}/ocr")
    public ApiResponse<Map<String, Object>> ocr(@PathVariable long inspectionId) {
        return ApiResponse.ok(service.ocrResult(inspectionId));
    }

    @PostMapping("/inspections/{inspectionId}/ocr")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rerun(@PathVariable long inspectionId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(service.rerun(inspectionId)));
    }

    /** 원본 파일. PDF 는 쪽 그림으로 바꾸지 않고 PDF 그대로 준다 (page 무시) */
    @GetMapping("/inspections/{inspectionId}/file")
    public ResponseEntity<byte[]> file(@PathVariable long inspectionId, @RequestParam(defaultValue = "1") int page) {
        FileStore.Stored f = service.file(inspectionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.name() == null ? "scan" : f.name(), StandardCharsets.UTF_8).build().toString())
                .contentType(f.mime() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(f.mime()))
                .body(service.read(f));
    }

    @PutMapping("/inspections/{inspectionId}")
    public ApiResponse<Map<String, Object>> save(@PathVariable long inspectionId, @RequestBody SaveRequest body) {
        return ApiResponse.ok(service.save(inspectionId, body.status(), body.fields()));
    }

    public record SaveRequest(String status, List<Map<String, Object>> fields) {
    }
}
