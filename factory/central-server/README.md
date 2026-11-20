# Factory Central Server

공장 제어망 안에서 동작하는 ArgOS 중앙 서버입니다. 센서값을 받아 단계를 판정하고 이벤트·경보를 만듭니다.

## 책임 범위

- 7일치 더미데이터를 한 번 처리해서 DB 에 저장 (1분 추이, 과거 이벤트·경보)
- 실시간 센서값 판정 (시연 시작 2026-11-16 09:00 이후)
- 센서 임계값 판정, 센서 이벤트 → 관제 단위 경보 묶기
- 시연 시계 (발표 날짜와 상관없이 "그날은 11/16 09:00")
  - **(2026-11-20 추가) 시연 시계는 쓰지 않습니다.** "지금"은 항상 실제 서버 시각이고, 봇·VMS·엣지가 보낸 `ts` 도 쓰지 않고 받은 순간의 서버 시각으로 기록합니다. `sim-start`(11/16 09:00)는 7일치 더미 데이터가 끝나는 시각으로만 씁니다 (더미 가져오기·`/dev/sim/reset` 의 경계). 그래서 11/16 09:00 ~ 서버를 켠 시각 사이는 데이터가 없는 구간이고, `/dev/sim/reset` 은 시계를 되돌리지 않고 11/16 09:00 이후 판정 결과만 지웁니다. 아래에 "시연 시계"라고 적힌 곳은 서버 시계로 읽어 주세요.
- 사전 허용된 설비 명령의 검증 및 전달 (예정)
- 연계 서버에 상태·추이·이벤트 등 허용된 데이터 제공 (예정)

## 개발 환경

- Java 17, Spring Boot 3.4.5, Maven
- PostgreSQL 16 (스키마·시드는 Flyway 가 자동으로 만듦)
- 센서값 실시간 입력: Redis (DAS 담당 설계 후 연결 예정)

## 시작 방법

1. 중앙 DB 를 만듭니다.
   ```sql
   CREATE USER argos_factory WITH PASSWORD 'change-me';
   CREATE DATABASE argos_factory OWNER argos_factory;
   ```
2. `.env.example` 을 참고해 환경변수를 설정합니다. (IntelliJ 실행 설정의 Environment variables 에 넣어도 됩니다)
3. `factory/central-server` 폴더에서 실행합니다: `mvn spring-boot:run`
   - 처음 켜면 Flyway 가 `V1__central_schema.sql`, `V2__seed_ulsan_paint.sql` 을 실행합니다.
   - `ARGOS_HISTORY_IMPORT=true` 면 7일치를 가져옵니다 (약 157만 줄, 몇십 초). 이미 있으면 건너뜁니다.
   - 더미데이터 경로 기본값 `../raw-data/dummy-data` 는 `factory/central-server` 에서 실행할 때 기준입니다.
     다른 폴더에서 실행하면 `ARGOS_HISTORY_PATH` 를 절대 경로로 주세요.

확인: `GET /actuator/health`, `GET /api/v1/system/info`, `GET /api/v1/system/clock`

## 구조

```
com.gte.argos.factory
├── judge/        판정 엔진 (스프링 없는 순수 Java, 7일치·실시간·테스트가 같은 코드)
│   ├── JudgementEngine    센서값 → 1분 추이 · 센서 이벤트 · 관제 단위 경보
│   ├── SensorJudge        센서 하나의 연속 샘플 판정
│   ├── TrendAggregator    1분 평균·최소·최대
│   └── AlarmCorrelator    이벤트를 관제 단위 경보로 묶기, FIRE/GAS 결정
├── history/      7일치 CSV 병합 읽기 · 가져오기
├── ingest/       실시간 입구 (LiveJudgementService), 개발용 API
├── store/        판정 결과 DB 저장 (JdbcTemplate batch)
├── master/       DB → 엔진용 센서·기준값·관제 단위 읽기
├── common/time/  시연 시계
└── config/       argos.* 설정
```

## 판정 규칙

