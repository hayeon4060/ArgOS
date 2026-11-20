# 백엔드 전달 사항 (프론트엔드 기준)

프론트엔드는 백엔드 개발 전이라 **예시 데이터(mock)** 로 화면을 만들고 있습니다.

- **기준 문서**: 설계 자료의 `ArgOS_API명세서.html` (API 87개, 프론트 쪽에서 화면 기준으로 먼저 적은 제안)
  - 설계 자료 묶음은 git에 올리지 않으므로, 필요하면 PM에게 받아 주세요.
- **이 문서**: 프론트가 실제로 구현한 API, 명세서와 **다르게 한 점 / 추가로 필요한 점 / 정해야 할 점**만 화면별로 정리합니다. 화면을 만들 때마다 계속 추가합니다.
- 예시 데이터 값: `gateway/frontend/src/api/mock/data.js` (명세서 응답 예시와 같은 모양)
- API 호출 코드: `gateway/frontend/src/api/*.js` (각 함수의 `http.get/post` 부분이 실제 요청)
- 예시 데이터 끄기: `gateway/frontend/.env` 에 `VITE_USE_MOCK=false` → `/api` 요청이 `http://localhost:8080` (gateway/backend) 으로 감

## 0. 남은 백엔드 요청 한눈에 (2026-11-17 전수조사로 develop 백엔드와 다시 대조)

이미 백엔드에 생긴 항목은 각 장에서 지우거나 '있음'으로 고쳤습니다. 남은 것만 급한 순서로 모았습니다. 자세한 내용은 '장' 칸의 장을 보세요.

| 순서 | 요청 | 장 | 프론트 지금 처리 |
| --- | --- | --- | --- |
| 1 | **`GET /equipment/{id}` 에 `processId`** (그리고 `seq` · `sensors[].usual`) | 8 | `processId` 가 없으면 주소 검사를 건너뜀 (예전엔 늘 '이 공정의 설비가 아닙니다' 로 화면이 열리지 않았음) |
| 2 | **처음 만든 계정(`pwResetRequired`)은 비밀번호를 바꾸기 전까지 모든 요청이 403** — 시연 계정은 `pw_reset_required=false` 로 넣어 주세요 | 2 | 비밀번호 강제 변경 화면 없음 (재원님 결정 대기) |
| 3 | **'연장'이 localhost 가 아닌 http 주소에서 실패**: `/auth/extend` 가 `argos_rt` 쿠키(기본 Secure)를 요구 → 쿠키가 저장되지 않아 401 → 로그아웃. 또 연장해도 JWT 는 첫 로그인 + 13시간에 만료 | 2 | 없음 |
| 4 | 화재 API 4개 · 화재 판정(`level: FIRE`) · `notifications.activeFire` (지금 null 고정) | 10 | 예시 데이터로만 동작 |
| 5 | 채팅 API 4개 · SSE `chat.message` · `unreadChatCount` (지금 0 고정) | 17 | 예시 데이터로만 동작 |
| 6 | 점검 · OCR 8개, 그날 점검 목록, `/inspections/today` 실제 데이터 (지금 늘 빈 목록) | 13 | 예시 데이터로만 동작 |
| 7 | 이용 로그 `GET /logs` · `/logs/export` (로그인 기록은 `login_history` 와 합쳐야 함) | 16 | 예시 데이터로만 동작 |
| 8 | 공장 관리 저장 `PUT /processes/{id}/layout` | 15 | 예시 데이터로만 저장 |
| 9 | 기준값 `sensors[].max` · `equipmentType`, tree `tags` · `equipmentCount`, 시뮬레이션 `peaks` | 11 | `max` 가 없으면 기준값으로 눈금 끝을 계산, `peaks` 가 없으면 `hits` 시각에 추이 값을 붙여 점을 찍음 |
| 10 | 체크리스트 요약의 `assignee` 모양 (`GET /equipment/{id}` 의 `activeChecklist.assignee` 는 `{ id, name }`, 알림 목록은 문자열) | 8 | 문자열과 `{ id, name }` 둘 다 받음 |
| 11 | 월별 보고서 `/reports/monthly` · 월 목록 · xlsx | 12 | 미리보기만 (예시) |
| 12 | 통계 `siteId` 조건 · 이력 `status` 탭 · 그 밖 필드 | 12 | 없음 |
| 13 | `GET /alarms/hourly` 를 **전일(어제)** 로 부름 — 야간 칸 범위를 `date 19시 ~ 다음 날 07시` 로 (2026-11-18) | 18 | `date=어제 날짜` 로 보냄, 야간 범위는 서버 계산 그대로 |

---

## 1. 공통 (명세서 '공통 규칙'과 같게 구현)

| 항목 | 프론트 구현 | 위치 |
| --- | --- | --- |
| 기본 주소 | `/api/v1` | `src/api/http.js` |
| 성공 응답 | `{ success: true, data }` → 화면에는 `data` 만 전달 | `src/api/http.js` |
| 실패 응답 | `{ success: false, error: { code, message } }` → `error.message` 를 화면에 그대로 표시 | `src/api/http.js` |
| 인증 | `Authorization: Bearer <accessToken>` | `src/api/http.js` |
| 401 | 로그인 상태에서 401 이면 로그아웃 후 로그인 화면으로. 단, 비밀번호를 다시 확인하는 요청의 '틀림' 코드(`PASSWORD_MISMATCH` · `FIRE_PASSWORD_MISMATCH`)는 로그아웃하지 않음 (요청별 `skipAuthLogout`) | `src/api/http.js` |
| 단계 | `NORMAL` / `WARN` / `ALARM` / `FIRE` | `src/types/codes.js` |
| 권한 | `HQ_ADMIN` / `PLANT_ADMIN` / `WORKER` | `src/types/codes.js` |

- 실시간: SSE `GET /stream` 연결함 (`src/api/live.js`, 이벤트 이름은 백엔드 `StreamHub` 와 같음)

### 부탁

- `error.message` 는 화면에 그대로 나가므로 **짧은 한국어 문장**으로 보내 주세요. (예: `아이디 또는 비밀번호가 맞지 않습니다`)

---

## 2. 로그인 (명세서 A1)

| API | 프론트 사용 | 명세서와 다른 점 |
| --- | --- | --- |
| `POST /auth/login` | 요청 `{ loginId, password, client: "WEB" }`. 응답의 `accessToken`, `user`, `sessionExpiresAt` 사용 | 백엔드에 있음 (`sessionExpiresAt` 포함) |
| `POST /auth/extend` | 상단 바 '연장' 버튼 | 백엔드에 있음. 단 쿠키 문제 (아래 '로그인 유지 시간') |
| `POST /auth/logout` | 로그아웃 버튼 | 없음 |
| `POST /auth/refresh` | **쓰지 않음** | **확정(2026-11-16 재원님)**: 토큰 자체를 13시간 유효로 하는 A안이라 리프레시 없음 (아래 '로그인 유지 시간') |
| `GET /me` | 아직 안 씀 (새로고침 시 지금은 브라우저에 저장한 user 사용) | - |

### 프론트가 쓰는 응답 필드

| 필드 | 쓰는 곳 |
| --- | --- |
| `accessToken` | 모든 요청 헤더 |
| `sessionExpiresAt` | 상단 바 남은 시간, 자동 로그아웃 (없으면 프론트가 '지금 + 13시간'으로 계산) |
| `user.name`, `user.loginId` | 상단 바 사용자 메뉴, 로그인 직후 불러오기 화면 |
| `user.role` | 첫 화면 분기, 메뉴·화면 권한 |
| `user.scope.siteId` | 공장 담당자의 첫 화면(담당 사업장 현황), 다른 사업장 주소 차단 |
| `user.scope.factoryIds` | 공장 담당자가 들어갈 수 있는 공장 대시보드, 다른 공장 주소 차단 |

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | **공장 담당자 첫 화면이 명세서와 다름 (가정)**: 명세서 `firstPage` 는 `FACTORY_DASHBOARD` 지만, 프론트는 화면설계안대로 **사업장 현황**(`/sites/{siteId}`)으로 보냄. 프론트는 `firstPage` 대신 `role` 과 `scope` 로 첫 화면을 정함. 확정되면 `gateway/frontend/src/router/home.js` 의 `PLANT_ADMIN_HOME` 한 줄만 바꾸면 됨 |
| 2 | 에러 `AUTH_LOCKED`(5회 잠김), `USER_INACTIVE`, `CLIENT_NOT_ALLOWED` 는 지금 `error.message` 를 그대로 보여 줌. 백엔드는 이미 `AUTH_INVALID.remaining` · `AUTH_LOCKED.unlockAt` 을 줌 → 화면에 보일지는 프론트가 정함 |
| 3 | **`pwResetRequired: true` 계정은 모든 요청이 403** (백엔드 `SecurityConfig` — `/me` · `/me/password` · `/auth/logout` · `/auth/extend` 만 허용). 처음 만든 관리자(`BootstrapAdmin`)와 `POST /users` 로 만든 사용자가 모두 이 상태로 시작. 설계안에 비밀번호 강제 변경 화면이 없어 **시연 계정은 `pw_reset_required=false` 로 넣어 주세요**. 강제 변경 화면을 만들지는 재원님 결정 대기 |

- 예시 계정(mock 전용): `hq_admin`(본사 관리자), `ulsan5_admin`(공장 담당자, 5공장) / 비밀번호 `argos1234`

### 로그인 유지 시간 (2026-11-16 변경)

재원님 요청으로 **로그인하면 13시간 유지, '연장'을 누르면 1시간 늘어나는 방식**으로 바꿨습니다. 명세서의 '액세스 토큰 30분 + 리프레시'와 다릅니다.

**방식 확정(2026-11-16 재원님): A안** — 토큰 자체를 13시간 유효로 하고, `POST /auth/refresh` 와 리프레시 토큰은 쓰지 않습니다. 연장하면 서버가 같은 토큰(또는 새 토큰)의 만료 시각을 1시간 늘립니다. (토큰을 새로 발급한다면 `/auth/extend` 응답에 `accessToken` 도 함께 주세요.)

| 항목 | 프론트 구현 | 백엔드에 필요한 것 |
| --- | --- | --- |
| 유지 시간 | 로그인 시각 + 13시간 | 있음 (토큰 13시간, 응답 `sessionExpiresAt`) |
| 연장 | 상단 바 '연장' → 만료 시각 + 1시간 (남은 시간에 더함, **횟수 제한 없음**) | 있음 (`POST /auth/extend` → `{ sessionExpiresAt }`, 횟수 제한 없음). **남은 문제 2가지** (아래) |
| 만료 | 남은 시간이 0이 되면 자동 로그아웃 → 로그인 화면에 "로그인 유지 시간이 끝났습니다" 안내 | 만료된 토큰으로 요청하면 401 (프론트는 로그아웃 처리) |
| 안내 | 만료 10분 전에 한 번 안내 문구 ("곧 끝납니다, 연장을 누르면 1시간") | 없음 |
| 새로고침 | 만료 시각을 브라우저(localStorage)에 저장해서 유지 | 없음 |

