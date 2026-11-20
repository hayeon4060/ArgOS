package com.gte.argos.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 중앙 피드 가져오기(FeedPoller)와 SSE ping 에 필요 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
