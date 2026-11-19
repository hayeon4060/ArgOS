-- 테이블 명세서 v5 (docs/erd/ArgOS_테이블명세서_v5.xlsx) 반영
-- 판정은 지금처럼 중앙 서버가 한다 (v5 의 Node-RED 엣지·Kafka 는 범위 밖). 컬럼·허용 값만 v5 에 맞춘다.
-- 기존 행은 지금 동작이 바뀌지 않게 옮긴다 (기준값 지속 횟수·복귀 여유 등).

-- ---------------------------------------------------------------
-- file_asset: 열화상 영상(VIDEO) 추가
-- ---------------------------------------------------------------
ALTER TABLE file_asset DROP CONSTRAINT IF EXISTS file_asset_kind_check;
ALTER TABLE file_asset ADD CONSTRAINT file_asset_kind_check
    CHECK (kind IN ('THERMAL', 'VIDEO', 'DOC_SCAN', 'FLOORPLAN', 'REPORT'));

-- ---------------------------------------------------------------
-- sensor_threshold: 복귀 횟수·복귀 여유·평균 시간·즉시 3단계 값
-- ---------------------------------------------------------------
-- 기존 행은 복귀 여유 0 (지금처럼 기준 아래면 정상). 새로 만드는 행부터 v5 기본값 5%.
ALTER TABLE sensor_threshold ADD COLUMN release_samples INT              NOT NULL DEFAULT 6;
ALTER TABLE sensor_threshold ADD COLUMN release_margin  DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE sensor_threshold ALTER COLUMN release_margin SET DEFAULT 0.05;
ALTER TABLE sensor_threshold ADD COLUMN alarm_avg_sec   INT;              -- 예약 (CO 60초 평균)
ALTER TABLE sensor_threshold ADD COLUMN immediate_value DOUBLE PRECISION; -- 이 값 이상이면 바로 3단계
ALTER TABLE sensor_threshold ALTER COLUMN warn_samples  SET DEFAULT 6;
ALTER TABLE sensor_threshold ALTER COLUMN alarm_samples SET DEFAULT 3;
ALTER TABLE sensor_threshold ADD CONSTRAINT sensor_threshold_release_check
    CHECK (release_samples >= 1 AND release_margin >= 0 AND release_margin < 1);

-- ---------------------------------------------------------------
-- sensor_trend: 통신 끊김 수, 값 비어 있을 수 있음
-- ---------------------------------------------------------------
ALTER TABLE sensor_trend ALTER COLUMN avg_v DROP NOT NULL;
ALTER TABLE sensor_trend ALTER COLUMN min_v DROP NOT NULL;
ALTER TABLE sensor_trend ALTER COLUMN max_v DROP NOT NULL;
ALTER TABLE sensor_trend ALTER COLUMN n SET DEFAULT 0;
ALTER TABLE sensor_trend ADD COLUMN lost_n SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------
-- sensor_daily: 센서 일 집계 (1분 추이를 하루로). 1분 추이는 1년 보관, 일 집계는 계속 남는다
-- ---------------------------------------------------------------
CREATE TABLE sensor_daily (
    sensor_id  BIGINT NOT NULL REFERENCES sensor (sensor_id),
    day        DATE   NOT NULL,
    avg_v      DOUBLE PRECISION,
    min_v      DOUBLE PRECISION,
    max_v      DOUBLE PRECISION,
    n          INT    NOT NULL DEFAULT 0,
    lost_n     INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (sensor_id, day)
);

INSERT INTO sensor_daily (sensor_id, day, avg_v, min_v, max_v, n, lost_n)
SELECT sensor_id, (ts AT TIME ZONE 'Asia/Seoul')::date,
       ROUND((SUM(avg_v * n) / NULLIF(SUM(n) FILTER (WHERE avg_v IS NOT NULL), 0))::numeric, 3)::double precision,
       MIN(min_v), MAX(max_v), SUM(n), SUM(lost_n)
FROM sensor_trend
GROUP BY sensor_id, (ts AT TIME ZONE 'Asia/Seoul')::date;