- **연장 남은 문제 1 (쿠키)**: WEB 로그인이면 `/auth/extend` 가 리프레시 쿠키 `argos_rt` 를 요구함. 이 쿠키는 기본이 `Secure`(`GATEWAY_COOKIE_SECURE:true`) 라서 **localhost 가 아닌 http 주소(예: 사내 IP)로 열면 쿠키가 저장되지 않아** 연장이 401 `REFRESH_EXPIRED` → 프론트는 로그아웃함. 시연 환경에서 `GATEWAY_COOKIE_SECURE=false` 로 하거나, A안(토큰 13시간)에 맞게 쿠키 없이 연장되게 해 주세요 (`AuthService` 120~127줄)
- **연장 남은 문제 2 (JWT 만료)**: 연장은 서버 세션만 1시간 늘리고 JWT 를 새로 주지 않음 → JWT 는 첫 로그인 + 13시간에 만료(`AuthTokens` 217~220줄)되어, 연장했어도 13시간이 지나면 `TOKEN_EXPIRED` 로 로그아웃됨. 연장 때 `accessToken` 을 같이 주면 프론트가 바꿔 씀
- 아직 정해야 할 것: 여러 탭·기기에서 같은 계정을 쓸 때 만료 시각을 공유할지 (서버 세션은 하나, 프론트는 탭마다 localStorage)
- 프론트 설정 위치: `gateway/frontend/src/features/session/sessionConfig.js` (13시간, 1시간, 10분)

---

## 3. 사업장 선택 (명세서 A2, 본사 관리자)

| API | 프론트 사용 | 명세서와 다른 점 |
| --- | --- | --- |
| `GET /sites` | 지도 핀, 도넛, 목록 머리줄 (`id, code, name, address, lat, lng, level, sensorLevels`) | 없음 (백엔드에 모두 있음) |
| `GET /sites/{siteId}/tree` | 목록에서 사업장을 펼칠 때 (처음 한 번만) | 없음 (`todayAlarmCount` 있음) |
| `GET /sites/{siteId}/overview` | '이동'·지도 핀·도넛을 누르면 사업장 현황 화면으로 감 (아래 5번) | - |

### 프론트가 쓰는 `tree` 필드

| 필드 | 쓰는 곳 |
| --- | --- |
| `factories[].id`, `name`, `level` | 공장 줄 |
| `factories[].levelsByType` | 센서 단계 막대·개수 (전류·온도·CO 를 더해서 표시) |
| `factories[].processes[].name`, `level` | 공정별 단계 칩 (이름 끝의 ' 공정'은 화면에서 줄여서 표시) |
| `factories[].todayAlarmCount` | 오늘 알림 |

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | 지도 핀 위치는 지도 그림 좌표라 프론트에 사업장 코드(`USN` 등, 서버 plant.code)별로 적어 둠. 사업장이 늘면 프론트 수정 필요 |
| 2 | 이번 범위에서 들어갈 수 있는 곳은 울산공장 5공장(`factoryId 5`)뿐이라 프론트에 고정. 나머지는 '준비 중' 안내. 백엔드 공장 overview 의 `accessible` · `hasFloorplan` 으로 바꿀 수 있음 (재원님 결정 대기) |

---

## 4. 상단 바 (사업장 안 화면 공통)

| API | 프론트 사용 | 메모 |
| --- | --- | --- |
| (임시) 사업장 이름 | 상단 바 '📍울산공장' | 이름만 주는 API 가 명세서에 없음. 실제 연결 시 지금은 `GET /sites/{siteId}/overview` 의 `site.name` 을 씀 (공장 담당자는 `GET /sites` 권한이 없어서). **이름만 주는 가벼운 API 또는 `GET /me` 의 `scope.siteName` 사용 여부 합의 필요** |

- 주소 구조: 사업장 현황 `/sites/{siteId}`, 공장 대시보드 `/sites/{siteId}/factories/{factoryId}`, 메뉴 화면 `/sites/{siteId}/history` 등 (프론트 주소, API 주소와는 별개)

---

## 5. 사업장 현황 (명세서 B1 '울산공장 현황')

| API | 프론트 사용 | 명세서와 다른 점 |
| --- | --- | --- |
| `GET /sites/{siteId}/overview` | 구역 현황 지도 색, 전체 공장 상태 카드, 센서별 단계 현황 도넛 | 아래 표 참고 |
| `GET /alarms?siteId=&date=today&size=20` | 실시간 알림 목록 (최근 순) | 진행 상태 칩은 `status` 로만 표시 (아래 참고) |
| `GET /alarms/hourly?siteId=&date=today` | 시간대별 알림 (주간 07~19시, 야간 19~07시) | 없음. 알림 없는 시간은 프론트가 0으로 채움 |
| `GET /alarms/{alarmId}` | 알림 줄 펼치기 (조치 내역) | 없음 |

### 프론트가 쓰는 `overview` 필드

| 필드 | 쓰는 곳 |
| --- | --- |
| `site.name` | 카드 제목 옆, 상단 바 |
| `levelsByType.CURRENT/TEMP/CO` | 센서별 단계 현황 (종류별 도넛, 합계) |
| `factories[].id`, `name`, `level` | 지도 구역 색, 공장 카드, 말풍선 |
| `factories[].code` | **지도 구역과 짝 맞춤** (`P1`~`P5`, `EV`=엔진·변속기). 백엔드에 있음 — 값이 시드와 같은지 합의 필요 |
| `factories[].levelsByType` | 공장 카드의 전류·온도·CO 작은 도넛 (백엔드는 세 종류를 늘 줌) |
| `factories[].sensorCount`, `todayAlarmCount` | 공장 카드 아래 줄, 말풍선 |
| `factories[].processes[].name`, `level` | 5공장 말풍선의 공정 목록 |

### 프론트가 쓰는 `alarms` 필드

| 필드 | 쓰는 곳 |
| --- | --- |
| `id`, `level`, `status`, `title`, `location` | 알림 줄 |
| `sensorType` | 아이콘 (전류 / 온도 / CO) |
| `sensorTag`, `value`, `unit` | 위치 옆 센서 값 |
| `raisedAt` | 시각 (`HH:mm:ss` 만 표시) |

- 진행 상태 칩: `OPEN` 발생 중 / `ACKED` 확인함 / `ACTING` 조치 중 / `CLOSED` 조치 완료
- 체크리스트 진행: 목록의 `checklist {id,status,doneCount,totalCount}` · `assignee` 사용 (백엔드 AlarmViews 에 있음)

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | 지도: 프론트는 설계안 고정 그림(울산공장 2D 지도)을 쓰고, 명세서의 `site.mapUrl`·`factories[].mapShape` 는 아직 안 씀. 사업장이 늘어나 지도를 서버에서 받을 때 다시 맞춤 |
| 2 | 이번 범위에서 공정 배치도로 들어갈 수 있는 공장은 5공장(`factoryId 5`)뿐이라 프론트에 고정 |
| 3 | **숫자 맞춤**: 설계안은 사업장 화면에서 '5공장 센서 19개', 공장 화면에서 공정 센서 합 61개(12+16+19+14)로 서로 다름. 프론트 예시 데이터는 **공정 합(61개)** 으로 맞춰 사업장 합계가 154개가 됨. 실제 데이터에서는 공장 `sensorCount`·`levelsByType` 이 공정 합과 같아야 함 |

---

## 6. 공장 대시보드 (명세서 B2 '5공장 공정 현황')

| API | 프론트 사용 | 명세서와 다른 점 |
| --- | --- | --- |
| `GET /factories/{factoryId}/overview` | 공정 배치도 건물 색·이름표, 공정별 설비 상태 카드, 센서별 단계 현황(공정 합) | 아래 표 참고 |
| `GET /alarms?factoryId=&date=today&size=20` | 이 공장의 실시간 알림 | 없음 (사업장 현황과 같은 API, 범위만 공장) |
| `GET /alarms/hourly?factoryId=&date=today` | 이 공장의 시간대별 알림 | 없음 (백엔드가 factoryId 범위 지원) |
| `GET /notifications/summary` | 상단 바 채팅·알림 빨간 숫자 (명세서 I1) | 없음 |

### 프론트가 쓰는 `overview` 필드

| 필드 | 쓰는 곳 |
| --- | --- |
| `factory.name`, `level` | 카드 제목·경로 |
| `processes[].id`, `name`, `level` | 건물 색, 이름표, 공정 카드 |
| `processes[].code` | **배치도 건물과 짝 맞춤** (`PRS` 프레스 / `BDY` 차체 / `PNT` 도장 / `ASM` 의장). 명세서 예시에는 `PNT` 만 있음 → **모든 공정에 code 필요, 값 합의 필요** |
| `processes[].equipmentCount`, `sensorCount` | 공정 카드, 요약 말풍선 |
| `processes[].equipmentLevels` | 공정 카드의 설비 단계 도넛(정상·주의·경보 설비 수). 백엔드에 있음 |
| `processes[].levelsByType` | 공정 카드의 전류·온도·CO 막대, 공장 전체 센서별 단계(공정 합) |
| `processes[].hasFloorplan` | 누르면 3D 설비 배치도로 갈지('도장 공정'만 true), 아니면 '아직 등록되지 않았습니다' 안내 |
| `cameras[].tag`, `processId` | 열화상 카메라 표시(IR-01 등), 공정 카드 아래 줄 |

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | 배치도 그림: 프론트는 설계안 고정 그림(5공장 공정 건물 4개)을 쓰고, 카메라 위치도 그림에 고정. 명세서 `cameras[].x, y` 는 아직 안 씀 |
| 2 | 사업장 이름(경로의 '울산공장'): 공장 overview 응답의 `siteId` · `siteName` 사용 (백엔드에 있음) |

---

## 7. 공정 3D 설비 배치도 (명세서 B3 '도장 공정 3D 배치도')

