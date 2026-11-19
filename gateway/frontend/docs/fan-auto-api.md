# 배기팬 자동 제어 · 기준값 관리 이동 — API 변경 (2026-11-19)

> **2026-11-19 최종 ERD 확정 뒤 달라진 값이 있습니다** (명령 `FAN_HZ` · Hz 단위, `source` 에 `RECOVERY`, 수동 승인 등). 최종 값은 `docs/api/erd-final-frontend-check.md` 6장을 보세요.

프론트 브랜치 `feature/frontend-fan-auto`. 화면은 예시 데이터(mock)로 먼저 만들었고, 아래가 백엔드에 필요한 변경입니다.
DB 변경은 같은 폴더 `fan-auto-db.md` 에 따로 적었습니다.

> **같은 날 2차 변경**: 스위치 2개(사용 상태 · 배기팬 자동 제어)를 기준값 관리 창으로 옮기고 대시보드 배기팬 조절 창을 지웠습니다. 아래 3장(layout `fanAuto`)은 더 쓰지 않고 `PATCH /equipment/{id}/settings` 로 바뀜 → **`equipment-settings-api.md`**

## 0. 한 줄 요약

- **가스(GAS) 알림이 나면 서버가 배기팬 회전수를 자동으로 올립니다.** 주의(2단계) 75%, 경보(3단계) 100%.
- 설비마다 **배기팬 자동/수동 스위치**가 생겼습니다 (공장 관리 › 설비 정보). 자동일 때만 올리고, 수동이면 알림 창에 안내만 합니다.
- 알림 응답 3곳에 `fanAuto`(자동 제어 결과) 한 칸을 붙여 주세요. 화면은 그 값을 보여 주기만 합니다.
- '설비 기준값 관리' 메뉴·화면은 없어지고 공장 관리 안의 창으로 옮겼습니다. **기준값 API는 그대로**입니다.

## 1. 자동 제어는 서버가 합니다 (새 주소 없음)

브라우저가 꺼져 있어도 동작해야 하고, 화면 여러 개가 열려 있으면 명령이 여러 번 가므로 **프론트는 명령을 보내지 않습니다.**

| 언제 | 할 일 |
|---|---|
| `alarm` 이 `hazard = 'GAS'`, `stage = 2`(주의)로 생길 때 | 그 관제 단위의 배기팬이 `fan_auto = true` 면 `FAN_RPM 1350` 명령 |
| `hazard = 'GAS'` 알림이 `stage = 3`(경보)로 생기거나 2 → 3 으로 올라갈 때 (`onAlarmOpened` / `onAlarmUpdated`) | `fan_auto = true` 면 `FAN_RPM 1800` 명령 |
| 배기팬이 꺼져 있으면 | `FAN_ON` 다음에 `FAN_RPM` |
| `fan_auto = false` (수동) | 명령 없음. 알림 응답의 `fanAuto.status = 'SKIPPED'` |
| `hazard = 'FIRE'` | 대상 아님 (화재는 자동 셧다운) |

- % 기준: 배기팬 최고 회전수 1,800 rpm (`POST /equipment/{id}/fan` 의 허용 범위 600~1,800 과 같음). 75% = **1,350 rpm**, 100% = **1,800 rpm**.
- 명령 기록은 지금처럼 `equipment_command` 에 남기고 `alarm_id` 를 채워 주세요. 자동 명령이라 사람(`requested_by`)이 없음 → DB 문서 2장.
- 이용 로그(`activity_log`)에도 `type = 'FAN'`, 내용 예 `상도 건조로 배기팬 RPM 1350 (가스 주의 자동)`.
- 명령 결과가 바뀌면 지금 있는 SSE `command.result` 와 함께 `alarm.updated` 도 보내 주세요 (화면이 알림을 다시 받아 `fanAuto.status` 를 갱신).

## 2. 알림 응답에 `fanAuto` 추가

붙는 곳 3군데 (모양은 모두 같음, 가스 알림이 아니거나 배기팬이 없으면 `null`):

- `GET /alarms` 의 `items[]` — 주의 알림 창(2초 길게 누르기 창)이 이 값을 씀
- `GET /alarms/{alarmId}/detail` — 주의 상세 창
- `GET /alarms/{alarmId}/response` — 경보 대응 창

```json
"fanAuto": {
  "equipmentId": 31,
  "fanName": "상도 건조로 배기팬",
  "mode": "AUTO",
  "percent": 75,
  "rpm": 1350,
  "beforeRpm": 1180,
  "status": "APPLIED",
  "at": "2026-11-19T16:38:21+09:00"
}
```

