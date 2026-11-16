# Factory Central Server

공장 제어망 안에서 동작하는 ArgOS 중앙 서버입니다. 센서값을 받아 단계를 판정하고 이벤트·경보를 만듭니다.

## 책임 범위

- 7일치 더미데이터를 한 번 처리해서 DB 에 저장 (1분 추이, 과거 이벤트·경보)
- 실시간 센서값 판정 (시연 시작 2026-11-16 09:00 이후)
- 센서 임계값 판정, 센서 이벤트 → 관제 단위 경보 묶기
- 시연 시계 (발표 날짜와 상관없이 "그날은 11/16 09:00")
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
| `POST /internal/alarms/{id}/close` `{ "userId": 7 }` | 종료 (→ CLOSED). 진행 중 이벤트가 있으면 409 `VALUE_STILL_OVER` + `details.sensors` |
| `GET /internal/feed?after=0&limit=500` | 실시간 변화. 연계 서버가 1초마다 `after=next` 로 가져가 SSE 로 전달 |

설비 명령 (연계 서버 → 중앙)

| API | 설명 |
| --- | --- |
| `POST /internal/equipment/{id}/commands` `{ "command": "SHUTDOWN", "alarmId": 1051, "userId": 7 }` | 202 `{ commandId, status: "SENT" }`. 명령: `FAN_ON`·`FAN_OFF`·`FAN_RPM`(value 600~1800)·`SHUTDOWN`·`RESTART` |
| `GET /internal/commands/{id}` | 명령 한 건 |

실제 설비가 없어 응답은 시뮬레이션입니다 (팬 1초, 셧다운·재가동 3초 뒤 완료). 셧다운은 `RUN → STOPPING → STOPPED`,
배기팬은 `fan_rpm` 이 바뀝니다. 진행은 피드 `COMMAND_RESULT`(SENT → DONE · FAILED). 센서값은 바꾸지 않습니다 (값 회복은 봇에 직접 명령).
오류: `COMMAND_NOT_ALLOWED`, `NOT_A_FAN`, `RPM_OUT_OF_RANGE`, `EQUIPMENT_STOPPED`, `ALREADY_STOPPED`, `ALREADY_RUNNING`.

기준값 (연계 서버 → 중앙)

| API | 설명 |
| --- | --- |
| `GET /internal/equipment/{id}/thresholds` | 센서별 계절 기준(`seasons`), `legal`, `activeSeason`, `version`(지금 기준 threshold_id 중 최댓값) |
| `PUT /internal/equipment/{id}/thresholds` `{ season, version, reason, userId, sensors: [{ sensorId, warn, alarm, lowWarn, lowAlarm, warnSamples, alarmSamples }] }` | 이전 행은 `valid_to` 로 닫고 새 행 추가(basis `PROPOSED`), 판정 엔진에 바로 반영(진행 중 판정 유지). 피드 `THRESHOLD_CHANGED`. 오류 `REASON_REQUIRED`, 409 `THRESHOLD_CONFLICT`, `SENSOR_NOT_IN_EQUIPMENT`, `WARN_NOT_BELOW_ALARM`, `LOOSER_THAN_LEGAL` |
| `POST /internal/thresholds/simulate` | 지난 N일 1분 추이(최소·최대)로 지금·제안 기준 발생 건수 비교 |
| `GET /internal/thresholds/history?equipmentId=&sensorId=&from=&to=` | 변경 전·후 |
| `PUT /internal/sensors/{id}` `{ sampleSec, active }` | 센서 수집 주기·사용 여부 |

기준값은 시연 시계 날짜의 계절 행이 우선이고, 없으면 `ALL` 행을 씁니다 (`Seasons`).

피드 항목 `type`: `SENSOR_VALUES`(`values: [[sensorId, ts, value], ...]`), `EVENT_OPENED`, `EVENT_ESCALATED`,
`EVENT_CLOSED`, `ALARM_OPENED`, `ALARM_UPDATED`, `ALARM_CLEARED`(이벤트가 모두 끝남), `ALARM_STATUS`(확인·조치·종료), `COMMAND_RESULT`, `THRESHOLD_CHANGED`, `RESET`.
`bootId` 가 바뀌면 중앙 서버가 다시 켜진 것이므로 `after=0` 부터, `gap=true` 면 `status`·`alarms` 를 다시 조회합니다.

## 아직 안 한 것

- Redis 입력 어댑터 (봇 → 중앙). `LiveJudgementService.ingest()` 를 부르면 됨
- 시연 시계 기준값을 Redis 로 공유 (봇·연계 서버와 시각 맞추기)
- 설비 명령을 봇(미니 PC)에 실제로 전달 (Redis 설계 후)
- 화재 판정 (화재 시나리오 데이터가 오면)
- 서버 재시작 때 진행 중이던 실시간 이벤트 이어 받기 (지금은 리셋으로 정리)