| 센서 | 주의 (2단계) | 경보 (3단계) | 지속 |
| --- | --- | --- | --- |
| 온도 TE | 설정값 +10 이상 | 설정값 +20 이상 | 주의 24샘플(2분), 경보 6샘플(30초) |
| 전류 IT | 정상 평균의 110% 이상 / 90% 이하 | 125% 이상 / 70% 이하 | 주의 24, 경보 6 |
| CO AT | 15 ppm 이상 | 30 ppm 이상 | 주의 24, 경보 12(60초) |
| 열화상 IR | 50 ℃ 이상 | 60 ℃ 이상 | 주의 24, 경보 6 |

- 이벤트 시작 시각 = 기준을 넘은 연속 구간의 첫 샘플, 끝 = 정상 6샘플(30초)이 이어진 구간의 첫 샘플
- 값이 수집 주기의 3배(15초) 넘게 비면 연속 카운트를 다시 셈
- 경보는 관제 단위(예: 상도 건조로 = 온도 + 배기팬 전류 + 버너실 CO + 열화상)마다 하나. 단계는 붙은 이벤트 중 최고
- 사고 종류: 온도·열화상이 있으면 FIRE, 없고 CO 가 있으면 GAS, 전류만이면 과전류 FIRE / 전류 저하(배기 약화) GAS
- 7일치는 이벤트가 끝나면 경보도 닫고(CLOSED), 실시간은 사람이 닫을 때까지 열어 둠
- 기준값은 `V2__seed_ulsan_paint.sql`. 바꾸려면 sensor_threshold 에 새 줄을 넣고 이전 줄 valid_to 를 채움

7일치 결과 (현재 더미데이터): 1분 추이 131,045줄, 경보 2건
- 11/11 14:22 중도 건조로 GAS 주의 (배기팬 전류 저하 IT-305 + CO 상승 AT-302)
- 11/13 10:43 상도 건조로 FIRE 주의 (건조로 온도 TE-305 152.6 ℃)

## 개발·시연용 API (`ARGOS_DEV_API=true` 일 때만)

| API | 설명 |
| --- | --- |
| `POST /api/v1/dev/readings` | 센서값 직접 넣기. `[{ "tag": "AT-301", "value": 18.5 }]` (ts 비우면 시연 시계 지금) |
| `POST /api/v1/dev/history-import?reset=true` | 7일치 다시 가져오기 |
| `POST /api/v1/dev/sim/reset` | 시연 리셋: 시계를 09:00 으로, 09:00 이후 이벤트·경보·명령·추이 삭제 |
| `POST /api/v1/dev/retention/run` | 보관 기간 지난 센서 값 지금 지우기 (평소에는 1시간마다 자동) |

## 데이터 보관 기간

센서 값은 정상값까지 전부 1분 추이(`sensor_trend`, 평균·최소·최대)로 저장해서 하루치 그래프를 그릴 수 있고,
**시연 시계 기준 7일**(`argos.retention.days`)이 지나면 하루 단위로 지웁니다 (오늘 0시 - 7일 이전) (`RetentionService`, 시작 1분 뒤부터 1시간마다).

| 데이터 | 보관 |
| --- | --- |
| `sensor_trend` (그래프용 1분 추이) | 7일 뒤 삭제 |
| `sensor_event_sample` (경보 구간 원본 샘플) | 7일 뒤 삭제 |
| `alarm`, `sensor_event` (경보·이벤트 이력) | 계속 보관 (통계·이력 최대 1년, 월간 보고서) |
| `file_asset` (경보 시작 이미지, VMS 연동 후) | 경보와 함께 계속 보관 |

- 실제 시각이 아니라 시연 시계 기준이라, 7일치 시연 데이터(11/09 09:00 ~)는 지워지지 않습니다.
- 5초 원본 값은 DB 에 저장하지 않고 메모리에 최근 1시간만 둡니다 (설비 상세 '최근 1시간' 그래프).

## 연계 서버 전용 API (`/api/v1/internal/**`)

