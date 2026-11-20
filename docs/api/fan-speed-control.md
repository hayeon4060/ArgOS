# 가스 주의 때 배기팬 속도 직접 조절 20 ~ 60 Hz (2026-11-20 요청)

프론트 브랜치 `feature/frontend-fan-speed-control`. 화면은 예시 데이터(mock)로 확인했고, **새 API 없이 이미 있는 `POST /equipment/{id}/fan` 의 `{ hz }`** 를 씁니다. 아래 1장은 지금 그대로 되는 것, 2장은 있으면 좋은 백엔드 변경입니다.

## 0. 한 줄 요약

- 가스 **주의** 알림 창 · 주의 상세 창의 '배기팬 자동 가동' 줄 아래에 **속도 직접 조절**(슬라이더 · − · + · '적용')이 생겼습니다.
- 범위는 **20 ~ 60 Hz** (= 600 ~ 1,800 rpm, Hz × 30). 화면은 Hz 를 크게, rpm 을 옆에 작게 보여 줍니다.
- 공장 담당자만 보입니다 (본사 관리자는 열람만, 서버도 403). 경보 대응 창에는 넣지 않았습니다.
- 자동 모드여도 바꿀 수 있습니다. 적용하면 '배기팬 직접 조절 47 Hz' 로 제목과 rpm 이 바뀝니다.

## 1. 쓰는 API (이미 있음)

`POST /api/v1/equipment/{equipmentId}/fan` — `EquipmentActionController.fan`

```json
{ "hz": 47 }
```

- `equipmentId`: 알림 `fanAuto.fanEquipmentId` (배기팬 설비 행). 없으면 `fanAuto.equipmentId`
- 응답 202 `{ commandId, equipmentId, status }`, 결과는 SSE `command.result`
- 에러: 400 `HZ_OUT_OF_RANGE` (20 ~ 60 밖), 409 `COMMAND_PENDING` (같은 명령 처리 중), 403 (본사 관리자) → 화면은 메시지를 토스트로 보여 줌
- 프론트 코드: `gateway/frontend/src/api/equipment.js setFanHz`, 화면 `features/alert/FanSpeedControl.vue`

## 2. 백엔드 요청 (있으면 좋음)

| 순서 | 요청 | 왜 | 프론트 지금 처리 |
| --- | --- | --- | --- |
| 1 | 알림 `fanAuto` 에 **`manualHz`** (담당자가 직접 정한 Hz, 없으면 null) | 창을 닫았다 다시 열거나 다른 PC 에서 볼 때도 '직접 조절 47 Hz' 로 보이게 | 이 창에서 보낸 값만 기억 (다시 열면 자동 값으로 보임) |
| 2 | `POST /equipment/{id}/fan` 에 **`alarmId`** (선택) | 명령(`equipment_command.alarm_id`) · 알림 타임라인(`FAN`)에 '담당자 47 Hz' 로 이어지게 | 보내지 않음 |
| 3 | 직접 정한 뒤 같은 알림이 **경보(3단계)로 올라가면** 자동(60 Hz)으로 다시 올릴지 | 담당자가 낮춘 값이 경보 때도 남으면 위험 | 서버 규칙 그대로 (가정: 자동이면 60 Hz 로 다시 올림) |
| 4 | 수동 모드 **승인 대기(AWAITING)** 중에 직접 조절하면 그 승인 대기 명령을 취소할지 | 나중에 승인하면 직접 정한 값을 덮어씀 | 승인 · 거절 버튼을 그대로 둠 |
| 5 | 알림이 끝나면 직접 정한 값도 `RECOVERY` 로 30 Hz 복귀하는지 | 지금 자동 값은 복귀 | 확인만 |

코드 위치: `TODO(백엔드` 로 검색 (`api/equipment.js`, `api/mock/fanAuto.js`).
