# 주의 상세 창 추이 그래프 2개 — 백엔드 전달 (2026-11-19)

프론트 브랜치 `feature/frontend-warn-trends`. **DB 변경은 없습니다.** `GET /alarms/{id}/detail` 응답에 필드 1개(`trends`)를 추가해 주세요.

## 1. 화면에서 바뀐 것

주의 상세 창 아래에 추이 그래프를 1개 → **2개**로 보여 줍니다 (재원님 요청).

| 유형 | 보여 주는 그래프 (왼쪽 → 오른쪽) |
|---|---|
| 화재 주의 (`hazard` = `FIRE`) | 온도 추이 · 전류 추이 |
| 가스 주의 (`hazard` = `GAS`) | CO 추이 · 전류 추이 |

## 2. 요청: `GET /alarms/{id}/detail` 에 `trends[]` 추가

지금은 알림이 난 센서 하나의 추이(`trend`)만 옵니다. 같은 설비의 센서 3종(전류·온도·CO) 추이를 배열로 같이 주세요.

```json
{
  "trend":  { "sensorId": 117, "tag": "CT-17", "unit": "A", "warn": 21.6, "alarm": 27, "points": [["2026-11-19T16:00:00+09:00", 18.2]] },
  "trends": [
    { "sensorId": 117, "tag": "CT-17", "type": "CURRENT", "unit": "A",   "warn": 21.6, "alarm": 27,  "points": [["2026-11-19T16:00:00+09:00", 18.2]] },
    { "sensorId": 215, "tag": "TE-15", "type": "TEMP",    "unit": "℃",   "warn": 160,  "alarm": 180, "points": [["2026-11-19T16:00:00+09:00", 142]] },
    { "sensorId": 313, "tag": "CO-13", "type": "CO",      "unit": "ppm", "warn": 20,   "alarm": 30,  "points": [["2026-11-19T16:00:00+09:00", 6]] }
  ]
}
```

- 한 항목의 모양은 지금 `trend` 와 같고 `type`(`CURRENT` · `TEMP` · `CO`)만 더합니다. `type` 이 없으면 화면이 `sensors[]` 에서 `tag` 로 찾습니다.
- 센서: 응답의 `sensors[]` 에 넣는 센서와 같은 것 (알림이 난 설비의 전류·온도·CO).
- 기간·간격: 지금 `trend` 와 같게 (발생 30분 전 ~ 지금, 1분 간격). `AlarmService.detail()` 에서 `central.series(List.of(e.sensorId()), …)` 를 센서 3개 ID 로 한 번에 부르면 됩니다.
- `warn` · `alarm`: 각 센서의 기준값 (그래프에 기준선으로 그림).
- 화면은 유형에 맞는 2개만 골라 씁니다. 3종을 다 주시면 나중에 조합이 바뀌어도 백엔드를 다시 고치지 않아도 됩니다. (2개만 주셔도 됩니다: 화재 = 온도·전류, 가스 = CO·전류)
- `trend` 는 그대로 두어 주세요 (알림이 난 센서를 찾는 데 씁니다).

## 3. 반영 전 동작

`trends` 가 없으면 화면은 지금처럼 `trend` 하나만 그립니다. 오류는 나지 않습니다.

## 4. 프론트 코드 위치

- `gateway/frontend/src/features/alert/alarmPlace.js` `trendsOf()` — `TODO(백엔드` 주석
- `gateway/frontend/src/features/alert/WarnDetailModal.vue`
- 예시 데이터: `gateway/frontend/src/api/mock/alertData.js`
