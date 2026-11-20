# API 명세서 (초안)

| 파일 | 내용 |
| --- | --- |
| `ArgOS_API명세서.html` | 화면별로 부르는 API, 요청·응답 예시, 권한, 쓰는 DB 테이블, 에러, SSE 이벤트, 푸시 종류 |
| `ArgOS_API구성도.png` | 화면 · 연계 서버 · DB · 중앙 서버 · 푸시 사이의 API 흐름 그림 |
| `stats-history-30days.md` | (2026-11-19 요청) 통계·이력 조회 기간 1년 → 최대 30일, 백엔드 할 일 |
| `alarm-modal-hazard.md` | (2026-11-19 요청) 주의·경보 창 유형 문구, 경보 대응 `title` 에 유형 넣기 |
| `warn-detail-trends.md` | (2026-11-19 요청) 주의 상세 창 추이 그래프 2개 — `GET /alarms/{id}/detail` 에 `trends[]` 추가 |
| `erd-final-frontend-check.md` | (2026-11-19) 최종 ERD ↔ 프론트 대조: 프론트에서 고친 것, ERD에 없는데 화면이 쓰는 값(확인 요청), 아직 화면에 없는 ERD 기능 |
| `hourly-alarm-one-day.md` | (2026-11-20 요청) 전일 시간대별 알림 주간/야간 → 하루 00~24시 한 줄 — `GET /alarms/hourly` 범위를 `date 00~24시` 로, `hours[]` 추가 |
| `backend-feedback-1120.md` | (2026-11-20) 백엔드 피드백 4건 프론트 반영: 그날 점검 목록 API, 열화상 사진 blob, 배기팬 수동 승인 · 거절 버튼, 실제 설비 코드 |
| `frontend-api-audit-1120.md` | (2026-11-20) 프론트 ↔ 백엔드 API 전수 비교: 프론트에서 고친 17건, 백엔드 요청(알림 `fireId`, 체크리스트 목록 `equipmentId`, 통계 `siteId`, 점검 목록 값, 배치 저장 설비 종류), 월별 보고서 31일 달 400 |
| `inspection-redesign-api.md` | (2026-11-20 요청) 점검 관리 개편 A안: 점검 건 목록 `GET /inspections/schedule` 에 `month` · `from&to` 와 구분 · 결과 칸, 스케줄 추가, 전자 서류 양식 보기 · 저장, 빈 양식 PDF, 스캔본 ↔ 점검 건 연결(`PUT /inspections/{id}` 의 `scheduleId`). 점검 관리는 F02 만 (변전소 F03 제외) |
| `scope-add.md` | (2026-11-20 요청) 사업장 · 공장 · 공정 추가 화면 연결 (`POST /sites · /factories · /processes`), 요청: `code` 비우면 서버가 만들기, 위경도 없는 사업장 |
| `equipment-add.md` | (2026-11-20 요청) 새 공정에 설비 추가: 지금 있는 설비 15종에서 골라 빈 배치판에 놓고 `PUT /processes/{id}/layout` 의 `equipment.create` 로 저장 (새 API · ERD 없음), 확인: `equipment_type` null 허용 |
| `paint-equipment-edit.md` | (2026-11-20 요청) 공장 관리 설비 추가 · 삭제를 도장 공정에도, 설비는 기존 배치도 그림 그대로: `PUT /processes/{id}/layout` 의 `equipment.delete` · `create[].shape` 사용 (새 API 없음), `equipment.shape` 에 넣는 그림 이름 표, 확인 4건 |
| `fan-speed-control.md` | (2026-11-20 요청) 가스 주의 때 배기팬 속도 직접 조절 20 ~ 60 Hz: 이미 있는 `POST /equipment/{id}/fan` 의 `{ hz }` 사용 (새 API 없음), 요청: 알림 `fanAuto.manualHz`, 명령에 `alarmId`, 경보 승격 · 승인 대기 · 복귀 규칙 확인 |
| `alarm-sensor-scope.md` | (2026-11-20) 경보 창 '센서 데이터'에 공정 센서가 전부 나오던 문제: `GET /alarms/{id}/response` 의 `sensors` 를 관제 단위 센서만으로 (프론트는 `detail` 로 먼저 걸러 둠) |

## 이 문서의 성격

- 프론트엔드 **화면 설계안을 기준으로 먼저 적은 초안**입니다.
- 주소, 필드 이름, 응답 모양, 인증 방식 등에는 **백엔드와 합의 전 가정**이 들어 있습니다. 확정되면 이 문서를 고치거나 OpenAPI 등으로 옮겨 주세요.
- 프론트엔드는 백엔드 개발 전까지 이 명세서의 응답 모양 그대로 예시 데이터(mock)를 만들어 화면을 개발합니다.

## 여는 방법

`ArgOS_API명세서.html` 을 내려받아 브라우저(Chrome, Edge 등)로 열면 됩니다. 이미지가 파일 안에 들어 있어 인터넷 연결이 없어도 열립니다.

- GitHub 웹에서는 HTML 이 화면으로 보이지 않고 소스로 보입니다. 저장소를 받은 뒤 로컬에서 여세요.

## 프론트엔드 추가 요청

명세서와 다르게 구현한 점, 추가로 필요한 필드, 정해야 할 점은 아래 문서에 화면별로 정리합니다.

- [`gateway/frontend/docs/backend-notes.md`](../../gateway/frontend/docs/backend-notes.md)

## 백엔드 요청 모음

2026-11-19부터 프론트가 백엔드에 필요한 API 변경·추가 요청은 이 폴더(`docs/api/`)에 파일로 둡니다. 코드에는 `TODO(백엔드` 로 검색되는 주석을 달아 둡니다.
