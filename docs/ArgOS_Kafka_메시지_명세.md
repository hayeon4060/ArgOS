# ArgOS 엣지 ↔ 서버 Kafka 메시지 명세

출처: DAS Node-RED 플로우 **"ArgOS 엣지 (v5)"** (`argos_edge_v5_flow.json`, 2026-11-19).
"ArgOS 엣지 (운영)"(11-18)의 다음 버전으로, ERD V6(edge_key·max_stage·rule, FAN_HZ/FAN_STOP, source)에 맞춘 것입니다.
아래 JSON 은 플로우의 function 노드가 실제로 만드는 필드 그대로입니다.

## 공통

| 항목 | 값 |
|---|---|
| 브로커 | `192.168.0.14:9092` (clientId `argos-edge-p5`, 인증 없음) |
| 값 형식 | JSON 문자열 (UTF-8) |
| 시각 | ISO-8601, 한국 시간 오프셋 포함 `2026-11-16T09:01:30+09:00` (초 단위) |
| 프로듀서 acks | `all` |
| 토픽 자동 생성 | 끔 → 브로커에 토픽을 미리 만들어야 함 |

| 토픽 | 방향 | Kafka key | 언제 |
|---|---|---|---|
| `argos.edge.raw` | 엣지 → 서버 | 태그 (`TE-305`) | 센서 값 1초마다, 통신 끊김 시 1건 |
| `argos.edge.stage` | 엣지 → 서버 | 태그 | 센서 단계가 **바뀔 때만** |
| `argos.edge.cmd` | 서버 → 엣지 | (엣지는 안 봄) | 배기팬 명령 발행 시 |
| `argos.edge.cmd.result` | 엣지 → 서버 | 설비 ID 문자열 (`"36"`) | 명령 처리 결과 |
| `argos.config.threshold` | 서버 → 엣지 | (엣지는 안 봄) | 기준값 생성·변경 시 |

엣지 컨슈머의 group id 는 비어 있음(플로우 기본값). `argos.config.threshold` 는 `fromBeginning: true` 로 읽으므로
엣지를 다시 켜면 토픽의 기준값을 처음부터 다시 적용함 → **센서별 최신 기준값이 토픽에 남아 있어야 함** (보관 기간 넉넉히, 또는 key=tag 로 compaction).

---

## 1. `argos.edge.raw` — 센서 값 (엣지 → 서버)

```json
{ "tag": "TE-305", "ts": "2026-11-16T09:01:30+09:00", "value": 152.4, "quality": "GOOD" }
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `tag` | string | 센서 태그 (배치도 계장 목록: TE-301~305, IT-301·302·303·305·307, AT-301~303) |
| `ts` | string | 측정 시각 |
| `value` | number | 측정값 (℃ / A / ppm) |
| `quality` | string | `GOOD` 정상, `COMM_LOST` 통신 끊김 |

통신 끊김: 어떤 태그가 **10초** 동안 안 들어오면 1번 보냄. `value` 는 마지막으로 받은 값.
```json
{ "tag": "IT-303", "ts": "2026-11-16T09:05:12+09:00", "value": 25.47, "quality": "COMM_LOST" }
```
→ 수집 서버는 이 건수를 `sensor_trend.lost_n` 으로 셈. 다시 들어오기 시작하면 `GOOD` 값이 이어서 옴 (복구 메시지 따로 없음).

열화상(IR-301)은 이 토픽으로 오지 않음 — VMS → REST → 수집 서버 경로.

## 2. `argos.edge.stage` — 센서 단계 변경 (엣지 → 서버)

센서 하나의 이상 구간 = `sensor_event` 1행. 시작(START) → 단계 바뀜(UPDATE, 0번 이상) → 끝(END).

시작 (1 → 2):
```json
{
  "edge_key": "P5-TE-305-20261116T090145",
  "phase": "START",
  "tag": "TE-305",
  "threshold_id": 5,
  "stage": 2,
  "prev_stage": 1,
  "max_stage": 2,
  "rule": "HI",
  "reason": "악화",
  "ts": "2026-11-16T09:01:45+09:00",
  "value": 150.8,
  "plc_address": "D310,2",
  "plc_written": [2, 1508],
  "plc_ok": true
}
```

격상 (2 → 3):
```json
{ "edge_key": "P5-TE-305-20261116T090145", "phase": "UPDATE", "tag": "TE-305", "threshold_id": 5,
  "stage": 3, "prev_stage": 2, "max_stage": 3, "rule": "HI", "reason": "악화",
  "ts": "2026-11-16T09:02:40+09:00", "value": 160.3,
  "plc_address": "D310,2", "plc_written": [3, 1603], "plc_ok": true }
