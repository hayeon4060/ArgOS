# 통계·이력 관리 개편 — 새로 추가·변경된 API

2026-11-19 프론트 개편(알림 이력 확장, 유형 화재·가스, 알림 상세 패널, CSV 내보내기)에 맞춰 백엔드가 해야 할 API 정리입니다.
DB 쪽은 `stats-history-db.md` 에 따로 정리했습니다.

| # | 구분 | API | 할 일 |
|---|------|-----|-------|
| 0 | 변경 | 통계 API 전부 (`/stats/*`) | 조회 기간 최대 **30일** (넘으면 400 `RANGE_TOO_LARGE`, 전에는 366일) |
| 1 | 변경 | `GET /api/v1/stats/alarms` | 조건 `hazard` 추가 |
| 2 | **신규** | `GET /api/v1/stats/alarms/{alarmId}` | 알림 상세 패널 한 번에 |
| 3 | 변경 | `GET /api/v1/stats/export` | 조건 `hazard` 추가, CSV 열 변경 |
| 4 | 안 씀 | `GET /api/v1/stats/heatmap` | 화면에서 '요일·시간대별 발생' 카드를 뺐음. 지워도 됨 |

센서값 CSV(상세 패널의 '센서값 내보내기')는 2번 응답의 `series` 로 프론트가 직접 만듭니다. 따로 API는 필요 없습니다.

---

## 0. 조회 기간 최대 30일 (2026-11-19 변경)

- `from ~ to` 가 30일을 넘으면 (양 끝 포함) `400 RANGE_TOO_LARGE` "한 번에 30일까지 조회할 수 있습니다."
- 대상: `/stats/summary` `/stats/trend` `/stats/by-process` `/stats/alarms` `/stats/export` (모두 `StatsService.scope()` 한 곳에서 검사)
- **백엔드 할 일**: `gateway/backend/.../stats/StatsService.java` `scope()` 의 `c.days() > 366` 을 `> 30` 으로, 메시지도 30일로. 그 줄 위에 `TODO(백엔드, 2026-11-19)` 주석을 달아 둠 (코드는 안 고침)
- 프론트도 '직접 선택' 달력에서 30일을 넘기면 미리 막습니다. 빠른 선택은 오늘 / 7일 / 30일.
- 일별 추이는 30일 이하라 언제나 일별(`DAY`)이고, 오늘만 고르면 시간별(`HOUR`)입니다. 주별(`WEEK`)은 더 나오지 않습니다.

## 1. `GET /api/v1/stats/alarms` — 조건 `hazard` 추가

알림 이력 표 머리줄에 **유형 거르기(전체 / 화재 / 가스)** 가 생겼습니다. 쪽 나눔(page·size)을 하므로 서버에서 걸러야 합니다.

| 조건 | 값 | 설명 |
|------|----|------|
| `hazard` (추가) | `FIRE` \| `GAS` \| 비움 | `alarm.hazard` 와 같은 값. 비우면 전체 |
| `size` | 14 | 한 쪽 14줄로 부름 (전에는 6) |

- 나머지 조건(from, to, factoryId, processId, sensorType, level, shift, status, sort, page)은 그대로입니다.
- 응답 줄의 `hazard` 는 지금도 오고 있어 그대로 씁니다 (`AlarmViews.item`).
- 표 열이 `센서 · 측정값` 대신 `유형` 으로 바뀌었지만 응답 필드는 그대로 둬도 됩니다.

## 2. `GET /api/v1/stats/alarms/{alarmId}` — 신규, 알림 상세 패널

알림 이력의 '상세'를 누르면 부릅니다. 해제된 지난 알림과 조치 중인 알림, 주의와 경보 모두 같은 모양입니다.
지금 있는 `/alarms/{id}/detail` 은 진행 중인 주의 1건 기준이라 지난 알림에는 쓸 수 없어 새로 만듭니다.

권한: 본사 관리자는 전체, 공장 담당자는 자기 공장 알림만 (`/stats/alarms` 와 같은 범위). 범위 밖이면 404 `ALARM_NOT_FOUND`.

### 응답

```json
{
  "id": 1057,
  "level": "ALARM",
  "status": "CLOSED",
  "hazard": "FIRE",
  "location": "5공장 · 도장 공정 · 상도 건조로 배기팬",
  "factoryId": 5,
  "processId": 12,
  "equipmentId": 31,
  "raisedAt": "2026-11-19T12:16:42+09:00",
  "closedAt": "2026-11-19T12:21:42+09:00",
  "actionMinutes": 5,
  "sensorTag": "CT-17",
  "sensorType": "CURRENT",
  "value": 27.5,
  "unit": "A",
  "threshold": 27,
  "ackedBy": "한지우",

  "series": {
    "from": "2026-11-19T12:16:42+09:00",
    "to": "2026-11-19T12:21:42+09:00",
    "intervalSec": 5,
    "sensors": [
      { "sensorId": 117, "tag": "CT-17", "type": "CURRENT", "unit": "A",   "warn": 21.6, "alarm": 27, "trigger": true,
        "points": [["2026-11-19T12:16:42+09:00", 27.0], ["2026-11-19T12:16:47+09:00", 27.3]] },
      { "sensorId": 215, "tag": "TE-15", "type": "TEMP",    "unit": "℃",   "warn": 160,  "alarm": 180, "trigger": false, "points": [] },
      { "sensorId": 313, "tag": "CO-13", "type": "CO",      "unit": "ppm", "warn": 20,   "alarm": 30,  "trigger": false, "points": [] }
    ]
  },

  "thermal": {
    "imageUrl": "/api/v1/files/88",
    "capturedAt": "2026-11-19T12:16:42+09:00",
    "max": 96.2, "avg": 48.6,
    "maxPoint": { "x": 0.62, "y": 0.13 },
    "cameraTag": "IR-01"
  },

  "timeline": [
    { "at": "2026-11-19T12:16:42+09:00", "step": "RAISED",   "by": null,     "text": "경보 발생 · 전류 27.5 A (기준 27)" },
    { "at": "2026-11-19T12:17:00+09:00", "step": "SHUTDOWN", "by": "한지우", "text": "설비 셧다운 요청" },
    { "at": "2026-11-19T12:21:42+09:00", "step": "CLOSED",   "by": "한지우", "text": "정상 범위 복귀 · 경보 해제" }
  ],

  "checklist": null,

  "shutdown": {
    "steps": [
      { "label": "버너 연료 차단", "at": "2026-11-19T12:17:20+09:00" },
      { "label": "컨베이어 정지", "at": "2026-11-19T12:18:30+09:00" },
      { "label": "배기팬 추가 가동 후 정지", "at": "2026-11-19T12:19:40+09:00" }
    ]
  }
}
```

