# 배기팬 제어 Kafka 시험 안내

데이터 수집 서버(`factory/central-server`)가 배기팬 명령을 엣지(DAS)로 보내고, 엣지 결과로 명령을 끝내는 것을 시험하는 방법.
메시지 모양은 [ArgOS_Kafka_메시지_명세.md](ArgOS_Kafka_메시지_명세.md) 3·4절과 같다.

## 흐름

```
화면 / API ─▶ 수집 서버 ─ argos.edge.cmd ─▶ 엣지(Node-RED) ─▶ 인버터
                  ▲                                │ 5초 뒤 레지스터 다시 읽음
                  └──── argos.edge.cmd.result ◀────┘
```

| 명령 | Kafka 켜짐 | Kafka 꺼짐 (기본) |
|---|---|---|
| `FAN_HZ` · `FAN_STOP` | 엣지로 보내고 엣지 결과로 끝냄 | 1초 뒤 완료 (시뮬레이션) |
| `SHUTDOWN` · `RESTART` | 시뮬레이션 (엣지 명세에 없음) | 시뮬레이션 |

실제 인버터는 **설비 36 = ED-FAN (전착 건조로 배기팬)** 하나뿐이다. 39 PR-FAN · 42 TC-FAN 은 엣지가 `REJECTED` 로 돌려준다.

## 준비

1. 브로커에 토픽 두 개가 있어야 한다 (자동 생성 꺼짐): `argos.edge.cmd`, `argos.edge.cmd.result`
2. 팀 내부망 PC 에서 수집 서버를 아래 환경변수로 켠다.

| 환경변수 | 값 | 설명 |
|---|---|---|
| `ARGOS_KAFKA_ENABLED` | `true` | 켜야 엣지로 보낸다 (기본 `false`) |
| `ARGOS_KAFKA_BOOTSTRAP` | `192.168.0.14:9092` | 브로커 주소 (기본 `localhost:9092`) |
| `ARGOS_KAFKA_GROUP_ID` | (비워 두기) | 결과를 받는 컨슈머 그룹. 기본은 `argos-collector-{PC 이름}` 으로 PC 마다 다르다 |

```bash
cd factory/central-server
ARGOS_KAFKA_ENABLED=true ARGOS_KAFKA_BOOTSTRAP=192.168.0.14:9092 FACTORY_DB_PASSWORD=... mvn spring-boot:run
```

켜지면 로그에 `partitions assigned: [argos.edge.cmd.result-0]` 가 나온다.

## 시험

화면에서 전착 배기팬을 조작하거나, 수집 서버 API 로 바로 보낸다.

```bash
# 50Hz
curl -X POST localhost:8081/api/v1/internal/equipment/36/commands \
     -H 'Content-Type: application/json' -d '{"command":"FAN_HZ","value":50}'
# 정지
curl -X POST localhost:8081/api/v1/internal/equipment/36/commands \
     -H 'Content-Type: application/json' -d '{"command":"FAN_STOP"}'
```

`ARGOS_INTERNAL_API_KEY` 를 정했으면 `-H 'X-Argos-Internal-Key: ...'` 도 붙인다.
응답은 바로 `SENT` 이고, 약 5초 뒤 엣지 결과가 오면 화면(SSE `command.result`)과 DB 에 반영된다.

| 엣지 result | 우리 DB `equipment_command.result` | 화면 |
|---|---|---|
| `DONE` | `DONE`, `equipment.fan_rpm` = `detail.fan_rpm` | 완료, 실제 회전수 |
| `REJECTED` (인버터 없음·범위 밖) | `REJECTED` | 실패 |
| `FAILED` · `EXPIRED` | 그대로 | 실패 |
| `DUPLICATE` (엣지가 이미 받은 command_id) | `FAILED` (실행 안 됐으므로) | 실패 |
| 15초 안에 결과 없음 | `FAILED` ("엣지 응답 없음 (15초)") | 실패 |

엣지 `detail` (`cmd_hz`, `output_hz`, `current_a`, `run_cmd`, `fan_rpm`, `message`)과 엣지 `completed_at` 은
`equipment_command.result_detail` 에 camelCase 로 남는다 (`cmdHz`, `outputHz`, `currentA`, `runCmd`, `fanRpm`, `message`, `edgeCompletedAt`, `edgeResult`).

로그에서 볼 줄:
```
엣지로 명령 보냄: 명령 6 설비 36(ED-FAN) FAN_HZ 50.0
엣지 명령 결과: 명령 6 설비 36 FAN_HZ 50.0 → DONE
```

## 주의

- **만료 시각** `expires_at` 은 실제 시각 + 30초로 보낸다 (DB·화면은 시연 시계). 엣지는 센서 데이터 ts 로 "지금"을 잡는다.
- **여러 PC 가 같이 시험할 때**: 결과 토픽은 모든 PC 가 받는다. 각 서버는 **자기 DB 에서 아직 처리 중(PENDING)이고 설비·명령이 같은 명령**에만
  결과를 반영하고, 나머지는 `엣지 명령 결과 무시` 로그만 남긴다. 그래도 엣지는 command_id 만 보고 중복을 거르므로,
  가능하면 **한 DB 에서만 명령을 보낸다**.
- **`DUPLICATE` 가 오면**: 엣지가 최근 command_id 200개를 메모리에 기억해서 생긴다 (DB 를 바꿨을 때 등). 엣지(Node-RED)를 재시작하면 풀린다.
- 결과가 늦게 와서(15초 뒤) 이미 `FAILED` 로 끝난 명령에는 반영하지 않는다. 실제 팬 상태는 다음 명령 결과로 맞춰진다.

## 로컬에서 시험 (내부망 없이)

```bash
docker run -d --name argos-kafka -p 9092:9092 apache/kafka:3.8.0
docker exec argos-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic argos.edge.cmd
docker exec argos-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic argos.edge.cmd.result
# 보낸 명령 보기
docker exec argos-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic argos.edge.cmd
# 결과 직접 넣기 (key|value)
docker exec -i argos-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic argos.edge.cmd.result --property parse.key=true --property key.separator='|'
36|{"command_id":6,"equipment_id":36,"command":"FAN_HZ","value":50,"result":"DONE","detail":{"output_hz":50,"fan_rpm":1500}}
```
(Git Bash 에서는 `MSYS_NO_PATHCONV=1` 을 앞에 붙인다.)
