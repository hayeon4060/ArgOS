package com.gte.argos.factory.config;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml 의 argos.* 설정
 *
 * @param zone          모든 시각을 읽고 보여줄 시간대
 * @param clock         서버 시계
 * @param historyImport 7일치 더미데이터 가져오기
 * @param devApi        개발·시연용 API (값 직접 넣기, 리셋) 사용 여부
 * @param internalApiKey 연계 서버 전용 API(/api/v1/internal/**) 키. 비우면 검사하지 않음 (로컬 개발)
 * @param retention     센서 값(그래프용) 보관 기간
 */
@ConfigurationProperties(prefix = "argos")
public record ArgosProperties(
        @DefaultValue("Asia/Seoul") ZoneId zone,
        @DefaultValue Clock clock,
        @DefaultValue HistoryImport historyImport,
        @DefaultValue("false") boolean devApi,
        @DefaultValue("") String internalApiKey,
        @DefaultValue Retention retention) {

    /**
     * 센서 값 보관. 그래프용 1분 추이(sensor_trend)와 경보 구간 샘플(sensor_event_sample)만 지운다.
     * 경보·이벤트 이력(alarm, sensor_event)과 경보 이미지는 통계·월간 보고서용이라 지우지 않는다.
     *
     * @param enabled 정기 삭제를 할지
     * @param days    며칠 지난 값을 지울지 (서버 시계 기준)
     */
    public record Retention(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("7") int days,
            @DefaultValue("31") int keepDays) {
        /** 실제로 쓰는 보관 일수 (keepDays). days 는 예전 설정 이름 */
        public int effectiveDays() {
            return keepDays > 0 ? keepDays : days;
        }
    }

    /**
     * @param simStart 7일치 더미 데이터가 끝나는 시각 (이보다 앞은 더미, 뒤는 실시간). 지금은 항상 실제 서버 시각이다
     */
    public record Clock(@DefaultValue("2026-11-16T09:00:00+09:00") OffsetDateTime simStart) {
    }

    /**
     * @param enabled 서버 시작 때 가져오기를 할지
     * @param path    더미데이터 CSV 폴더
     * @param reset   이미 가져온 데이터가 있어도 지우고 다시 가져올지
     */
    public record HistoryImport(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("../raw-data/dummy-data") String path,
            @DefaultValue("false") boolean reset) {
    }
}
