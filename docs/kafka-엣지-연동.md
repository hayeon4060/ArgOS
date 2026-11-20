# 엣지(DAS) Kafka · VMS 연동 — 데이터 수집 서버

데이터 수집 서버(`factory/central-server`)가 엣지·VMS 와 주고받는 것 전체. 메시지 모양은 [ArgOS_Kafka_메시지_명세.md](ArgOS_Kafka_메시지_명세.md),
배기팬 시험 방법은 [kafka-배기팬-시험.md](kafka-배기팬-시험.md).

```
봇 ─MQTT─▶ 엣지(Node-RED) ─ argos.edge.raw   ─▶ 수집 서버: 1분 추이 · 현재값 (판정 안 함)
                          ─ argos.edge.stage ─▶ 수집 서버: 센서 이벤트 → 관제 단위 조합 경보 → (가스) 배기팬 자동 제어
                          ◀ argos.config.threshold ─ 수집 서버: 서버 시작·기준값 변경 때 모든 센서 기준값
                          ◀ argos.edge.cmd / ▶ argos.edge.cmd.result : 배기팬 명령·결과
봇 ─HTTP─▶ VMS 서버(IR 판정) ─REST POST /api/v1/vms/ir─▶ 수집 서버: 열화상 이벤트 · 화재 경보 · 사진
```

## 정한 것

| 항목 | 내용 |
|---|---|
| 단계 판정 | **센서 단계는 엣지(DAS)가 판정** (`argos.edge.stage`). 서버는 다시 판정하지 않고 받은 단계로 `sensor_event` 를 만든다 |
| 경보 | **서버는 조합 경보만** 만든다: 같은 관제 단위 센서들의 이벤트를 묶어 경보 1건 (단계 = 가장 높은 단계, 종류 = 온도·IR 있으면 FIRE, CO 면 GAS, 전류 저하면 GAS) |
| 시각 | **엣지·VMS 가 보낸 `ts` 는 쓰지 않고, 받은 시각(서버 시계)으로 저장** (2026-11-20). 봇이 CSV 시각(11-16 09:02:10~09:07:10)을 5분마다 반복해 보내도 화면이 실시간처럼 흐른다 |
| 기준값 연속 횟수 | 엣지는 1초 샘플이라 DB 값(5초 샘플 24·6·6)이 아니라 **주의 15 · 경보 15 · 복귀 20** 을 보낸다 (환경변수로 바꿀 수 있음) |
| Kafka 꺼짐 | 지금처럼 개발용 입력(`/api/v1/dev/readings`)을 서버 판정 엔진이 단계까지 판정 |

## 토픽별 처리

| 토픽 | 처리 |
|---|---|
| `argos.edge.raw` | 값 → 실시간 현재값·1분 추이(`sensor_trend`)·열린 알림의 센서 값·열린 이벤트 샘플. `quality: COMM_LOST` → `sensor_trend.lost_n` |
| `argos.edge.stage` START | 이벤트 시작: `edge_key`, `rule`, `threshold_id`(null 가능), `plc_ok` 저장. 같은 구간 키가 이미 열려 있으면 무시 (다시 온 메시지) |
| `argos.edge.stage` UPDATE | 단계 올림 → 경보에도 반영. 3 → 2 는 이벤트 단계만 (경보는 내려가지 않음). 열린 구간이 없으면 그 단계로 새로 연다 |
| `argos.edge.stage` END | 이벤트 끝, `peak_value` 는 엣지 값. 경보는 사람이 닫을 때까지 열려 있음 |
| `argos.config.threshold` | 서버 시작 때, 기준값·센서 저장 때 IR 빼고 모든 센서 (key = 태그). **압축(compact) 토픽으로 만들어 주세요** |

- 같은 `edge_key` 가 DB 에 이미 있으면 (봇 시나리오 반복·엣지 재시작) 뒤에 `-2`, `-3` 을 붙여 저장한다.
- 다른 구간 START 가 오면 앞 구간의 END 가 빠진 것으로 보고 앞 구간을 닫는다. 서버가 도중에 켜져서 모르는 구간의 END 가 오면 DB 에 열린 채 남은 그 구간을 닫는다.
- 가스 경보가 열리거나 단계가 오르면 지금처럼 배기팬 자동 제어(자동이면 엣지로 명령, 수동이면 승인 대기).

## 실행

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `ARGOS_KAFKA_ENABLED` | `false` | 켜면 위 토픽을 모두 주고받는다 |
| `ARGOS_KAFKA_BOOTSTRAP` | `localhost:9092` | 브로커 (팀: `192.168.0.14:9092`) |
| `ARGOS_KAFKA_GROUP_ID` | `argos-collector-{PC 이름}` | PC 마다 달라야 각자 모든 메시지를 받는다 |
| `ARGOS_EDGE_WARN_SAMPLES` · `ARGOS_EDGE_ALARM_SAMPLES` · `ARGOS_EDGE_RELEASE_SAMPLES` | `15` · `15` · `20` | 엣지로 보내는 연속 횟수 |

브로커에 있어야 하는 토픽: `argos.edge.raw`, `argos.edge.stage`, `argos.edge.cmd`, `argos.edge.cmd.result`, `argos.config.threshold`(compact).

켜지면 로그에 `엣지로 기준값 13개 보냄 (서버 시작, 연속 횟수 15·15·20)` 과 `partitions assigned` 3줄(raw · stage · cmd.result)이 나온다.
단계가 오면 `엣지 단계: P5-AT-301-… START AT-301 stage=2 rule=HI plc_ok=true`.

## VMS 서버 (열화상 IR-301)

`vms_server.py --main-url http://{수집 서버 IP}:8081/api/v1/vms/ir` 로 실행한다 (기본값 `127.0.0.1:8080/vms/ir` 은 우리 주소가 아님).
화면 영상 주소는 IR-301 `sensor.stream_url` 을 `http://{VMS IP}:9000/stream/IR-301` 로 바꾸면 된다.