```

끝 (→ 1, `peak_value` 는 END 에만):
```json
{ "edge_key": "P5-TE-305-20261116T090145", "phase": "END", "tag": "TE-305", "threshold_id": 5,
  "stage": 1, "prev_stage": 2, "max_stage": 3, "rule": "HI", "reason": "완화·복귀",
  "ts": "2026-11-16T09:12:05+09:00", "value": 144.6, "peak_value": 170.2,
  "plc_address": "D310,2", "plc_written": [1, 1446], "plc_ok": true }
```

| 필드 | 타입 | 설명 → `sensor_event` 컬럼 |
|---|---|---|
| `edge_key` | string | `P5-{태그}-{시작 시각 yyyyMMddTHHmmss}`, 구간 내내 같음 → `edge_key` (UNIQUE, 중복 수신 막기) |
| `phase` | string | `START` / `UPDATE` / `END` |
| `tag` | string | → `sensor_id` (태그로 찾기) |
| `threshold_id` | number \| null | 판정에 쓴 기준값 ID. 서버가 기준값을 아직 안 보냈으면 **null** (엣지 내장 기본값으로 판정) → `threshold_id` |
| `stage` | 1 \| 2 \| 3 | 바뀐 뒤 단계 (1 정상 2 주의 3 경고). START/UPDATE → `stage`, END 는 1 |
| `prev_stage` | 1 \| 2 \| 3 | 바뀌기 전 단계 |
| `max_stage` | 2 \| 3 | 구간 중 최고 단계 → `max_stage` |
| `rule` | string | `HI` 상한 / `LO` 하한(전류 저하·정지) / `IMMEDIATE` 즉시 3단계(CO ≥ 200) → `rule`. (`AVG`·`IR` 은 엣지에서 안 냄) |
| `reason` | string | `악화` / `완화·복귀` (표시용) |
| `ts` | string | 단계가 바뀐 시각. START 의 ts → `started_at`, END 의 ts → `ended_at` |
| `value` | number | 그 순간 값 |
| `peak_value` | number | END 에만. 구간 최고값(`LO` 면 최저값) → `peak_value` |
| `plc_address` | string \| null | PLC 단계 기록 주소 (`D310,2`). PLC 매핑 없는 태그는 null |
| `plc_written` | [stage, 값×10] \| null | PLC 에 쓴 값 |
| `plc_ok` | boolean \| null | PLC 기록 성공 → `plc_ok` |

판정 (1초 샘플): 2단계 15회 연속 → 2, 3단계 15회 연속 또는 즉시 값 → 3, 기준 안쪽(복귀 여유 `release_margin` 만큼) `release_samples` 회 연속 → 1, 3단계에서 3단계 미만이 `release_samples` 회 연속 → 2 (UPDATE).

## 3. `argos.edge.cmd` — 배기팬 명령 (서버 → 엣지)

```json
{
  "command_id": 101,
  "equipment_id": 36,
  "equipment_code": "ED-FAN",
  "command": "FAN_HZ",
  "value": 50,
  "source": "USER",
  "expires_at": "2026-11-16T09:04:30+09:00"
}
```

| 필드 | 필수 | 설명 (`equipment_command` 컬럼) |
|---|---|---|
| `command_id` | ✅ | 명령 ID. 엣지가 최근 200개를 기억해 같은 ID 는 `DUPLICATE` 로 돌려보냄 |
| `equipment_id` | ✅ (또는 code) | 중앙 서버 시드 설비 ID. 지금 인버터 연결: **36 = ED-FAN (전착 건조로 배기팬)** |
| `equipment_code` | 선택 | `equipment_id` 로 못 찾을 때 코드로 찾음 |
| `command` | ✅ | `FAN_HZ` (value = 20~60 Hz) / `FAN_STOP` |
| `value` | FAN_HZ 만 | 목표 주파수 Hz |
| `source` | 선택 | `USER` / `AUTO` / `RECOVERY` (엣지는 결과에 그대로 돌려줌) |
| `expires_at` | 선택 | 이 시각이 지나 도착하면 `EXPIRED` |

엣지가 바로 거절하는 경우 (결과만 보내고 인버터는 안 건드림):
`DUPLICATE` 같은 command_id / `EXPIRED` 만료 / `REJECTED` 인버터 없는 설비·범위 밖 Hz·지원 안 하는 명령.

## 4. `argos.edge.cmd.result` — 명령 결과 (엣지 → 서버)

인버터에 쓰고 **5초 뒤** 인버터 레지스터(4~9)를 다시 읽어서 확인한 결과 (명령 1건에 결과 1건):
```json
{
  "command_id": 101,
  "equipment_id": 36,
  "command": "FAN_HZ",
  "value": 50,
  "source": "USER",
  "result": "DONE",
  "completed_at": "2026-11-16T09:03:41+09:00",
  "detail": {
    "target_hz": 50, "cmd_hz": 50, "run_cmd": 5058, "output_hz": 50,
    "current_a": 24.8, "fan_rpm": 1500, "message": "50Hz 도달"
  }
}
```

거절된 경우 (`detail` 은 message 만):
```json
{ "command_id": 102, "equipment_id": 39, "command": "FAN_HZ", "value": 50, "source": "AUTO",
  "result": "REJECTED", "completed_at": "2026-11-16T09:03:40+09:00",
  "detail": { "message": "equipment_id 39 인버터 없음" } }
