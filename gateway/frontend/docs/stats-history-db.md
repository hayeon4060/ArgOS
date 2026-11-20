# 통계·이력 관리 개편 — DB 변경 사항

2026-11-19 프론트 개편에 맞춘 DB 정리입니다. API 는 `stats-history-api.md` 를 보세요.
기준 DDL: `db/argos_ddl.sql`, 중앙 서버 `factory/central-server/.../V1__central_schema.sql`

## 요약

| # | 구분 | 테이블 | 내용 |
|---|------|--------|------|
| 1 | 그대로 씀 | `alarm.hazard` | 유형(화재·가스)은 이미 있는 `hazard ('FIRE','GAS')` 를 씀. **새 컬럼 없음** |
| 2 | **신규** | `alarm_sensor_sample` | 알림이 열려 있는 동안 그 설비 센서 전부의 5초 값 (상세 그래프·센서값 CSV) |
| 3 | 인덱스 추가 | `alarm` | 유형 거르기 + 기간 조회용 `(hazard, raised_at)` |
| 4 | 보관 기간 확인 | `sensor_trend`, `alarm_sensor_sample` | 조회가 최대 30일이라 최소 31일만 남기면 됨 |

---

## 1. 유형 = `alarm.hazard` (변경 없음)

```sql
hazard varchar(4) NOT NULL CHECK (hazard IN ('FIRE', 'GAS'))
```

- 화면 이름: `FIRE` = 화재, `GAS` = 가스.
- 정하는 기준(중앙 서버가 알림을 만들 때): CO 센서로 난 알림 → `GAS`, 전류·온도·열화상 → `FIRE`.
  지금 중앙 서버가 이렇게 넣고 있는지만 확인해 주세요.

## 2. 신규 테이블 `alarm_sensor_sample`

알림 상세 패널 그래프는 **센서가 기준을 처음 넘은 시각부터 조치 완료(해제)까지** 그립니다.
경보는 1~5분 만에 해제되는 경우가 많아 1분 평균(`sensor_trend`)으로는 점이 몇 개뿐입니다.
그래서 알림이 열려 있는 동안만 그 설비 센서 전부(전류·온도·CO)의 5초 값을 남깁니다.

- 지금 있는 `sensor_event_sample` 은 이벤트를 만든 센서 1개만 남기고 '선택'이라, 나머지 두 센서 값이 없습니다.

```sql
-- 알림 센서 샘플: 알림이 열려 있는 동안(처음 기준 초과 ~ 해제) 그 관제 단위 센서 전부의 5초 값.
-- 통계·이력 알림 상세 그래프와 '센서값 내보내기' CSV 에 씀. 월별 RANGE 파티션(ts) 권장.
CREATE TABLE alarm_sensor_sample (
    alarm_id             bigint NOT NULL,
    sensor_id            int NOT NULL,
    ts                   timestamptz NOT NULL,
    value                numeric,
    CONSTRAINT pk_alarm_sensor_sample PRIMARY KEY (alarm_id, sensor_id, ts)
);
-- FK: alarm_id → alarm(alarm_id), sensor_id → sensor(sensor_id)
```

넣는 시점 (데이터 수집 서버 또는 중앙 서버):

1. 알림이 생기면(`alarm` INSERT) 그 관제 단위 센서 목록을 잡아 둠 (IR 제외)
2. 알림이 `CLOSED` 될 때까지 5초 값이 들어올 때마다 위 테이블에 INSERT
3. 시작 값: 알림 센서 이벤트의 `sensor_event.started_at` 부터 (이미 지난 몇 초는 `sensor_event_sample` 에서 옮겨 와도 됨)

양: 알림 1건이 10분이면 센서 3개 × 120 = 360행. 한 달 90건이면 3만 행 정도라 부담이 적습니다.

API 에서 쓰는 법:
- `closed_at - started_at` 이 30분 이하 → 이 테이블(5초)
- 30분 넘음 → `sensor_trend` 1분 평균 (`central.series(..., "1m")`)

## 3. 인덱스 추가

```sql
-- 알림 이력 표: 기간 + 유형 거르기 (GET /stats/alarms?hazard=, /stats/export?hazard=)
CREATE INDEX ix_alarm_hazard_raised ON alarm (hazard, raised_at DESC);
```

`alarm (raised_at)` 인덱스가 이미 있으면 그걸로도 충분할 수 있습니다. 데이터가 적은 지금은 선택입니다.

## 4. `sensor_trend` 보관 기간

- 통계·이력은 **한 번에 최대 30일**까지 조회합니다(`RANGE_TOO_LARGE`, 2026-11-19 1년 → 30일로 바꿈).
- 30분이 넘는 알림의 상세 그래프는 `sensor_trend` 1분 평균, 30분 이하는 `alarm_sensor_sample` 5초 값으로 그립니다.
- 오늘 기준 30일 전 알림까지 상세 그래프가 나오려면 두 테이블 모두 **최소 31일**(달 경계 여유를 두면 2개월 파티션) 남기면 됩니다.
- 요약 카드의 '이전 기간보다'는 `alarm` 테이블만 쓰므로 센서 값 보관 기간과는 상관없습니다.

### 4-1. 보관 기간 이후 정리 (제안)

```sql
-- 월별 파티션이면: 2개월 지난 파티션을 DETACH 후 DROP
-- 파티션이 없으면 (매일 새벽 한 번)
DELETE FROM alarm_sensor_sample WHERE ts < now() - interval '31 days';
```

## 그대로 쓰는 테이블 (참고)

| 화면 | 테이블 |
|------|--------|
| 조치 이력 | `alarm` (acked_at, closed_at) + `alarm_action` |
| 체크리스트 | `checklist` + `checklist_item` (seq, content, done_at, result, memo) |
| 열화상 1장 | `alarm.thermal_file` → `file_asset` |
| 기준값(그래프 점선) | `sensor_threshold` (알림 시각에 적용 중이던 행) |
| 셧다운 단계 | `equipment_command` / `alarm_action` (SHUTDOWN) |
