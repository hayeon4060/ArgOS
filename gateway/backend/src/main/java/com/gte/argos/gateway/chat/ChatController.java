package com.gte.argos.gateway.chat;

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

/** 채팅 (명세서 I2). roomId = 사업장 id */
@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping("/chat/rooms")
    public ApiResponse<List<Map<String, Object>>> rooms() {
        return ApiResponse.ok(service.rooms());
    }

    @GetMapping("/chat/rooms/{roomId}/messages")
    public ApiResponse<Map<String, Object>> messages(@PathVariable long roomId,
                                                     @RequestParam(required = false) Long before,
                                                     @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(service.messages(roomId, before, Math.max(1, Math.min(100, size))));
    }

    @PostMapping("/chat/rooms/{roomId}/messages")
    public ApiResponse<Map<String, Object>> send(@PathVariable long roomId, @RequestBody SendRequest body) {
        return ApiResponse.ok(service.send(roomId, body.body(), body.alarmId()));
    }

    @PostMapping("/chat/rooms/{roomId}/read")
    public ApiResponse<Map<String, Object>> read(@PathVariable long roomId, @RequestBody ReadRequest body) {
        return ApiResponse.ok(service.read(roomId, body.lastReadMessageId() == null ? 0 : body.lastReadMessageId()));
    }

    public record SendRequest(String body, Long alarmId) {
    }

    public record ReadRequest(Long lastReadMessageId) {
    }
}
