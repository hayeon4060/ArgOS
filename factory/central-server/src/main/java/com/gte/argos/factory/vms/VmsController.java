package com.gte.argos.factory.vms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.common.api.ApiResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * VMS 서버 → 중앙 열화상 값 (시험용: 인증 없음, 응답 200 이면 정상).
 *
 * <pre>
 * POST /api/v1/vms/ir  application/json       ① 평소 값 (1초마다)
 * POST /api/v1/vms/ir  multipart/form-data    ② 단계 진입: meta(JSON 글자) + snapshot(jpeg)
 * GET  /api/v1/internal/files/{fileId}        저장한 사진 (연계 서버가 화면에 보여 줄 때)
 * </pre>
 */
@RestController
public class VmsController {

    private final VmsService service;
    private final ObjectMapper json;

    public VmsController(VmsService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping(value = "/api/v1/vms/ir", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> value(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.receive(body, null, null, null));
    }

    @SuppressWarnings("unchecked")
    @PostMapping(value = "/api/v1/vms/ir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> stageEntry(@RequestPart("meta") String meta,
            @RequestPart(value = "snapshot", required = false) MultipartFile snapshot) throws IOException {
        Map<String, Object> m = json.readValue(meta, Map.class);
        return ApiResponse.ok(service.receive(m, snapshot == null ? null : snapshot.getBytes(),
                snapshot == null ? null : snapshot.getOriginalFilename(),
                snapshot == null ? null : snapshot.getContentType()));
    }

    @GetMapping("/api/v1/internal/files/{fileId}")
    public ResponseEntity<byte[]> file(@PathVariable long fileId) throws IOException {
        VmsService.Stored f = service.file(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.mime() == null ? "application/octet-stream" : f.mime()))
                .body(Files.readAllBytes(f.path()));
    }
}
