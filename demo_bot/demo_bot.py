#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ArgOS 시연용 센서 데이터 전송 봇 (터미널)

터미널에 명령을 입력하면 scenarios 폴더의 CSV를 1초 간격으로 Node-RED에 전송합니다.
  전착_2단계   전착 건조로 가스 2단계 (CO 15~30 ppm)
  전착_3단계   전착 건조로 화재 3단계 (TE 설정온도 +20 ℃ 이상)
  상도_2단계   상도 건조로 가스 2단계
  상도_3단계   상도 건조로 화재 3단계
  q            시나리오를 끝내고 정상 데이터로 복귀
  정상         q와 같음
  상태         지금 재생 중인 시나리오 보기
  도움말       명령어 보기
  나가기       프로그램 끝내기 (exit, quit)

아무 시나리오도 없을 때는 모든 센서의 정상 데이터를 계속 반복해서 보냅니다.

열화상(IR-301)은 다른 센서와 경로가 다릅니다. TE·IT·AT는 MQTT(또는 Node-RED)로 가지만,
IR은 카메라 역할로 CSV 값(시각·시리얼·온도)을 VMS 서버(--vms-url)로 HTTP POST 합니다.
단계 판정, ROI, 스냅샷, 메인 서버 전송은 VMS 서버가 맡습니다.

전송 방식 (--mode)
  mqtt (기본)  MQTT 브로커로 센서 1개당 1메시지 발행. paho-mqtt 필요 (python -m pip install paho-mqtt)
  http         Node-RED http in 으로 JSON 배열 POST. 표준 라이브러리만 사용
