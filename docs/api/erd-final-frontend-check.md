# 최종 ERD ↔ 프론트 대조 결과 (2026-11-19)

프론트엔드 → 백엔드 전달 문서입니다. 2026-11-19 최종 ERD(테이블 35개, `docs/erd/`, `db/argos_ddl.sql`)와
프론트(`gateway/frontend`)가 쓰는 필드 이름 · 값 · 구조를 비교했습니다.
**프론트 코드는 1장만 고쳤고, 백엔드 코드는 고치지 않았습니다.** 3 ~ 5장이 백엔드에서 확인해 주실 내용입니다.

- 프론트 브랜치: `feature/frontend-erd-final`
- 기준: develop `899f41a`(#55), 연계 서버 `gateway/backend/docs/API_IMPLEMENTED.md`, 중앙 서버 `V1 ~ V3` 스키마

## 0. 한눈에

| 구분 | 건수 | 어디 |
|---|---|---|
| 프론트에서 고친 것 | 3 | 1장 |
| 값이 달라도 그대로 두는 것 (연계 서버가 화면 값으로 바꿔 줌) | 8 | 2장 |
| ERD에 없는데 프론트가 기대하는 값 → **백엔드 확인 요청** | 12 | 3장 |
| ERD에는 있는데 화면이 아직 안 쓰는 것 → 정해지면 프론트 작업 | 4 | 4장 |
| ERD 그림 · 저장소 DDL · 서버 스키마끼리 다른 곳 | 7 | 5장 |

## 1. 프론트에서 고친 것

| # | 내용 | ERD 근거 | 고친 파일 |
|---|---|---|---|
| 1 | 기준값 저장(`PUT /equipment/{id}/thresholds`)과 미리보기(`POST /thresholds/simulate`)에 **하한 기준 `lowWarn` · `lowAlarm` 을 받은 값 그대로 실어 보냄**. 전에는 `warn` · `alarm` 만 보내서, 전류 센서를 저장하면 서버의 하한 기준이 `null` 로 덮일 수 있었음 | `sensor_threshold.low_warn` · `low_alarm` | `features/threshold/saveFlow.js`, `ThresholdModal.vue`, `api/thresholds.js`, `api/mock/thresholdData.js` |
| 2 | 배기팬 평소 회전수 1,180 → **1,200 rpm** (40 Hz × 30). 알림 창의 `1,200 → 1,350 rpm` 표시와 예시 데이터 | `equipment_command.value` 주석: 정상 40 Hz / 가스 주의 45 Hz / 경보 60 Hz, `equipment.fan_rpm` = 30 × Hz | `types/codes.js`, `api/mock/fanAuto.js`, `api/mock/processData.js` |
| 3 | 설비 설정 '배기팬 자동 제어' 설명에 **"알림이 끝나면 평소대로 되돌립니다"** 추가. 전에는 '알림이 끝나도 그대로 둔다'고 가정했음 | `equipment_command.source = 'RECOVERY'` (경보 해제 후 40 Hz 복귀) | `features/threshold/EquipmentSettingsCard.vue` |

예시 데이터의 전류 센서 하한은 DB 시드와 같은 규칙(평소 값의 90% / 70%)으로 넣었습니다.

## 2. 값이 달라도 그대로 두는 것 (연계 서버가 바꿔 줌)

화면은 DB 값이 아니라 API 값을 씁니다. 아래 대응이 지금처럼 유지되면 프론트는 바꿀 것이 없습니다.

| DB (최종 ERD) | 화면 · API 값 | 비고 |
|---|---|---|
| `alarm.stage` · `sensor_event.stage` 2 / 3 | `level` `WARN` / `ALARM` (+ 정상 `NORMAL`, 화재 `FIRE`) | `Codes.java` |
| `sensor.sensor_type` `TE` / `IT` / `AT` / `IR` | `TEMP` / `CURRENT` / `CO` / `IR` | |
| `equipment.run_state` `RUN` / `STOPPING` / `STOPPED` | `RUNNING` / `STOPPING` / `STOPPED` | |
| `alarm.hazard` `FIRE` / `GAS` | 같음 | 통계 · 이력 '유형' |
| `alarm.status` `OPEN` / `ACKED` / `ACTING` / `CLOSED` | 같음 | |
| `checklist.status` `SENT` / `IN_PROGRESS` / `REPORTED` / `CONFIRMED` / `CANCELLED` | 같음 | |
| `inspection_schedule.status` `PLANNED` / `DONE` / `OVERDUE` | `PLANNED` / `DONE` / `DELAYED` | 예정 시각이 지났으면 서버가 `DELAYED` |
| `sensor_threshold.warn_samples` · `alarm_samples` (횟수) | `warnHoldSec` · `alarmHoldSec` (초) | 초 ÷ 수집 주기 |

필드 이름은 지금처럼 camelCase 로 받습니다 (`fan_auto` → `fanAuto`, `is_active` → `active`, `pos_x` · `pos_y` · `pos_z` → `pos { x, y, z }`, `equipment_type` → `type`, `raised_at` → `raisedAt`).

## 3. ERD에 없는데 프론트가 기대하는 값 (백엔드 확인 요청)

화면이 이미 쓰고 있는 값입니다. 최종 ERD에 컬럼이 없으니 **ERD에 넣을지, 다른 값으로 만들어 줄지** 알려 주세요.
(★ = 지금 서버 DB에는 있는데 최종 ERD에서 빠진 것)

| # | 화면 | 프론트가 받는 값 | ERD 상태 | 제안 |
|---|---|---|---|---|
| 1 | 공정 화면 '오늘 설비 점검' | `items[].time` (예정 **시각**) | `inspection_schedule.planned_at` 이 `DATE` 뿐 | ★ 연계 DB 의 `planned_time TIME` 을 ERD에 추가 |
| 2 | 점검 관리 | 일정과 서류 양식 · 공정 연결 (`formType`, `processId`) | `inspection_schedule` 에 `form_type` · `process_id` 없음 | ★ ERD에 추가 |
| 3 | 점검 관리 등록 목록 · 스캔 보기 | `fileName` (올린 파일 원래 이름) | `file_asset` 에 이름 컬럼 없음 (`path` 만) | ★ `orig_name` 추가 |
| 4 | 점검 관리 | 올린 시각, 인식 실패 상태 `FAILED` | `ocr_result` 에 `uploaded_at` 없음, `status` 에 `FAILED` 없음 | ★ 둘 다 추가 |
| 5 | 공정 배치도 아래 설비 줄 | 설비 `seq` (공정 흐름 순서 1 ~ 8) | `equipment` 에 순서 컬럼 없음 (`factory.sort_order` 만 있음) | `equipment.sort_order` 추가 |
| 6 | 공장 관리 저장 | 배치 판 번호 `version` (409 `LAYOUT_CONFLICT`) | `process` 에 없음 | ★ 중앙 DB 의 `process.layout_version` 을 ERD에 추가 |
| 7 | 기준값 관리 저장 | 설비별 `version` (409 `THRESHOLD_CONFLICT`), 변경 사유 `reason` | `sensor_threshold` 에 판 번호 없음. 사유는 `note`(200자)에 넣는지 확인 | `threshold_id` 최댓값을 판 번호로 써도 됨 |
| 8 | 알림 타임라인 `GET /alarms/{id}` | 단계 `REPORT` · `CONFIRM` · `CLOSE` · `CALL` · `FAN` | `alarm_action.action` CHECK 는 `PUSH, BROADCAST, SHUTDOWN, CHECKLIST, MANUAL_NOTE, ACK, REASSIGN, EVACUATED` | ★ 연계 DB 가 쓰는 5개를 ERD CHECK 에 추가 |
| 9 | 체크리스트 조치 확인 | `result` (`RESOLVED` / `PARTS_PLANNED` / `NEED_RECHECK`) + `memo` | `checklist` 에 결과 · 메모 컬럼 없음 (`checklist_item` 에만 있음) | `checklist.result` · `memo` 추가 또는 `alarm_action.detail` 에 저장 |
| 10 | 경보 종료 | `{ result, followUp }` 글 | `alarm.close_reason` 은 코드 3개뿐 | `alarm_action`(`CLOSE`) `detail` 에 저장하는지 확인 |
| 11 | 체크리스트 보낼 사람 고르기 | `onDuty` (지금 근무 중) | `work_shift` 는 시간만 있고 사람과 이어진 표가 없음 | `app_user.shift_id` 또는 배정표 추가 (지금은 항상 `true`) |
| 12 | 사용자 삭제 | 삭제한 사람은 목록 · 로그인에서 빠지고 아이디 재사용 불가 | `app_user` 에 `deleted_at` 없음 | ★ ERD에 추가 |

계산으로 만들어 주는 값(ERD 컬럼 불필요)은 지금처럼 주시면 됩니다: `actionMinutes`, `valueStillOver`, `limitType`, `usual`(직전 7일 평균), 게이지 `max`, `scopeText`, `lastLoginAt`, `pushReady`, `unreadCount`, `remindAvailableAt`.

## 4. ERD에는 있는데 화면이 아직 안 쓰는 것

| # | ERD | 지금 화면 | 필요한 것 |
|---|---|---|---|
| 1 | **배기팬 수동 승인**: `equipment.fan_auto = false` 는 '담당자 승인 후 제어', `equipment_command.result` `AWAITING` / `DENIED`, `approved_by` · `approved_at` | 수동이면 알림 창에 '현장에서 확인해 주세요' 안내만 (승인 버튼 없음) | 승인 · 거절 API 주소와 알림 응답 `fanAuto` 에 실을 값(`commandId`, `status: AWAITING / DENIED`). 정해지면 알림 창에 버튼 추가 |
| 2 | **전류 하한 기준**: `sensor_threshold.low_warn` · `low_alarm` | 기준값 관리 창에 상한(주의 · 경보) 입력 칸만 있음. 게이지 · 추이 그래프도 상한 선만. 저장할 때는 받은 하한을 그대로 돌려보냄 (1장 1번) | 화면에서 하한도 고치게 할지 결정 |
| 3 | `sensor_threshold.immediate_value`(CO 200 ppm 즉시 경보) · `alarm_avg_sec`(60초 평균) · `release_samples` · `release_margin` | 화면에 없음 (서버 판정 전용으로 봄) | 기준값 창에 보여 줄 필요가 있으면 응답에 추가 |
| 4 | `alarm.close_reason` (`AUTO_RECOVERED` / `ACTION_DONE` / `MANUAL`) | 통계 · 이력 상세에 종료 사유 표시 없음 | 응답에 `closeReason` 을 주면 '조치 완료' 옆에 표시 가능 |

`fanAuto.status` 는 화면 값 4개(`SENT` / `APPLIED` / `FAILED` / `SKIPPED`)를 그대로 씁니다. 최종 ERD `equipment_command.result` 와의 대응 제안:

| `equipment_command.result` | 화면 `fanAuto.status` |
|---|---|
| `PENDING` | `SENT` (올리는 중) |
| `DONE` | `APPLIED` |
| `FAILED` · `REJECTED` · `EXPIRED` · `CANCELLED` | `FAILED` |
| `DUPLICATE` | 앞선 명령의 결과를 따름 |
| 명령 없음 (수동) | `SKIPPED` |
| `AWAITING` · `DENIED` | 4장 1번이 정해지면 추가 |

`rpm` · `beforeRpm` 은 rpm 으로 주세요 (Hz × 30: 1,200 / 1,350 / 1,800).

## 5. ERD 그림 · 저장소 DDL · 서버 스키마끼리 다른 곳

프론트가 정할 일은 아니지만 대조하다 보인 차이입니다. 프론트는 **지금 돌아가는 서버 값**(오른쪽)을 기준으로 두었습니다.

| # | 최종 ERD (그림 · `db/argos_ddl.sql`) | 지금 서버 | 프론트 영향 |
|---|---|---|---|
| 1 | ERD 그림 `sensor.stream_url` / 저장소 DDL `sensor.video_url` | — | 없음 (화면은 아직 영상 주소를 안 씀). 이름만 하나로 |
| 2 | ERD 그림 `sensor_event` 에는 `video_file_id` 없음 / 저장소 DDL 에는 있음 | — | 없음 |
| 3 | `factory.code` 주석 `F1 ~ F5`, `process.code` 주석 `PRESS / BODY / PAINT / ASSY / INSP / COMMON` | 중앙 시드 `P1 ~ P5` · `EV`, `PRS` / `BDY` / `PNT` / `ASM` | **있음**. 사업장 지도(`UlsanMap.vue`)와 공장 배치도(`PlantMap.vue`)가 이 코드로 구역을 찾음. 바꾸면 프론트도 같이 고쳐야 하니 미리 알려 주세요 |
| 4 | `equipment.equipment_type` 주석 · DDL 시드 `배기팬 / 건조로 / 탱크 …` (한글) | 중앙 시드 `FAN` / `OVEN` / `BURNER` / `TANK` / `PUMP` / `CAMERA` | **있음**. 공장 관리 '설비 종류' 목록과 체크리스트 양식이 영문 코드 기준 |
| 5 | `equipment_command.command` `FAN_HZ` / `FAN_STOP`, 값은 Hz | 중앙 `FAN_ON` / `FAN_OFF` / `FAN_RPM` / `SHUTDOWN` / `RESTART`, 값은 rpm. `result` 도 `PENDING / OK / FAIL` | 없음 (웹은 `POST /equipment/{id}/fan` 을 더는 안 부름). 셧다운 명령을 어디에 남기는지만 확인 |
| 6 | `chat_read.last_read_msg_id` · `updated_at` | 연계 DB `last_msg_id` · `read_at` | 없음 (API 는 `lastReadMessageId`) |
| 7 | DDL 시드 설비 코드 `TOP-OVEN` · 센서 태그 `IT-307` · `TE-305` · `AT-303` · `IR-301` | 중앙 시드 `TC-OVEN`, 태그는 같음 | 프론트 예시 데이터는 설계안 태그(`CT-17` · `TE-15` · `CO-13`)와 코드(`PNT-TOP-OVEN`)를 씀. 실제 서버에 붙으면 서버 값이 그대로 보임. 공정 배치도는 설비 코드로 그림 위치를 찾으므로(`paintLayout.js` `svgKeyOf`) **설비 코드가 확정되면 알려 주세요** |

## 6. 프론트 옛 문서에서 달라진 내용

`gateway/frontend/docs/` 의 아래 문서는 ERD 확정 전 제안이라 값이 다릅니다. 최종 ERD가 우선입니다.

| 문서 | 옛 내용 | 최종 ERD |
|---|---|---|
| `fan-auto-db.md` 2장 | `command` 에 `FAN_ON / FAN_OFF / FAN_RPM`, 값 rpm | `FAN_HZ / FAN_STOP`, 값 Hz |
| `fan-auto-db.md` 2장 | `source` `USER / AUTO` | `USER / AUTO / RECOVERY` |
| `fan-auto-db.md` 2장 | `result` `PENDING / OK / FAIL` | `AWAITING / PENDING / DONE / FAILED / REJECTED / EXPIRED / DUPLICATE / CANCELLED / DENIED` |
| `fan-auto-api.md` 2장 | `beforeRpm` 1,180 | 1,200 (40 Hz) |
| `fan-auto-api.md` 5장 3번 | 알림이 끝나도 팬을 되돌리지 않음 | `RECOVERY` 로 40 Hz 복귀 |
| `equipment-settings-api.md` 6장 1번 | 수동일 때 승인 버튼이 필요한지 질문 | 필요 (4장 1번) |
| `stats-history-db.md` 2장 | `alarm_sensor_sample` 신규 제안 | ERD에 그대로 반영됨 |