| 필드 | 값 | 설명 |
|---|---|---|
| `equipmentId` | 숫자 | 배기팬이 달린 설비 id (화면의 설비 기준) |
| `fanName` | 글자 | 창에 그대로 보여 줌 |
| `mode` | `AUTO` / `MANUAL` | 알림이 났을 때의 스위치 값 |
| `percent` | 75 / 100 / null | 수동이면 null |
| `rpm` | 1350 / 1800 / null | 올린 목표 회전수 |
| `beforeRpm` | 숫자 | 올리기 전 회전수 (`1,180 → 1,350 rpm` 표시용) |
| `status` | `SENT` / `APPLIED` / `FAILED` / `SKIPPED` | 명령 보냄 / 반영됨 / 실패 / 수동이라 안 보냄 (`equipment_command.result` PENDING·OK·FAIL 에 맞춤) |
| `at` | 시각 | 명령을 보낸 시각 |

화면 표시:
- `AUTO` + `APPLIED` → '배기팬 자동 가동 75%' + `1,180 → 1,350 rpm` (주의는 주황, 경보는 빨강)
- `AUTO` + `SENT` → '배기팬 75%로 올리는 중'
- `AUTO` + `FAILED` → '명령이 전달되지 않았습니다. 현장에서 배기팬을 확인해 주세요' (2차: 대시보드 팬 조절 창 삭제)
- `MANUAL` → '수동이라 자동으로 바꾸지 않았습니다' 안내

## 3. 공장 관리 배치 API에 `fanAuto` 추가

### GET `/processes/{processId}/layout`

`equipment[]` 에 한 칸 추가:

```json
{ "id": 31, "code": "PNT-TOP-OVEN", "name": "상도 건조로", "type": "OVEN", "active": true, "fanAuto": true, "pos": { "x": null, "y": null, "z": null } }
```

- 배기팬이 있는 설비: `true`(자동) / `false`(수동). 배기팬이 없는 설비: `null` → 화면에 스위치가 안 보임
- 값이 아예 없으면 화면은 '자동'으로 봄 (`GET /processes/{id}/status` 의 `fan` 이 있는 설비만 스위치 표시)

### PUT `/processes/{processId}/layout` (명세서 H1, 백엔드 아직 없음)

`equipment.update[]` 한 줄에 `fanAuto` 가 들어갈 수 있음. 다른 값(이름·종류·사용 상태)과 같이 '저장' 한 번에 옴.

```json
{ "version": 14, "equipment": { "create": [], "update": [{ "id": 31, "name": "상도 건조로", "type": "OVEN", "active": true, "fanAuto": false }], "delete": [] }, "sensors": { "create": [], "update": [], "delete": [] } }
```

- 배기팬이 없는 설비 줄에는 `fanAuto` 를 넣지 않음
- 바꾸면 이용 로그 `type = 'LAYOUT'` 에 `배기팬 수동` / `배기팬 자동` 이 남게 해 주세요

## 4. 설비 기준값 관리: 화면 → 창 (API 변경 없음)

- 상단 메뉴 '설비 기준값 관리'와 `/sites/{id}/thresholds` 화면을 지움
- 공장 관리 › 설비 정보 › **'설비 기준값 관리'** 단추 → 같은 기능의 창 (왼쪽 설비 트리만 빠짐)
- 쓰는 API 그대로: `GET /equipment/tree?factoryId=`, `GET /equipment/{id}/thresholds`, `POST /thresholds/simulate`, `PUT /equipment/{id}/thresholds`, `GET /thresholds/history`, `GET /sensors/series`
- 설비 상세의 '설비 기준값 관리' 버튼 · 이벤트의 '기준값 관리에서 보기' → 설비 상세 화면 위에 같은 창이 뜸 (2차, 화면 이동 없음)
- 이용 로그·권한: 기준값 저장 권한은 전과 같음 (공장 담당자)

## 5. 확인해 주세요

1. **배기팬이 설비 행으로 따로 있음**: DB 시드에는 `TOP-FAN 상도 건조로 배기팬` 이 별도 설비이고, 화면(공장 관리·공정 배치도)은 건조로 한 칸에 배기팬이 붙은 모양입니다. 스위치 값은 배기팬 행(`TOP-FAN`)의 `fan_auto` 에 저장하고, layout 응답에서는 그 배기팬이 붙은 설비(건조로) 줄에 `fanAuto` 로 실어 주는 방식을 제안합니다. 다르게 하려면 알려 주세요.
2. **100% = 1,800 rpm 이 맞는지** (최고 회전수 기준). 정격 1,180 rpm 기준이면 75% 가 정격보다 낮아져서 최고 회전수 기준으로 잡았습니다.
3. **알림이 끝난 뒤 팬을 되돌릴지**: 지금은 아무것도 안 하는 것으로 가정 (담당자가 현장에서 낮춤 — 대시보드 팬 조절 창은 2차에서 삭제). 자동으로 정격(1,180 rpm)으로 돌리려면 알려 주세요.
4. 가스 주의가 난 뒤 담당자가 손으로 회전수를 바꿨는데 같은 알림이 경보로 올라가면 → 자동이면 100% 로 다시 올림 (가정).
5. (전부터 있던 차이) `equipment_command.command` CHECK 에 `FAN_OFF` 가 없는데 `EquipmentActionService.fan()` 은 `FAN_OFF` 를 보냄 → DB 문서 2장에 같이 적음.
