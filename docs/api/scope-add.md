# 사업장 · 공장 · 공정 추가 — 백엔드 전달 (2026-11-20)

프론트 브랜치 `feature/frontend-factory-add`. **DB 변경은 없습니다.** 이미 있는 API(`FactoryAdminController`)를 그대로 부릅니다. 아래 3번의 요청 2건만 봐 주세요.

## 1. 화면에서 바뀐 것 (재원님 요청)

| 화면 | 바뀐 것 | 부르는 API |
|---|---|---|
| 사업장 선택 › 사업장 목록 | 제목줄에 '사업장 추가' 단추 → 이름 입력('창원' → '창원공장') → 그 사업장의 공장 관리로 바로 이동 | `POST /sites` |
| 공장 관리 › 공장 드롭다운 | 맨 아래 '공장 추가' (본사 관리자만) | `POST /factories` |
| 공장 관리 › 공정 드롭다운 | 맨 아래 '공정 추가' → 추가하면 바로 조회해서 설비 없는 빈 배치도 | `POST /processes`, `GET /processes/{id}/layout · status` |
| 상단 메뉴 | 공장이 하나도 없는 사업장은 대시보드 · 통계 · 점검 탭을 숨기고 '관리'만 | `GET /sites/{id}/tree` (공장 수) |

- 추가한 뒤에는 `GET /sites/{siteId}/tree` 를 다시 불러서 응답의 `id` 로 새 공장 · 공정을 고릅니다.
- 화면설계안은 드롭다운 옆 '+ 추가' 단추 하나였는데, 재원님 요청으로 드롭다운 맨 아래 줄로 옮겼습니다.

## 2. 보내는 값

```json
// POST /sites  (본사 관리자만)
{ "name": "창원공장", "code": "S1K3Z9Q" }

// POST /factories
{ "siteId": 9001, "code": "F1K3ZA2", "name": "1공장" }

// POST /processes
{ "factoryId": 9002, "code": "P1K3ZB7", "name": "도장 공정" }
```

- 화면에서는 **이름만** 입력받습니다. `code` 는 프론트가 만들어 보냅니다: 머리글자(S · F · P) + 지금 시각 36진수 끝 6자리 (7자, DB `varchar(10)` 안).
- `address` · `lat` · `lng` · `tagPrefix` 는 보내지 않습니다 (DB 에서 비워도 되는 칸).
- 같은 이름 · 코드면 서버의 `409 CODE_TAKEN` 메시지를 그대로 띄웁니다. 같은 상위 안의 같은 이름은 화면에서 먼저 막습니다.

## 3. 요청

1. **`code` 를 비워 보내면 서버가 만들어 주기** (설비 추가의 `EQ-{processId}-{n}` 처럼). 되면 프론트의 `autoCode()` (`gateway/frontend/src/api/sites.js`)를 지우겠습니다. 지금은 `MasterAdminService.createPlant · createFactory · createProcess` 가 `code` 없으면 400 입니다.
2. **주소 · 위경도 없는 사업장 확인**: `GET /sites` 에서 `address` · `lat` · `lng` 가 `null` 로 와도 프론트는 '주소 미등록'으로 그립니다. 사업장 선택 지도의 핀은 코드별 좌표(`KoreaMap.vue PIN_LAYOUT`)라 새 사업장은 핀이 안 찍힙니다. 주소 · 위경도 입력을 화면에 넣을지는 재원님과 정할 예정입니다.

## 4. 확인한 서버 동작 (코드 기준)

- `POST /factories · /processes` 는 본사 관리자 · 공장 담당자(담당 범위 안) 모두 됨. 화면은 공장 추가를 본사 관리자에게만 보여 줍니다 (공장 담당자는 담당 공장이 고정이라).
- 새 공정의 `GET /processes/{id}/layout` 은 `equipment: []` 로 옴 → 공장 관리 배치도는 격자 바닥만 그리고 '아직 설비가 없습니다'.
- 코드 위치: `TODO(백엔드` 로 검색 (`api/sites.js autoCode`).
