package com.gte.argos.gateway.factory;

import com.gte.argos.gateway.common.api.ApiResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 공장 관리 · 설비 재배치 (명세서 H1). 조회(GET /sites · /factories · /processes · layout)는 대시보드 API 를 같이 쓴다 */
@RestController
@RequestMapping("/api/v1")
public class FactoryAdminController {

    private final FactoryAdminService service;

    public FactoryAdminController(FactoryAdminService service) {
        this.service = service;
    }

    @PostMapping("/sites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> site(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createSite(body)));
    }

    @PostMapping("/factories")
    public ResponseEntity<ApiResponse<Map<String, Object>>> factory(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createFactory(body)));
    }

    @PostMapping("/processes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> process(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createProcess(body)));
    }

    @PutMapping("/processes/{processId}/layout")
    public ApiResponse<Map<String, Object>> layout(@PathVariable long processId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveLayout(processId, body));
    }

    @PostMapping("/processes/{processId}/floorplan")
    public ResponseEntity<ApiResponse<Map<String, Object>>> floorplan(
            @PathVariable long processId, @RequestParam("file") MultipartFile file,
            @RequestParam Double widthM, @RequestParam Double depthM) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.uploadFloorplan(
                processId, file.getBytes(), file.getOriginalFilename(), file.getContentType(), widthM, depthM)));
    }
}
