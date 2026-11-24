# 전일 시간대별 알림을 하루(00~24시) 한 줄로 — 백엔드 전달 (2026-11-20)

프론트 브랜치 `feature/frontend-map-hover-hourly-day`. **DB 변경은 없습니다.** `GET /alarms/hourly` 의 집계 범위와 응답 모양을 바꿔 주세요.

## 1. 화면에서 바뀐 것

사업장 현황 · 공장 대시보드 오른쪽 '전일 시간대별 알림' 카드를 **주간/야간 두 줄 → 하루(00~24시) 한 줄**로 합쳤습니다 (재원님 요청).

| 전 | 후 |
|---|---|
| 주간 07~19시 12칸 + 야간 19~07시 12칸, 두 줄 | 00~23시 24칸, 한 줄 |
| 줄마다 알림 · 조치 건수 | 하루 알림 · 조치 건수 하나 |

## 2. 지금 서버와 맞지 않는 점

`AlarmService.hourly()` 의 집계 범위는 `date 07:00 ~ 다음 날 07:00` 입니다 (`hourlyRange`). 화면이 `day` 와 `night` 를 합쳐서 00~23시로 그리면,

- 07~23시 칸: `date`(어제) 값 → 맞음
- **00~06시 칸: `date + 1`(오늘) 새벽 값** → '전일' 제목과 맞지 않음

## 3. 요청: 범위를 `date 00:00 ~ date+1 00:00` 으로, 응답에 하루 값 추가

```json
{
  "date": "2026-11-19",
  "from": "2026-11-19T00:00:00+09:00",
  "to":   "2026-11-20T00:00:00+09:00",
  "alarmCount": 8,
  "actionCount": 7,
  "hours": [
    { "hour": 3,  "warn": 1, "alarm": 0, "action": 1 },
    { "hour": 14, "warn": 2, "alarm": 1, "action": 2 }
  ]
}
```

- `hourlyRange(day)` 를 `day.atStartOfDay(...)` ~ `+1일` 로 바꾸면 됩니다.
- `alarmCount` · `actionCount` · `hours` 는 지금 `shift(byHour, from, to)` 가 만드는 값과 같은 모양이고, 0~24시 전체로 한 번 만들면 됩니다 (`shift(byHour, 0, 24)`).
- `hours` 는 지금처럼 알림이 있는 시간만 주셔도 됩니다. 빈 시간은 프론트가 0으로 채웁니다.
- `day` · `night` 는 화면이 더 쓰지 않습니다. 남겨 두셔도 되고 빼셔도 됩니다.
- 조치(`action`)는 지금처럼 확인 시각이 범위 안인 것만 세면 됩니다.

## 4. 반영 전 동작

응답에 `hours` 가 없으면 프론트가 `day` + `night` 를 합쳐서 그립니다. 오류는 나지 않지만 **00~06시 칸은 오늘 새벽 값**이 들어갑니다 (위 2번). `hours` 가 오면 그대로 씁니다. 프론트를 다시 고치지 않아도 됩니다.

## 5. 프론트 코드 위치

- `gateway/frontend/src/api/dashboard.js` — `toOneDay()` (두 칸 → 하루 합치기, `TODO(백엔드)` 주석), `getHourlyAlarms()`
- `gateway/frontend/src/features/dashboard/HourlyAlarmCard.vue` — 카드 (한 줄)
- `gateway/frontend/src/features/dashboard/HourlyChart.vue` — 차트 (24칸)