### 필드 설명

| 필드 | 설명 |
|------|------|
| `actionMinutes` | 해제됐으면 `closedAt - raisedAt` (분, 1 이상). 조치 중이면 지금까지 지난 분 |
| `series.from` | **센서가 주의·경보 기준을 처음 넘은 시각**. 알림을 만든 센서 이벤트의 `sensor_event.started_at` (여럿이면 가장 이른 것) |
| `series.to` | **조치 완료 시각** = `alarm.closed_at`. 조치 중이면 지금 |
| `series.intervalSec` | `from~to` 가 30분 이하면 5(초), 넘으면 60(1분 평균). 아래 'DB' 참고 |
| `series.sensors` | 그 설비(관제 단위)의 센서 **전부**(전류·온도·CO). 열화상(IR)은 빼고 `thermal` 로 |
| `sensors[].trigger` | 이 알림을 만든 센서면 true (그래프 선을 굵게) |
| `sensors[].warn/alarm` | 알림이 났을 때 적용 중이던 기준값 (`sensor_threshold` 의 그 시각 행) |
| `sensors[].points` | `[[시각, 값], ...]` 시각 오름차순. 값이 없는 구간은 점을 빼도 됨 |
| `thermal` | 발생 때 찍은 열화상 1장 (`alarm.thermal_file`). 그림이 아직 없으면 `imageUrl: null` → 화면이 예시 그림 사용 |
| `timeline` | 조치 이력. `alarm` 의 확인·해제 + `alarm_action` 을 시각순으로. `step`: `RAISED` `ACK` `CHECKLIST` `REPORTED` `SHUTDOWN` `CLOSED` |
| `checklist` | 주의일 때. 경보면 null. `{ assignee, doneCount, totalCount, items: [{ seq, content, doneAt, result, memo }] }` (`checklist_item` 그대로) |
| `shutdown` | 경보일 때 셧다운 단계. 주의면 null. `{ steps: [{ label, at }] }` (`/alarms/{id}/response` 의 shutdown 과 같은 모양) |

### 센서 시계열 만드는 법 (제안)

- 1분 평균: 이미 있는 `central.series(sensorIds, from, to, "1m")` 를 그대로 쓰면 됩니다 (`AlarmService.detail` 의 trend 와 같은 방식).
- 5초 값: 짧은 알림(경보는 1~5분 만에 해제되는 경우가 많음)은 1분 평균이면 점이 1~5개뿐이라 그래프가 안 됩니다.
  그래서 30분 이하는 5초 원시값을 씁니다. 저장 방법은 `stats-history-db.md` 2번.

## 3. `GET /api/v1/stats/export` — 알림 이력 상세 CSV

오른쪽 위 '내보내기'를 누르면 **지금 기간·조건의 알림 이력 상세**를 CSV 로 바로 저장합니다 (엑셀 형식 고르는 창은 뺐음).

- 조건: 지금과 같고 `hazard` 추가 (`format` 은 csv 만 씀)
- 파일: UTF-8 BOM 붙은 CSV (엑셀 한글 깨짐 방지). 파일 이름은 프론트가 정함: `알림이력_20261021-20261119.csv`
- 열 (순서대로):

```
알림 번호,발생 시각,해제 시각,유형,공장,공정,설비,등급,알림 센서,센서 종류,발생 값,기준값,단위,상태,확인자,조치,조치 담당자,체크리스트,조치 시간(분)
```

| 열 | 값 |
|----|----|
| 유형 | 화재 / 가스 (`alarm.hazard`) |
| 공장 · 공정 · 설비 | `location` 을 나눈 것 |
| 등급 | 주의 / 경보 / 화재 |
| 상태 | 확인 대기 / 확인함 / 조치 중 / 해제 |
| 조치 | 체크리스트 / 설비 셧다운 |
| 체크리스트 | 완료/전체 (예: 4/5), 경보는 빈칸 |
| 시각 | `2026-11-19 12:16:42` (한국 시간) |

## 바탕화면 저장에 대해 (참고)

웹 화면은 보안상 저장 경로를 바탕화면으로 직접 정할 수 없습니다.
크롬·엣지에서는 저장 창을 **바탕화면에서 열고 파일 이름을 미리 채워** 두므로 '저장'만 누르면 바탕화면에 저장됩니다.
저장 창이 없는 브라우저는 다운로드 폴더에 저장됩니다 (`src/utils/saveFile.js`). 백엔드가 할 일은 없습니다.
