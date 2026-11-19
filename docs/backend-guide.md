# ArgOS 백엔드 가이드

서버 실행 → API 명세 보기 → 작업 현황(체크리스트)을 한 곳에 정리한 문서.
최종 정리: 2026-11-19 (배기팬 자동 제어 · 통계·이력 개편 반영)

---

## 1. 서버 실행

### 준비 (한 번만)
- PostgreSQL 실행 중 (포트 5432, DB 비밀번호 `1234`)
  - DB: `argos_factory` (중앙), `argos_gateway` (연계)
- JDK 17 이상. 시스템 기본이 JDK 11 이라 아래 명령에서 `JAVA_HOME` 을 직접 지정한다.
- Maven 은 IntelliJ 에 들어 있는 것을 쓴다 (PATH 에 mvn 없음).

### 실행 순서
**① 중앙 서버(8081) → ② 연계 서버(8080) → ③ 프론트(5173)**
연계 서버는 중앙 서버에서 데이터를 받아 오므로 중앙을 먼저 켠다. 순서가 바뀌면 API 가 502.

IntelliJ 아래 **Terminal** 탭을 3개 열고 하나씩 실행 (PowerShell 기준).

#### ① 중앙 서버 (8081)
```powershell
cd C:\Users\hayeon\workspace\ArgOS\factory\central-server
$env:JAVA_HOME="C:\Users\hayeon\.jdks\ms-25.0.4.1"
$env:FACTORY_DB_PASSWORD="1234"
$env:ARGOS_DEV_API="true"
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" spring-boot:run
```
`Started FactoryCentralServerApplication` 이 나오면 다음으로.

#### ② 연계 서버 (8080)
```powershell
cd C:\Users\hayeon\workspace\ArgOS\gateway\backend
$env:JAVA_HOME="C:\Users\hayeon\.jdks\ms-25.0.4.1"
$env:GATEWAY_DB_PORT="5432"
$env:GATEWAY_DB_PASSWORD="1234"
$env:GATEWAY_JWT_SECRET_BASE64="<로컬용 비밀키>"
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" spring-boot:run
```
- `<로컬용 비밀키>`: 32바이트 이상을 Base64 로 만든 값. 만들기:
  `python -c "import os,base64;print(base64.b64encode(os.urandom(32)).decode())"`
  (비밀키는 git 에 올리지 않는다. 바꾸면 기존 로그인만 풀림)
- `GATEWAY_DB_PORT=5432` 필수 (기본값이 5433).

`Started GatewayBackendApplication` 이 나오면 다음으로.

#### ③ 프론트 (5173)
```powershell
cd C:\Users\hayeon\workspace\ArgOS\gateway\frontend
npm run dev
```

### 켜졌는지 확인
| 확인 | 주소 |
| --- | --- |
| 중앙 서버 | http://localhost:8081/actuator/health → `UP` |
| 연계 서버 | http://localhost:8080/actuator/health → `UP` |
| 화면 | http://localhost:5173 |

### 로컬 계정 (비밀번호 `Argos1234!`)
| 아이디 | 권한 | 비고 |
| --- | --- | --- |
| `admin` | 본사 관리자 (HQ_ADMIN) | 웹 |
| `plant5` | 공장 관리자 (PLANT_ADMIN), 5공장 | 웹 |
| `worker12` | 작업자 (WORKER), 공정 12 | 앱 로그인만 |

### 끄기 · 다시 켜기
- 끄기: 해당 터미널에서 `Ctrl + C`
- 서버 코드를 바꿨으면: `Ctrl + C` → `↑` 로 마지막 mvn 명령 다시 실행
  (같은 터미널이면 `$env:` 값은 남아 있음. 새 터미널이면 처음부터 다시 입력)
- 프론트는 코드 바꾸면 자동 반영

### 자주 나는 오류
| 오류 | 원인 · 해결 |
| --- | --- |
| `Could not resolve placeholder 'GATEWAY_JWT_SECRET_BASE64'` | 연계 서버 비밀키 환경변수 빠짐 |
| `Connection refused` … `5433` | `GATEWAY_DB_PORT=5432` 빠짐 |
| `release version 17 not supported` | `JAVA_HOME` 이 JDK 11. 위 명령대로 지정 |
| `Port 8080 was already in use` | 이미 켜진 서버 있음. 끄거나 작업 관리자에서 java 종료 |
| 연계 서버 API 가 502 | 중앙 서버가 꺼져 있음 |