-- ---------------------------------------------------------------
-- sensor_event: 중복 방지 키, 최고 단계, 판정 규칙(HI/LO/AVG/IMMEDIATE/IR), 열화상 정보, PLC 기록 결과
-- ---------------------------------------------------------------
ALTER TABLE sensor_event ADD COLUMN edge_key      VARCHAR(40);
ALTER TABLE sensor_event ADD COLUMN max_stage     SMALLINT;
ALTER TABLE sensor_event ADD COLUMN roi           JSONB;
ALTER TABLE sensor_event ADD COLUMN image_file_id BIGINT REFERENCES file_asset (file_id);
ALTER TABLE sensor_event ADD COLUMN video_file_id BIGINT REFERENCES file_asset (file_id);
ALTER TABLE sensor_event ADD COLUMN plc_ok        BOOLEAN;

UPDATE sensor_event SET max_stage = stage;
ALTER TABLE sensor_event ALTER COLUMN max_stage SET NOT NULL;
ALTER TABLE sensor_event ADD CONSTRAINT sensor_event_max_stage_check CHECK (max_stage IN (2, 3) AND max_stage >= stage);

-- 지금까지는 모두 지속 판정(DURATION). 하한 쪽으로 벗어난 이벤트는 LO, 나머지는 HI
ALTER TABLE sensor_event DROP CONSTRAINT IF EXISTS sensor_event_rule_check;
ALTER TABLE sensor_event ALTER COLUMN rule DROP DEFAULT;
UPDATE sensor_event e
   SET rule = CASE
                  WHEN (t.low_warn IS NOT NULL AND e.peak_value <= t.low_warn)
                    OR (t.low_alarm IS NOT NULL AND e.peak_value <= t.low_alarm) THEN 'LO'
                  ELSE 'HI'
              END
  FROM sensor_threshold t
 WHERE t.threshold_id = e.threshold_id;
UPDATE sensor_event SET rule = CASE WHEN rule IN ('HI', 'LO') THEN rule ELSE 'HI' END;
ALTER TABLE sensor_event ADD CONSTRAINT sensor_event_rule_check
    CHECK (rule IN ('HI', 'LO', 'AVG', 'IMMEDIATE', 'IR'));

-- 중복 방지 키 = 공장 코드 + 태그 + 시작 시각 (예: P5-AT-301-20261117T175525). 겹치면 뒤에 번호
WITH k AS (
    SELECT e.event_id,
           LEFT(f.code || '-' || s.tag || '-' || to_char(e.started_at AT TIME ZONE 'Asia/Seoul', 'YYYYMMDD"T"HH24MISS'), 36) AS key
    FROM sensor_event e
    JOIN sensor s     ON s.sensor_id = e.sensor_id
    JOIN equipment eq ON eq.equipment_id = s.equipment_id
    JOIN process p    ON p.process_id = eq.process_id
    JOIN factory f    ON f.factory_id = p.factory_id
), n AS (
    SELECT event_id, key, ROW_NUMBER() OVER (PARTITION BY key ORDER BY event_id) AS rn FROM k
)
UPDATE sensor_event e
   SET edge_key = CASE WHEN n.rn = 1 THEN n.key ELSE n.key || '-' || n.rn END
  FROM n
 WHERE n.event_id = e.event_id;
UPDATE sensor_event SET edge_key = 'EV-' || event_id WHERE edge_key IS NULL;
ALTER TABLE sensor_event ALTER COLUMN edge_key SET NOT NULL;
ALTER TABLE sensor_event ADD CONSTRAINT sensor_event_edge_key_key UNIQUE (edge_key);

-- ---------------------------------------------------------------
-- alarm: 종료 사유
-- ---------------------------------------------------------------
ALTER TABLE alarm ADD COLUMN close_reason VARCHAR(20)
    CHECK (close_reason IN ('AUTO_RECOVERED', 'ACTION_DONE', 'MANUAL'));
UPDATE alarm
   SET close_reason = CASE
                          WHEN closed_by IS NULL THEN 'AUTO_RECOVERED'
                          WHEN acked_by IS NOT NULL THEN 'ACTION_DONE'
                          ELSE 'MANUAL'
                      END
 WHERE status = 'CLOSED';

-- ---------------------------------------------------------------
-- equipment_command: FAN_HZ(주파수) · FAN_STOP, 보낸 쪽(source), 수동 승인, 만료 시각, 결과 상세
-- SHUTDOWN · RESTART 는 v5 에 없지만 경보 셧다운·재가동 시연에 쓰므로 남긴다
-- ---------------------------------------------------------------
ALTER TABLE equipment_command DROP CONSTRAINT IF EXISTS equipment_command_command_check;
ALTER TABLE equipment_command DROP CONSTRAINT IF EXISTS equipment_command_result_check;