| API | 프론트 사용 | 명세서와 다른 점 |
| --- | --- | --- |
| `GET /processes/{processId}/layout` | 설비 목록(이름·순서), 센서가 어느 설비에 붙었는지 | 아래 표 참고 |
| `GET /processes/{processId}/status` | 설비 상태 테두리 색, 센서 이름표 색, 배기팬 상태, 상도 건조로 센서 게이지 | 없음 |
| `POST /equipment/{equipmentId}/fan` | 배기팬 창 '적용' → `{ power: "ON"/"OFF" }` 또는 `{ rpm: 600~1800 }` | 없음 (백엔드 202 + SSE `command.result`). 프론트는 접수(202) 되면 화면 값을 바로 바꿈 (**프론트 TODO: `command.result` 로 확정·5초 안에 안 오면 되돌리기**) |
| `GET /alarms?processId=&date=today` | 이 공정의 실시간 알림 | 없음 (백엔드가 processId 범위 지원) |
| `GET /inspections/today?processId=` | 오늘 설비 점검 카드 | 없음 |

### 프론트가 쓰는 `layout` 필드

| 필드 | 쓰는 곳 |
| --- | --- |
| `name` | 경로·카드 제목 ('도장 공정') |
| `equipment[].id`, `name` | 설비 줄, 말풍선, 설비 상세로 이동 |
| `equipment[].code` | **그림 속 설비와 짝 맞춤** (`PNT-TOP-OVEN` → 그림의 상도 건조로). 모든 설비에 code 필요, 값 합의 필요 (프론트 가정: `PNT-PRETREAT`, `PNT-ED-TANK`, `PNT-ED-OVEN`, `PNT-SEALING`, `PNT-PRIMER-BOOTH`, `PNT-PRIMER-OVEN`, `PNT-TOP-BOOTH`, `PNT-TOP-OVEN`, `PNT-AHU`, `PNT-PAINT-MIX`, `PNT-INCINERATOR`) → **2026-11-20 실제 코드로 바꿈** (24장) |
| **`equipment[].seq`** | 공정 흐름 순서 번호(1~8) — 그림과 아래 설비 줄의 파란 번호. **명세서에 없음 → 추가 필요** (공장 관리 화면에서 정하는 값으로 보임) |
| `sensors[].id`, `tag`, `type`, `unit`, `equipmentId` | 설비별 센서 묶기 (`equipmentId` 가 null 이면 예비 센서: 개수에는 넣고 그림에는 안 나옴) |

- `equipment[].pos`·`size`·`shape`, `sensors[].pos`, `floorplan` 은 아직 안 씀: 그림은 설계안 고정 그림(축측 SVG)이고 위치도 그림에 고정. 공장 관리(설비 재배치) 화면을 만들 때 이 값으로 그리는 방식을 다시 정함

### 프론트가 쓰는 `status` 필드

| 필드 | 쓰는 곳 |
| --- | --- |
| `equipment[].level` | 설비 둘레 상태 테두리 (주의·경보만 빛번짐) |
| `equipment[].fan.power`, `rpm` | 배기팬 창 (켜기/끄기, 회전수) |
| `sensors[].value`, `level`, `alarm` | 센서 이름표 색, 말풍선 값, 게이지(눈금 끝 = 경보 기준 × 1.2) |

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | 버너실 테두리: 프론트는 설비의 **CO 센서 단계**로 칠함 (전류만 주의면 건물만 주황, 버너실은 초록). 서버가 버너를 별도 설비로 줄지, 설비 안 부품 단계로 줄지 |
| 2 | 배기팬 끄기: 명세서는 '2초 길게 누르기'인데 지금은 ON/OFF 버튼 + '적용' 두 번 누르기로 대신함 (실수 방지 방식 합의) |
| 3 | 공정 센서 수: 도장 공정 19개 = 전류 9 + 온도 6 + CO 4. 설계안 그림에는 CO 센서가 3개(CO-11~13)라, 예시 데이터에서는 4번째를 예비 센서 CO-14(설비 미연결)로 둠 |

---

## 8. 설비 상세 (명세서 B4 '설비 상세')

| API | 프론트 사용 | 명세서와 다른 점 |
| --- | --- | --- |
| `GET /equipment/{equipmentId}` | 제목 줄(경로·상태·체크리스트 진행), 게이지 3개(눈금·기준), 열화상 카메라 id | 아래 표 참고 (**`processId`, `seq`, `sensors[].usual` 아직 없음** — 2026-11-17 develop 확인) |
| `GET /equipment/{equipmentId}/events?date=today` | 이벤트 이력 (최근이 위) | **`type: RECORD`(가동 시작·일일 점검 완료 같은 기록) 추가 필요**, `status`(조치 중 표시) 추가 |
| `GET /thermal/{cameraId}/latest` | 열화상 그림, 최고·최저 온도, 최고 지점 | 없음 (`?equipmentId=` · `scale` · `zone` 백엔드에 있음) |
| `GET /sensors/series?ids=&from=&to=&step=&compare=yesterday` | 센서 추이 3줄 + 전날 겹쳐 보기 | 없음. 프론트는 전날 값(`compare`)을 늘 같이 받고 스위치는 보이기만 바꿈 |
| `GET /processes/{processId}/layout`, `/status` | '설비 위치' 그림 (공정 배치도와 같은 그림을 이 설비 둘레만 크게) | 없음 (B3 과 같은 API) |

### 프론트가 쓰는 `GET /equipment/{id}` 필드

| 필드 | 쓰는 곳 |
| --- | --- |
| `name`, `path` (`["울산공장","5공장","도장 공정"]`) | 제목 줄 경로 |
| **`processId`** | 주소의 공정과 같은지 확인 (다르면 '이 공정의 설비가 아닙니다'). **백엔드 응답에 없음 → 추가 필요**. 없으면 프론트는 검사를 건너뜀 (2026-11-17, 예전에는 없으면 늘 오류로 화면이 안 열렸음) |
| **`seq`** | '설비 위치' 카드 설명 '도장 공정 · 8번 설비' (B3 `layout.equipment[].seq` 와 같은 값) |
| `code` | 그림 속 설비와 짝 맞춤 (B3 과 같음) |
| `level` | 제목 줄 상태 배지, 열화상 최고 온도 글자 색 |
| `sensors[].id`, `tag`, `type`, `unit`, `value`, `level` | 게이지, 추이 줄 |
| `sensors[].max` | 게이지 눈금 끝 (공정 배치도의 작은 고리는 경보 × 1.2 로 그림 → 이 값으로 통일할지 정해야 함) |
| `sensors[].warn`, `alarm` | 게이지 바깥 눈금 색 구간, 추이 기준선 |
| **`sensors[].usual`** | 게이지 아래 '평소 18.0 A'. **명세서에 없음 → 추가 필요** (최근 정상 구간 평균 등, 계산 방법 합의) |
| `cameraId` | 열화상 요청 (null 이면 카드 숨김) |
| `activeChecklist` | 제목 줄 '체크리스트 진행 2/5 김도현'. 백엔드 모양 `{ id, status, doneCount, totalCount, assignee: { id, name } }`. 프론트 예시는 `assignee` 가 이름 문자열 → **둘 다 받도록 함** (2026-11-17, 예전엔 '[object Object]'). 알림 목록의 `assignee`(문자열)와 같게 맞출지 합의 |

### 프론트가 쓰는 `events` 필드

| 필드 | 쓰는 곳 |
| --- | --- |
| `at` | 시각 |
| `type` | `ALARM` / `ACTION` / `THRESHOLD` / **`RECORD`(추가)** — 걸러 보기(전체·알람·조치·기준값)와 배지 |
| `level` | 알람 점·배지 색 (주의 주황 · 경보 빨강 · 정상이면 회색, 예: '전류 상승 시작') |
| `text`, `detail` | 제목, 한 줄 설명 (예: `CT-17 22.4 A (기준 21.6 A)`) |
| `alarmId` | 알림에 딸린 조치인지 (점 색 주황) |
| **`status`** | `ACTING` 이면 배지를 '조치 중'으로. **추가 필요** |
| `by` | 받기만 함 (설명 글자에 이미 이름을 넣어 보내 주세요) |

### 프론트가 쓰는 `thermal` 필드

| 필드 | 쓰는 곳 |
| --- | --- |
| `imageUrl`, `capturedAt` | 그림, '마지막 수신' 시각 |
| `max`, `min`, `maxPoint {x, y}` | 제목줄 최고·최저, 그림 위 최고 지점 표시 (그 설비 구역이 없으면 `null` → 표시 안 함) |
| `avg` | 받기만 함 (설계안 화면에 평균 칸이 없음, 화면설계서 B4 에는 있음) |
| **`scale {min, max}`** | 오른쪽 색 눈금 글자 (지금 20~120 ℃). **추가 필요** |
| **`zone {name, desc}`** | 그림 왼쪽 위 '배기팬 모터부', 아래 '상도 건조로 외벽 · 배기팬 · 버너실'. **추가 필요** (없으면 '도장 공정 전체') |

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | 추이 간격: 명세서는 1시간 = 5초, 오늘 = 1분 평균. 프론트 요청은 1시간 = `1m`, 6시간·오늘 = `10m` (설계안 그림 기준). 서버가 정하는 대로 따라가도 됨 |
| 2 | 실시간 값: 지금은 예시 데이터 모드에서만 2초마다 값이 조금 흔들림. SSE `sensor.value`, `thermal.frame` 연결은 SSE 작업 때 (`features/equipment/useLiveValues.js` 자리) |
| 3 | 기준값이 바뀐 시점의 세로 표시(화면설계서 B4): 이벤트의 `THRESHOLD` 시각으로 그릴지, series 응답에 넣어 줄지 |
| 4 | 예시 데이터 시각: 설계안 시각표(16:38:20 전류 주의 → 16:38:31 확인 → 16:41:05 체크리스트 전송 → 조치 중)를 **상황을 바꾼 순간이 '지금'이 되도록 옮김** (2026-11-17, `api/mock/scenario.js` `anchorAt`). 실제 서버는 실제 시각 |

---

## 9. 알림 대응 — 주의 (명세서 C1 · C2, 화면설계서 C1 ~ C3 · I1)

백엔드 코드(`gateway/backend` 의 `AlarmController` · `ChecklistController` · `StreamHub`)가 생겨서, 프론트 예시 데이터는 **코드의 주소·응답 필드**에 맞췄습니다(명세서와 다르면 코드 기준).