---

## 2. API 명세 보기

> `feature/api-docs` 브랜치가 develop 에 머지된 뒤부터 사용 가능.

### 방법 1. 브라우저 (Swagger UI)
1. 연계 서버를 켠다.
2. http://localhost:8080/swagger-ui.html 접속 (로그인 없이 열림)
3. `01 로그인 · 내 정보` → `POST /api/v1/auth/login` → **Try it out** → 실행
   ```json
   { "loginId": "admin", "password": "Argos1234!", "client": "WEB" }
   ```
4. 응답의 `accessToken` 을 복사 → 오른쪽 위 **Authorize** 에 붙여넣기
5. 이제 나머지 API 를 바로 호출해 볼 수 있다.

- 화면별로 01~18 묶음(로그인, 대시보드, 알림 … 시스템)으로 나뉘어 있다.
- 원본 명세(OpenAPI JSON): http://localhost:8080/v3/api-docs

### 방법 2. Postman
1. Postman → **Import** → `gateway/backend/docs/postman/ArgOS-gateway.postman_collection.json`
2. 컬렉션 변수 `baseUrl` 확인 (기본 `http://localhost:8080`)
3. `01 로그인 · 내 정보` → `POST /auth/login` 보내기 → 토큰이 자동 저장된다.
4. 나머지 요청은 그냥 Send.
   - 경로 값 기본: `factoryId=5`, `processId=12`, `siteId=1`, 나머지 `1`
   - 선택 쿼리는 꺼져 있음 (Params 에서 체크하면 켜짐)

**팀 공유**: Postman 에서 Team 워크스페이스를 만들고 컬렉션을 옮긴 뒤 팀원 초대.
요청마다 실제 응답을 **Save Response** 로 저장해 두면 예시가 붙어 문서처럼 보인다.

**API 가 바뀌었을 때**: 서버를 켠 상태에서
```powershell
cd C:\Users\hayeon\workspace\ArgOS\gateway\backend\docs\postman
python make_collection.py
```
→ 컬렉션 파일이 다시 만들어진다. Postman 에 다시 Import.

### 응답 필드 설명은 어디서?
명세서에는 요청 주소·파라미터·요청 본문은 잘 나오지만, 응답은 대부분 `Map` 이라 필드가 자세히 안 나온다.
응답 필드 · 명세서와 다른 점은 아래 문서를 기준으로 본다.

| 문서 | 내용 |
| --- | --- |
| `gateway/backend/docs/API_IMPLEMENTED.md` | 연계 서버 API 상세 (화면 → 연계) |
| `factory/central-server/README.md` | 중앙 서버 internal API (연계 → 중앙) |

---

## 3. 작업 현황 (체크리스트)

이 표가 백엔드 작업 현황이다. 작업이 끝나면 이 표를 같이 갱신한다.
✅ 완료(develop 머지) · 🔀 PR 대기 · ❌ 안 됨 · ⏸ 보류 · 🔧 설정만 하면 됨

### 머지 대기 (PR 만들어서 머지 필요)
| 상태 | 브랜치 | 내용 |
| --- | --- | --- |
| 🔀 | `feature/erd-v5` | 테이블 명세서 v5 + 최신 DDL (배기팬 자동 제어, 알림 센서 샘플, 통계·이력 개편), FcmClientTest 수정 |

### 중앙 서버 (8081)
| 상태 | 항목 |
| --- | --- |
| ✅ | 스키마 · 시드, 판정 엔진 (주의 24샘플 / 경보 6샘플 / 회복 6샘플) |
| ✅ | 7일치 더미 데이터 가져오기, 시연 시계 (2026-11-16 09:00 시작, 리셋 API) |
| ✅ | 실시간 피드, internal API (master · status · series · events · alarms · feed) |
| ✅ | 설비 명령 시뮬레이션, 기준값 관리 internal API |
| 🔀 | ERD v5: 즉시 경보 · 회복 여유값 · 일 집계(`sensor_daily`) · 이벤트 키 · 명령 상태 확장 (`feature/erd-v5`) |
| 🔀 | 배기팬 자동 제어 (가스 주의 45Hz · 경보 60Hz, 종료 때 40Hz 복귀), 설비 설정 저장 |
| 🔀 | 보관 기간: 1분 추이 · 샘플 1년 (DDL 기준). 이벤트 · 알림 · 명령은 지우지 않음 |
| ❌ | VMS 열화상 IR-301 받는 API (`POST /vms/ir`, 전송 규격 확인 중) |