연계 서버가 HTTP 로 불러 화면용 모양으로 바꿉니다. 화면은 직접 부르지 않습니다.
`ARGOS_INTERNAL_API_KEY` 를 설정하면 `X-Argos-Internal-Key` 헤더가 맞아야 합니다 (비우면 검사 안 함).
단계는 숫자(1 정상, 2 주의, 3 경보), 센서 종류는 DB 값(TE/IT/AT/IR) 그대로 줍니다.
시각 파라미터를 비우면 시연 시계 기준 오늘 0시 ~ 지금입니다.

| API | 설명 |
| --- | --- |
| `GET /internal/master` | 사업장·공장·공정·설비·센서(지금 기준값 포함)·관제 단위 |
| `GET /internal/status?processId=` | 센서별 지금 값·단계, 설비별 단계·운전 상태, 범위별 단계(`levels.plants/factories/processes/units`) |
| `GET /internal/series?sensorIds=305,317&from=&to=&step=` | 추이. step 5s(최근 1시간, 실시간 메모리) / 1m / 10m, 비우면 기간에 맞춰 자동. 최대 31일 |
| `GET /internal/events?from=&to=&sensorId=&equipmentId=&processId=&openOnly=` | 센서 이벤트 (판정 당시 기준값 포함) |
| `GET /internal/events/{id}/samples` | 이벤트 구간 5초 값 |
| `GET /internal/alarms?from=&to=&status=OPEN,ACKED&minStage=&unitId=&processId=&factoryId=&plantId=&page=&size=` | 경보 목록 (원인 이벤트, `valueStillOver` 포함) |
| `GET /internal/alarms/{id}` | 경보 한 건 |
| `POST /internal/alarms/{id}/ack` `{ "userId": 7 }` | 확인 (OPEN → ACKED). 이미 확인됐으면 409 `ALARM_ALREADY_ACKED` |
| `POST /internal/alarms/{id}/acting` `{ "userId": 7 }` | 조치 중 (→ ACTING) |
| `POST /internal/alarms/{id}/close` `{ "userId": 7 }` | 종료 (→ CLOSED). 진행 중 이벤트가 있으면 409 `VALUE_STILL_OVER` + `details.sensors`. 종료 사유 `closeReason`: 조치 중(ACTING)이었으면 `ACTION_DONE`, 아니면 `MANUAL` (7일치는 `AUTO_RECOVERED`). 경보 중 주파수를 바꾼 배기팬은 40Hz 로 복귀 명령(`trigger: RECOVERY`) |
| `GET /internal/feed?after=0&limit=500` | 실시간 변화. 연계 서버가 1초마다 `after=next` 로 가져가 SSE 로 전달 |

설비 명령 (연계 서버 → 중앙)

| API | 설명 |
| --- | --- |
| `POST /internal/equipment/{id}/commands` `{ "command": "FAN_HZ", "value": 50, "userId": 7 }` | 202 `{ commandId, status: "SENT" \| "DUPLICATE", value, trigger }`. 명령: `FAN_HZ`(value 20~60 Hz, 평상시 40)·`FAN_STOP`·`SHUTDOWN`·`RESTART` |
| `GET /internal/commands/{id}` | 명령 한 건 |

실제 설비가 없어 응답은 시뮬레이션입니다 (팬 1초, 셧다운·재가동 3초 뒤 완료). 셧다운은 `RUN → STOPPING → STOPPED`,
배기팬은 `fan_rpm` = 주파수 × 30 (40Hz = 1200rpm). 진행은 피드 `COMMAND_RESULT`(SENT → DONE · FAILED · EXPIRED · CANCELLED). 센서값은 바꾸지 않습니다 (값 회복은 봇에 직접 명령).
- DB 결과(`equipment_command.result`): `PENDING`(API 에서는 SENT) → `DONE`·`FAILED`·`EXPIRED`(30초 `expires_at` 지남)·`CANCELLED`(셧다운하면 처리 중 팬 명령 취소). 같은 설비에 같은 명령·값이 처리 중이면 `DUPLICATE` 로 기록만 하고 실행 안 함
- `result_detail`: `{ targetHz, outputHz, message }`
- `SHUTDOWN`·`RESTART` 는 테이블 명세서 v5 에 없지만 경보 셧다운 시연에 써서 남겨 둠
오류: `COMMAND_NOT_ALLOWED`(예전 `FAN_ON`·`FAN_OFF`·`FAN_RPM` 포함), `NOT_A_FAN`, `HZ_OUT_OF_RANGE`, `EQUIPMENT_STOPPED`, `ALREADY_STOPPED`, `ALREADY_RUNNING`.

