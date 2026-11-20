# 백엔드 피드백 4건 프론트 반영 (2026-11-20)

백엔드 팀원 피드백 4건을 프론트에서 반영했습니다. **백엔드 코드는 고치지 않았습니다.** 꼭 필요한 백엔드 작업은 없고, 5장은 확인 · 선택 사항입니다.

| # | 피드백 | 프론트 반영 | 주로 바뀐 파일 (`gateway/frontend/src/`) |
|---|---|---|---|
| 1 | 그날 점검 목록을 실제 API로 | `GET /inspections/schedule?date=&factoryId=` 연결 | `api/inspections.js`, `features/inspection/ScheduleCard.vue` |
| 2 | 열화상 사진은 `GET /thermal/images/{fileId}` blob | 토큰을 붙여 blob 으로 받아 `<img>` 에 표시 | `api/equipment.js`, `features/equipment/useThermalSrc.js`, `ThermalImage.vue`, `history/AlarmDetailPanel.vue` |
| 3 | 가스 알림 배기팬 승인 · 거절 버튼 | `fanAuto.status = AWAITING` + `commandId` 면 버튼 | `features/alert/FanAutoNotice.vue`, `api/equipment.js` |
| 4 | 실제 설비 코드 사용 | 배치도 그림 짝 맞춤을 실제 코드로 | `features/process/paintLayout.js`, `processEquipment.js`, `PaintMap.vue`, `pages/factory/FactoryPage.vue` |

mock 모드(`VITE_USE_MOCK` 기본 true)도 같은 응답 모양으로 계속 동작합니다.

## 1. 그날 점검 목록 — `GET /inspections/schedule`

- 점검 관리 › 점검 일정 달력에서 점이 있는 날을 누르면 부릅니다. 공장 담당자는 자기 공장 `factoryId` 를 붙입니다.
- 응답(`InspectionService.daySchedule`) 그대로 씁니다: `scheduleId, date, time, title, formType, formName, processId, processName, equipmentId, equipmentName, assignee, status(DONE · PLANNED · DELAYED), doneAt, doneBy`.
- 작은 창: 제목 + `시각 · 공정 · 담당자` + 상태. 빈 목록이면 '점검 일정이 없습니다', 실패하면 오류 문구.

## 2. 열화상 사진 — `GET /thermal/images/{fileId}`

- 알림 상세 · 경보 창 · 화재 창 · 통계 상세의 `thermal.imageUrl` 이 `/api/v1/thermal/images/{fileId}` 이면 axios(토큰 헤더)로 `responseType: 'blob'` 으로 받아 `blob:` 주소로 띄웁니다. 주소가 바뀌거나 창이 닫히면 `URL.revokeObjectURL`.
- `imageUrl` 이 null 이면 지금처럼 단계별 예시 그림을 씁니다 (`GET /thermal/{cameraId}/latest` 는 아직 null).
- 받기 실패(404 등)면 그림 칸이 비어 보입니다 (통계 상세는 '열화상 없음').

## 3. 배기팬 수동 승인 · 거절 — `POST /fan-commands/{commandId}/approve · deny`

- 주의 알림 창 · 주의 상세 창 · 경보 대응 창의 배기팬 줄(`FanAutoNotice`)에서 `mode = MANUAL`, `status = AWAITING`, `commandId` 가 있으면 '승인' · '거절' 버튼을 보여 줍니다.
- 버튼은 **공장 담당자만** 보입니다. 본사 관리자는 '공장 담당자의 승인을 기다리고 있습니다' 안내만 (서버도 403).
- 누르면 응답 `status`(SENT · DENIED)와 `rpm` 을 바로 보여 주고, 뒤이어 오는 SSE `alarm.updated` 로 받은 새 `fanAuto` 가 오면 그 값을 씁니다.
- 409 `COMMAND_NOT_AWAITING`(다른 담당자가 먼저 처리 · 알림 종료로 취소)이면 버튼만 숨기고 '이미 처리된 승인 요청입니다' 알림.
- 화면 문구: 승인 대기 → '배기팬 75% 가동 승인 대기', 승인 → '배기팬 75%로 올리는 중 (승인)' · '배기팬 가동 75% (승인)', 거절 → '배기팬 가동 거절'.
- 공장 관리 › 기준값 관리 창의 '배기팬 자동 제어' 설명도 '수동이면 알림 창에서 공장 담당자가 승인해야 올립니다' 로 바꿨습니다.
- mock: 수동 설비에 가스 알림이 나면 `AWAITING` + `commandId`(8100번부터), 승인 · 거절 결과를 기억하고 두 번째 요청은 409.

## 4. 실제 설비 코드

배치도 그림(`paint-map.svg`)은 화면설계안 그림이라 설비 코드로 그림 속 위치를 찾습니다. 중앙 시드(`V2__seed_ulsan_paint.sql`, 공정 12) 코드 기준으로 바꿨습니다.

| 실제 코드 | 그림 |
|---|---|
| `PT-TANK` · `PT-PUMP` | 전처리 |
| `ED-TANK` · `ED-PUMP` | 전착 탱크 |
| `ED-OVEN` · `ED-FAN` · `ED-BURNER` | 전착 건조로 |
| `PR-OVEN` · `PR-FAN` · `PR-BURNER` | 중도 건조로 |
| `TC-OVEN` · `TC-FAN` · `TC-BURNER` · `IR-CAM-01` | 상도 건조로 |

- 실제 서버는 배기팬 · 버너실 · 펌프 · 카메라가 따로 설비라 그림 하나에 여러 대가 붙습니다. 그림에서는 **대표 설비(`seq` 가 있는 탱크 · 건조로) 하나로 묶고**, 단계는 묶인 설비 중 가장 높은 단계, 센서는 전부 보여 줍니다. 그림 위치 · 사용 여부도 대표 설비 값만 씁니다.
- 아래 설비 줄 · 목록에는 지금처럼 설비가 한 대씩 다 나옵니다.
- mock 설비 코드도 같은 5개(`PT-TANK` · `ED-TANK` · `ED-OVEN` · `PR-OVEN` · `TC-OVEN`)로 바꿨습니다. mock 설비 id(24 ~ 34)는 그대로입니다.

## 5. 확인 · 남은 점 (급하지 않음)

| # | 내용 | 지금 화면 | 백엔드 |
|---|---|---|---|
| 1 | **화면설계안에는 있는데 실제 시드에 없는 설비**: 실링, 중도 부스, 상도 부스, 공조기(AHU), 도료 혼합실, 소각설비 | mock 만 `PNT-` 코드로 그림에 나옴. 실제 서버에서는 그림만 있고 상태 색은 정상 | 시드에 넣을지 팀에서 결정 (넣으면 코드 알려 주세요) |
| 2 | **배치도 센서 이름표**: 그림 속 이름표는 설계안 태그(`CT-17` · `TE-15` · `CO-13`) | 실제 태그(`TE-305` · `IT-307` …)와 달라 실제 서버에서는 이름표 색이 안 바뀜 (설비 테두리 색은 바뀜) | 없음 (프론트 그림 정리 때 맞춤) |
| 3 | 설비 단계 합치기: `GET /processes/{id}/status` 의 `equipment[].level` 이 설비마다 따로 | 화면에서 관제 단위로 합침 | 선택: 관제 단위 단계를 주면 화면 합치기를 뺄 수 있음 (`TODO(백엔드)` 주석) |
