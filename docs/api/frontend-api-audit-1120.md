# 프론트 ↔ 백엔드 API 전수 비교 결과 (2026-11-20)

프론트엔드 → 백엔드 전달 문서입니다. develop(`bab6230`, PR #70까지) 기준으로 프론트 API 모듈 19개(`gateway/frontend/src/api/*.js`)와
화면 코드를 연계 서버 컨트롤러 · 서비스(`gateway/backend`), 중앙 서버 시드, 최종 ERD(`db/argos_ddl.sql`)와 하나씩 맞춰 봤습니다.
**백엔드 코드는 고치지 않았습니다.** 프론트에서 맞출 수 있는 것은 모두 프론트에서 고쳤고(1장), 2 ~ 7장이 백엔드에 요청 · 확인드리는 내용입니다.
이미 다른 문서(`docs/api/*.md`)에 있는 요청은 다시 적지 않았습니다.

## 0. 한눈에

| 구분 | 건수 | 어디 |
|---|---|---|
| 프론트에서 고친 것 | 13 + 2차 4 | 1장, 9장 |
| 백엔드 요청 (프론트가 임시로 맞춰 둠, 반영되면 프론트 코드가 줄어듦) | 4 | 2 · 3 · 4 · 6장 |
| 백엔드 버그로 보이는 것 | 1 | 5장 1번 |
| 확인 · 선택 사항 | 6 | 5장 2 ~ 3번, 7장 |

주소 · 메서드는 전부 일치했습니다 (프론트 API 모듈의 요청 83곳 모두 컨트롤러에 있는 주소). 차이는 응답 필드 · 값 · 오류 처리 쪽이었습니다.

## 1. 프론트에서 고친 것

| # | 내용 | 원인 (서버 기준) | 고친 파일 (`gateway/frontend/src/`) |
|---|---|---|---|
| 1 | 사업장 코드 `ULS-01` · `ASN-01` · `JNJ-01` → **`USN` · `ASN` · `JJU`**. 실제 서버에서는 전국 지도에 사업장 핀이 안 나왔음 | 중앙 시드 `plant.code` (`V2__seed_ulsan_paint.sql`). `-01` 은 화면설계안 예시 값을 그대로 옮긴 것이라 뺐음 | `features/site/KoreaMap.vue`, `api/mock/data.js` |
| 2 | 로그인 연장 때 **새 `accessToken` 으로 바꿔 끼움**. 전에는 만료 시각만 바꿔서, 연장해도 예전 토큰이 원래 시각에 끝났음 | `POST /auth/extend` 응답 `{ accessToken, expiresIn, sessionExpiresAt }` | `stores/auth.js`, `api/auth.js` |
| 3 | **`pwResetRequired` 계정은 로그인 직후 비밀번호 변경 창**을 먼저 띄움. 바꾸면 첫 화면으로, 닫으면 로그아웃. 전에는 무시해서 로그인 뒤 모든 화면이 403 이었음 | `SecurityConfig`: 비밀번호 변경 전에는 `/me` · `/me/password` · 로그아웃 · 연장만 허용 | `features/auth/LoginForm.vue`, `features/user/PasswordModal.vue`, `stores/auth.js`, `router/index.js` |
| 4 | **화재 창 연결**: 화재 유형 경보가 있으면 `GET /fires` 로 화재를 찾아 그 알림을 화재(`FIRE`)로 표시하고 `fireId` 를 채움. 전에는 실제 서버에서 화재가 나도 일반 경보 창만 열렸음 | 알림 `level` 은 `ALARM` 까지만, `GET /alarms/{id}/response` 의 `fireId` 는 null 고정 (2장) | `api/alarms.js`, `api/emergency.js` |
| 5 | **본사 화재 현황**: 목록 건마다 상세(`GET /fires/{id}`)를 받아 합침. 전에는 목록 줄을 상세로 써서 실제 서버에서 화면 오류 | `GET /fires` 는 요약 9개 값만 줌 (방송 · 근거 값 · 조치 · 잠금 없음) | `api/emergency.js` |
| 6 | 화재 열화상 사진이 없으면(`thermal.imageUrl` null) 다른 창처럼 예시 그림 사용 | `GET /fires/{id}` | `api/emergency.js` |
| 7 | **진행 중 체크리스트 목록**을 `doneCount` · `totalCount` 로 표시, 설비는 알림에서 찾음. 전에는 `items` · `equipmentId` 를 써서 실제 서버에서 상단 바 진행 칸이 오류 | `GET /checklists` 줄에는 `items` · `equipmentId` 가 없음 (3장) | `stores/alert.js`, `features/alert/HeaderChecklistButton.vue`, `AlertHost.vue`, `features/process/EquipmentStrip.vue`, `api/mock/alertData.js` |
| 8 | **조치 확인 409 `VALUE_STILL_OVER`** 를 실패가 아니라 '확인됨, 알림은 열려 있음'으로 안내하고 창을 닫음 | `ChecklistService.confirm`: 체크리스트는 확인되고 알림만 열어 둠 | `features/alert/ChecklistProgressModal.vue` |
| 9 | 기준값 미리보기 그래프(최근 7일): `step=30m` → **`10m`**, 기간도 PC 시계 → **서버 시연 시계**. 전에는 400 | 중앙 서버 `step` 은 `5s` · `1m` · `10m` 만 받음 | `api/thresholds.js` |
| 10 | 통계 · 이력 표 '경과' 분을 서버 시연 시계로 계산 (전에는 PC 시계) | `GET /stats/alarms` 는 `serverTime` 을 주지 않음 | `features/history/AlarmHistoryTable.vue` |
| 11 | 월별 보고서: 공장을 안 고르면 내려받기 전에 안내 | `GET /reports/monthly` 는 `factoryId` 필수 | `features/history/MonthlyReportModal.vue` |
| 12 | 점검 등록 목록: 확인 필요 수 · 진행률이 없을 때 `undefined개` · `undefined%` 대신 '–' · '검수 대기' · 0% | `GET /inspections` 줄에 `needCheckCount` · `progress` 없음 (6장) | `features/inspection/InspectionListModal.vue`, `UploadQueue.vue` |
| 13 | SSE `master.changed` 가 오면 화면을 다시 불러옴 (빠져 있었음) | `FeedPoller` | `api/live.js` |

그 밖에 예시 데이터를 서버와 같게 맞췄습니다: 오류 코드 `ALARM_ALREADY_ACKED` · `COMMAND_PENDING`, 이상 센서 필드 `tag`. '백엔드에 아직 없음'으로 남아 있던 주석(화재 · 채팅 · 이용 로그 · 배치 저장 · 월별 보고서)도 지금 상태로 고쳤습니다.

## 2. 요청: 알림과 화재 연결

화재는 `fire_incident` 에 따로 기록되는데 알림 쪽 응답에서는 찾을 수 없습니다.

| 응답 | 지금 | 요청 |
|---|---|---|
| `GET /alarms` 의 `items[]`, SSE `alarm.created` · `alarm.updated` | 화재 여부를 알 수 없음 (`level` 은 `ALARM`) | **`fireId`** 추가 (화재가 아니면 null) |
| `GET /alarms/{id}/response` | `fireId: null` 고정 (`AlarmService.response`) | 그 알림의 화재 id |
| `GET /fires` (목록) | `alarmId` 없음 | `alarmId` 추가 (상세에는 이미 있음) |
| `GET /notifications/summary` | `unreadChatCount: 0`, `activeFire: null` 고정 ('아직' 주석) | 채팅 · 화재가 구현됐으니 실제 값으로. 화면은 지금 이 두 값을 안 쓰므로 급하지 않음 |

- 지금 프론트 동작: 화재 유형(`hazard: FIRE`) 경보가 있을 때만 `GET /fires` + `GET /fires/{id}` 를 더 불러 짝을 찾습니다. `fireId` 가 오면 이 요청이 없어집니다.
- 코드 위치: `api/alarms.js` `markFires()`, `api/emergency.js` `getAlarmResponse()` · `getActiveFires()` (`TODO(백엔드)` 주석)

## 3. 요청: 체크리스트 목록 줄에 설비 · 공정 번호

`GET /checklists?status=ACTIVE` 줄(`ChecklistService.listRow`)에 **`equipmentId` · `processId`** 를 넣어 주세요. `GET /checklists/{id}` 에는 이미 있습니다.

- 쓰는 곳: 공정 화면 설비 줄의 '체크리스트 2/5' 칩 (그 설비의 체크리스트 찾기)
- 지금 프론트 동작: 체크리스트의 `alarmId` 로 알림 목록에서 `equipmentId` 를 찾습니다. 알림의 설비는 원인 센서가 달린 설비(예: 배기팬)라 대표 설비(건조로)와 다르면 칩이 안 붙을 수 있습니다.
- 코드 위치: `stores/alert.js` `checklistOfEquipment()`, `api/checklists.js` `getActiveChecklists()`

## 4. 요청: 통계 요약 · 추이 · 공정별도 `siteId` 받기

`GET /stats/alarms` · `/stats/export` 는 `siteId` 를 받는데 `GET /stats/summary` · `/stats/trend` · `/stats/by-process` 는 받지 않습니다 (`StatsController`).
프론트는 다섯 API 에 같은 조건을 보냅니다. 본사 관리자가 사업장 하나를 보고 있을 때 위쪽 요약 · 그래프만 전 사업장 값이 됩니다 (지금은 시드가 울산 하나라 차이가 안 보임).

- 요청: 세 API 도 `AlarmCriteria` 에 `siteId` 를 넘겨 주세요 (`matches()` 는 이미 처리함).
- 코드 위치: `api/stats.js` 머리 주석

## 5. 월별 보고서

| # | 내용 | 요청 |
|---|---|---|
| 1 | **31일짜리 달은 PDF 가 400 `RANGE_TOO_LARGE`**. `ReportService.monthly()` 가 그 달 전체로 `stats.summary()` 를 부르는데, `StatsService.scope()` 의 30일 제한(`stats-history-30days.md` 반영분)에 걸림. 2026-10 은 실패, 2026-11 은 됨 | 보고서 경로는 30일 제한을 건너뛰게 (프론트 요청으로 생긴 제한이라 같이 알려 드립니다) |
| 2 | 미리보기 내용(요약 4칸 · 공장별 등급 · 주요 경보)과 고를 수 있는 달 목록 API 가 없음. 실제 서버에서도 창 오른쪽 미리보기는 예시 값이 보임 | 선택: `GET /reports/monthly/preview?factoryId=&month=` (JSON). 없으면 프론트에서 미리보기를 빼는 쪽으로 정리 |
| 3 | `factoryId` 필수 → 본사 관리자가 '전체 공장'으로는 못 받음 | 확인: 공장별만 지원이면 지금 그대로 (프론트가 공장 선택을 안내함) |

## 6. 요청: 점검 등록 목록 줄

`GET /inspections` 줄(`InspectionService.list`)에 아래 값을 넣어 주세요. `GET /inspections/{id}/ocr` 에는 있습니다.

| 값 | 쓰는 곳 | 없을 때 지금 화면 |
|---|---|---|
| `needCheckCount` | 등록 목록 '확인 필요' 칸, 오른쪽 줄 설명 | '–', '검수 대기' |
| `progress` | 인식 중 진행 막대 | 0% |
| `reviewedAt` | 저장한 서류의 '저장 시각' | 올린 시각으로 대신 표시 |

## 7. 확인 · 선택 사항

| # | 내용 | 지금 화면 |
|---|---|---|
| 1 | 비밀번호 변경 전 403 의 오류 코드가 일반 `FORBIDDEN`. `PASSWORD_RESET_REQUIRED` 같은 전용 코드면 화면이 구분할 수 있음 | 로그인 응답 `pwResetRequired` 로 처리해서 문제없음 |
| 2 | 로그인 실패 응답의 `remaining`(남은 횟수) · `unlockAt`(잠금 해제 시각)을 화면에 보여 줄지 | 서버 문구만 표시 |
| 3 | `GET /inspections/{id}/file` 이 PDF 를 그대로 줌 → 스캔 보기 창은 그림(`<img>`)이라 PDF 는 안 보임 (`API_IMPLEMENTED.md` '아직 없는 것'에 있음) | JPG · PNG 만 보임 |
| 4 | `GET /inspections` 의 `status` 는 하나만 받음. 프론트도 하나만 보내 문제없음 | – |
| 5 | `GET /equipment/{id}` 에 `processId` · `seq` 없음. 화면은 주소의 공정 번호를 써서 문제없음 | – |
| 6 | 최종 ERD DDL 시드(`db/argos_ddl.sql`)는 공장 `F5` · 공정 `PAINT`, 중앙 시드는 `P5` · `PNT`. 프론트 지도는 중앙 시드 값 기준 (`erd-final-frontend-check.md` 5장 3번과 같은 내용, 바뀌면 알려 주세요) | – |

## 8. 일치 확인한 것 (바꿀 것 없음)

- 주소 · 메서드: 로그인, 사업장 · 공장 · 공정 · 설비, 알림 · 체크리스트 · 셧다운, 배기팬 승인 · 거절, 화재, 통계, 기준값 · 설비 설정, 점검(일정 포함), 사용자, 이용 로그, 채팅, 열화상(사진 blob 포함)
- 값(enum): 단계 `NORMAL / WARN / ALARM`, 센서 `CURRENT / TEMP / CO / IR`, 운전 `RUNNING / STOPPING / STOPPED`, 알림 상태, 체크리스트 상태, 유형 `FIRE / GAS`, 계절 `ALL / SPRING_FALL / SUMMER / WINTER`, 권한 3종, `fanAuto.status`, 점검 상태
- 권한: 주의 확인 · 체크리스트 전송 · 조치 확인 · 셧다운 · 경보 종료 · 화재 해제 · 배기팬 승인은 공장 담당자만, 화재 잠금 풀기 · 사업장 목록은 본사만 — 화면 버튼 노출과 서버 검사가 같음
- SSE: `alarm.created · alarm.updated · checklist.updated · level.changed · threshold.changed · command.result · fire.started · fire.updated · fire.cleared · equipment.updated · chat.message · master.changed · reset` 을 받으면 다시 불러옴. `sensor.value` · `ping` 은 쓰지 않음
- 2차 검증에서 화면 동작까지 확인: 이용 로그(조건 · 유형 묶음 · 내려받기), 채팅(대화방 · 글 · 읽음), 사용자 관리(목록 · 상세 · 추가 · 수정 · 정지 · 삭제 · 초기화 · 내 정보) — 9장 외에는 차이 없음
- mock 모드(`VITE_USE_MOCK` 기본 true)는 그대로 동작합니다 (화면 9개 + 주의 · 경보 · 화재 상황 전환 확인).

## 9. 2차 검증 추가분 (이용 로그 · 채팅 · 사용자 · 공장 관리 배치 저장)

1차에서 응답 필드 이름만 본 네 영역을 화면 동작까지 다시 맞춰 봤습니다.

**프론트에서 고친 것 (4건)**

| # | 내용 | 원인 (서버 기준) | 고친 파일 |
|---|---|---|---|
| 1 | 채팅 대화방에 상대가 없어도(`peer: null`) 패널이 열리게 '담당자 없음'으로 채움. 전에는 본사 관리자가 채팅을 열면 화면 오류 | `ChatService.peer`: 그 사업장에 공장 담당자가 없으면 null (아산 · 전주) | `api/chat.js` |
| 2 | 채팅 알림 카드: 설비 이름이 없으면 제목만 표시 (`… · null` 방지) | 지워진 알림은 `equipmentName: null` | `features/chat/ChatMessageList.vue` |
| 3 | 이용 로그 유형 `FACTORY` 를 '설비 수정' 칸에 넣음. 전에는 칸 없이 영문 코드로 보이고 유형 걸러 보기에서 빠짐 | `FactoryAdminService` 가 사업장 · 공장 · 공정 추가, 배치 저장, 배치도 올리기를 `FACTORY` 로 남김 | `features/log/logMeta.js` |
| 4 | 배치 저장 때 `pos.z` 를 받은 값 그대로 실어 보냄. 전에는 `{ x, y }` 만 보내 저장할 때마다 설비 높이 값이 지워짐 | 중앙 `MasterAdminService.saveLayout`: `pos` 가 오면 `pos_x · pos_y · pos_z` 를 함께 덮어씀 | `features/factory/layoutDraft.js` |

**백엔드 요청 (1건)**

- **배치 저장이 설비 종류(`type`)를 반영하지 않습니다.** `PUT /processes/{id}/layout` 의 `equipment.update[]` 에서 `pos · rot · size · name · active · fanAuto · managerIds` 는 처리하는데 `type` 은 빠져 있습니다 (중앙 `MasterAdminService.saveLayout`). 공장 관리에서 종류를 바꾸고 저장하면 '저장했습니다'가 뜨지만 다시 불러오면 원래 종류입니다.
  - 요청: `if (u.containsKey("type"))` → `UPDATE equipment SET equipment_type = ?` 추가. 종류 변경을 막을 계획이면 알려 주세요 (프론트에서 종류 칸을 읽기 전용으로 바꿈).
  - 참고: 이용 로그는 배치 저장을 `FACTORY`, 설비 설정(`PATCH /equipment/{id}/settings`)을 `LAYOUT` 으로 남깁니다. 의도와 반대로 보이면 확인해 주세요 (화면은 둘 다 '설비 수정' 칸).

**확인만 한 것 (차이 없음)**: 사용자 관리 전체, 이용 로그 조건(`from · to · factoryId · processId · type · page · size`)과 CSV, 채팅 글 30개씩 · 이전 글 `before` · 읽음.
