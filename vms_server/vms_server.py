#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ArgOS VMS 서버 (열화상 IR-301)

데모 봇(카메라 역할)이 보내는 IR 측정값을 받아서
  1) IR 단계(1/2/3)를 직접 판정하고
  2) 값 + 단계를 REST로 메인 서버에 전달합니다.
  3) 2단계/3단계에 처음 진입한 순간에는 ROI + 스냅샷을 함께(multipart) 보냅니다.
  4) 3단계일 때만 대시보드가 볼 수 있는 영상 주소(/stream/IR-301)를 엽니다.

받는 쪽 (봇 -> VMS)
  POST /ir   {"ts","type":"IR","serial":"IR-301","value":61.2,"unit":"℃","quality":"GOOD"}

보내는 쪽 (VMS -> 메인 서버, --main-url)
  평소 (매 초)  application/json  {"ts","type","serial","value","unit","quality","stage"}
  단계 진입 때  multipart/form-data
                  meta     = 위 JSON + "roi"
                  snapshot = 사진 파일 (진입 시각과 같은 이름의 사진)

판정 규칙 (IR-301 ROI 최고온도, 1초 간격)
  3단계  > 60 ℃  가 15회 연속
  2단계  > 50 ℃  가 15회 연속
  복귀   <= 50 ℃ 가 20회 연속 (복귀하면 1단계)
  한 번 올라간 단계는 복귀 조건을 만족할 때까지 유지합니다.
  (기준값과 횟수는 아래 상수에서 바꿉니다. 온도 상승 속도 조건(3 ℃/분)은 아직 넣지 않았습니다.)

