package com.gte.argos.factory.common.api;

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
                "service", "factory-central-server",
                "role", "CONTROL_NETWORK_CENTRAL",
                "status", "UP"
        );
    }
}