| API | 프론트 사용 | 명세서·코드와 다른 점 |
| --- | --- | --- |
| `GET /alarms?status=OPEN,ACKED,ACTING&date=today&factoryId=` | 상단 바 상태 띠, 주의 창 자동 열기, 체크리스트 칩 | 없음 (공장 담당자는 담당 공장 범위로 보냄) |
| `GET /alarms` (실시간 알림 카드) | 줄의 진행 칩·'조치 중 · 김도현 (2/5)' | 응답의 `checklist {id,status,doneCount,totalCount}` · `assignee` 사용 (코드 AlarmViews 그대로) |
| `GET /alarms/{id}` | 알림 줄 펼치기 (발생 → 확인 → 체크리스트 전송 → 조치 → 조치 확인) | `timeline[].step` 중 `RAISED · ACK · CHECKLIST · REPORT · CONFIRM` 만 씀 |
| `GET /alarms/{id}/detail` | 주의 상세, 센서·열화상 확인 창 | 아래 '추가 필요' 참고 |
| `POST /alarms/{id}/ack` | 2초 길게 누르기 끝 | 없음. 이미 확인된 알림이면 409 를 가정 (`ALREADY_ACKED`) |
| `GET /checklist-templates?equipmentType=&sensorType=` | 주의 상세 기본 5개 항목 | 없음 |
| `GET /processes/{id}/workers` | 받는 사람 고르기, 담당자 변경 | 없음. 기본 선택 = 근무 중인 첫 사람 (진행 건 수가 응답에 없어서) |
| `POST /checklists` | 전송 | 없음 |
| `GET /checklists?status=ACTIVE&factoryId=` | 상단 바 체크리스트 진행 버튼, 완료 알림 띠 | 없음 |
| `GET /checklists/{id}` | 진행 현황 창 | `serverTime` 추가 요청 (아래) |
| `POST /checklists/{id}/remind` | 다시 알림 (30초 대기 표시) | 화면설계서는 60초, **코드 30초를 따름** |
| `PATCH /checklists/{id}/assignee` | 담당자 변경 (사유: 응답 없음 / 부재 / 기타) | 화면설계서는 `PATCH /checklists/{id}`, **코드 주소를 따름** |
| `POST /checklists/{id}/confirm` | 조치 확인 (결과 3가지 + 한 줄 메모) | 없음. `REPORTED` 일 때만 단추를 켬 |

### 예전 추가 요청 → 해결됨

- detail 의 `usual` · `rule.max` · `sensors[].warn/alarm` · `thermal`(usualAtMax · maxPoint · zoneName · cameraTag) · `trend` 기준선 · `equipmentId`, 체크리스트 `serverTime` 은 **모두 백엔드에 생김** (2026-11-17 확인)

### 실시간 (SSE) — 지금은 예시 상황으로 흉내

- 프론트는 `src/api/live.js` 의 `emitLive(이벤트)` 한 곳으로 '바뀌었다'를 받고, 열린 화면·상단 바·창이 조용히 다시 불러옵니다(`useLiveReload`).
- SSE 연결 때 `GET /stream` 의 `alarm.created · alarm.updated · checklist.updated · level.changed · sensor.value` 를 받으면 `emitLive` 만 부르면 됩니다.
- 처음에는 이벤트 내용으로 부분 갱신하지 않고 **다시 불러오기**만 합니다(단순하게). 느리면 그때 이벤트 내용으로 바꿉니다.

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | 본사 관리자: 주의 창·확인·전송·조치 확인 단추 없이 **열람만**. 서버도 막음 (403 `FORBIDDEN`) — 해결됨 |
| 2 | 주의 '나중에(5분 뒤 다시)'·주의음: 설계안에 없어 프론트는 안 만듦. 필요하면 서버가 다시 알림을 보낼지 프론트 타이머로 할지 |
| 3 | 체크리스트 '응답 없음': 프론트가 마지막 항목 완료(없으면 전송) 후 10분이 지나면 표시. 서버가 상태로 줄지 |
| 4 | 예상 원인 단추: 지금은 보기만. 누르면 그 원인에 맞는 항목으로 바꾸려면 `GET /checklist-templates` 에 원인별 묶음이 필요 |

---

## 10. 알림 대응 — 경보 · 화재 (명세서 C4 · C4F · C5, 화면설계서 C4 · C5)

| API | 프론트 사용 | 백엔드 코드 | 명세서·코드와 다른 점 / 추가 필요 |
| --- | --- | --- | --- |
| `GET /alarms/{id}/response` | 경보·화재 창 전체 | 있음 | 아래 '추가 필요 필드' |
| `POST /equipment/{id}/shutdown` | 설비 하나 셧다운 (2초 길게) | 있음 | 없음 |
| `POST /processes/{id}/shutdown` | '도장 공정 전체' 셧다운 (2초 + 확인) | 있음 | 없음 (`equipmentIds` 비움) |
| `POST /processes/{id}/call-workers` | 공정 담당자 호출 | 있음 | 없음 |
| `POST /alarms/{id}/close` | 경보 종료 (셧다운 뒤, 결과 한 줄) | 있음 | 없음. 409 `VALUE_STILL_OVER` 면 알림 띠 |
| `GET /fires?status=ACTIVE` | 본사 사업장 선택 화면 화재 띠·화재 상세 | **없음** | 명세서 C5 기준으로 프론트 구현 |
| `GET /fires/{id}` | 화재 창, 본사 화재 상세 | **없음** | 명세서 C4F + 추가 필드 |
| `POST /fires/{id}/clear` | 비밀번호 다시 입력 해제 | **없음** | 틀릴 때 401 `FIRE_PASSWORD_MISMATCH` 는 **프론트에서 처리함** — 로그아웃하지 않고 '비밀번호가 맞지 않습니다 · 남은 횟수'(`error.remaining`) (아래) |
| `POST /fires/{id}/approve-unlock` | 본사 관리자 '잠금 풀기 승인' | **없음** | 명세서 기준 |
| `GET /notifications/summary` | 상단 바 | 있음 | `activeFire` 를 `{ id, location }` 로 가정 |

### 추가 필요 필드

| 어디 | 필드 | 쓰는 곳 |
| --- | --- | --- |
- 예전에 요청한 response 의 `location` · `thermal` · `reasonDetail` · `shutdown` · `valueStillOver` · `equipment[].runState` 는 **백엔드에 생김** (2026-11-17 확인). 남은 것만 아래에 둠

| 어디 | 필드 | 쓰는 곳 |
| --- | --- | --- |
| `/alarms/{id}/response` | `siteId` · `factoryId` · `processId` · `unitName` | 공정 담당자 호출 · 공정 전체 셧다운 · '설비 배치도에서 보기' 주소와 문구. **지금은 같은 알림의 목록 줄(`GET /alarms`)에서 꺼내 씀** (`features/alert/alarmPlace.js`) — 응답에 같이 오면 그걸 쓰면 됨 |
| `/fires/{id}` | `thermal.cameraTag` | 열화상 아래 'IR-01 · 도장 공정' |
| `/fires/{id}` | `values { thermalMax, temp, co }` | 감지 근거 숫자 3칸 |
| `/fires/{id}` | `actions[].text` · `running` | 자동 조치 줄 문구, '송출 중' 칩 |
| `/fires/{id}` | `serverTime` | 경과 시간을 서버 시계로 |

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | ~~화재 해제 비밀번호가 틀릴 때 401 → 로그아웃되는 문제~~ → **프론트에서 처리함** (2026-11-17 재원님 결정: 백엔드는 401 그대로). `api/emergency.js` 가 `skipAuthLogout: ['FIRE_PASSWORD_MISMATCH']` 를 붙여, 이 코드면 로그아웃하지 않음. 같은 요청에서도 `TOKEN_EXPIRED` · `AUTH_REQUIRED` 면 원래대로 로그아웃. 백엔드가 400 으로 바꿔도 그대로 동작 |
| 2 | 본사 관리자: 경보·화재 창은 **열람만** (셧다운·해제 단추 없음), 해제 잠금 풀기 승인만 함. 서버도 셧다운을 막음 — 코드는 `ACTION_NOT_ALLOWED` 가 아니라 403 `FORBIDDEN` (프론트는 코드로 나누지 않아 영향 없음) |
| 2-1 | **화재**: 백엔드에 `/fires*` 4개가 없고, 단계가 3단계(`ALARM`)까지만 나와 `FIRE` 가 오지 않음 (central `Codes`). `hazard`(FIRE/GAS)는 사고 종류라 '화재가 났다'는 뜻이 아님. `GET /notifications/summary` 의 `activeFire` 는 null 고정 → **실제 서버로는 화재 흐름이 나오지 않음** |
| 3 | 경보 창 '공정 내 센서 데이터': 명세서는 공정 센서 전부, 설계안은 상도 건조로 3종 → 프론트는 응답의 `sensors` 를 그대로 그림(지금 예시는 3개) |
| 4 | 화재 중 추가 셧다운(다른 설비): 화면설계서에 있지만 설계안 화재 창에는 없어 아직 안 만듦 |
| 5 | 화재가 해제된 뒤 본사 화재 상세를 '해제됨 17:21 · 한지우'로 10초 보여 주는 것(C5): 아직 안 만듦 (해제되면 바로 사업장 목록으로) |

---

## 11. 설비 기준값 관리 (명세서 F1, 화면설계서 F1)

백엔드 코드(`ThresholdController` · `ThresholdAdminService`)의 주소·필드에 예시 데이터를 맞췄습니다. 저장한 값은 예시 상황(`scenario.thresholds`)에 남아 **설비 상세·공정 화면의 주의/경보 판정과 기준선에도 바로 반영**됩니다.

| API | 프론트 사용 | 명세서·코드와 다른 점 |
| --- | --- | --- |
| `GET /equipment/tree?factoryId=` | 왼쪽 설비 선택 (공정별, 검색) | 설비 줄에 `tags` (센서 번호 목록 'CT-17 · TE-15 · CO-13') · `equipmentCount` 추가 필요 (없으면 센서 번호 검색만 안 됨) |
| `GET /equipment/{id}/thresholds` | 머리·계절 탭·센서 카드 | `equipmentType` (건조로면 '건조로 3대 모두' 보이기), `sensors[].max` (기준 띠 끝) 추가 필요. **`max` 가 없으면 프론트가 저장된 경보 · 주의 기준 · 현재값 중 큰 값의 1.25배로 계산** (2026-11-17, 예전엔 띠가 깨짐). 평소 값은 `setpoint` 를 씀 |
| `POST /thresholds/simulate` | 변경 시뮬레이션 (입력 멈추고 0.4초 뒤) | 응답에 `peaks [{ at, value }]` (그래프에 찍을 점) 추가 요청. 지금 백엔드는 `hits`(시각만) → **프론트가 둘 다 받음**: `hits` 면 그 시각에 가장 가까운 추이 값을 붙여 점을 찍음 (2026-11-17). 요청에 `season` 이 없어 지금 입력 값 그대로 계산. `warnHoldSec` 은 안 보냄 (서버 기본값) |
| `PUT /equipment/{id}/thresholds` | 저장 (확인 창 → 사유 한 줄) | 없음. 409 `THRESHOLD_CONFLICT` 면 안내 문구. 지속 조건(초)은 저장된 값 그대로 보냄 |
| `GET /thresholds/history?equipmentId=&from=&to=` | 오른쪽 변경 이력 + 계단 그래프 | 행에 '주의/경보 중 무엇이 바뀌었는지'가 없어 `before`·`after` 를 비교해 프론트가 정함 |
| `GET /sensors/series?ids=&from=&to=&step=30m` | 시뮬레이션 7일 그래프 | 명세서 7일은 10분 평균. 그래프가 무거워 30분 간격(`step`)을 가정 — 서버가 `step` 을 안 받으면 10분 그대로 써도 됨 |