### 연계 서버 (8080) — 화면별
| 상태 | 화면 | 항목 |
| --- | --- | --- |
| ✅ | A1 로그인 · 내 정보 | 로그인 13시간, 연장 1시간(새 토큰), 쿠키 제거, 내 정보 수정 |
| ❌ | A1 내 정보 | `GET /me` 에 `scopeText` (담당 범위 문구) |
| ✅ | 대시보드 · 공정 현황 | 현황, 배치도, 센서 시계열, `limitType` |
| ✅ | 알림 | 목록 · 상세 · 확인 · 종료 · 대응, 전일 시간대별(야간 19시~07시) |
| ✅ | 설비 명령 | 팬 · 설비 정지 · 공정 정지 · 작업자 호출 |
| 🔀 | 배기팬 자동 제어 | 알림 응답 `fanAuto`, `PATCH /equipment/{id}/settings`, 기준값 조회 `active`·`fanAuto` |
| ✅ | 체크리스트 | 템플릿 · 배정 · 확인 · 리마인드 · 보고 |
| ✅ | C4F · C5 · M2 화재 | 화재 목록 · 상세 · 해제 · 잠금 해제 승인 · 대피 확인 |
| ❌ | C4F 화재 상세 | `thermal.cameraTag`, `values{thermalMax,temp,co}`, `actions[].running`, `serverTime` |
| ❌ | 화재 시나리오 | 상도 CSV 로 화재 판정 점검(A), 화재 영상 `화재3단계.mp4` 연결(B) |
| ✅ | F1/D2 기준값 관리 | 설비 트리 · 기준값 조회/저장 · 시뮬레이션 · 이력 |
| ❌ | F1 기준값 관리 | `equipmentCount`, `equipmentType`, `sensors[].max`, simulate `peaks` |
| ⏸ | F1 기준값 관리 | 여러 설비 한 번에 저장 (있으면 좋음, 중앙도 수정 필요) |
| ✅ | D1 통계 · 이력 | 요약 · 추이 · 공정별 · 히트맵 · 알림 목록, CSV · xlsx, 월간 보고서 PDF |
| 🔀 | D1 통계 · 이력 | 개편 반영: 조건 `hazard`·`status`·`siteId`, 알림 상세 `GET /stats/alarms/{id}`, CSV 새 열 |
| ✅ | E1 점검 · OCR | 업로드 → 인식 → 검토 → 완료, 오늘 점검 |
| ❌ | E1 점검 관리 | summary `month`, doc-forms `group`·`options`·`row`, `GET /inspections/schedule?date=` |
| 🔧 | E1 OCR | 지금은 시연용 인식기. 실제 인식은 `CLOVA_OCR_URL`·`CLOVA_OCR_SECRET` 설정 |
| ✅ | G1 사용자 관리 | 목록 · 등록 · 수정 · 상태 · 비밀번호 초기화 |
| ✅ | G2 이용 로그 | 목록 · 내보내기 |
| ✅ | H1 공장 관리 | 사업장 · 공장 · 공정, 배치도 · 도면 업로드 |
| ✅ | I2 채팅 | 방 · 메시지 · 읽음 |
| ❌ | I2 채팅 | 첨부 알림에 `level`, `sensorTag`, `value`, `unit` |
| ✅ | 실시간 | SSE `/stream` |
| 🔧 | 푸시 · 방송 | FCM `FCM_CREDENTIALS_PATH`, 방송 `BROADCAST_API_URL` 설정 (비우면 로그만) |
| ✅ | API 명세 | Swagger UI · Postman |

### 범위 밖 · 나중
| 상태 | 항목 |
| --- | --- |
| ⏸ | Kafka(엣지 → 수집) · Redis(수집 → 중앙) 연동 — 지금은 HTTP · 1초 폴링, Redis 는 DAS 담당 |
| ⏸ | `thermal.frame`, 점검 서류 PDF 를 쪽 그림으로 바꾸기 |
| ⏸ | 시연 데이터 · 봇 시나리오 |

### 다음에 할 순서 (제안)
1. `feature/erd-v5` 머지
2. ❌ VMS 열화상 받는 API (전송 규격 답변 후)
3. ❌ 화면 필드 보완 (점검 · 화재 상세 · 채팅 · 내 정보)
4. ❌ 화재 시나리오 점검
