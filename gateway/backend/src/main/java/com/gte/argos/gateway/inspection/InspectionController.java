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
    private final InspectionScheduleService schedules;
    private final FormTemplatePdf templates;

    public InspectionController(InspectionService service, InspectionScheduleService schedules,
                                FormTemplatePdf templates) {
        this.service = service;
        this.schedules = schedules;
        this.templates = templates;
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

    /**
     * 점검 건 목록. date(하루) · month(YYYY-MM) · from&to 중 하나 (점검 관리 개편 A안 3-1).
     * formType 을 주면 그 양식만 (점검 관리는 F02)
     */
    @GetMapping("/inspections/schedule")
    public ApiResponse<List<Map<String, Object>>> schedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long factoryId, @RequestParam(required = false) String formType) {
        return ApiResponse.ok(schedules.list(date, month, from, to, factoryId, formType));
    }

    /** 스케줄 추가 창 고르는 칸 (3-2) */
    @GetMapping("/inspections/schedule/options")
    public ApiResponse<Map<String, Object>> scheduleOptions(@RequestParam(required = false) Long factoryId) {
        return ApiResponse.ok(schedules.options(factoryId));
    }

    /** 스케줄 추가 (3-3) — 반복이면 여러 건 */
    @PostMapping("/inspections/schedule")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> addSchedule(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(schedules.create(body)));
    }

    /** 양식 값 (3-4) */
    @GetMapping("/inspections/schedule/{scheduleId}/form")
    public ApiResponse<Map<String, Object>> scheduleForm(@PathVariable long scheduleId) {
        return ApiResponse.ok(schedules.form(scheduleId));
    }

    /** 예정 · 누락 건 전자 작성 (3-5) */
    @PutMapping("/inspections/schedule/{scheduleId}/form")
    public ApiResponse<Map<String, Object>> saveScheduleForm(@PathVariable long scheduleId,
                                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(schedules.saveForm(scheduleId, body));
    }

    /** 스케줄 없이 점검일지 작성 (3-5) */
    @PostMapping("/inspections/records")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addRecord(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(schedules.createRecord(body)));
    }

    /** 빈 서류 양식 PDF (3-6) */
    @GetMapping("/doc-forms/{formType}/template")
    public ResponseEntity<byte[]> template(@PathVariable String formType) {
        FormTemplatePdf.Template t = templates.build(formType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(t.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(t.bytes());
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

    /**
     * OCR 저장. scheduleId(3-7): 숫자면 그 점검 건에 잇기, null 이면 인식 값으로 새 점검 건, 없으면 올릴 때 고른 건 그대로
     */
    @PutMapping("/inspections/{inspectionId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> save(@PathVariable long inspectionId, @RequestBody Map<String, Object> body) {
        Object fields = body.get("fields");
        boolean link = body.containsKey("scheduleId");
        Long scheduleId = body.get("scheduleId") instanceof Number n ? n.longValue() : null;
        return ApiResponse.ok(service.save(inspectionId, body.get("status") == null ? null : String.valueOf(body.get("status")),
                fields instanceof List<?> l ? (List<Map<String, Object>>) l : null, link, scheduleId));
    }
}
