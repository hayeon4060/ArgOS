# 연계 서버 구현 API (로그인 외)

API 명세서 기준으로 구현한 것과, 명세서와 다르게 한 점입니다. 주소는 모두 `/api/v1` 아래입니다.
화면이 부르는 API는 전부 로그인 토큰이 필요하고, 서버가 요청마다 권한·담당 범위를 확인합니다.

## 구조

```
웹·앱 ──REST/SSE──▶ 연계 서버 ──HTTP──▶ 중앙 서버 (/api/v1/internal/**)
                     │                    마스터·현재 상태·추이·이벤트·경보 원본, 설비 명령
                     └─ 연계 DB: 사용자·로그인(V1), 조치 이력·체크리스트(V2)
```

- 사업장·공장·공정·설비·센서 마스터는 연계 DB 에 두지 않고 중앙에서 받아 30초 캐시합니다 (`MasterCache`).
- "지금"은 중앙 서버의 시연 시계를 따릅니다 (`SimTime`, 그날은 2026-11-16 09:00).
- 실시간은 중앙 피드를 1초마다 가져와 SSE 로 보냅니다 (`FeedPoller`).
- 화면 값으로 바꿔서 줍니다: 단계 1/2/3 → `NORMAL/WARN/ALARM`, 센서 TE/IT/AT/IR → `TEMP/CURRENT/CO/IR`, 운전 RUN → `RUNNING`.

## 설정

| 환경변수 / 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `argos.central.base-url` (`CENTRAL_SERVER_BASE_URL`) | `http://localhost:8081` | 중앙 서버 주소 |
| `argos.central.api-key` (`CENTRAL_API_KEY`) | 비어 있음 | 중앙 서버 `ARGOS_INTERNAL_API_KEY` 와 같은 값 |
| `argos.central.feed-enabled` | `true` | 실시간 피드 가져오기 (테스트에서는 꺼도 됨) |

## API

