package com.gte.argos.gateway.thermal;

import com.gte.argos.gateway.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 열화상 최신 값 (B4 설비 상세의 열화상 카드).
 *
 * <pre>
 * GET /api/v1/thermal/{cameraId}/latest?equipmentId=41
 * </pre>
 * cameraId 는 IR 센서 id 다 ({@code GET /equipment/{id}} 의 cameraId).
 */
@RestController
@RequestMapping("/api/v1")
public class ThermalController {

    private final ThermalService service;

    public ThermalController(ThermalService service) {
        this.service = service;
    }

    /** VMS 열화상 사진 (단계가 오를 때 찍은 것). 로그인 필요 */
    @GetMapping("/thermal/images/{fileId}")
    public org.springframework.http.ResponseEntity<byte[]> image(@PathVariable long fileId) {
        org.springframework.http.ResponseEntity<byte[]> r = service.image(fileId);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(r.getHeaders().getContentType() == null
                        ? org.springframework.http.MediaType.IMAGE_JPEG : r.getHeaders().getContentType())
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(r.getBody());
    }

    @GetMapping("/thermal/{cameraId}/latest")
    public ApiResponse<Map<String, Object>> latest(@PathVariable long cameraId,
                                                   @RequestParam(required = false) Long equipmentId) {
        return ApiResponse.ok(service.latest(cameraId, equipmentId));
    }
}