표준 라이브러리만 사용합니다 (설치할 것 없음).
"""
import argparse
import json
import mimetypes
import os
import queue
import re
import threading
import urllib.error
import urllib.request
import webbrowser
from collections import deque
import uuid
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# ───────── 판정 기준 (팀 규칙) ─────────
T_STAGE2 = 50.0      # 이 값을 넘으면 2단계 범위
T_STAGE3 = 60.0      # 이 값을 넘으면 3단계 범위
N_STAGE2 = 15        # 2단계 연속 횟수
N_STAGE3 = 15        # 3단계 연속 횟수
N_RECOVER = 20       # 복귀(정상) 연속 횟수


def level_of(value):
    if value > T_STAGE3:
        return 3
    if value > T_STAGE2:
        return 2
    return 1


class Judge:
    """센서 1개의 단계 판정기. 값이 들어올 때마다 update()를 부릅니다."""

    def __init__(self):
        self.stage = 1
        self.c2 = self.c3 = self.cn = 0   # 2단계 이상 / 3단계 / 정상 연속 횟수

    def update(self, value):
        """(새 단계, 이번에 단계에 처음 진입했는지) 를 돌려줍니다."""
        lv = level_of(value)
        self.c2 = self.c2 + 1 if lv >= 2 else 0
        self.c3 = self.c3 + 1 if lv == 3 else 0
        self.cn = self.cn + 1 if lv == 1 else 0
        old, new = self.stage, self.stage
        if old < 3 and self.c3 >= N_STAGE3:
            new = 3
        elif old < 2 and self.c2 >= N_STAGE2:
            new = 2
        if old > 1 and self.cn >= N_RECOVER:
            new = 1
        self.stage = new
        return new, new > old


# ───────── 메인 서버로 보내기 ─────────
def build_multipart(meta, snap_path):
    boundary = "----argos" + uuid.uuid4().hex
    parts = []
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="meta"\r\n'
                 f'Content-Type: application/json; charset=utf-8\r\n\r\n'.encode("utf-8")
                 + json.dumps(meta, ensure_ascii=False).encode("utf-8") + b"\r\n")
    if snap_path:
        fn = os.path.basename(snap_path)
        ctype = mimetypes.guess_type(fn)[0] or "application/octet-stream"
        with open(snap_path, "rb") as f:
            data = f.read()
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="snapshot"; filename="{fn}"\r\n'
                     f'Content-Type: {ctype}\r\n\r\n'.encode("utf-8") + data + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode("utf-8"))
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


class Forwarder(threading.Thread):
    """큐에 쌓인 메시지를 메인 서버로 보냅니다. 못 보내면 버립니다 (쌓아 두지 않음)."""

    def __init__(self, main_url):
        super().__init__(daemon=True)
        self.main_url = main_url
        self.q = queue.Queue(maxsize=200)
        self.last_error = None
        self.sent = 0
        self.dropped = 0

    def put(self, meta, snap_path=None):
        try:
            self.q.put_nowait((meta, snap_path))
        except queue.Full:
            self.dropped += 1

    def run(self):
        while True:
            meta, snap = self.q.get()
            try:
                if snap or "roi" in meta:
                    body, ctype = build_multipart(meta, snap)
                else:
                    body, ctype = json.dumps(meta, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8"
                req = urllib.request.Request(self.main_url, data=body, headers={"Content-Type": ctype}, method="POST")
                with urllib.request.urlopen(req, timeout=3) as resp:
                    resp.read()
                if self.last_error:
                    log("메인 서버 연결이 복구되었습니다.")
                self.last_error = None
                self.sent += 1
            except (urllib.error.URLError, OSError) as e:
                self.dropped += 1
                msg = f"메인 서버 전송 실패: {e}"
                if msg != self.last_error:
                    log(f"[경고] {msg} (주소: {self.main_url}) - 못 보낸 데이터는 버립니다")
                self.last_error = msg


def log(text):
    print(f"[{datetime.now():%H:%M:%S}] {text}", flush=True)


# ───────── 사진 고르기 ─────────
SNAP_RE = re.compile(r"_(\d{8})_(\d{6})\.(jpg|jpeg|png)$", re.I)


def pick_snapshot(snap_dir, serial, ts):
    """진입 시각(ts)과 같은 시각의 사진을 고르고, 없으면 가장 가까운 시각의 사진을 고릅니다."""
    if not snap_dir or not os.path.isdir(snap_dir):
        return None
    try:
        t0 = datetime.strptime(ts, "%Y-%m-%d %H:%M:%S")
    except ValueError:
        return None
    best, best_d = None, None
    for fn in os.listdir(snap_dir):
        if not fn.startswith(serial):
            continue
        m = SNAP_RE.search(fn)
        if not m:
            continue
        t = datetime.strptime(m.group(1) + m.group(2), "%Y%m%d%H%M%S")
        d = abs((t - t0).total_seconds())
        if best_d is None or d < best_d:
            best, best_d = fn, d
    return os.path.join(snap_dir, best) if best else None


# ───────── 서버 ─────────
class Vms:
    def __init__(self, args):
        self.args = args
        self.judges = {}
        self.lock = threading.Lock()
        self.fwd = None if args.no_forward else Forwarder(args.main_url)
        if self.fwd:
            self.fwd.start()
        self.roi = None
        if args.roi and os.path.isfile(args.roi):
            with open(args.roi, encoding="utf-8-sig") as f:
                r = json.load(f)
            self.roi = {k: r[k] for k in ("roi_id", "frame", "bbox") if k in r}
        self.received = 0
        self.history = {}   # 시리얼 -> 최근 값들 (대시보드 그래프용)
        self.last_ts = {}
        self.raw = deque(maxlen=40)   # 봇(카메라 역할)이 보낸 요청 원본 (화면 표시용)

    def handle_ir(self, msg):
        serial, ts = msg["serial"], msg["ts"]
        value = float(msg["value"])
        with self.lock:
            j = self.judges.setdefault(serial, Judge())
            before = j.stage
            stage, entered = j.update(value)
            self.received += 1
            self.history.setdefault(serial, deque(maxlen=120)).append(value)
            self.last_ts[serial] = ts
            counts = f"2단계↑{j.c2} 3단계{j.c3} 정상{j.cn}"
        out = {"ts": ts, "type": "IR", "serial": serial, "value": value,
               "unit": msg.get("unit", "℃"), "quality": msg.get("quality", "GOOD"), "stage": stage}
        snap = None
        if entered:
            out["roi"] = self.roi
            snap = pick_snapshot(self.args.snapshot_dir, serial, ts)
            log(f"★ {serial} {before}단계 -> {stage}단계 진입  {value}℃  "
                f"스냅샷={os.path.basename(snap) if snap else '없음'}")
        elif stage != before:
            log(f"★ {serial} {before}단계 -> {stage}단계 복귀  {value}℃")
        elif not self.args.quiet:
            print(f"\r[{datetime.now():%H:%M:%S}] {serial} {ts} {value}℃  {stage}단계  ({counts})   ", end="", flush=True)
        if self.fwd:
            self.fwd.put(out, snap)
        return stage

    def record_raw(self, method, path, ctype, body_text):
        with self.lock:
            self.raw.append({"받은시각": datetime.now().strftime("%H:%M:%S"), "요청": f"{method} {path}",
                           "Content-Type": ctype, "본문": body_text})

    def stage_of(self, serial):
        with self.lock:
            j = self.judges.get(serial)
            return j.stage if j else 1


DASH_HTML = r'''<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><title>ArgOS VMS - IR-301</title>
<style>
body{font-family:'Malgun Gothic','Apple SD Gothic Neo',sans-serif;margin:0;background:#f3f6f4;color:#1b2420}
header{background:#1f3b34;color:#fff;padding:14px 24px;font-size:18px}
main{max-width:900px;margin:20px auto;padding:0 16px}
.card{background:#fff;border-radius:10px;padding:18px 22px;margin-bottom:16px;box-shadow:0 1px 3px #0002}
.row{display:flex;gap:28px;align-items:center;flex-wrap:wrap}
.val{font-size:60px;font-weight:700;line-height:1}
.badge{display:inline-block;padding:6px 18px;border-radius:20px;color:#fff;font-weight:700;font-size:20px}
.s1{background:#2e8b57}.s2{background:#e6a100}.s3{background:#d32f2f}
.small{color:#5c6a64;font-size:14px}
canvas{width:100%;height:240px;display:block}
video{width:100%;max-width:640px;border-radius:8px;background:#000}
h3{margin:0 0 10px;font-size:16px}
pre{background:#10201b;color:#d7f5e6;padding:12px 14px;border-radius:8px;font-size:13px;line-height:1.5;max-height:300px;overflow:auto;white-space:pre-wrap;word-break:break-all;margin:6px 0 0}
</style></head><body>
<header>ArgOS VMS 서버 &mdash; 열화상 IR-301 (1초마다 자동 갱신)</header>
<main>
<div class="card" id="top"><div class="small">아직 받은 값이 없습니다. 봇에서 IR을 보내면 여기에 표시됩니다.</div></div>
<div class="card"><h3>최근 값 (기준선: 50 ℃ 2단계, 60 ℃ 3단계)</h3><canvas id="chart"></canvas></div>
<div class="card"><h3>수신 원본 (봇/카메라 &rarr; VMS, 받은 그대로 · 최신이 위)</h3><div class="small" id="rawhead"></div><pre id="raw">아직 받은 데이터가 없습니다.</pre></div>
<div class="card" id="vcard" style="display:none"><h3>3단계 실시간 영상</h3><div id="vbox"></div></div>
<div class="small" id="foot"></div>
</main>
<script>
var curSerial = null;
function txt(id, t){ document.getElementById(id).textContent = t; }
function drawChart(vals){
  var c = document.getElementById('chart'), dpr = window.devicePixelRatio || 1;
  var w = c.clientWidth, h = c.clientHeight;
  c.width = w * dpr; c.height = h * dpr;
  var g = c.getContext('2d'); g.scale(dpr, dpr); g.clearRect(0, 0, w, h);
  if (!vals.length) return;
  var lo = Math.min(40, Math.min.apply(null, vals) - 2), hi = Math.max(70, Math.max.apply(null, vals) + 2);
  var L = 44, R = 12, T = 10, B = 20;
  function Y(v){ return T + (hi - v) / (hi - lo) * (h - T - B); }
  g.font = '12px sans-serif'; g.textBaseline = 'middle';
  [[50, '#e6a100'], [60, '#d32f2f']].forEach(function(l){
    g.strokeStyle = l[1]; g.setLineDash([6, 4]); g.beginPath(); g.moveTo(L, Y(l[0])); g.lineTo(w - R, Y(l[0])); g.stroke();
    g.setLineDash([]); g.fillStyle = l[1]; g.fillText(l[0] + '℃', 4, Y(l[0]));
  });
  g.strokeStyle = '#1f3b34'; g.lineWidth = 2; g.beginPath();
  vals.forEach(function(v, i){
    var x = L + (vals.length === 1 ? 0 : i / (vals.length - 1) * (w - L - R));
    if (i === 0) g.moveTo(x, Y(v)); else g.lineTo(x, Y(v));
  });
  g.stroke(); g.lineWidth = 1;
  g.fillStyle = '#5c6a64'; g.fillText('오래된 값', L, h - 8); g.fillText('최신', w - R - 28, h - 8);
}
function render(d){
  var keys = Object.keys(d['센서']);
  if (!keys.length) { txt('foot', '받은 건수 0'); return; }
  var serial = keys[0], s = d['센서'][serial];
  var top = document.getElementById('top');
  top.innerHTML = '';
  var row = document.createElement('div'); row.className = 'row';
  var a = document.createElement('div'); a.innerHTML = '<div class="small"></div><div class="val"></div>';
  a.children[0].textContent = serial + '  ' + (s['마지막시각'] || '');
  a.children[1].textContent = (s['마지막값'] === null ? '-' : s['마지막값'].toFixed(1)) + ' ℃';
  var b = document.createElement('div'); b.innerHTML = '<span class="badge"></span><div class="small" style="margin-top:8px"></div>';
  b.children[0].className = 'badge s' + s.stage; b.children[0].textContent = s.stage + '단계';
  b.children[1].textContent = '연속: 2단계 이상 ' + s['연속_2단계이상'] + '회 / 3단계 ' + s['연속_3단계'] + '회 / 정상 ' + s['연속_정상'] + '회';
  row.appendChild(a); row.appendChild(b); top.appendChild(row);
  drawChart(s['최근값']);
  var box = document.getElementById('vbox'), vcard = document.getElementById('vcard');
  if (s.stage === 3) {
    if (!box.firstChild || curSerial !== serial) {
      box.innerHTML = '';
      var v = document.createElement('video'); v.src = '/stream/' + serial;
      v.autoplay = true; v.muted = true; v.loop = true; v.controls = true; v.setAttribute('playsinline', '');
      box.appendChild(v);
    }
    vcard.style.display = 'block';
  } else { box.innerHTML = ''; vcard.style.display = 'none'; }
  curSerial = serial;
  var rawList = d['수신원본'] || [];
  if (rawList.length) {
    var last = rawList[rawList.length - 1];
    txt('rawhead', last['요청'] + '   Content-Type: ' + last['Content-Type']);
    txt('raw', rawList.slice().reverse().map(function(r){ return '[' + r['받은시각'] + '] ' + r['본문']; }).join('\n'));
  }
  var m = d['메인서버'];
  txt('foot', '받은 건수 ' + d['받은_건수'] + (m ? '  /  메인 서버로 보냄 ' + m['보냄'] + ', 버림 ' + m['버림'] : '  /  메인 서버로 전송 안 함'));
}
function tick(){
  fetch('/status', {cache: 'no-store'}).then(function(r){ return r.json(); }).then(render)
    .catch(function(){ txt('foot', 'VMS 서버에 연결할 수 없습니다 (서버가 꺼졌는지 확인하세요)'); });
}
tick(); setInterval(tick, 1000);
</script></body></html>
'''


def open_dashboard(url):
    """대시보드를 크롬으로 엽니다. 크롬을 못 찾으면 기본 브라우저로 엽니다."""
    candidates = [
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        os.path.expandvars(r"%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"),
    ]
    for path in candidates:
        if os.path.isfile(path):
            try:
                webbrowser.register("argos-chrome", None, webbrowser.BackgroundBrowser(path))
                if webbrowser.get("argos-chrome").open(url):
                    return
            except Exception:
                pass
    try:
        webbrowser.open(url)
    except Exception:
        pass


def make_handler(vms):
    class H(BaseHTTPRequestHandler):
        def _json(self, code, obj):
            body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            if self.path.split("?")[0] != "/ir":
                return self._json(404, {"ok": False, "error": "POST /ir 만 받습니다"})
            try:
                n = int(self.headers.get("Content-Length", 0))
                raw = self.rfile.read(n).decode("utf-8")
                vms.record_raw("POST", self.path, self.headers.get("Content-Type", ""), raw)  # 받은 그대로 기록
                msg = json.loads(raw)
                stage = vms.handle_ir(msg)
            except (ValueError, KeyError) as e:
                return self._json(400, {"ok": False, "error": f"형식 오류: {e}"})
            self._json(200, {"ok": True, "stage": stage})

        def do_GET(self):
            path = self.path.split("?")[0]
            if path == "/":
                body = DASH_HTML.encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            if path == "/status":
                with vms.lock:
                    st = {s: {"stage": j.stage, "마지막값": (vms.history[s][-1] if vms.history.get(s) else None),
                              "마지막시각": vms.last_ts.get(s), "연속_2단계이상": j.c2, "연속_3단계": j.c3, "연속_정상": j.cn,
                              "최근값": list(vms.history.get(s, []))}
                          for s, j in vms.judges.items()}
                f = vms.fwd
                with vms.lock:
                    raw = list(vms.raw)
                return self._json(200, {"받은_건수": vms.received, "센서": st, "수신원본": raw,
                                        "메인서버": None if not f else {"주소": f.main_url, "보냄": f.sent, "버림": f.dropped}})
            m = re.fullmatch(r"/stream/([\w\-]+)", path)
            if m:
                return self.stream(m.group(1))
            self._json(404, {"ok": False, "error": "없는 주소"})

        def stream(self, serial):
            """3단계일 때만 영상을 내줍니다 (평소에는 404)."""
            if vms.stage_of(serial) < 3:
                return self._json(404, {"ok": False, "error": f"{serial}은(는) 3단계가 아니라서 송출하지 않습니다"})
            video = vms.args.video
            if not video or not os.path.isfile(video):
                return self._json(404, {"ok": False, "error": "영상 파일이 없습니다 (--video)"})
            size = os.path.getsize(video)
            start, end = 0, size - 1
            rng = self.headers.get("Range")
            code = 200
            if rng:
                m = re.fullmatch(r"bytes=(\d*)-(\d*)", rng.strip())
                if m and (m.group(1) or m.group(2)):
                    if m.group(1):
                        start = int(m.group(1))
                        end = int(m.group(2)) if m.group(2) else size - 1
                    else:
                        start = max(0, size - int(m.group(2)))
                    end = min(end, size - 1)
                    code = 206
            self.send_response(code)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(end - start + 1))
            if code == 206:
                self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            try:
                with open(video, "rb") as f:
                    f.seek(start)
                    left = end - start + 1
                    while left > 0:
                        chunk = f.read(min(65536, left))
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        left -= len(chunk)
            except (BrokenPipeError, ConnectionResetError):
                pass

        def do_OPTIONS(self):
            self.send_response(204)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Headers", "Content-Type, Range")
            self.end_headers()

        def log_message(self, *a):  # 기본 접속 로그는 끔
            pass

    return H


def main():
    ap = argparse.ArgumentParser(description="ArgOS VMS 서버 (열화상 IR 판정 + 메인 서버 전송)")
    ap.add_argument("--host", default="0.0.0.0", help="받을 주소, 기본 0.0.0.0 (같은 컴퓨터만이면 127.0.0.1)")
    ap.add_argument("--port", type=int, default=9000, help="받을 포트, 기본 9000")
    ap.add_argument("--main-url", default="http://127.0.0.1:8080/vms/ir", help="메인 서버 IR 수신 주소")
    ap.add_argument("--no-forward", action="store_true", help="메인 서버로 보내지 않음 (판정만 화면에 표시)")
    ap.add_argument("--roi", default=os.path.join(BASE_DIR, "assets", "상도건조로_IR_roi.json"), help="ROI JSON")
    ap.add_argument("--snapshot-dir", default=os.path.join(BASE_DIR, "assets", "snapshot"), help="스냅샷 사진 폴더")
    ap.add_argument("--video", default=os.path.join(BASE_DIR, "assets", "상도건조로_IR_3단계_영상.mp4"),
                    help="3단계 때 송출할 영상 파일")
    ap.add_argument("--quiet", action="store_true", help="매 초 한 줄 표시를 끔 (단계 변화만 표시)")
    ap.add_argument("--no-browser", action="store_true", help="시작할 때 브라우저를 자동으로 열지 않음")
    args = ap.parse_args()

    vms = Vms(args)
    srv = ThreadingHTTPServer((args.host, args.port), make_handler(vms))
    print("=" * 60)
    print(" ArgOS VMS 서버")
    shown = "127.0.0.1" if args.host in ("0.0.0.0", "") else args.host  # 0.0.0.0은 브라우저로 열 수 없는 주소
    print(f" 받는 주소: http://{shown}:{args.port}/ir  (봇 --vms-url)")
    print(f" 메인 서버: {'전송 안 함 (--no-forward)' if args.no_forward else args.main_url}")
    print(f" ROI: {'있음' if vms.roi else '없음'}  /  사진 폴더: {'있음' if os.path.isdir(args.snapshot_dir) else '없음'}"
          f"  /  영상: {'있음' if os.path.isfile(args.video) else '없음'}")
    print(f" 판정: 2단계 >{T_STAGE2:g}℃ {N_STAGE2}회, 3단계 >{T_STAGE3:g}℃ {N_STAGE3}회, 복귀 {N_RECOVER}회 연속")
    print(f" 영상(3단계일 때만): http://{shown}:{args.port}/stream/IR-301")
    print(f" 값 보기(자동 갱신): http://{shown}:{args.port}/   (JSON 상태: /status)")
    print("=" * 60)
    if not args.no_browser:  # 서버가 뜬 직후 대시보드를 크롬으로 엽니다
        url = f"http://{shown}:{args.port}/"
        threading.Timer(1.0, open_dashboard, args=(url,)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n종료합니다.")
        srv.server_close()


if __name__ == "__main__":
    main()
