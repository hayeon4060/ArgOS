package com.gte.argos.factory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 작업 켜기 (센서 값 보관 기간 정리 등) */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