### 정한 것 (2026-11-17 재원님 확인)

| 번호 | 내용 |
| --- | --- |
| 1 | **변경 사유는 필수**: 확인 창에서 사유가 비어 있으면 저장 단추가 꺼짐. 서버도 빈 `reason` 을 400 `REASON_REQUIRED` 로 막음 (해결됨) |
| 2 | **권한**: 본사 관리자·공장 담당자 모두 기준값을 수정할 수 있음 (설계안 그대로) |
| 3 | **'건조로 3대 모두' 저장**: 프론트가 설비마다 `PUT` 을 부름(바꾼 칸만 같은 값으로). 일부 설비만 실패하면 나머지는 저장되고, 화면에 '중도 건조로는 저장하지 못했습니다(사유)' 처럼 알림 |

### 선택 요청 (있으면 좋음)

| 번호 | 내용 |
| --- | --- |
| 1 | 여러 설비를 한 번에 저장하는 API (예: `PUT /thresholds/bulk` 에 `equipmentIds`). 있으면 '건조로 3대 모두' 가 한 번에 성공/실패해서 일부만 저장되는 일이 없어짐. 없어도 지금 방식으로 동작함 |

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | 기준값을 바꾸면 설비 상세 '이벤트 이력'(기준 변경 줄)에도 남길지 — 지금 예시는 기준값 화면 이력에만 남김 |

---

## 12. 통계·이력 관리 (명세서 D1, 화면설계서 '통계·이력 관리')

백엔드 코드(`StatsController` · `StatsService`)의 주소·조건·응답 모양에 예시 데이터를 맞췄습니다. 예시 데이터는 알림 목록 한 벌(오늘 = 지금 예시 상황의 알림, 지난 1년 = 고정 값)에서 요약·추이·공정별·요일시간·표·CSV 를 모두 계산해서 숫자가 서로 맞습니다.

| API | 프론트 사용 | 명세서·코드와 다른 점 / 추가 필요 |
| --- | --- | --- |
| `GET /stats/summary` | 요약 4칸 + 직전 기간 대비 | `checklistDoneRate` 는 0~100(%) 그대로 씀. '미완료 n건' 용 `checklistNotDoneCount` 추가 필요 (없으면 칸에 증감만 보임) |
| `GET /stats/trend` | 일별 알림 추이, 요약의 작은 그래프 | 주별로 바뀌는 기준: 화면설계서 90일, **코드 62일을 따름**. 칸마다 평균 조치 시간 `avgActionMinutes` 추가 필요 (요약 '평균 조치 시간' 작은 선) |
| `GET /stats/by-process` | 공정별 알림 | `IR` 이 있으면 '열화상' 막대도 그림. 0건 공정은 응답에 없어서 줄도 없음 (설계안은 4개 공정을 늘 보여 줌) |
| `GET /stats/heatmap` | 요일 · 시간대별 발생 | 없음 |
| `GET /stats/alarms` | 알림 이력 표 (한 쪽 6행) | `page` 는 0부터(화면은 1부터 보여 줌). **탭(전체 / 조치 중 / 해제)용 `status` 조건이 없음** → 프론트는 `status=ACTIVE|CLOSED` 를 보냄 (추가 필요). 조치 중 '경과 n분'용 `serverTime` 있으면 좋음 |
| `GET /stats/export` | 내보내기 (CSV) | 엑셀(xlsx)을 골라도 CSV 를 줌 → 화면에 '지금은 CSV 로 받습니다' 표시. 파일 이름·열은 코드 그대로 (화면설계서의 시트 4개 엑셀은 아직 없음) |
| `GET /reports/monthly` | 월별 보고서 PDF | **백엔드에 없음**. 고를 수 있는 달 목록 API 도 없음 → 예시 모드는 미리보기만, PDF 내려받기는 '백엔드 연결 후' 안내 |

### 모든 조건 공통

| 내용 |
| --- |
| `siteId` 조건이 없음 → 프론트는 `siteId` 를 같이 보냄 (본사 관리자가 '전체 공장' 을 고르면 그 사업장 공장만 보고 싶음). 서버가 안 받으면 다른 사업장 알림까지 섞일 수 있음 |
| 공장 담당자는 공장 선택이 담당 공장으로 고정 (`factoryId` 를 늘 보냄). 서버 `scope()` 가 범위 밖 공장이면 막는 것과 맞음 |
| 기간이 30일 넘으면 `400 RANGE_TOO_LARGE` — 프론트도 달력에서 미리 막음('최대 30일까지 조회할 수 있습니다') (2026-11-19 1년 → 30일. **백엔드 변경 필요**: StatsService.scope() 의 TODO 주석 참고) |
| 내보내기는 파일(Blob)로 받아서, 서버가 `400 EXPORT_TOO_LARGE` 같은 JSON 오류를 주면 공통 처리(`http.js`)가 내용을 읽지 못해 '요청을 처리하지 못했습니다' 로만 보임 → 백엔드 연결 때 내보내기 오류만 Blob 을 글자로 읽어 보여 주도록 고칠 예정 (지금은 공통 처리를 건드리지 않음) |
| 행의 공정·설비 이름은 `location`('5공장 · 도장 공정 · 상도 건조로 배기팬')을 나눠서 씀 → `processName` · `equipmentName` 을 따로 주면 더 안전 |
| 표의 '조치' 칸(체크리스트 n/n · 설비 셧다운 · 확인 대기)을 정할 필드가 없음 → 체크리스트가 있으면 'n/n', 경보이고 끝났으면 '설비 셧다운', 그 밖은 상태로 정함. `actionType` (CHECKLIST · SHUTDOWN) 이 있으면 좋음 |

### 화면설계서에만 있고 아직 안 만든 것

- 차트에서 걸러 보기 (막대·공정 조각·요일시간 칸을 누르면 표만 그 조건으로, 표 위 조건 단추)
- 요약 칸을 누르면 표 걸러 보기, 표 열 제목 정렬, 한 쪽 행 수(6·20·50) 바꾸기
- 행을 누르면 오른쪽 조치 내역 패널 (지금은 설비 상세로 이동 — 설계안 그대로)
- 주소(`?range=30d&plant=5…`)에 조건 남기기, 조건 초기화 단추, 선택 상자를 바꾼 뒤 '조회'를 눌러야 적용(파란 점) — 설계안은 바꾸면 바로 조회라서 설계안을 따름
- 근무조를 고르면 히트맵에서 그 시간대만 진하게

---

## 13. 점검 관리 — 수기 서류 OCR (명세서 E1, 화면설계서 E1)

**백엔드(develop)에 점검·서류·OCR 컨트롤러가 아직 없어** 명세서 E1 의 주소·응답 모양으로 예시 데이터를 만들었습니다(`api/inspections.js`, `api/mock/inspectionData.js`).
글자 인식은 백엔드가 **네이버 CLOVA OCR(템플릿)** 을 부르고 키도 백엔드에만 둡니다. 프론트에는 OCR 키·주소를 두지 않고, 명세서의 결과(`fields`)만 받습니다.

| API | 프론트 사용 | 명세서와 다른 점 / 추가 필요 |
| --- | --- | --- |
| `GET /inspections/summary?month=&factoryId=` | 왼쪽 이번 달 현황 도넛, 서류별 막대, 달력 점 | 응답에 `month` 추가 필요 (카드 제목 '2026년 11월') |
| `GET /doc-forms` | 인식 결과 칸 순서·구역 | 칸마다 **`group`**(구역 제목), 고르는 칸 `options`(양호/불량/해당 없음 등), 표의 줄 번호 `row` 추가 필요. F01 은 측정값 칸 `time`('08:50 작업 전'), 체크리스트 칸 `note`('배기팬 CF-03 가동')도 |
| `POST /inspections` (multipart) | 올리기 (진행률은 `onUploadProgress`) | 없음. 202 뒤 `GET …/ocr` 를 2초마다 부름 |
| `GET /inspections/{id}/ocr` | 원본 대조 (상자 `box` 는 그림 크기 대비 비율) | 저장된 서류를 열 때 '저장 · 11-12 16:44 · 오승민' 을 보이려고 `reviewedBy` · `reviewedAt` 추가 필요. `FAILED` 는 422 `OCR_FAILED` 로 받음 |
| `GET /inspections/{id}/file?page=` | 스캔 원본 | 로그인 토큰이 필요해 그림을 받아 `blob:` 주소로 씀 (서류를 바꾸거나 화면을 떠나면 정리) |
| `PUT /inspections/{id}` | 임시 저장(REVIEW) · 검수 완료(DONE) | 없음. 확인 필요가 남으면 409 `NEED_CHECK_REMAINS` 를 가정. 값을 고치거나 `checked: true` 면 확인 필요가 풀림 |
| `POST /inspections/{id}/ocr` | 다시 인식 | 없음 |
| `GET /inspections?from=&to=&formType=&status=&page=&size=` | 오른쪽 등록 목록(최근 7건), 날짜별 전체 보기 창 | 줄마다 `needCheckCount`(확인 필요 n개), `progress`(인식 중 %), `reviewedAt` 추가 필요 |
| (없음) 그날 점검 목록 | 달력 날짜를 눌렀을 때 작은 창 | **명세서에 없음** — 백엔드가 `GET /inspections/schedule?date=&factoryId=` 추가 → 2026-11-20 프론트 연결 (24장) |

### 서류 양식 F01 ~ F05 칸 (ArgOS 시연용 가상 양식, `api/mock/inspectionData.js` 의 `/doc-forms`)

예시 원본 그림은 F01 만 있고, F02 ~ F05 는 칸 정의만 둡니다. 판정 칸은 `양호 / 불량 / 해당 없음` 중 하나(`type: choice`).