--dry-run은 두 방식 모두 설치 없이 동작합니다.
"""
import argparse
import csv
import json
import os
import re
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime

UNITS = {"TE": "℃", "IT": "A", "AT": "ppm", "IR": "℃"}
QUALITY = "GOOD"

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SCEN_DIR = os.path.join(BASE_DIR, "scenarios")
NORMAL_DIR = "정상"
CSV_START_SEC = 9 * 3600 + 2 * 60 + 10  # CSV의 시각은 09:02:10 시작 (영상 시각과 동일, 1초 간격으로 301행)
CSV_STEP_SEC = 1          # CSV 행 사이의 간격(초)


def load_dir(path):
    """폴더 안의 CSV를 읽어 {센서 시리얼: {인덱스: (값, CSV의 timestamp)}, ...} 와 {시리얼: 센서종류} 로 돌려줍니다."""
    streams, types = {}, {}
    for fn in sorted(os.listdir(path)):
        if not fn.lower().endswith(".csv"):
            continue
        with open(os.path.join(path, fn), encoding="utf-8-sig", newline="") as f:
            rows = list(csv.DictReader(f))
        serial = None
        for r in rows:
            serial = r["sensor_serial_no"]
            h, m, s = (int(x) for x in r["timestamp"].split(" ")[1].split(":"))
            idx = (h * 3600 + m * 60 + s - CSV_START_SEC) // CSV_STEP_SEC
            streams.setdefault(serial, {})[idx] = (float(r["value"]), r["timestamp"].strip())
            types[serial] = r["sensor_type"]
        if not rows:  # 데이터 행이 없는 파일(IR 이벤트 없음)은 파일명으로 시리얼을 알 수 없으므로 건너뜀
            continue
    return streams, types


def normalize_command(text):
    t = re.sub(r"[\s\-]+", "_", text.strip())
    m = re.fullmatch(r"(전착|중도|상도)_?(\d)_?단계", t)
    return f"{m.group(1)}_{m.group(2)}단계" if m else t


def mqtt_topic(base, r):
    area = "vms" if r["sensor_type"] == "IR" else "paint"  # 열화상은 VMS 경로
    return f"{base.rstrip('/')}/{area}/{r['sensor_type']}/{r['sensor_serial_no']}"


def mqtt_message(r):
    """내부 형식(CSV 컬럼 이름)을 팀 MQTT 메시지 형식으로 바꿉니다. ts는 CSV 값 그대로."""
    return json.dumps({"ts": r["timestamp"], "type": r["sensor_type"], "serial": r["sensor_serial_no"],
                       "value": r["value"], "unit": UNITS.get(r["sensor_type"], ""), "quality": QUALITY},
                      ensure_ascii=False)


class MqttSender:
    """paho-mqtt로 센서 1개당 1메시지를 발행합니다. 끊긴 동안의 데이터는 쌓지 않고 버립니다."""

    def __init__(self, host, port, qos, topic_base, username, password):
        import paho.mqtt.client as mqtt  # MQTT 모드에서만 필요
        self.mqtt, self.qos, self.topic_base = mqtt, qos, topic_base
        client_id = f"argos-demo-bot-{uuid.uuid4().hex[:6]}"
        try:  # paho-mqtt 2.x
            self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id)
        except AttributeError:  # paho-mqtt 1.x
            self.client = mqtt.Client(client_id=client_id)
        if username:
            self.client.username_pw_set(username, password)
        self.client.reconnect_delay_set(min_delay=1, max_delay=5)
        self.client.connect_async(host, port, keepalive=30)
        self.client.loop_start()  # 연결·재연결·QoS 1 응답은 백그라운드 스레드가 처리

    def send(self, payload):
        if not self.client.is_connected():
            raise OSError("브로커에 연결되어 있지 않음 (자동 재연결 시도 중)")
        for r in payload:
            info = self.client.publish(mqtt_topic(self.topic_base, r), mqtt_message(r), qos=self.qos)
            if info.rc != self.mqtt.MQTT_ERR_SUCCESS:
                raise OSError(f"발행 실패 (rc={info.rc})")

    def close(self):
        self.client.disconnect()
        self.client.loop_stop()


class Bot:
    def __init__(self, args):
        self.mode, self.url, self.interval = args.mode, args.url, args.interval
        self.dry_run, self.quiet = args.dry_run, args.quiet
        self.vms_url, self.no_vms = args.vms_url, args.no_vms
        self.only_ir = args.only_ir
        self.vms_error = None
        self.target = args.url if self.mode == "http" else f"mqtt://{args.host}:{args.port} ({args.topic_base}/...)"
        self.topic_base = args.topic_base
        self.mqtt = None
        if self.mode == "mqtt" and not self.dry_run and not self.only_ir:
            self.mqtt = MqttSender(args.host, args.port, args.qos, args.topic_base, args.username, args.password)
        self.lock = threading.Lock()
        self.stop_event = threading.Event()
        self.normal, self.types = load_dir(os.path.join(SCEN_DIR, NORMAL_DIR))
        self.normal_len = max(max(v) for v in self.normal.values()) + 1
        self.scenarios = {}  # "상도_2단계" -> (폴더이름, streams, types, 길이)
        for d in sorted(os.listdir(SCEN_DIR)):
            if d == NORMAL_DIR or not os.path.isdir(os.path.join(SCEN_DIR, d)):
                continue
            key = d.split("(")[0]
            st, ty = load_dir(os.path.join(SCEN_DIR, d))
            n = max((max(v) for v in st.values() if v), default=300) + 1
            self.scenarios[key] = (d, st, ty, n)
        # 이벤트 센서(IR): 시나리오에만 있고 정상 폴더에는 없는 센서. 이벤트가 없으면 value=null로 보냄
        self.event_sensors = {s: ty[s] for _, st, ty, _ in self.scenarios.values() for s in st
                              if s not in self.normal}
        self.active = None       # 현재 시나리오 키 (없으면 정상)
        self.scen_idx = 0
        self.norm_idx = 0
        self.last_error = None
        self.sent_ticks = 0

    # ───────── 전송 ─────────
    def build_payload(self):
        readings = {}
        for serial, vals in self.normal.items():
            readings[serial] = (self.types[serial], vals.get(self.norm_idx % self.normal_len))
        if self.active:
            _, st, ty, n = self.scenarios[self.active]
            for serial, vals in st.items():  # 시나리오 센서는 정상 값을 덮어씀 (IR은 이벤트가 있을 때만 값이 있음)
                readings[serial] = (ty[serial], vals.get(self.scen_idx % n))
        for serial, typ in self.event_sensors.items():
            readings.setdefault(serial, (typ, None))
        # 값이 없는 이벤트 센서에 붙일 timestamp: 같은 회차 다른 센서의 CSV 시각
        tick_ts = next(e[1] for _, e in readings.values() if e is not None)
        out = []
        for serial, (typ, entry) in sorted(readings.items()):
            if entry is None:  # 이벤트 센서(IR)는 해당 시각에 이벤트가 없으면 value=null
                ts, val = tick_ts, None
            else:
                val, ts = entry  # CSV의 timestamp를 바꾸지 않고 그대로 전송
                val = round(val, 2)
            out.append({"timestamp": ts, "sensor_type": typ, "sensor_serial_no": serial, "value": val})
        return out

    def print_dry_run(self, payload):
        if self.mode == "mqtt":
            lines = [f"  {mqtt_topic(self.topic_base, r)}  {mqtt_message(r)}" for r in payload]
        else:
            lines = [f"  POST {self.url}  {json.dumps(payload, ensure_ascii=False)}"]
        print("\n" + "\n".join(lines))

    def post(self, payload):
        if self.dry_run:
            if not self.quiet:
                self.print_dry_run(payload)
            return True
        try:
            if self.mode == "mqtt":
                self.mqtt.send(payload)
                ok = True
            else:
                req = urllib.request.Request(self.url, data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                                             headers={"Content-Type": "application/json; charset=utf-8"},
                                             method="POST")
                with urllib.request.urlopen(req, timeout=3) as resp:
                    ok = 200 <= resp.status < 300
        except (urllib.error.URLError, OSError) as e:
            msg = f"전송 실패: {e}"
            if msg != self.last_error:
                print(f"\n[경고] {msg} (주소: {self.target})\n> ", end="", flush=True)
            self.last_error = msg
            return False
        if self.last_error:
            print("\n[알림] 연결이 복구되었습니다.\n> ", end="", flush=True)
        self.last_error = None
        return ok

    def post_vms(self, rows):
        """IR 행을 VMS 서버로 한 건씩 HTTP POST 합니다 (카메라가 VMS에 측정값을 주는 역할)."""
        if not rows or self.no_vms:
            return True
        if self.dry_run:
            if not self.quiet:
                print("\n".join(f"  POST {self.vms_url}  {mqtt_message(r)}" for r in rows))
            return True
        ok = True
        for r in rows:
            try:
                req = urllib.request.Request(self.vms_url, data=mqtt_message(r).encode("utf-8"),
                                             headers={"Content-Type": "application/json; charset=utf-8"},
                                             method="POST")
                with urllib.request.urlopen(req, timeout=2) as resp:
                    ok = ok and 200 <= resp.status < 300
            except (urllib.error.URLError, OSError) as e:
                msg = f"VMS 전송 실패: {e}"
                if msg != self.vms_error:
                    print(f"\n[경고] {msg} (주소: {self.vms_url})\n> ", end="", flush=True)
                self.vms_error = msg
                return False
        if self.vms_error:
            print("\n[알림] VMS 연결이 복구되었습니다.\n> ", end="", flush=True)
        self.vms_error = None
        return ok

    def loop(self):
        next_t = time.monotonic()
        while not self.stop_event.is_set():
            with self.lock:
                payload = self.build_payload()
                ir_rows = [p for p in payload if p["sensor_type"] == "IR"]
                main_rows = [p for p in payload if p["sensor_type"] != "IR"]
                ok = True if self.only_ir else self.post(main_rows)  # --only-ir: TE·IT·AT는 보내지 않음
                ok = self.post_vms(ir_rows) and ok  # IR은 MQTT/Node-RED가 아니라 VMS로
                label = self.active or "정상"
                shown = payload if self.active is None else [
                    p for p in payload if p["sensor_serial_no"] in self.scenarios[self.active][1]]
                self.norm_idx += 1
                if self.active:
                    self.scen_idx += 1
                self.sent_ticks += 1
            if not self.quiet:
                tag = " [dry-run]" if self.dry_run else ""
                if self.only_ir:  # TE·IT·AT는 보내지 않고 IR만 VMS로
                    ir_txt = "  ".join(f"{p['sensor_serial_no']}={p['value']}" for p in ir_rows)
                    vals = f"IR만 VMS로 전송{tag}: {ir_txt}"
                elif self.active:
                    vals = "  ".join(f"{p['sensor_serial_no']}={'null' if p['value'] is None else p['value']}"
                                     for p in shown)
                else:
                    vals = f"센서 {len(payload)}개 정상 전송(IR {len(ir_rows)}개는 VMS){tag}"
                mark = "" if ok else " (전송 실패)"
                print(f"\r[{datetime.now():%H:%M:%S}] {label:<8} {vals}{mark}\n> ", end="", flush=True)
            next_t += self.interval
            self.stop_event.wait(max(0.0, next_t - time.monotonic()))

    # ───────── 명령 ─────────
    def set_scenario(self, key):
        with self.lock:
            self.active, self.scen_idx = key, 0
            if key:  # 시나리오 시작 시 정상 데이터도 같은 행부터 시작해, 한 번에 보내는 timestamp가 모두 같도록 맞춤
                self.norm_idx = 0

    def help(self):
        print("명령어:")
        for k in sorted(self.scenarios):
            d = self.scenarios[k][0]
            print(f"  {k:<10} -> {d}")
        print("  q          시나리오를 끝내고 정상으로 복귀 (정상 입력도 같음)")
        print("  상태       지금 재생 중인 시나리오")
        print("  나가기     프로그램 끝내기 (exit, quit)")

    def handle(self, text):
        cmd = normalize_command(text)
        if not cmd:
            return True
        if cmd in ("q", "정상", "끝"):
            self.set_scenario(None)
            print("정상 데이터로 복귀합니다. (정상이 연속 20회(20초) 들어오면 시스템이 복귀로 판정합니다)")
        elif cmd in ("나가기", "exit", "quit"):
            return False
        elif cmd in ("상태", "status"):
            conn = ""
            if self.mqtt:
                conn = "  /  브로커 " + ("연결됨" if self.mqtt.client.is_connected() else "연결 안 됨")
            print(f"현재: {self.active or '정상'}  /  전송 {self.sent_ticks}회  /  주소: {self.target}{conn}"
                  + ("  (dry-run)" if self.dry_run else ""))
        elif cmd in ("도움말", "help", "?"):
            self.help()
        elif cmd in self.scenarios:
            self.set_scenario(cmd)
            print(f"시나리오 시작: {self.scenarios[cmd][0]}  ('q' 입력 시 정상으로 복귀)")
        else:
            print(f"알 수 없는 명령입니다: {text.strip()}  ('도움말' 입력)")
        return True


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="ArgOS 시연용 센서 데이터 전송 봇")
    ap.add_argument("--mode", choices=("mqtt", "http"), default="mqtt", help="전송 방식, 기본 mqtt")
    ap.add_argument("--host", default="192.168.0.14", help="MQTT 브로커 주소, 기본 192.168.0.14")
    ap.add_argument("--port", type=int, default=1883, help="MQTT 브로커 포트, 기본 1883")
    ap.add_argument("--qos", type=int, choices=(0, 1, 2), default=1, help="MQTT QoS, 기본 1")
    ap.add_argument("--topic-base", default="argos/P5",
                    help="토픽 앞부분, 기본 argos/P5 (-> argos/P5/paint/TE/TE-305)")
    ap.add_argument("--username", default=os.environ.get("ARGOS_MQTT_USER"), help="MQTT 아이디 (없으면 인증 없이 연결)")
    ap.add_argument("--password", default=os.environ.get("ARGOS_MQTT_PASSWORD"),
                    help="MQTT 비밀번호 (환경변수 ARGOS_MQTT_PASSWORD로도 지정 가능)")
    ap.add_argument("--url", default="http://localhost:1880/sensors", help="http 모드의 Node-RED http in 주소")
    ap.add_argument("--vms-url", default="http://127.0.0.1:9000/ir",
                    help="IR(열화상) 값을 보낼 VMS 서버 주소, 기본 http://127.0.0.1:9000/ir")
    ap.add_argument("--no-vms", action="store_true", help="IR을 보내지 않음 (TE·IT·AT만 전송)")
    ap.add_argument("--only-ir", action="store_true",
                    help="IR만 VMS로 보냄 (TE·IT·AT와 MQTT 브로커는 쓰지 않음, paho-mqtt 불필요)")
    ap.add_argument("--interval", type=float, default=1.0, help="전송 간격(초), 기본 1")
    ap.add_argument("--dry-run", action="store_true", help="전송하지 않고 보낼 내용을 화면에 출력")
    ap.add_argument("--quiet", action="store_true", help="매 전송마다 출력하지 않음")
    args = ap.parse_args()

    try:
        bot = Bot(args)
    except ImportError:
        print("paho-mqtt가 설치되어 있지 않습니다. 먼저 설치하세요:\n  python -m pip install paho-mqtt\n"
              "(설치 없이 점검만 하려면 --dry-run, HTTP로 보내려면 --mode http)")
        sys.exit(1)
    print("=" * 60)
    print(" ArgOS 시연용 센서 데이터 봇")
    if args.only_ir:
        print(" TE·IT·AT: 보내지 않음 (--only-ir)")
    else:
        print(f" 전송 방식: {args.mode.upper()}  /  주소: {bot.target}"
              + ("  [dry-run: 실제 전송 안 함]" if args.dry_run else ""))
    print(f" IR(열화상): " + ("전송 안 함 (--no-vms)" if args.no_vms else f"HTTP POST -> {args.vms_url}")
          + ("  [dry-run: 실제 전송 안 함]" if args.dry_run and args.only_ir else ""))
    if args.mode == "mqtt" and not args.only_ir:
        print(f" QoS {args.qos}  /  센서 1개당 1메시지  /  인증: {'있음' if args.username else '없음'}")
    ev = ", ".join(sorted(bot.event_sensors))
    print(f" 전송 간격: {args.interval}초 / 센서 {1 if args.only_ir else len(bot.normal) + len(bot.event_sensors)}개"
          + (f" ({ev}는 이벤트가 없으면 value=null)" if ev else ""))
    print("=" * 60)
    bot.help()
    print()
    t = threading.Thread(target=bot.loop, daemon=True)
    t.start()
    try:
        while True:
            try:
                text = input("> ")
            except EOFError:
                break
            if not bot.handle(text):
                break
    except KeyboardInterrupt:
        pass
    bot.stop_event.set()
    t.join(timeout=2)
    if bot.mqtt:
        bot.mqtt.close()
    print("\n봇을 종료합니다.")


if __name__ == "__main__":
    main()
