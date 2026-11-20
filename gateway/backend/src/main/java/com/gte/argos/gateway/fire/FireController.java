package com.gte.argos.gateway.fire;

import com.gte.argos.gateway.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 화재 대응 (명세서 C4F · C5) */
@RestController
@RequestMapping("/api/v1")
public class FireController {

    private final FireService service;

    public FireController(FireService service) {
        this.service = service;
    }

    @GetMapping("/fires")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Long siteId) {
        return ApiResponse.ok(service.list(status, siteId));
    }

    @GetMapping("/fires/{fireId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long fireId) {
        return ApiResponse.ok(service.detail(fireId));
    }

    @PostMapping("/fires/{fireId}/clear")
    public ApiResponse<Map<String, Object>> clear(@PathVariable long fireId, @RequestBody ClearRequest body) {
        return ApiResponse.ok(service.clear(fireId, body.password(), body.memo()));
    }

    @PostMapping("/fires/{fireId}/approve-unlock")
    public ApiResponse<Map<String, Object>> approveUnlock(@PathVariable long fireId) {
        return ApiResponse.ok(service.approveUnlock(fireId));
    }

    @PostMapping("/fires/{fireId}/evacuated")
    public ApiResponse<Map<String, Object>> evacuated(@PathVariable long fireId) {
        return ApiResponse.ok(service.evacuated(fireId));
    }

    public record ClearRequest(String password, String memo) {
    }
}