| 양식 | 구역(group)과 칸 |
| --- | --- |
| F01 밀폐공간 작업허가서 | 작업 개요(신청일·작업 장소·설비번호·작업 기간·책임자·감시인·작업자) / 가스 농도 측정(CO 3회, 기준 30 ppm) / 사전 안전조치 8개 / 작업 종료 확인 |
| F02 환기·배기설비 점검일지 | 점검 정보(일자·시각, 구분 일상/주간/월간, 공장, 설비번호 EF-, 구역, 점검자, 확인자) / 점검 항목 12개(판정·측정값·비고 — 모터 전류 A, 베어링 ℃, 필터 차압 Pa, 배기 풍량 m³/min, CO 감지기 ppm 은 측정값) / 이상 사항 및 조치 3줄(항목 No·이상 내용·조치 내용·기한·완료일) / 종합 판정(정상 운전/조건부 운전/운전 중지)·종합 의견 |
| F03 수변전설비 점검일지 | 점검 정보(일자·시각, 구분 일상/월간/분기, 공장, 전기실명, 설비번호 TR-, 수전 전압 22.9kV/154kV/기타, 점검자, 확인자) / 계측 기록(R·S·T상 전압 V·전류 A, 최대 수요전력 kW, 부하율 %, 역률 %, 변압기 온도 ℃, 적산 전력량 kWh) / 점검 항목 10개(케이블 접속부 열화상 ℃, 전기실 환경 ℃ 는 측정값) / 이상 사항 및 조치 2줄 / 종합 판정(정상/주의/운전 정지 필요) |
| F04 안전보건교육 일지 | 교육 정보(일자, 시작~끝·총 시간, 공장, 장소, 대상 구역, 교육 구분 5종, 특별교육 작업 5종, 방법 집체/현장/시청각, 강사 성명·소속) / 교육 내용 3줄(과목·세부 내용·시간 분) / 교육 인원(대상·참석·불참, 불참자, 보충 교육일) / 수강자 명단 20명(성명·사번·서명) |
| F05 위험성평가표 | 평가 정보(일자, 구분 최초/정기/수시/상시, 공장, 공정·작업, 구역, 평가자, 참여 근로자) / 위험성 평가 및 감소 대책 8줄(세부 작업, 유해·위험요인, 재해 형태, 현재 안전조치, 가능성 1~3, 중대성 1~3, 위험성 = 가능성 × 중대성, 감소 대책, 개선 후 위험성, 담당/완료일). **양식의 예시 행은 인식 대상이 아님** |

### 정한 것 (2026-11-17)

| 번호 | 내용 |
| --- | --- |
| 1 | **본사 관리자는 점검 관리 열람만** (올리기·고치기·저장 없음). 바꿔 달라는 요청이 오면 바꿈 |
| 2 | 서류를 저장했을 때 연결된 점검 일정(`scheduleId`)과 공정 화면 '오늘 설비 점검'을 완료로 바꾸는 것은 **백엔드 연결 때 정함** (예시는 이번 달 현황 숫자만 바뀜) |

### 참고

| 내용 |
| --- |
| 예시 전용: 파일 이름에 '실패' 가 들어가면 인식 실패로 끝남 (화면 확인용). 예시 원본이 F01 뿐이라 올린 파일은 모두 F01 로 인식됨 |

---

## 14. 사용자 관리 (명세서 G1 · A1, 화면설계서 G1)

처음에는 백엔드에 `/users` 가 없어 명세서 G1 모양으로 만들었고(`api/users.js`, `api/mock/userData.js`), **이후 #28(사용자 관리 API, `user/UserController`)로 아래 API가 모두 생겼습니다.** 주소 · 보내는 값은 프론트와 같습니다. `/me` 는 코드의 `MeView`(id, loginId, name, phone, role, scope, pwResetRequired, lastLoginAt) 그대로 씁니다.