기준값 (연계 서버 → 중앙)

| API | 설명 |
| --- | --- |
| `GET /internal/equipment/{id}/thresholds` | 센서별 계절 기준(`seasons`), `legal`, `activeSeason`, `version`(지금 기준 threshold_id 중 최댓값) |
| `PUT /internal/equipment/{id}/thresholds` `{ season, version, reason, userId, sensors: [{ sensorId, warn, alarm, lowWarn, lowAlarm, warnSamples, alarmSamples }] }` | 이전 행은 `valid_to` 로 닫고 새 행 추가(basis `PROPOSED`), 판정 엔진에 바로 반영(진행 중 판정 유지). 피드 `THRESHOLD_CHANGED`. 오류 `REASON_REQUIRED`, 409 `THRESHOLD_CONFLICT`, `SENSOR_NOT_IN_EQUIPMENT`, `WARN_NOT_BELOW_ALARM`, `LOOSER_THAN_LEGAL` |
| `POST /internal/thresholds/simulate` | 지난 N일 1분 추이(최소·최대)로 지금·제안 기준 발생 건수 비교 |
| `GET /internal/thresholds/history?equipmentId=&sensorId=&from=&to=` | 변경 전·후 |
| `PUT /internal/sensors/{id}` `{ sampleSec, active }` | 센서 수집 주기·사용 여부 |

기준값은 시연 시계 날짜의 계절 행이 우선이고, 없으면 `ALL` 행을 씁니다 (`Seasons`).

공장 관리 (연계 서버 → 수집 서버, H1)

| API | 설명 |
| --- | --- |
| `POST /internal/plants` · `/internal/factories` · `/internal/processes` | 사업장·공장·공정 추가. 이름·코드가 겹치면 409 `CODE_TAKEN` |
| `GET /internal/processes/{id}/layout-meta` | `version`, `floorplan`, 설비별 `rot` |
| `PUT /internal/processes/{id}/layout` | 설비·센서 추가·수정·삭제를 한 트랜잭션으로. 버전 다르면 409 `LAYOUT_CONFLICT`, `TAG_TAKEN`, `SENSOR_HAS_OPEN_ALARM`. 저장하면 판정 엔진 다시 불러옴 |
| `PUT /internal/processes/{id}/floorplan` `{ url, widthM, depthM }` | 배치도 정보 |

바뀌면 피드 `MASTER_CHANGED` `{ what, id }`. 테이블 변경: `V3__process_layout.sql` (process.layout_version · floorplan_*, equipment.rot)

피드 항목 `type`: `SENSOR_VALUES`(`values: [[sensorId, ts, value], ...]`), `EVENT_OPENED`, `EVENT_ESCALATED`,
`EVENT_CLOSED`, `ALARM_OPENED`, `ALARM_UPDATED`, `ALARM_CLEARED`(이벤트가 모두 끝남), `ALARM_STATUS`(확인·조치·종료), `COMMAND_RESULT`, `THRESHOLD_CHANGED`, `MASTER_CHANGED`, `RESET`.
`bootId` 가 바뀌면 중앙 서버가 다시 켜진 것이므로 `after=0` 부터, `gap=true` 면 `status`·`alarms` 를 다시 조회합니다.

## ERD V6 반영 (`V4__erd_v6.sql`)

`docs/erd/ArgOS_테이블명세서_v5.xlsx` 기준. 판정은 지금처럼 중앙 서버가 합니다 (v5 의 Node-RED 엣지·Kafka 는 범위 밖). 컬럼·허용 값만 맞췄습니다.

