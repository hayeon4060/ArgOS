# 통계·이력 조회 기간 최대 30일 (2026-11-19 변경 요청)

프론트엔드 → 백엔드 요청입니다. 프론트는 이미 반영했고, **백엔드 코드는 아직 안 고쳤습니다.**

## 바뀌는 것

| 항목 | 전 | 후 |
|------|----|----|
| 한 번에 조회할 수 있는 기간 (`from ~ to`, 양 끝 포함) | 366일 (1년) | **30일** |
| 넘었을 때 응답 | `400 RANGE_TOO_LARGE` "한 번에 1년까지 조회할 수 있습니다." | `400 RANGE_TOO_LARGE` "한 번에 30일까지 조회할 수 있습니다." |

대상 API: `GET /api/v1/stats/summary` `/stats/trend` `/stats/by-process` `/stats/alarms` `/stats/export`

## 백엔드 할 일

1. `gateway/backend/src/main/java/com/gte/argos/gateway/stats/StatsService.java` 의 `scope()`
   - `if (c.days() > 366)` → `> 30`, 메시지도 30일로
   - 그 줄 바로 위에 `TODO(백엔드, 2026-11-19)` 주석을 달아 둠 (검색: `TODO(백엔드`)
2. `/stats/trend` 단위 자동 선택(`HOUR`/`DAY`/`WEEK`)은 그대로 둬도 됨. 30일 이하라 `WEEK` 는 더 나오지 않음
3. `gateway/backend/docs/API_IMPLEMENTED.md` 의 "한 번에 최대 1년." → "한 번에 최대 30일."

## DB (보관 기간)

- 알림 상세 그래프용 센서 값(`sensor_trend` 1분 평균, 신규 `alarm_sensor_sample` 5초 값)은 **최소 31일**만 남기면 됨 (전에는 1년)
- 정리 예: `DELETE FROM alarm_sensor_sample WHERE ts < now() - interval '31 days';` (월별 파티션이면 2개월 지난 파티션 DROP)
- 요약 카드 '이전 기간보다'는 `alarm` 테이블만 써서 센서 값 보관과 상관없음

## 프론트에서 한 것 (참고)

- 통계·이력 '직접 선택' 달력에서 30일을 넘기면 "최대 30일까지 조회할 수 있습니다." 로 미리 막음 (`features/history/historyFilter.js` `MAX_RANGE_DAYS = 30`)
- 빠른 선택: 오늘 / 7일 / 30일

자세한 화면·API·DB 변경: `gateway/frontend/docs/stats-history-api.md` 0장, `stats-history-db.md` 4장