| API | 프론트 사용 | 백엔드 | 명세서와 다른 점 / 추가 필요 |
| --- | --- | --- | --- |
| `GET /users?role=&factoryId=&active=&q=&page=&size=` | 목록(한 쪽 15행) + 권한별 인원 도넛 | 있음 (#28) | 없음 (`inactiveCount` · 줄의 `factoryId` 있음) |
| `GET /users/{id}` | 오른쪽 패널(고른 사용자) | 있음 (#28) | 없음 (`scopeText` 있음) |
| `GET /users/check-login-id?loginId=` | 추가 폼 아이디 칸을 벗어날 때 | 있음 (#28) | 화면설계서는 `/users/check-id`, 명세서 이름을 따름 |
| `POST /users` | 사용자 추가 → 임시 비밀번호 한 번 표시(복사) | 있음 (#28) | 없음 |
| `PUT /users/{id}` | 이름·연락처·권한·담당 범위 | 있음 (#28) | 진행 중 체크리스트가 있는 공정 담당자는 공정을 뺄 수 없음 (409 `HAS_ACTIVE_CHECKLIST`, 백엔드에 있음) |
| `PATCH /users/{id}/status` | 수정 폼의 사용 상태 스위치 (저장할 때 같이 보냄) | 있음 (#28) | 마지막 공장 담당자 정지 막기 `409 LAST_PLANT_ADMIN` (백엔드에 있음) |
| `DELETE /users/{id}` | 삭제(확인 창) | 있음 (#28) | 자기 자신 삭제 `409 CANNOT_DELETE_SELF` (백엔드에 있음) |
| `POST /users/{id}/reset-password` | 비밀번호 초기화 → 임시 비밀번호 | 있음 (#28) | 없음 |
| `GET /me` | 내 정보(기본 패널) | 있음 | 담당 범위 문구(`scopeText`)가 없어 프론트가 공장 목록으로 만듦 |
| `PUT /me` | 내 정보 수정(이름·연락처) | 있음 (#28) | 명세서 기준 |
| `PUT /me/password` | 비밀번호 변경 창 | 있음 | 규칙은 코드 기준(8자 이상, 영문·숫자·특수문자 — 설계안은 영문·숫자 8자) |

- #28 과 프론트 예시 데이터의 작은 차이 (화면 동작에는 영향 없음): `check-login-id` 는 백엔드가 아이디 형식이 틀려도 `available: false`(예시는 중복만 봄) · 목록 순서는 백엔드가 권한 → id 순 · 이름 1~100자 · 연락처 길이 검사(`VALIDATION_FAILED`)는 백엔드에만 · 오류 문구 몇 개가 다름(화면은 서버 문구를 그대로 보여 줌) · `GET /me` 에는 `scopeText` · `createdAt` · `history` 가 없음

### 권한 범위 (화면설계서 G1 그대로, 예시 데이터도 같은 규칙)

| 권한 | 볼 수 있는 사람 | 줄 수 있는 권한 | 그 밖 |
| --- | --- | --- | --- |
| 본사 관리자 | 전체 | 본사 관리자 · 공장 담당자 · 공정 담당자 | 자기 자신은 권한 변경·정지·삭제 불가 |
| 공장 담당자 | 소속 공장의 공장 담당자 · 공정 담당자 | 공장 담당자 · 공정 담당자 (소속 공장 안에서만, 공장 선택 고정) | 본사 관리자는 보지도 고치지도 못함 |
| 공정 담당자 | 웹 화면 없음 (모바일 앱 '내 정보') | – | – |

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | ~~`PUT /me/password` 에서 지금 비밀번호가 틀리면 401 → 로그아웃되는 문제~~ → **프론트에서 처리함** (2026-11-17 재원님 결정: 백엔드는 401 그대로). `api/users.js` 가 `skipAuthLogout: ['PASSWORD_MISMATCH']` 를 붙여 로그아웃하지 않고 '지금 비밀번호가 맞지 않습니다'. 같은 요청에서도 `TOKEN_EXPIRED` · `AUTH_REQUIRED` 면 원래대로 로그아웃 (10장 화재 해제와 같은 방식) |
| 2 | 공장 담당자의 담당 범위: 설계안은 '울산공장 전체', 코드·예시 계정(`ulsan5_admin`)은 5공장(`factoryIds: [5]`) → 코드대로 **공장 단위**로 함 |
| 3 | 알림 받기 켜기·끄기: 설계안 폼에 스위치가 있지만 API 에 필드가 없음 → '모바일 앱 등록됨 · 알림 받음'(`pushReady`) 표시만 |

---

## 15. 공장 관리 — 설비 재배치 (명세서 H1, 화면설계서 H1, DB 명세 equipment)

저장 값의 이름 · 뜻을 **DB 테이블 명세(equipment)와 백엔드 `GET layout` 응답에 맞췄습니다** (2026-11-17). 백엔드에는 `GET /processes/{processId}/layout` 만 있고 `PUT`(배치 저장)은 아직 없어 예시 데이터로 저장합니다(`api/process.js` `saveProcessLayout`, `api/mock/layoutData.js`). 저장한 값은 예시 `GET layout` 에 덮어써져서 대시보드 공정 배치도 · 설비 상세 설비 위치에도 바로 보입니다.

| API | 프론트 사용 | 백엔드 | 비고 |
| --- | --- | --- | --- |
| `GET /processes/{processId}/layout` | 설비 목록 · 배치도 · 설비 정보 (대시보드와 같은 API) | 있음 | 설비 한 줄의 `type`(equipment_type) · `pos { x, y, z }`(pos_x · pos_y · pos_z) · `active`(is_active) 를 그대로 씀. `managers` 는 지금 빈 목록, `version` 은 1 고정 |
| `PUT /processes/{processId}/layout` | '저장' — 바꾼 설비만 한 번에 | **없음** | 본문 `{ version, equipment: { create, update, delete }, sensors: { … } }`. 지금은 `equipment.update` 만 씀. 판 번호가 다르면 `409 LAYOUT_CONFLICT` 가정 |

### 프론트가 보내는 `equipment.update` 한 줄 (GET 응답과 같은 이름)

```json
{ "id": 33, "pos": { "x": 412.5, "y": 718.0 }, "name": "도료 혼합실 A", "type": "TANK", "active": false }
```

| 필드 | DB 컬럼 | 뜻 |
| --- | --- | --- |
| `pos.x` · `pos.y` | `equipment.pos_x` · `pos_y` | 배치도 화면 좌표 — **이 화면 배치도 그림(SVG)의 좌표계에서 설비 가운데 점**. 미터가 아님. 옮긴 설비만 보냄 (값이 없으면 그림에 그려진 자리) |
| `name` | `equipment.name` | 설비 이름 |
| `type` | `equipment.equipment_type` | 설비 종류 코드. 화면에는 한글 이름 |
| `active` | `equipment.is_active` | 사용 상태 (false 면 배치도에 흐리게) |

- 끌어서 옮기기는 그대로입니다. '격자에 맞추기'를 켜면 격자 한 칸씩 옮기고, 저장할 때 그 자리를 화면 좌표로 바꿔 `pos` 로 보냅니다.
- 설비 정보의 위치 X · Y 는 이제 격자 칸 번호가 아니라 **화면 좌표 숫자**(pos_x · pos_y)를 보이고 고칩니다. 변경 줄도 'X 412 → 474 · Y 718 → 738' 처럼 씁니다.
- 연결된 센서 이름표는 설비와 함께 움직임 (프론트 그림에서만 — 센서 `pos` 는 바꾸지 않음)

### 설비 종류 코드 (`equipment_type`)

지금 돌아가는 DB 시드와 체크리스트 양식에 실제로 있는 코드만 화면 목록에 넣었습니다 (`factory/central-server` `V2__seed_ulsan_paint.sql`, gateway `V2__gateway_alarm_response.sql`).

| 코드 | 화면 이름 |
| --- | --- |
| `OVEN` | 건조로 |
| `FAN` | 배기팬 |
| `BURNER` | 버너실 |
| `TANK` | 탱크 |
| `PUMP` | 펌프 |
| `CAMERA` | 열화상 카메라 |

- 목록에 없는 코드가 오면 '코드 (목록에 없는 종류)', 비어 있으면 '정해지지 않음'으로 보입니다.
- 예시 데이터의 실링 · 부스 · 공조기 · 도료 혼합실 · 소각설비는 정해진 코드가 없어 `type: null` 로 둡니다 (예전 예시 값 `BOX` · `BOOTH` 는 뺌 — `BOX` 는 모양(shape) 값).

### 아직 안 만든 것 (설계안에는 있음)

- 설비 크기 · 모양 · 높이(`size_*` · `shape` · `pos_z`) · 담당자 바꾸기, 센서 연결 · 해제, 설비 추가 · 삭제 (`create` · `delete`)
- 배치도 그림 올리기(PNG · SVG · DXF), 사업장 · 공장 · 공정 추가
- 배치도 확대 · 축소, 옮기기 전 자리(점선 그림자 · 화살표)
- 다른 설비와 겹치는지 검사 (지금은 겹쳐도 저장됨)

### 정해야 할 것

| 번호 | 내용 |
| --- | --- |
| 1 | **`pos_x` · `pos_y` 의 좌표계**: DB 설명은 'SCADA 배치도 화면용 X · Y 좌표'뿐이라 원점 · 단위가 정해져 있지 않음. 프론트는 이 화면 배치도 그림(SVG viewBox)의 좌표 · 설비 가운데 점으로 둠. 다른 화면(SCADA 등)과 같은 좌표를 써야 하면 기준을 정해야 함. 지금 DB 시드는 비어 있음(NULL) |
| 2 | **`equipment_type` 코드가 두 가지로 적혀 있음**: 돌아가는 시드 · 체크리스트 양식은 영문(`OVEN` · `FAN` …), `db/argos_ddl.sql` 시드는 한글(`건조로` · `배기팬` · `탱크` …). 프론트는 영문을 따름 — 어느 쪽이 맞는지 |
| 3 | 부스 · 실링 · 공조기 · 도료 혼합실 · 소각설비의 `equipment_type` 코드 (DB 설명에 '부스'는 있지만 코드가 없음) |
| 4 | 판 번호가 다를 때(다른 사람이 먼저 저장) 409 와 응답 모양 — 지금은 문구만 띄우고 바꾼 값은 그대로 둠, '조회'로 다시 불러옴 |

---

## 16. 이용 로그 (명세서 G2, 화면설계서 G2)

백엔드(develop)는 `common/ActivityLogger` 로 `activity_log` 에 **쓰기만 하고 읽는 API(`/logs`)는 아직 없어** 명세서 G2 모양으로 예시 데이터를 만들었습니다(`api/logs.js`, `api/mock/logData.js`).

| API | 프론트 사용 | 백엔드 | 명세서와 다른 점 / 추가 필요 |
| --- | --- | --- | --- |
| `GET /logs?from=&to=&factoryId=&processId=&type=&q=&page=&size=` | 표(한 쪽 16행, 설계안) + 유형별 건수 + 시간대별 이용 | **없음** | `type` 은 화면 한 칸이 코드 여러 개라 **쉼표로 이어 보냄**(`LOGIN,LOGOUT`) — 명세서는 하나라 가정. `topUsers` 추가 필요 (아래) |
| `GET /logs/export` (같은 조건) | CSV로 내보내기 | **없음** | 파일 이름은 프론트가 정함(`ArgOS_이용로그_5공장_도장공정_20261111-20261117.csv`, 화면설계서). 내려받은 것도 로그에 남김(명세서) |

### 유형 코드 ↔ 화면 칸 (설계안 8칸)

| 화면 칸 | type 코드 | 백엔드에서 쓰는 곳 |
| --- | --- | --- |
| 로그인 | `LOGIN` · `LOGOUT` | 로그인 쪽 (명세서) |
| 조치 | `CHECKLIST` · `FAN` | `ChecklistService`(담당자 변경 등) · `EquipmentActionService`(팬) |
| 셧다운 | `SHUTDOWN` · `FIRE_CLEAR` | `EquipmentActionService` · (화재 해제는 백엔드 없음) |
| 기준값 변경 | `THRESHOLD` | `ThresholdAdminService` |
| 설비 수정 | `LAYOUT` | `ThresholdAdminService` (공장 관리 저장 API 생기면 거기도) |
| 사용자 | `USER` | `UserService` (#28) |
| 서류 등록 | `INSPECTION` | (명세서) |
| 내려받기 | `EXPORT` | `StatsService` (+ `/logs/export`) |

- 명세서 type 목록에 없는 `CHECKLIST` 를 백엔드가 쓰고 있어 화면은 '조치' 칸으로 묶음
- **로그인 기록**: 로그인 성공 · 실패는 `login_history` 에만 남고 `activity_log` 에는 `LOGIN` 이 없음 (로그아웃 · 비밀번호 변경은 `activity_log`). `/logs` 를 만들 때 두 표를 합쳐 '로그인' 칸에 보여 주세요
- **시각 기준이 섞임**: 로그아웃 · 비밀번호 변경 기록은 실제 시각(`now()`), 그 밖의 `ActivityLogger` 기록은 시연 시계. 또 `ActivityLogger` 는 `ip_addr` 를 남기지 않음 (화면 '로그인 (PC, 10.21.4.17)' 같은 내용)

### 응답에 있으면 좋은 것 (명세서에 없음)

| 필드 | 쓰는 곳 |
| --- | --- |
| `topUsers: [{ loginId, name, count }]` (4명) | 시간대별 이용 카드 아래 '많이 쓴 사용자' (설계안). 없으면 빈 칸 |
| `countsByType` 를 **유형 조건 없이** 셈 | 유형 칸을 눌러 거른 뒤에도 다른 칸 건수가 보이게 (예시 데이터는 이렇게 함) |

### 보이는 범위 (화면설계서 G2)

- 본사 관리자: 전체 / 공장 담당자: 소속 공장 기록만, **본사 관리자의 기록은 안 보임** → 서버가 걸러 줘야 함 (프론트는 공장 선택만 잠금)

---

## 17. 채팅 (명세서 I2, 화면설계서 I2)

백엔드(develop)에는 **채팅 API 가 없고** `GET /notifications/summary` 의 `unreadChatCount` 도 0 고정이라, 명세서 I2 모양으로 예시 데이터를 만들었습니다(`api/chat.js`, `api/mock/chatData.js`).

| API | 프론트 사용 | 백엔드 | 명세서와 다른 점 / 추가 필요 |
| --- | --- | --- | --- |
| `GET /chat/rooms` | 대화 상대 목록 · 상단 바 채팅 숫자(안 읽은 수 합계) | **없음** | 없음 (방 = 사업장, 본사 관리자 3곳 · 공장 담당자 1곳) |
| `GET /chat/rooms/{roomId}/messages?before=&size=` | 글 30개씩, 맨 위 '이전 메시지 더 보기' | **없음** | `items` 순서: 프론트는 **오래된 것 → 최근** 으로 받는다고 가정 |
| `POST /chat/rooms/{roomId}/messages` | 보내기 `{ body, alarmId }` | **없음** | 입력 최대 500자(화면설계서) — 명세서 오류 기준은 1,000자 |
| `POST /chat/rooms/{roomId}/read` | 패널에서 그 방을 보고 있으면 읽음 | **없음** | 없음 |
| SSE `chat.message` | 새 글 → 방 목록 · 열린 방 글 다시 받기 | **StreamHub 에 없음** | `api/live.js` 다시 불러오기 이벤트에 `chat.message` 추가함 → 이 이벤트가 오면 다른 화면도 조용히 다시 불러옴 |

### 글에 붙인 알림 (`alarm`)

| 필드 | 명세서 | 쓰는 곳 |
| --- | --- | --- |
| `id` · `title` · `equipmentName` | 있음 | 말풍선 안 알림 카드 |
| `level` | **없음** | 카드 왼쪽 막대 색 (주의 주황 · 경보/화재 빨강) — 없으면 주황 |
| `sensorTag` · `value` · `unit` | **없음** | 카드 둘째 줄 'CT-17 22.4 A' — 없으면 안 보임 |

---

## 18. 대시보드 카드 정리 (2026-11-18, 재원님 결정)

사업장 현황 · 공장 대시보드 · 공정 3D 설비 배치도 오른쪽 카드를 정리했습니다. **새 API · DB 변경은 없습니다.** 이미 있는 응답에서 쓰는 필드만 바뀌었습니다.

### 화면에서 바뀐 것

| 화면 | 바뀐 것 |
| --- | --- |
| 사업장 현황 (`/sites/{siteId}`, 울산공장) | '센서별 단계 현황' 카드 삭제. '전체 공장 상태'를 화면 끝까지 넓히고, 공장마다 전류 · 온도 · CO 칸에 **지금 알림(주의 · 경보) 센서 수만** 표시 (전체 센서 수 · 도넛 삭제, 알림 없으면 초록 체크). 오른쪽 줄은 '전일 시간대별 알림' → '실시간 알림' |
| 공장 대시보드 (`/sites/{siteId}/factories/{factoryId}`, 5공장) | '센서별 단계 현황' 카드 삭제, '공정별 설비 상태'를 화면 끝까지 |
| 공정 3D 설비 배치도 (`.../processes/{processId}`) | '센서별 단계 현황' 자리에 **'기준값 근접 센서'** (주의 기준에 가까운 순 5개, 값이 바뀌면 순위가 바로 바뀜, 줄을 누르면 그 설비 상세로) |
| 공통 | '시간대별 알림' → **'전일 시간대별 알림'** (어제 하루) |

### API 영향

| API | 바뀐 점 | 백엔드 할 일 |
| --- | --- | --- |
| `GET /alarms/hourly` | `date=today` → **`date=어제 날짜`** (`YYYY-MM-DD`, 시연 시계 기준) | 아래 '정할 점 1' |
| `GET /sites/{siteId}/overview` | 사업장 합계 `levelsByType` 는 화면에서 더 이상 안 씀. `factories[].levelsByType` 는 `WARN` · `ALARM`(+`FIRE`) 개수만 씀 | 없음 (지워도 되고 남겨도 문제없음). `factories[].sensorCount` · `todayAlarmCount` 는 지도 말풍선에서 계속 씀 |
| `GET /factories/{factoryId}/overview` | 공정 `levelsByType` 를 합쳐 그리던 카드가 없어짐 (`processes[]` 는 그대로 씀) | 없음 |
| `GET /processes/{processId}/layout` + `/status` | 기준값 근접 센서를 **프론트가 계산**: `layout.sensors[]` 의 `type` · `unit` · `equipmentId` + `status.sensors[]` 의 `value` · `level` · `warn` · `alarm` → `value ÷ warn` 이 큰 순 5개. 주의 기준(`warn`)이 없는 센서(열화상 등) · 설비에 안 붙은 예비 센서는 뺌 | 없음 (develop 백엔드에 이미 있는 필드) |
| SSE `sensor.value` (`scope=process:{id}`) | 백엔드는 이미 보냄. 프론트 `api/live.js` 는 아직 이 이벤트를 쓰지 않음 | 없음. **프론트 할 일**: 연결 전까지 실제 서버 모드에서는 5초마다 `/status` 를 다시 받아 순위를 바꿈 |

### 정할 점

1. **전일 야간 범위**: 화면은 '주간 07~19시 · 야간 19~07시'. 지금 `AlarmService.hourly` 는 `date` 하루(00~24시) 알림만 모아서 야간 칸에 **같은 날 00~06시**를 넣습니다. '전일' 이면 `date 07:00 ~ 다음 날 07:00` 범위로 모으는 게 맞아 보입니다 (예: 11-17 주간 07~19시, 야간 11-17 19시 ~ 11-18 07시).
   - **2026-11-20 바뀜**: 화면이 주간/야간 구분 없이 하루(00~24시) 한 줄이 되어 범위를 다시 `date 00:00 ~ 다음 날 00:00` 으로 요청합니다 → [`docs/api/hourly-alarm-one-day.md`](../../../docs/api/hourly-alarm-one-day.md)
2. **낮을수록 위험한 기준(미달)**: 이벤트에 `lowSide`(미달)가 있는데, 기준값 근접 센서는 '값 ÷ 주의 기준(높을수록 위험)' 으로만 셉니다. 하한 기준 센서가 실제로 있다면 `status.sensors[]` 에 하한 여부를 알려 주세요 (그 센서는 '주의 기준 ÷ 값' 으로 세도록 프론트를 고침).

## 19. 오늘 알림 내역 (2026-11-19, 재원님 요청)

사업장 현황 · 공장 대시보드 · 공정 화면 오른쪽의 '실시간 알림' 카드를 **'오늘 알림 내역'** 으로 바꿨습니다 (`features/dashboard/AlarmListCard.vue`).

- 표시 기준: **오늘 0시부터 지금까지 하루 누적** (전에는 최근 20건만). `getAlarms` 기본 `size` 20 → **500** (`GET /alarms?date=today&size=500`). central-server 는 한 번에 최대 500건까지 줌 → 백엔드 할 일 없음.
- 제목 옆 '누르면 조치 내역' 글자 대신 **'과거 알림' 버튼** → 통계·이력 관리 화면으로 이동 (지난 알림은 그 화면에서 기간을 골라 봄). 오른쪽은 '누적 n건 | 미조치 n건'.
- 정할 점: 하루 알림이 500건을 넘으면 카드에는 최근 500건만 보임 (서버 한도와 같음).

## 20. 가스 알림 배기팬 자동 제어 · 기준값 관리 이동 (2026-11-19, 재원님 결정)

- 가스(`hazard = 'GAS'`) 주의 → 배기팬 75%(1,350 rpm), 경보 → 100%(1,800 rpm). **서버가 알림을 만들 때 명령을 보냄** (프론트는 보여 주기만).
- 설비별 '배기팬 자동 제어' 스위치(공장 관리 › 설비 정보) — 자동일 때만 올림, 수동이면 알림 창에 안내만.
- 알림 응답(`GET /alarms` items, `/alarms/{id}/detail`, `/alarms/{id}/response`)에 `fanAuto` 한 칸, layout GET/PUT 에 `fanAuto` 한 칸.
- '설비 기준값 관리' 메뉴·화면 삭제 → 공장 관리 › 설비 정보 › '설비 기준값 관리' 창. 기준값 API 는 그대로.
- 자세한 내용: **`docs/fan-auto-api.md`**(API), **`docs/fan-auto-db.md`**(DB: `equipment.fan_auto`, `equipment_command.source`).

## 21. 설비 설정 스위치 이동 · 대시보드 배기팬 조절 삭제 (2026-11-19 2차, 재원님 요청)

- 대시보드(공정 배치도 · 설비 상세)에서 배기팬을 눌러 회전수를 바꾸던 창 삭제 → 웹은 `POST /equipment/{id}/fan` 을 더는 안 부름 (API는 지우지 않음).
- 설비 상세에 '설비 기준값 관리' 버튼 → 같은 창이 설비 상세 위에 뜸 (화면 이동 없음, API 변경 없음).
- 사용 상태 · 배기팬 자동 제어 스위치를 기준값 관리 창으로 옮김 → **새 `PATCH /equipment/{id}/settings`** `{ active?, fanAuto? }`, `GET /equipment/{id}/thresholds` 에 `active` · `fanAuto`, SSE `equipment.updated`.
- DB 변경 없음. 자세한 내용: **`docs/equipment-settings-api.md`**.

## 22. 주의·경보 창 유형 문구 · 주의 상세 창 합치기 (2026-11-19, 재원님 요청)

- 주의·경보 창에 유형 문구: `hazard` GAS → '가스 주의/경보', FIRE → '화재 주의/경보' (없으면 센서 종류로).
- '센서 데이터 · 열화상 확인' 창을 주의 상세 창에 합침 (`GET /alarms/{id}/detail` 한 번으로 그림). 새 API·DB 없음.
- 요청 1개: `GET /alarms/{id}/response` 의 `title` 에 유형 넣기. 자세한 내용: 저장소 **`docs/api/alarm-modal-hazard.md`**.

## 23. 주의 상세 창 추이 그래프 2개 · 공장 말풍선 위치 (2026-11-19, 재원님 요청)

- 주의 상세 창 아래 추이 그래프 2개: 화재 주의 = 온도·전류, 가스 주의 = CO·전류.
- 요청 1개: `GET /alarms/{id}/detail` 에 센서별 추이 `trends[]` 추가 (지금은 알림 센서 하나의 `trend` 만). 없으면 화면은 `trend` 하나만 그림. 자세한 내용: 저장소 **`docs/api/warn-detail-trends.md`**.
- 사업장 지도에서 공장을 누르면 뜨는 공정 목록 말풍선을 공장 오른쪽에 띄움 (화면만 바뀜, API 없음).

## 24. 백엔드 피드백 4건 반영 (2026-11-20)

- 그날 점검 목록: 예시 데이터만 쓰던 것을 `GET /inspections/schedule?date=&factoryId=` 로 바꿈. 작은 창에 시각 · 공정 · 담당자 같이 표시.
- 열화상 사진: `thermal.imageUrl`(`/api/v1/thermal/images/{fileId}`)은 로그인이 필요해 axios 로 blob 을 받아 띄움 (`useThermalSrc`).
- 배기팬 수동 승인: `fanAuto.status = AWAITING` 이고 `commandId` 가 있으면 알림 창에 '승인' · '거절' 버튼 (공장 담당자만) → `POST /fan-commands/{commandId}/approve · deny`.
- 실제 설비 코드: 배치도 그림 짝 맞춤을 `PT-TANK` · `ED-OVEN` · `TC-OVEN` … 기준으로 바꿈. 배기팬 · 버너실 · 펌프 · 열화상 카메라는 같은 그림에 묶음.
- 자세한 내용: 저장소 **`docs/api/backend-feedback-1120.md`**.

## 25. 사업장 · 공장 · 공정 추가 (2026-11-20, 재원님 요청)

- 사업장 선택 '사업장 추가' → 이름만 입력('창원' → '창원공장') → `POST /sites` → 그 사업장 공장 관리로 이동. 공장이 없는 사업장은 상단 메뉴에 '관리'만.
- 공장 관리 공장 · 공정 드롭다운 맨 아래 '공장 추가' · '공정 추가' → `POST /factories` · `POST /processes`. 새 공정은 설비 없는 빈 배치도.
- `code` 는 프론트가 자동으로 만들어 보냄 (서버가 비워도 만들어 주면 지움). 자세한 내용: 저장소 **`docs/api/scope-add.md`**.

## 26. 새 공정에 설비 추가 (2026-11-20, 재원님 요청)

- 도장 공정 그림이 없는 공정(새로 추가한 공정)은 빈 배치판 + 설비 목록 '설비 추가' → 지금 있는 설비 15종에서 골라 놓기.
- 저장은 이미 있는 `PUT /processes/{id}/layout` 의 `equipment.create` (code 는 비워서 서버가 만듦). 새 API · ERD 변경 없음.
- 확인 요청: 부스 · 실링 등 `equipment_type` 이 null 인 설비 저장 가능 여부. 자세한 내용: 저장소 **`docs/api/equipment-add.md`**.

## 27. 공장 관리 설비 추가 · 삭제, 설비 그림 그대로 (2026-11-20, 재원님 요청)

- 도장 공정에서도 '설비 추가' · '설비 삭제'. 추가한 설비는 도장 공정 그림의 그 설비 그림을 복사해서 놓음 (새 공정도 같은 배치도 바닥).
- 새 API 없음: `PUT /processes/{id}/layout` 의 `equipment.delete`, `create[].shape`(그림 이름 → DB `equipment.shape`).
- 삭제는 서버 규칙 그대로: 센서가 붙은 설비는 지우지 않고 사용 안 함.
- 확인 요청 4건(`shape` 응답, 삭제 결과, 시드에 없는 그림 설비, `equipment_type` null): 저장소 **`docs/api/paint-equipment-edit.md`**.

