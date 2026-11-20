# DAS 엣지 — Node-RED 플로우 "ArgOS 엣지 (v5)"

DAS PC 의 Node-RED 에서 돌리는 엣지 플로우입니다. 센서값 수집 → 센서별 단계 판정 → PLC 기록 → Kafka 송신, 서버 명령을 받아 배기팬 인버터를 제어합니다.
Kafka 메시지 모양은 [docs/ArgOS_Kafka_메시지_명세.md](../../docs/ArgOS_Kafka_메시지_명세.md), 배기팬 명령 시험은 [docs/kafka-배기팬-시험.md](../../docs/kafka-배기팬-시험.md) 를 따릅니다.

| 파일 | 내용 |
|---|---|
| `argos_edge_v5_flow.json` | 플로우 탭 1개 + 쓰는 설정 노드 (MQTT 브로커 · Modbus · PLC 연결) |

## 흐름

| 줄 | 하는 일 | 입출력 |
|---|---|---|
| ⓪ | 판정 기준값 받기 (시작할 때마다 처음부터 다시 읽음) | Kafka `argos.config.threshold` |
| ① | 센서값 받기 (1초, 센서별 메시지) | MQTT `argos/#` |
| ①-b | 모든 원시값 보내기 | Kafka `argos.edge.raw` |
| ② | 센서별 단계 판정 (1 정상 / 2 주의 / 3 경고), 바뀔 때만 출력 | 기준값 없으면 플로우 기본값 |
| ③ | 단계가 바뀌면 PLC 기록 `[단계, 값×10]` (센서별 2워드, D300~D311) | MC 프로토콜 |
| ④ | 단계 변경 보내기 (`edge_key` + START / UPDATE / END) | Kafka `argos.edge.stage` |
| ⑤ | 서버 명령 받기 → 대상 · 만료 · 중복 확인 | Kafka `argos.edge.cmd` (`FAN_HZ` 20~60 / `FAN_STOP`) |
| ⑤-x | 인버터 실행: 주파수(주소 4, Hz×100) → 운전(주소 5, 5058) / 정지 5057 | Modbus TCP |
| ⑤-s | 현장 비상 정지 버튼 (서버 · Kafka 안 거침) | Modbus TCP |
| ⑥ | 5초 뒤 인버터 레지스터 4~9 읽어 도달 확인 → 결과 보내기 | Kafka `argos.edge.cmd.result` |
| ⑦ | 센서가 10초간 안 오면 `quality: COMM_LOST` 1건 | Kafka `argos.edge.raw` |

- 엣지는 **센서 단계**만 판정합니다. 가스 · 화재 조합, 알림, 배기팬 자동/수동(`fan_auto`)·승인·복귀는 서버가 하고 엣지는 받은 명령만 실행합니다.
- 열화상(IR-301)은 엣지를 거치지 않습니다 (VMS → 서버).
- 시각은 **센서 데이터의 `ts` 를 "지금"으로** 씁니다 (시연 시계 11/16 기준). `COMM_LOST` · `completed_at` · 명령 만료 비교가 모두 이 시각입니다.

## 준비

1. **Node-RED** (만든 환경: 5.0.9) — 팔레트 관리에서 설치

   | 모듈 | 만든 환경 버전 | 쓰는 곳 |
   |---|---|---|
   | `node-red-contrib-kafkajs` | 0.0.7 | Kafka 송수신 |
   | `node-red-contrib-modbus` | 5.62.1 | 인버터 (미니 PC 게이트웨이) |
   | `node-red-tienho-mcprotocol` | 2.0.0 | PLC 쓰기 |

2. **Kafka 브로커**가 먼저 켜져 있어야 합니다. 꺼진 채로 Node-RED 를 켜면 Kafka 노드 접속 실패로 Node-RED 가 약 20초 뒤 종료됩니다.
   토픽(자동 생성 꺼짐): `argos.edge.raw` `argos.edge.stage` `argos.edge.cmd` `argos.edge.cmd.result` `argos.config.threshold`
3. **MQTT 브로커** (Mosquitto) — 센서 · 챗봇이 `argos/<공장>/<공정>/<TE|IT|AT>/<태그>` 로 보냄

## 가져오기

1. Node-RED 메뉴 → 가져오기(Ctrl+I) → 파일 선택 → `argos_edge_v5_flow.json` → **새로운 플로우**로 가져오기
2. 아래 설정을 자기 환경에 맞게 고친 뒤 배포
3. 같은 MQTT · PLC · 인버터를 쓰는 다른 탭(예전 엣지 탭 등)은 **꺼 둘 것** — 켜 두면 같은 데이터를 두 번 처리합니다

## 바꿔야 할 설정

| 어디 | 지금 값 | 설명 |
|---|---|---|
| 설정 노드 `argos-kafka` (kafkajs-client) | `192.168.0.14:9092` | Kafka 브로커. DAS PC IP 가 바뀌면 같이 변경 |
| MQTT 브로커 설정 노드 | `127.0.0.1:1883` | Node-RED 와 같은 PC 의 Mosquitto |
| Modbus 설정 노드 `서버PC` | `192.168.0.100:502`, unit 1 | 인버터(LS iG5A) RS-485 게이트웨이 |
| PLC 설정 노드 `PLC` | `192.168.0.50:8004` (TCP, 3E, Binary) | 미쓰비시 Q03UDE. **포트 하나에 접속 하나만** 응답하므로 4조 포트 8004 사용 |
| ⑤ 명령 검증 function 의 `INVERTERS` | `36 = ED-FAN` | 실제 인버터가 연결된 설비 (중앙 서버 시드 ID). 다른 설비 명령은 `REJECTED` |
| ② 판정 function 의 `DEFAULTS` / `PLC` | 센서 6개 기본 기준값 · PLC 주소 | 서버 기준값(⓪)을 받으면 그 값이 우선 |

## 확인 방법

- 노드 아래 상태 글자: ①-b 전송 건수, ② `전체 정상 (6개)` 또는 이상 센서, ③ 마지막 PLC 쓰기, ⑥ 명령 결과
- Kafka 에 실제로 들어간 메시지 (DAS PC):

  ```bash
  docker exec argos-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic argos.edge.stage --from-beginning --property print.key=true
  ```
