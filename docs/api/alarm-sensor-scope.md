# 경보 창 '센서 데이터'에 공정 센서가 전부 나오는 문제 — 백엔드 요청 (2026-11-20)

프론트 브랜치 `feature/frontend-alarm-sensor-scope`. **새 API · DB 변경은 없습니다.** 프론트에서 먼저 걸러서 화면은 고쳤고, 백엔드에는 응답을 줄여 달라는 요청 1건입니다.

## 1. 무슨 일이 있었나

상도 건조로 3단계 화재 경보 창의 '센서 데이터'에 TE-301 ~ TE-305, IT-301 … 처럼 **도장 공정 센서가 전부** 게이지로 나왔습니다.
설계안(`mAlarm()`)은 경보가 난 관제 단위의 **센서 3종(전류 · 온도 · CO)** 만 보여 줍니다.

## 2. 원인

`GET /alarms/{alarmId}/response` 의 `sensors` 가 공정 전체입니다.

- `AlarmService.response()` 가 `central.status(processId).sensors()` 를 **거르지 않고** 모두 넣음 (주석에도 '같은 공정의 센서 전부')
- 같은 서비스의 `detail()` (주의 상세) 과 `FireService` 는 관제 단위 구성 센서(`control_unit_sensor`)로 거름 → 주의 상세 · 화재 창은 정상
- 프론트는 받은 `sensors` 를 그대로 그렸음

열화상 카메라 IR-301 때문은 아닙니다. IR-301 은 관제 단위 5(상도 건조로)에만 들어 있는 센서이고, 문제는 응답이 단위가 아니라 공정 기준이라서입니다.

상도 건조로(관제 단위 5)의 센서는 여러 설비에 나뉘어 달려 있어서, 설비 이름으로는 거를 수 없습니다.

| 태그 | 종류 | 달린 설비 |
|---|---|---|
| TE-305 | 온도 | 상도 건조로 |
| IT-307 | 전류 | 상도 건조로 배기팬 |
| AT-303 | CO | 상도 건조로 버너실 |
| IR-301 | 열화상 | 상도 열화상 카메라 (경보 창에서는 왼쪽 열화상 칸으로 따로 보여 줌) |

## 3. 프론트에서 한 것 (지금 서버 그대로 동작)

- 경보 창을 열 때 `GET /alarms/{id}/detail` 을 **한 번** 불러 그 `sensors` 태그(관제 단위 센서)만 남김 (`features/alert/alarmSensors.js`)
  - 값 · 단계는 자주 다시 받는 `response` 쪽 값을 그대로 씀. `detail` 은 무거워서 SSE 마다 다시 부르지 않음
  - `detail` 을 못 받으면 예전처럼 받은 목록에서 열화상만 빼고 보여 줌
- 게이지는 전류 · 온도 · CO 만, 순서는 설계안처럼 전류 → 온도 → CO
- 화재 창 '감지 근거' 칸의 센서 이름(`온도 TE-…`, `CO AT-…`)도 같은 목록에서 찾음 (전에는 공정 전체에서 찾아 `TE-301` 이 나올 수 있었음)
- 단계 배지 · 게이지 색 · 테두리: 설비 상세와 같은 규칙(서버 단계와 '지금 값 vs 위쪽 기준' 중 높은 쪽, `utils/sensorLevel.js`, PR #91)

## 4. 요청

`AlarmService.response()` 의 `sensors` 를 `detail()` 처럼 **관제 단위 구성 센서만** 넣어 주세요.

```java
// detail() 과 같은 방법
CentralDto.Unit unit = m.unit(a.unitId());
Set<Long> unitSensors = new HashSet<>();
if (unit != null) unit.members().forEach(x -> unitSensors.add(x.sensorId()));
for (SensorStatus s : status.sensors()) {
    if (unitSensors.isEmpty() || unitSensors.contains(s.sensorId())) {   // 단위를 모르면 지금처럼 전부
        sensors.add(sensorRow(s, m));
    }
    ...
}
```

- `equipment`(셧다운할 설비 목록)는 지금처럼 **공정 전체 그대로** 두어 주세요. 공정 전체 셧다운에 씁니다.
- `cameraId` 도 같은 반복문에서 고르니, 단위의 IR 센서가 먼저 잡히도록 같이 걸러지면 더 맞습니다.
- 바뀌면 프론트의 `detail` 추가 호출은 빼도 됩니다. 바뀌기 전 · 후 모두 화면은 같게 나옵니다.

## 5. 확인해 주세요

- 응답 `sensors[].level` 은 엣지 판정 단계(`argos.edge.stage`)라 값보다 늦을 수 있어, 화면은 값이 위쪽 기준을 넘으면 그 단계로 올려 보여 줍니다 (PR #91 과 같은 규칙).
