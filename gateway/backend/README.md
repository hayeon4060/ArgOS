# Gateway Backend

연계 PC에서 웹 프론트엔드와 React Native 앱에 API를 제공하는 초기 Spring Boot 프로젝트입니다.

## 책임 범위

- 로그인과 역할 기반 접근 제어
- 대시보드, 경보·조치 이력, 통계 API
- SSE 기반 실시간 상태 및 경보 전달
- 사용자·공장·공정·설비·기준값 관리 API
- 점검 일정, 체크리스트, 문서 인식 결과 관리
- FCM 푸시 요청 및 중앙 서버와 허용된 데이터·명령 교환

연계 서버는 센서·PLC·카메라에 직접 접속하지 않습니다. FCM, OCR, 중앙 서버 연동은 계약이 확정된 뒤 별도 모듈로 구현합니다.

## 개발 환경

- Java 17
- Spring Boot 3.4.5
- PostgreSQL 16
- Maven

## 시작 방법

1. `.env.example`을 참고해 환경변수를 설정합니다.
2. 연계 서버용 PostgreSQL을 실행합니다.
3. Maven이 설치된 환경에서 `mvn spring-boot:run`을 실행합니다.

상태 확인: `GET /actuator/health`  
서비스 확인: `GET /api/v1/system/info`

## 로그인 API

모든 경로는 `/api/v1`부터 시작합니다. 액세스 토큰은 30분이며, 최초 세션은 로그인 후 13시간에 만료됩니다. `POST /auth/extend`를 누를 때마다 기존 만료 시각에 1시간을 더합니다. 연장은 자동으로 일어나지 않습니다.

| 경로 | 용도 |
| --- | --- |
| `POST /auth/login` | `loginId`, `password`, `client`(`WEB`/`APP`)로 로그인 |
| `POST /auth/refresh` | 액세스 토큰 자동 갱신. 세션 만료 시각은 그대로 유지 |
| `POST /auth/extend` | 토큰으로 세션 만료 시각을 1시간 연장 |
| `POST /auth/logout` | 세션 즉시 폐기 |
| `GET /me` | 내 정보와 담당 범위 조회 |
| `PUT /me/password` | 본인 비밀번호 변경 |

웹의 갱신 토큰은 HttpOnly 쿠키, React Native 앱의 갱신 토큰은 응답 본문으로 전달합니다. 앱은 OS 보안 저장소에 보관해야 합니다. 서버 DB에는 갱신 토큰 원문 대신 SHA-256 해시를 저장합니다. 보호된 요청은 `Authorization: Bearer <accessToken>`을 사용합니다.

환경변수 `GATEWAY_JWT_SECRET_BASE64`에 32바이트 이상 무작위 값의 Base64 인코딩을 넣어야 합니다. HTTPS 환경에서는 `GATEWAY_COOKIE_SECURE=true`로 설정합니다. 비어 있는 개발 DB에서만 최초 본사 관리자를 만들려면 `GATEWAY_BOOTSTRAP_ADMIN_LOGIN`과 `GATEWAY_BOOTSTRAP_ADMIN_PASSWORD`를 함께 설정합니다. 첫 로그인 후 비밀번호 변경이 필요합니다.

> **2026-11-18 변경**: 웹은 refresh 쿠키(`argos_rt`)를 쓰지 않고 JWT(Bearer) 하나로 인증합니다. 토큰 유효 시간은 13시간(`access-seconds: 46800`)이고,
> `POST /auth/extend` 는 세션을 1시간 늘리면서 새 `accessToken` 을 돌려줍니다. `POST /auth/refresh` 는 앱(본문 `refreshToken`) 전용입니다.
> 위 설명 중 30분 토큰·웹 쿠키 부분은 이 변경 전 내용이고, `GATEWAY_COOKIE_SECURE`·`cookie-same-site` 설정은 이제 쓰지 않습니다.

`src/main/resources/db/migration/V1__gateway_auth.sql`은 연계 DB의 인증 테이블을 만드는 초기 마이그레이션입니다. 다른 팀의 통합 DB 마이그레이션과 합칠 때 테이블 소유권과 적용 순서를 조정해야 합니다.

## 대시보드 · 알림 · 체크리스트 · SSE

로그인 외에 구현한 API 목록과 명세서와 다른 점은 [docs/API_IMPLEMENTED.md](docs/API_IMPLEMENTED.md) 에 있습니다.
중앙 서버(`factory/central-server`)가 켜져 있어야 화면 데이터가 나옵니다.


## API 명세서 (Swagger · Postman)

- 서버를 켜고 `http://localhost:8080/swagger-ui.html` 에서 화면별 API 목록을 보고 바로 호출해 볼 수 있다 (오른쪽 위 Authorize 에 accessToken).
- 원본 OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Postman 컬렉션: `docs/postman/` (불러오는 법은 그 폴더 README)
- 운영에서 숨기려면 `ARGOS_API_DOCS=false`
