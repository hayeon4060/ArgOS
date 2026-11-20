package com.gte.argos.gateway.common.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of(
                "service", "gateway-backend",
                "role", "LINKAGE_PC_API",
                "status", "UP"
        );
    }
}