UPDATE equipment_command SET command = 'FAN_HZ', value = ROUND((value / 30)::numeric)::double precision
 WHERE command = 'FAN_RPM';
UPDATE equipment_command SET command = 'FAN_HZ', value = 40 WHERE command = 'FAN_ON';
UPDATE equipment_command SET command = 'FAN_STOP', value = NULL WHERE command = 'FAN_OFF';
UPDATE equipment_command SET result = 'DONE'   WHERE result = 'OK';
UPDATE equipment_command SET result = 'FAILED' WHERE result = 'FAIL';

ALTER TABLE equipment_command ADD CONSTRAINT equipment_command_command_check
    CHECK (command IN ('FAN_HZ', 'FAN_STOP', 'SHUTDOWN', 'RESTART'));
ALTER TABLE equipment_command ADD CONSTRAINT equipment_command_result_check
    CHECK (result IN ('AWAITING', 'PENDING', 'DONE', 'FAILED', 'REJECTED', 'EXPIRED', 'DUPLICATE', 'CANCELLED', 'DENIED'));
-- 보낸 쪽: USER 사람이 화면에서 / AUTO 가스 알림 때 서버 자동 제어 / RECOVERY 알림 종료 때 40Hz 복귀
ALTER TABLE equipment_command ADD COLUMN source VARCHAR(8) NOT NULL DEFAULT 'USER'
    CHECK (source IN ('USER', 'AUTO', 'RECOVERY'));
ALTER TABLE equipment_command ADD COLUMN approved_by BIGINT;       -- 수동 승인 (AWAITING → PENDING · DENIED)
ALTER TABLE equipment_command ADD COLUMN approved_at TIMESTAMPTZ;
ALTER TABLE equipment_command ALTER COLUMN requested_by DROP NOT NULL; -- AUTO · RECOVERY 는 요청자 없음
ALTER TABLE equipment_command ADD COLUMN expires_at    TIMESTAMPTZ;
ALTER TABLE equipment_command ADD COLUMN result_detail JSONB;
CREATE INDEX ix_equipment_command_pending ON equipment_command (equipment_id) WHERE result = 'PENDING';

-- ---------------------------------------------------------------
-- equipment.fan_auto: 배기팬 자동 제어 스위치 (배기팬만 값, 나머지 NULL). 지금 있는 배기팬은 자동으로 시작
-- ---------------------------------------------------------------
ALTER TABLE equipment ADD COLUMN fan_auto BOOLEAN;
UPDATE equipment SET fan_auto = TRUE WHERE equipment_type = 'FAN';

-- ---------------------------------------------------------------
-- alarm_sensor_sample: 알림이 열려 있는 동안 그 관제 단위 센서 전부(IR 제외)의 5초 값 (알림 상세 그래프 · 센서값 CSV)
-- ---------------------------------------------------------------
CREATE TABLE alarm_sensor_sample (
    alarm_id   BIGINT      NOT NULL REFERENCES alarm (alarm_id) ON DELETE CASCADE,
    sensor_id  BIGINT      NOT NULL REFERENCES sensor (sensor_id),
    ts         TIMESTAMPTZ NOT NULL,
    value      DOUBLE PRECISION,
    PRIMARY KEY (alarm_id, sensor_id, ts)
);

-- 이미 있는 알림은 원인 이벤트 샘플을 옮겨 둔다 (다른 센서 값은 없음)
INSERT INTO alarm_sensor_sample (alarm_id, sensor_id, ts, value)
SELECT ae.alarm_id, e.sensor_id, s.ts, s.value
FROM alarm_event ae
JOIN sensor_event e ON e.event_id = ae.event_id
JOIN sensor_event_sample s ON s.event_id = e.event_id
JOIN sensor se ON se.sensor_id = e.sensor_id AND se.sensor_type <> 'IR'
ON CONFLICT DO NOTHING;

-- 알림 이력: 유형 + 기간
CREATE INDEX ix_alarm_hazard_raised ON alarm (hazard, raised_at DESC);
