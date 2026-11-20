package com.gte.argos.factory.common.time;

import com.gte.argos.factory.common.api.ApiResponse;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서버 시계 확인 (now = 실제 서버 시각, simStart = 7일치 더미가 끝나는 시각). 연계 서버·프론트가 "지금"을 맞출 때 쓴다 */
@RestController
@RequestMapping("/api/v1/system")
public class ClockController {

    private final DemoClock clock;

    public ClockController(DemoClock clock) {
        this.clock = clock;
    }

    @GetMapping("/clock")
    public ApiResponse<ClockResponse> clock() {
        return ApiResponse.ok(new ClockResponse(
                clock.nowOffset(),
                clock.simStart().atZone(clock.zone()).toOffsetDateTime(),
                clock.zone().getId()));
    }

    public record ClockResponse(OffsetDateTime now, OffsetDateTime simStart, String zone) {
    }
}