| 화면 | API | 권한 | 비고 |
| --- | --- | --- | --- |
| 공통 | `GET /system/clock` | 로그인 | 시연 시계. 화면의 "오늘"은 이 값 기준 |
| A2 | `GET /sites` | 본사 | |
| A2 | `GET /sites/{id}/tree` | 본사 | 공장마다 `todayAlarmCount` 추가 |
| B1 | `GET /sites/{id}/overview` | 본사·공장 | 공장마다 `accessible`(내가 들어갈 수 있는지) 추가 |
| H1 | `GET /factories?siteId=` · `GET /processes?factoryId=` | 본사·공장 | 담당 범위만 |
| B2 | `GET /factories/{id}/overview` | 본사·공장 | `equipmentLevels`, `siteId`, `siteName` 추가 |
| B3 | `GET /processes/{id}/layout` | 전체 | `version` 은 1 고정, `floorplan` 은 null (파일 없음) |
| B3 | `GET /processes/{id}/status` | 전체 | 센서마다 `lowWarn`·`lowAlarm` 추가 (전류는 아래쪽 기준도 있음) |
| B4 | `GET /equipment/{id}` | 전체 | 센서 게이지 `max` 는 경보 기준 × 1.3 |
| B4 | `GET /equipment/{id}/events?date=&type=` | 전체 | ALARM(시작·해제), ACTION(조치 이력). THRESHOLD 는 아직 (변경 이력은 `/thresholds/history`) |
| B4 | `GET /sensors/series?ids=&from=&to=&step=&compare=yesterday` | 전체 | 최근 1시간은 5초, 그 이상은 1분·10분 |
| B1 | `GET /alarms?siteId·factoryId·processId&date=&from=&to=&status=&minLevel=&page=&size=` | 전체 | 줄마다 `checklist`(진행), `assignee`, `actionMinutes`, `valueStillOver` 추가 |
| B1 | `GET /alarms/hourly?siteId·factoryId&date=` | 본사·공장 | |
| B1 | `GET /alarms/{id}` | 전체 | 조치 내역 timeline |
| C1 | `GET /alarms/{id}/detail` | 전체 | `thermal` 은 null (열화상 그림 없음), `templateId` 추가 |
| C4 | `GET /alarms/{id}/response` | 전체 | `fireId` 는 null (화재 판정 전) |
| C0 | `POST /alarms/{id}/ack` | 공장 | 409 `ALARM_ALREADY_ACKED` (`error.ackedBy`) |
| C4 | `POST /alarms/{id}/close` `{ result, followUp }` | 공장 | 409 `VALUE_STILL_OVER` (`error.sensors`) |
| I1 | `GET /notifications/summary` | 전체 | 채팅·화재는 아직 0 / null |
| C1 | `GET /checklist-templates?equipmentType=&sensorType=` | 본사·공장 | |
| C1 | `GET /processes/{id}/workers` | 본사·공장 | `onDuty` 는 항상 true (근무조 정보 없음) |
| C1 | `POST /checklists` | 공장 | 푸시 실패해도 201, `push: "FAILED"` (명세서는 502) |
| I1 | `GET /checklists?status=&factoryId=` | 본사·공장 | |
| C2 | `GET /checklists/{id}` | 전체 | 공정 담당자는 자기 것만 |
| C2 | `POST /checklists/{id}/remind` | 공장 | 30초 잠금, 429 `REMIND_COOLDOWN` |
| C2 | `PATCH /checklists/{id}/assignee` | 공장 | |
| C2 | `POST /checklists/{id}/confirm` | 공장 | 값이 아직 기준 위면 체크리스트는 확인, 알림은 열린 채 409 `VALUE_STILL_OVER` |
| M2 | `GET /me/checklists?status=ACTIVE·DONE` | 공정 | |
| M2 | `PATCH /checklists/{id}/items/{itemId}` | 공정 | |
| M2 | `POST /checklists/{id}/report` | 공정 | 409 `ITEMS_NOT_DONE` |
| M1 | `POST /me/push-token` · `DELETE /me/push-token` | 로그인 | |
| B3 | `POST /equipment/{id}/fan` `{ power }` 또는 `{ rpm }` | 공장 | 202, 결과는 SSE `command.result` |
| C4 | `POST /equipment/{id}/shutdown` `{ alarmId, reason }` | 공장 | 202 |
| C4 | `POST /processes/{id}/shutdown` `{ alarmId, equipmentIds, reason }` | 공장 | 202, 이미 정지된 설비는 건너뜀 |
| C4 | `POST /processes/{id}/call-workers` `{ alarmId }` | 공장 | |
| 실시간 | `GET /stream?scope=process:12` | 로그인 | 아래 |

### SSE (`GET /stream`)

`sensor.value`(그 공정을 볼 때만) · `level.changed` · `alarm.created` · `alarm.updated` · `checklist.updated` ·
`command.result` · `threshold.changed` · `reset` · `ping`(15초). 모양은 명세서와 같고, 다음이 다릅니다.

- `alarm.updated` 에 `level`, `hazard`, `valueStillOver` 를 함께 보냄 (값이 기준 아래로 30초 유지되면 `valueStillOver: false` → 종료 버튼 켜기)
- `threshold.changed` `{ equipmentId, processId, season, at }`: 명세서에 없던 이벤트. 기준값이 바뀌면 B3·B4 게이지 기준을 다시 불러오면 됨
- `reset`: 시연 리셋·중앙 재시작 때. 화면은 전부 다시 불러오면 됨
- `Last-Event-ID` 로 놓친 이벤트 다시 받기는 아직 없음
- `thermal.frame`, `fire.*`, `chat.message` 는 아직 없음

### 기준값 관리 (D2)

권한: 본사·공장 관리자 (공장 관리자는 담당 공장 설비만).