| 테이블 | 바뀐 것 |
| --- | --- |
| `sensor_event` | `edge_key`(공장 코드-태그-시작 시각, 예 `P5-TE-305-20261116T090000`, UNIQUE), `max_stage`, `rule` = `HI`·`LO`·`AVG`·`IMMEDIATE`·`IR` (예전 `DURATION` → 하한 이벤트 `LO`, 나머지 `HI`), `roi`·`image_file_id`·`video_file_id`·`plc_ok`(비워 둠). API 이벤트에 `maxStage`·`edgeKey` 추가 |
| `sensor_threshold` | `release_samples`(복귀 연속 수), `release_margin`(복귀 여유), `alarm_avg_sec`(예약), `immediate_value`(이 값 이상이면 바로 경보, 규칙 `IMMEDIATE`). 기본값 2단계 6 · 3단계 3 · 복귀 6 · 여유 0.05. **기존 행은 지속 횟수 그대로(24·6), 복귀 여유 0** 이라 판정이 바뀌지 않음. 기준값을 고쳐 저장하면 복귀 값들은 이전 행을 그대로 이어 받음 |
| `sensor_trend` | `lost_n`(통신 끊김 수, 지금 입력에 품질 값이 없어 0) |
| `sensor_daily` | 새 테이블. 1분 추이를 하루로 집계(샘플 수 가중 평균·최소·최대·n·lost_n). 10분마다 어제·오늘, 7일치 가져오기 뒤 전체, 1분 추이를 지우기 전에 그 날짜를 집계. 일 집계는 지우지 않음. 리셋하면 11/16 이후를 지우고 11/16 을 다시 집계 |
| `alarm` | `close_reason` (`AUTO_RECOVERED`·`ACTION_DONE`·`MANUAL`). API 경보에 `closeReason` |
| `equipment_command` | 명령 `FAN_HZ`·`FAN_STOP`, 결과 7가지, `trigger`(`APPROVED`·`RECOVERY`), `expires_at`, `result_detail`. 예전 행은 `FAN_RPM` → `FAN_HZ`(rpm÷30), `OK` → `DONE`, `FAIL` → `FAILED` |

명세서와 다르게 둔 것: 컬럼 길이(코드 varchar(20) 등 지금이 더 김), `sensor_trend.n`·`sample_sec` 은 int, `SHUTDOWN`·`RESTART` 명령.

## 아직 안 한 것

- Redis 입력 어댑터 (봇 → 중앙). `LiveJudgementService.ingest()` 를 부르면 됨
- 시연 시계 기준값을 Redis 로 공유 (봇·연계 서버와 시각 맞추기)
- 설비 명령을 봇(미니 PC)에 실제로 전달 (Redis 설계 후)
- 화재 판정 (화재 시나리오 데이터가 오면)
- 서버 재시작 때 진행 중이던 실시간 이벤트 이어 받기 (지금은 리셋으로 정리)

## 2026-11-19 최신 DDL 반영 (배기팬 자동 제어 · 통계·이력)

`V4__erd_v6.sql` 에 더한 것 (`db/ArgOS ERD V6.sql`, 기본값·허용 값은 `docs/erd/argos_ddl_v5_postgresql.sql`):

| 대상 | 내용 |
| --- | --- |
| `equipment_command` | `trigger` 대신 `source`(USER · AUTO · RECOVERY), `approved_by`·`approved_at`, 결과 `AWAITING`·`DENIED` 추가. 셧다운 시연용 SHUTDOWN · RESTART 는 남김 |
| `equipment.fan_auto` | 배기팬 자동 제어 스위치. 지금 배기팬 3대는 true |
| `alarm_sensor_sample` | 알림이 열려 있는 동안 관제 단위 센서 전부(IR 제외)의 5초 값. 기존 알림은 원인 이벤트 샘플을 옮겨 둠 |
| `alarm (hazard, raised_at)` 인덱스 | 알림 이력 유형 거르기 |

