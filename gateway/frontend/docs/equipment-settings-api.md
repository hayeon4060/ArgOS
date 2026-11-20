# 설비 설정 스위치 이동 · 대시보드 배기팬 조절 삭제 — API 변경 (2026-11-19, 2차)

프론트 브랜치 `feature/frontend-fan-auto`. 같은 날 1차 변경(`fan-auto-api.md`)에 이어지는 재원님 요청 3가지입니다.
**DB 변경은 없습니다** (`equipment.is_active`, `equipment.fan_auto` 는 이미 있음). 그래서 DB 파일은 따로 만들지 않았습니다.

## 0. 한 줄 요약

| 화면 변경 | API 영향 |
|---|---|
| 1. 대시보드(공정 배치도 · 설비 상세)에서 배기팬을 눌러 회전수 · 켜기/끄기를 바꾸던 창 삭제 | 웹은 `POST /equipment/{id}/fan` 을 더는 부르지 않음 |
| 2. 설비 상세에 '설비 기준값 관리' 버튼 → 공장 관리와 같은 창이 **설비 상세 화면 위에** 뜸 (화면 이동 없음) | 없음 (창이 쓰는 기준값 API 그대로) |
| 3. 사용 상태 · 배기팬 자동 제어 스위치를 공장 관리 설비 정보 카드 → 기준값 관리 창 '설비 설정' 줄로 옮김 | **새 API `PATCH /equipment/{id}/settings`**, 기준값 GET 응답에 2칸, SSE 이벤트 1개 |

## 1. 새 API: `PATCH /equipment/{equipmentId}/settings`

창이 공장 관리 밖(설비 상세)에서도 열리므로, 스위치를 누르면 배치 '저장'을 기다리지 않고 **바로 저장**합니다.

요청 (바꾼 값만):
```json
{ "active": false }
{ "fanAuto": true }
```

| 필드 | DB | 비고 |
|---|---|---|
| `active` | `equipment.is_active` | |
| `fanAuto` | `equipment.fan_auto` | 배기팬이 없는 설비에 오면 400 |

응답:
```json
{ "equipmentId": 31, "active": true, "fanAuto": false }
```
- 배기팬이 없는 설비는 `fanAuto: null`
- 권한: 기준값 저장과 같게 (공장 담당자, 자기 공장만)
- 배치 판 번호(`version`)는 **올리지 않음** — 공장 관리에서 배치를 고치는 중에 창에서 스위치를 눌러도 배치 저장이 409로 막히지 않게
- 이용 로그 `type = 'LAYOUT'`(또는 `EQUIPMENT`)에 '사용 상태 사용 안 함' / '배기팬 수동' 처럼 남겨 주세요
- 저장 뒤 SSE **`equipment.updated`** 를 보내 주세요 (아래 3장)

## 2. `GET /equipment/{equipmentId}/thresholds` 응답에 2칸 추가

창이 처음 열릴 때 스위치 값을 이 응답에서 읽습니다.
```json
{ "equipmentId": 31, "name": "상도 건조로", "...": "(지금 응답 그대로)", "active": true, "fanAuto": true }
```

## 3. SSE 이벤트 `equipment.updated` 추가 (StreamHub)

- 1장 PATCH 가 성공하면 `{ "equipmentId": 31 }` 정도로 보내 주세요. 열린 화면(설비 상세 · 공정 배치도)이 조용히 다시 불러와 '사용 안 함' 흐림 표시를 맞춥니다.
- 프론트는 `src/api/live.js` RELOAD_EVENTS 에 이미 넣어 둠.

## 4. 공장 관리 layout (1차 3장 수정)

- `PUT /processes/{id}/layout` — 웹은 이제 `fanAuto` 를 **보내지 않습니다** (설비 정보 카드에 스위치가 없음). `active` 는 배치도 위쪽 ON/OFF 로 계속 보냄.
- `GET /processes/{id}/layout` 의 `fanAuto` 는 웹이 쓰지 않게 됐으니 넣지 않아도 됩니다.

## 5. 쓰지 않게 된 것

- `POST /equipment/{id}/fan` — 웹 화면에서 호출하던 곳이 모두 사라짐. 서버 내부 자동 제어(가스 알림 → 75 · 100%)와 앱에서 쓸 수 있으니 **지우지는 말아 주세요**.

## 6. 확인해 주세요

1. 수동(`fan_auto = false`)일 때 알림 창 문구를 '현장에서 배기팬을 확인해 주세요'로 바꿨습니다 (대시보드에서 손으로 올리는 창이 없어져서). DB 주석에는 '수동이면 담당자 승인 후 제어'라고 되어 있는데, 웹에 승인 버튼이 필요하면 알려 주세요.
