package com.gte.argos.gateway.central;

/**
 * 데이터 수집 서버 ↔ 중앙 서버 Redis 규격 (수집 서버 factory/central-server 의 RedisKeys 와 같아야 함).
 *
 * <pre>
 * 수집 → 중앙
 *   argos:feed           스트림. 실시간 변화. 번호 = {bootMs}-{seq}, 필드 bootId, seq, type, at, data(JSON)
 *   argos:feed:meta      해시. bootId, bootMs, latest
 *   argos:live           해시. sensorId → {"ts","value"} (센서별 지금 값)
 *   argos:raw:{sensorId} 스트림. 최근 1시간 1초 값
 *
 * 중앙 → 수집 (제어 명령, 요청 · 응답)
 *   argos:cmd            스트림. 필드 requestId, op, payload(JSON), sentAt(epoch ms)
 *   argos:reply:{id}     리스트. 응답 1건 {success, data} | {success:false, error{status, code, message, details}}
 * </pre>
 */
public final class RedisKeys {

    public static final String FEED = "argos:feed";
    public static final String FEED_META = "argos:feed:meta";
    public static final String LIVE = "argos:live";
    public static final String RAW_PREFIX = "argos:raw:";
    public static final String CMD = "argos:cmd";
    public static final String REPLY_PREFIX = "argos:reply:";

    /** 명령 스트림 보관 건수 */
    public static final long CMD_MAXLEN = 1_000;

    private RedisKeys() {
    }
}