- 배기팬 자동 제어 `FanAutoService`: 실시간 가스 알림 주의 45Hz · 경보 60Hz (`fan_auto` 인 팬, 이미 그 이상이면 안 보냄). 7일치 가져오기에서는 보내지 않음
- 명령 `result_detail` 에 `beforeRpm` (바꾸기 전 회전수). 피드 `COMMAND_RESULT` 에 `alarmId`·`source`
- 수동 승인(AWAITING → PENDING · DENIED)은 칸만 있고 흐름은 없음 (웹에 승인 버튼 없음, 수동이면 안내만)
- 보관: 1분 추이 · 이벤트 샘플 · 알림 센서 샘플 **31일** (`argos.retention.keep-days`, `ARGOS_RETENTION_KEEP_DAYS`, 기본 31. 통계·이력 조회가 30일까지라서). 예전 `days`(7) 는 안 씀. 경보·이벤트·명령 이력은 지우지 않음
- V6: `sensor.stream_url` (VMS 실시간 영상 주소, `PUT /internal/sensors/{id}` `{ streamUrl }` 로 넣음, master 의 `sensors[].streamUrl`). 영상 파일은 저장하지 않음 (`sensor_event.video_file_id` 없음)

| API | 내용 |
| --- | --- |
| `GET /api/v1/internal/alarms?hazard=` | 유형 조건 |
| `GET /api/v1/internal/alarms/{id}` 등 알림 응답 | `fanAuto` 추가 (가스 알림만) |
| `GET /api/v1/internal/alarms/{id}/series` | 알림 상세 그래프. 30분 이하 5초(`alarm_sensor_sample`), 넘으면 1분 평균 |
| `PATCH`(또는 `POST`) `/api/v1/internal/equipment/{id}/settings` | `{ active?, fanAuto? }` 바로 저장, 배치 판 번호 안 올림. 배기팬 없으면 400 `NO_FAN` |
| `GET /api/v1/internal/master` 의 `equipment[].fanAuto` | 배기팬 자기 값, 배기팬이 달린 건조로는 그 팬 값, 나머지 null |

## VMS 열화상 받기 (2026-11-19, VMS 전송 규격 IR-301 시험용)

| API | 내용 |
| --- | --- |
| `POST /api/v1/vms/ir` (JSON) | ① 평소 값 1초마다 `{ ts, type, serial, value, unit, quality, stage }` |
| `POST /api/v1/vms/ir` (multipart) | ② 단계 진입: `meta`(① + `roi`) + `snapshot`(jpeg) |
| `GET /api/v1/internal/files/{fileId}` | 저장한 사진 (연계 서버 `/thermal/images/{id}` 가 거쳐 감) |

- 인증 없음 (시험용). 응답 200 이면 정상, 형식 오류는 400 `VALIDATION_FAILED`, 모르는 serial 은 404
- 단계는 VMS 판정 그대로: 중앙은 다시 판정하지 않고 받은 단계로 이벤트(`rule = IR`)·경보(FIRE)를 만든다. 1 이 오면 이벤트 끝. 3 → 2 는 무시
- 값은 1분 추이·실시간 현재값으로. 1초 원시값 테이블은 없다
- 사진: `ARGOS_FILES_DIR`(기본 `./data/files`)/thermal/날짜/ 에 저장, `file_asset`(THERMAL) → `sensor_event.image_file_id` · `roi`, `alarm.thermal_file`
- ② 가 유실돼도 ① 의 stage 로 이벤트·경보를 만든다 (사진만 없음). `ts` 는 시간대 없으면 한국 시각, `+09:00` 도 받음
- 기준값 화면 '변경 시뮬레이션' 응답에 `peaks [{ at, value }]` 추가

## 배기팬 수동 승인 (2026-11-19)

- `fan_auto = false` 인 배기팬은 가스 알림 때 `equipment_command` 에 `result = AWAITING`(source AUTO, 요청자 없음)만 남긴다. 경보로 올라가면 같은 명령의 목표만 60 Hz 로
- `POST /api/v1/internal/commands/{id}/approve` `{ userId }` → `approved_by`·`approved_at`, PENDING 으로 실행 (1초 뒤 DONE)
- `POST /api/v1/internal/commands/{id}/deny` `{ userId }` → DENIED. 승인 대기가 아니면 409 `COMMAND_NOT_AWAITING`
- 알림을 종료하면 그 알림의 승인 대기 명령은 CANCELLED
- 피드 `COMMAND_RESULT` 에 `approvedBy`, 알림 `fanAuto` 에 `commandId`, 상태 `AWAITING`·`DENIED`