```

| 필드 | 설명 → `equipment_command` |
|---|---|
| `result` | `DONE` (도달 또는 변속 중) / `FAILED` (지령 불일치·읽기 실패) / `REJECTED` / `EXPIRED` / `DUPLICATE` → `result` |
| `completed_at` | → `completed_at` |
| `detail` | → `result_detail` (JSON) |
| `detail.cmd_hz` / `output_hz` | 인버터 지령 주파수 / 실제 출력 주파수 |
| `detail.run_cmd` | 운전 지령 레지스터 값 (5058 운전, 5057 정지) |
| `detail.current_a` | 출력 전류 A |
| `detail.fan_rpm` | 출력 Hz × 30 (→ `equipment.fan_rpm` 갱신용) |

`FAILED` 로 읽기 자체가 실패한 경우에는 `completed_at` 이 엣지 시각으로 채워짐.

## 5. `argos.config.threshold` — 기준값 (서버 → 엣지)

센서 1개 = 메시지 1개. `sensor_threshold` 현재 행 그대로.
```json
{
  "threshold_id": 8,
  "tag": "IT-303",
  "warn_value": 28.05,
  "alarm_value": 31.88,
  "low_warn": 22.95,
  "low_alarm": 17.85,
  "immediate_value": null,
  "warn_samples": 15,
  "alarm_samples": 15,
  "release_samples": 20,
  "release_margin": 0.05
}
```
```json
{ "threshold_id": 11, "tag": "AT-301", "warn_value": 15, "alarm_value": 30, "low_warn": null, "low_alarm": null,
  "immediate_value": 200, "warn_samples": 15, "alarm_samples": 15, "release_samples": 20, "release_margin": 0.05 }
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `tag` | ✅ | 센서 태그 |
| `threshold_id` | 권장 | 엣지가 이후 `argos.edge.stage` 의 `threshold_id` 로 그대로 돌려줌 |
| `warn_value` / `alarm_value` | 하나 이상 | 상한 2·3단계 (`warn < alarm` 이어야 함) |
| `low_warn` / `low_alarm` | 선택 | 하한 2·3단계 (`low_warn > low_alarm` 이어야 함) |
| `immediate_value` | 선택 | 이 값 이상이면 즉시 3단계 |
| `warn_samples` / `alarm_samples` / `release_samples` | 선택 | 1초 샘플 연속 횟수. 빠지면 엣지 기본값 15 / 15 / 15 |
| `release_margin` | 선택 | 복귀 여유 (기준값에서 이만큼 안쪽으로 와야 정상). 빠지면 0 |

엣지는 검증에 실패하면 그 메시지를 버리고 노드 상태에만 표시함 (응답 토픽 없음). 적용하면 그 태그의 연속 횟수를 0 으로 초기화.
`alarm_avg_sec` (CO 60초 평균)는 지금 엣지가 읽지 않음.

---

## 확인·맞춰야 할 것

1. **복귀 횟수**: 팀 결정은 20회(20초)인데 엣지 기본값은 15회. 서버가 `release_samples: 20` 을 보내면 그 값으로 동작함.
2. **엣지 내장 기본값**: 서버 기준값이 오기 전까지는 6개 태그(AT-301·303, IT-303·307, TE-303·305)만 내장 값으로 판정하고, 나머지 태그는 판정하지 않음 (`raw` 만 보냄). IT-307 내장 하한 17.85 는 서버 시드(정상 18.0A → 하한 16.2)와 다름 → **서버가 켜질 때 모든 센서 기준값을 이 토픽에 한 번씩 보내 주세요.**
3. **인버터**: 실제 연결된 인버터는 전착 건조로 배기팬(설비 36) 하나뿐. 다른 배기팬 명령은 `REJECTED` 로 돌아옴.
4. **IR-301**: Kafka 가 아니라 VMS REST 경로. 이 문서 범위 밖.