| API | 비고 |
| --- | --- |
| `GET /equipment/tree?factoryId=` | 공정 › 설비 › 센서 트리 |
| `GET /equipment/{id}/thresholds` | 센서별 계절(`ALL`·`SUMMER`·`WINTER`…) 기준, `legal`(CO 법정 30ppm), `activeSeason`, `version`. 지속 조건은 초(`warnHoldSec`·`alarmHoldSec`) |
| `POST /thresholds/simulate` `{ sensorId, warn, alarm, lowWarn, lowAlarm, warnHoldSec, alarmHoldSec, days }` | 지난 N일(기본 7) 1분 추이로 "지금 기준 vs 바꾼 기준" 발생 건수 비교 (`current`·`proposed`·`diff`·`hits`) |
| `PUT /equipment/{id}/thresholds` `{ season, version, reason, sensors: [...] }` | 사유 필수. 바로 판정에 반영(`appliedNow: true`, 진행 중 판정은 유지). 오류 `REASON_REQUIRED`, 409 `THRESHOLD_CONFLICT`(다른 사람이 먼저 저장), `WARN_NOT_BELOW_ALARM`, `LOOSER_THAN_LEGAL`. 이용 로그 `THRESHOLD` |
| `GET /thresholds/history?equipmentId=&sensorId=&from=&to=` | 변경 전·후, 변경자 이름 (최초 값은 "초기 설정") |
| `GET /sensors/{id}` · `PUT /sensors/{id}` `{ sampleSec, active }` | 센서 수집 주기·사용 여부 |

- 화면은 초, 중앙 DB 는 샘플 수로 저장합니다. 샘플 = 초 ÷ 수집 주기(올림, 최소 1).
- 기준값은 "지금 계절" 행이 우선, 없으면 `ALL`. 계절은 시연 시계 날짜 기준 (11월 = 가을).

### 통계 · 이력 (D1)

권한: 본사·공장 관리자. 모든 API 가 같은 조건을 받습니다:
`from`·`to`(필수, `2026-11-10` 형식, 끝 날짜 포함) · `factoryId` · `processId` · `sensorType`(CURRENT·TEMP·CO·IR) · `level`(WARN·ALARM) · `shift`(DAY 07~19시 · NIGHT).
센서 종류는 경보의 대표 센서 기준, 근무조는 발생 시각 기준. 한 번에 최대 1년.

| API | 비고 |
| --- | --- |
| `GET /stats/summary` | `alarmCount`·`dangerCount`(경보 단계)·`avgActionMinutes`(종료된 것만)·`checklistDoneRate`(%) 마다 `{ value, diff }`. diff 는 바로 앞 같은 길이 기간 대비 |
| `GET /stats/trend?unit=HOUR·DAY·WEEK` | `points[{ key, warn, alarm, day, night }]`, 빈 구간도 0 으로 채움. unit 을 비우면 1일→HOUR, 62일 이하→DAY, 그 이상→WEEK |
| `GET /stats/by-process` | 공정별 `CURRENT·TEMP·CO·IR·total`, 많은 순 |
| `GET /stats/heatmap` | `cells[{ dow(월=1), hour, count }]`(0건 칸 생략), `max` |
| `GET /stats/alarms?sort=raisedAt,desc·actionMinutes,desc&page=&size=` | 이력 표. 줄 모양은 `/alarms` 와 같음 (명세서의 `/alarms` 필터 추가 대신 별도 주소) |
| `GET /stats/export?format=csv` | 지금 조건 그대로 CSV (UTF-8 BOM, 엑셀에서 바로 열림). **`format=xlsx` 도 지금은 CSV 로 줍니다.** 5만 건 초과는 400 `EXPORT_TOO_LARGE`. 이용 로그 `EXPORT` |

- 월간 보고서 PDF (`/reports/monthly`) 는 아직 없습니다.
- 계산은 연계 서버에서 합니다: 기간 경보를 중앙에서 500건씩 받아 거른 뒤 셉니다 (시연 데이터 규모 기준).

## 아직 없는 것

화재(fire_incident), 월간 보고서 PDF, xlsx 내보내기, 점검·OCR(`/inspections/*`),
사용자 관리(`/users/*`), 이용 로그(`/logs`), 채팅, 공장 관리(배치 저장), 실제 FCM 전송.