## Redis 연동 — 데이터 수집 서버 ↔ 중앙 서버 (2026-11-20 아키텍처, A단계)

`docs/아키텍쳐.png` 기준 이름: **이 서버 = 데이터 수집 서버**, 연계 서버(`gateway/backend`) = **중앙 서버**.
`ARGOS_REDIS_ENABLED=true` 일 때만 Redis 에 연결한다. 기본값 `false` 면 지금처럼 HTTP 만 쓴다 (두 서버 같은 값으로).

| 방향 | 키 | 종류 | 내용 |
| --- | --- | --- | --- |
| 수집 → 중앙 | `argos:feed` | 스트림 (2만 건) | 실시간 변화. 번호 `{bootMs}-{seq}`, 필드 `bootId` `seq` `type` `at` `data`(JSON). `type` 은 `/internal/feed` 와 같음 |
| 수집 → 중앙 | `argos:feed:meta` | 해시 | `bootId`, `bootMs`, `latest`. 수집 서버가 다시 켜지면 `bootId` 가 바뀌고 중앙은 화면에 `reset` |
| 수집 → 중앙 | `argos:live` | 해시 | 필드 `sensorId` → `{"ts":"…+09:00","value":…}` 센서별 지금 값 |
| 수집 → 중앙 | `argos:raw:{sensorId}` | 스트림 (3,600건) | 최근 1시간 1초 값 (`ts`, `value`) |
| 중앙 → 수집 | `argos:cmd` | 스트림, 소비 그룹 `collector` | 제어 명령 `requestId` `op` `payload`(JSON) `sentAt`(epoch ms) |
| 수집 → 중앙 | `argos:reply:{requestId}` | 리스트 (60초) | 응답 1건. HTTP 와 같은 `{success, data}` · `{success:false, error{status, code, message, details}}` |

제어 명령 `op`

| op | payload | HTTP 로 하던 것 |
| --- | --- | --- |
| `COMMAND` | `equipmentId, command, value?, alarmId?, userId?` | `POST /internal/equipment/{id}/commands` |
| `APPROVE` | `commandId, userId` | `POST /internal/commands/{id}/approve` |
| `DENY` | `commandId, userId` | `POST /internal/commands/{id}/deny` |

- 중앙은 응답을 10초 기다리고, 없으면 504 `UPSTREAM_TIMEOUT`. 수집 서버는 보낸 지 15초 지난 요청은 실행하지 않고 504 `REQUEST_EXPIRED`
- 수집 서버의 4xx 는 같은 상태·코드로 화면까지 간다 (예: 409 `COMMAND_NOT_AWAITING`)
- Redis 가 잠깐 꺼지면 그동안의 변화는 버리고, 다시 붙으면 중앙이 번호가 빈 것을 보고(gap) 화면에 `reset`
- 조회(master · status · series · alarms · 통계 등)와 확인·종료·기준값·배치 저장은 아직 HTTP (B·C단계에서 옮김)
- 판정은 지금처럼 이 서버도 한다 (DAS 판정과 별개로 관제 단위 조합 판정, 1초 → 1분 집계). Kafka 입력은 메시지 예시를 받으면 `LiveJudgementService.ingest()` 에 붙인다

로컬 Redis

```bash
docker run -d --name argos-redis -p 6379:6379 redis:7.4-alpine
# 확인: docker exec argos-redis redis-cli XLEN argos:feed
```

| 환경변수 | 기본값 |
| --- | --- |
| `ARGOS_REDIS_ENABLED` | `false` |
| `REDIS_HOST` · `REDIS_PORT` · `REDIS_PASSWORD` | `localhost` · `6379` · (없음) |

## 배기팬 기준 주파수 (2026-11-20 변경)

정상 30Hz (900rpm) · 주의 45Hz · 경보 60Hz (`EquipmentCommandService.HZ_NORMAL`, `FanAutoService.HZ_WARN · HZ_ALARM`).
경보 종료 복귀와 시연 리셋이 30Hz 로 돌아간다. 위에 '평상시 40Hz' 라고 적힌 곳은 이 값으로 읽는다. DB 는 `V6__fan_normal_30hz.sql`.
