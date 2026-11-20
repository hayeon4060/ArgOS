package com.gte.argos.factory.redis;

/**
 * 데이터 수집 서버 ↔ 중앙 서버 Redis 규격 (중앙 서버 gateway/backend 의 RedisKeys 와 같아야 함).
 *
 * <pre>
 * 수집 → 중앙
 *   argos:feed           스트림. 실시간 변화(경보·이벤트·상태·명령 결과·센서값). 번호 = {bootMs}-{seq}
 *                        필드 bootId, seq, type, at, data(JSON)
 *   argos:feed:meta      해시. bootId, bootMs, latest (수집 서버가 켜질 때마다 bootId 가 바뀜)
 *   argos:live           해시. 필드 = sensorId, 값 = {"ts","value"} (센서별 지금 값, 1초 집계 캐시)
 *   argos:raw:{sensorId} 스트림. 최근 1시간 1초 값 (필드 ts, value)
 *
 * 중앙 → 수집 (제어 명령, 요청 · 응답)
 *   argos:cmd            스트림. 필드 requestId, op, payload(JSON), sentAt(epoch ms). 소비 그룹 collector
 *   argos:reply:{id}     리스트. 응답 1건 {success, data} | {success:false, error{status, code, message, details}}
 * </pre>
 */
public final class RedisKeys {

    public static final String FEED = "argos:feed";
    public static final String FEED_META = "argos:feed:meta";
    public static final String LIVE = "argos:live";
    public static final String RAW_PREFIX = "argos:raw:";
    public static final String CMD = "argos:cmd";
    public static final String CMD_GROUP = "collector";
    public static final String REPLY_PREFIX = "argos:reply:";

    /** 피드 스트림 보관 건수 (LiveFeed 와 같음) */
    public static final long FEED_MAXLEN = 20_000;
    /** 센서별 1초 값 보관 건수 (1시간) */
    public static final long RAW_MAXLEN = 3_600;

    private RedisKeys() {
    }
}
