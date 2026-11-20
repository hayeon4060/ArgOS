#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
메인 서버 대역 (시험용)

VMS 서버가 보내는 POST /vms/ir 을 받아서
  - 터미널에 한 줄씩 보여 주고
  - 브라우저 화면(http://127.0.0.1:8080/)에 "받은 데이터 그대로" 보여 주고
  - 단계 진입 때 온 스냅샷 사진은 received 폴더에 저장합니다.
진짜 메인 서버가 준비되기 전에 VMS 서버를 시험할 때 씁니다. 표준 라이브러리만 사용합니다.

  python mock_main_server.py                 # 포트 8080, 크롬 자동 열림
  python mock_main_server.py --port 8081
  python mock_main_server.py --no-browser    # 브라우저를 열지 않음
"""
import argparse
import json
import os
import re
import threading
import webbrowser
from collections import deque
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(BASE_DIR, "received")

LOCK = threading.Lock()
RAW = deque(maxlen=60)       # 받은 요청 원본 (최신이 오른쪽)
ENTRIES = deque(maxlen=12)   # 단계 진입 기록 (사진 포함)
STATE = {"total": 0, "entries": 0}


def parse_multipart(body, ctype):
    """multipart/form-data 를 [(머리글 글자, 파일이름, 이름, 바이트)] 로 풀어 줍니다."""
    boundary = re.search(r"boundary=(.+)$", ctype).group(1).strip('"').encode()
    parts = []
    for part in body.split(b"--" + boundary):
        part = part.strip(b"\r\n")
        if not part or part == b"--":
            continue
        head, _, data = part.partition(b"\r\n\r\n")
        h = head.decode("utf-8", "replace")
        name = re.search(r'name="([^"]*)"', h)
        fn = re.search(r'filename="([^"]*)"', h)
        parts.append((h, fn.group(1) if fn else None, name.group(1) if name else "", data))
    return parts


DASH_HTML = r"""<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<title>메인 서버 대역 (시험용)</title>
<style>
 body{font-family:'Malgun Gothic',system-ui,sans-serif;margin:0;background:#f4f5f7;color:#1c2330}
 header{background:#1c2330;color:#fff;padding:14px 22px;display:flex;gap:24px;align-items:baseline;flex-wrap:wrap}
 header h1{font-size:18px;margin:0} header span{font-size:13px;color:#b9c2d3}
 main{padding:18px 22px;display:grid;grid-template-columns:1fr 1fr;gap:18px}
 @media(max-width:1000px){main{grid-template-columns:1fr}}
 section{background:#fff;border:1px solid #dde1e8;border-radius:8px;padding:14px 16px}
 h2{font-size:14px;margin:0 0 4px} .sub{font-size:12px;color:#6b7587;margin-bottom:8px}
 pre{margin:0;background:#101521;color:#d6deeb;padding:10px 12px;border-radius:6px;font-size:12px;line-height:1.55;
     height:520px;overflow:auto;white-space:pre-wrap;word-break:break-all;font-family:Consolas,monospace}
 .hd{color:#7fdbca}.ft{color:#ffcb6b}.tm{color:#8896ae}
 .entry{border:1px solid #e3e6ec;border-radius:6px;padding:10px;margin-bottom:10px;display:flex;gap:12px}
 .entry img{width:200px;height:150px;object-fit:cover;border-radius:4px;background:#ddd}
 .entry code{display:block;font-size:12px;white-space:pre-wrap;word-break:break-all}
 .none{color:#8896ae;font-size:13px;padding:30px 0;text-align:center}
 .b{display:inline-block;padding:1px 8px;border-radius:10px;color:#fff;font-size:12px}
 .s2{background:#e08a00}.s3{background:#d6322b}
</style></head><body>
<header><h1>메인 서버 대역 (시험용)</h1>
<span>받은 요청 <b id="tot">0</b>건 · 단계 진입 <b id="ent">0</b>건</span>
<span>주소: POST /vms/ir · 1초마다 자동 갱신</span></header>
<main>
 <section><h2>수신 원본 (VMS → 메인 서버, 받은 그대로 · 최신이 위)</h2>
  <div class="sub">가공 없이 받은 요청의 머리글(Content-Type)과 본문입니다. 평소 값은 JSON, 단계 진입 때는 multipart(meta + snapshot)입니다.</div>
  <pre id="raw">아직 받은 데이터가 없습니다.</pre></section>
 <section><h2>단계 진입 기록 (사진 + ROI)</h2>
  <div class="sub">단계가 바뀔 때 한 번만 오는 데이터입니다. 사진은 received 폴더에도 저장됩니다.</div>
  <div id="ents"><div class="none">아직 단계 진입이 없습니다.</div></div></section>
</main>
<script>
const esc=s=>String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
async function tick(){
 try{
  const j=await (await fetch('/status',{cache:'no-store'})).json();
  document.getElementById('tot').textContent=j.받은_건수;
  document.getElementById('ent').textContent=j.단계진입_건수;
  const raw=document.getElementById('raw');
  if(j.수신원본.length){
   raw.innerHTML=j.수신원본.slice().reverse().map(r=>
    '<span class="tm">['+esc(r.시각)+']</span> <span class="hd">'+esc(r.요청)+'   Content-Type: '+esc(r.형식)+'   ('+r.크기+'바이트)</span>\n'+esc(r.본문)).join('\n\n');
  }
  const e=document.getElementById('ents');
  if(j.단계진입.length){
   e.innerHTML=j.단계진입.slice().reverse().map(x=>
    '<div class="entry">'+(x.사진?'<img src="'+x.사진+'">':'<div style="width:200px;height:150px;background:#eee"></div>')+
    '<div><span class="b s'+x.단계+'">'+x.단계+'단계 진입</span> <b>'+esc(x.serial)+'</b><br>'+esc(x.ts)+' · '+x.value+'℃<br>'+
    '<code>ROI: '+esc(JSON.stringify(x.roi))+'</code>'+(x.사진?'<code>사진: '+esc(x.사진파일)+' ('+x.사진크기+'바이트)</code>':'')+'</div></div>').join('');
  }
 }catch(err){}
}
tick();setInterval(tick,1000);
</script></body></html>"""


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


class H(BaseHTTPRequestHandler):
    def _send(self, code, ctype, body):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path in ("/", "/index.html"):
            self._send(200, "text/html; charset=utf-8", DASH_HTML.encode("utf-8"))
        elif path == "/status":
            with LOCK:
                obj = {"받은_건수": STATE["total"], "단계진입_건수": STATE["entries"],
                       "수신원본": list(RAW), "단계진입": list(ENTRIES)}
            self._send(200, "application/json; charset=utf-8", json.dumps(obj, ensure_ascii=False).encode("utf-8"))
        elif path.startswith("/received/"):
            name = os.path.basename(path)
            fp = os.path.join(OUT_DIR, name)
            if os.path.isfile(fp):
                with open(fp, "rb") as fh:
                    self._send(200, "image/jpeg", fh.read())
            else:
                self._send(404, "text/plain", b"not found")
        else:
            self._send(404, "text/plain", b"not found")

    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(n)
        ctype = self.headers.get("Content-Type", "")
        now = datetime.now().strftime("%H:%M:%S")
        route = self.path.split("?")[0]
        try:
            rec = {"시각": now, "요청": f"POST {self.path}", "형식": ctype, "크기": len(body), "본문": ""}
            if route == "/vms/ir":
                if ctype.startswith("multipart/form-data"):
                    parts = parse_multipart(body, ctype)
                    f = {p[2]: p for p in parts}
                    meta_text = f["meta"][3].decode("utf-8")
                    meta = json.loads(meta_text)
                    lines = []
                    snap_name = None
                    snap_len = 0
                    for head, fn, name, data in parts:
                        lines.append("--- 부분: " + head.replace("\r\n", " | "))
                        if name == "snapshot":
                            snap_name, snap_len = fn, len(data)
                            lines.append(f"(JPEG 사진 {len(data)}바이트 — 내용은 생략)")
                        else:
                            lines.append(data.decode("utf-8", "replace"))
                    rec["본문"] = "\n".join(lines)
                    saved = ""
                    snap_url = None
                    if "snapshot" in f and snap_name:
                        os.makedirs(OUT_DIR, exist_ok=True)
                        with open(os.path.join(OUT_DIR, snap_name), "wb") as fh:
                            fh.write(f["snapshot"][3])
                        saved = f"  사진 저장: received/{snap_name} ({snap_len}바이트)"
                        snap_url = "/received/" + snap_name
                    entry = {"serial": meta.get("serial"), "ts": meta.get("ts"), "value": meta.get("value"),
                             "단계": meta.get("stage"), "roi": meta.get("roi"),
                             "사진": snap_url, "사진파일": snap_name, "사진크기": snap_len}
                    with LOCK:
                        STATE["total"] += 1
                        STATE["entries"] += 1
                        RAW.append(rec)
                        ENTRIES.append(entry)
                    print(f"\n[{now}] ★ 단계 진입 {meta['serial']} {meta['ts']} {meta['value']}℃ -> {meta['stage']}단계{saved}")
                    print(f"          ROI: {json.dumps(meta.get('roi'), ensure_ascii=False)}")
                else:
                    text = body.decode("utf-8")
                    meta = json.loads(text)
                    rec["본문"] = text
                    with LOCK:
                        STATE["total"] += 1
                        RAW.append(rec)
                    print(f"\r[{now}] {meta['serial']} {meta['ts']} {meta['value']}℃ {meta['stage']}단계  (받은 건수 {STATE['total']})   ",
                          end="", flush=True)
            else:
                # 그 밖의 주소(예: 센서 봇의 /sensors)는 화면에만 기록하고 200만 돌려줌
                rec["본문"] = body.decode("utf-8", "replace")[:2000]
                with LOCK:
                    STATE["total"] += 1
                    RAW.append(rec)
        except Exception as e:  # 시험용이라 어떤 오류든 화면에 띄우고 400 응답
            print(f"\n[오류] {e}")
            self._send(400, "application/json", b'{"ok":false}')
            return
        self._send(200, "application/json", b'{"ok":true}')

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="메인 서버 대역 (시험용)")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--no-browser", action="store_true", help="브라우저를 자동으로 열지 않음")
    a = ap.parse_args()
    url = f"http://127.0.0.1:{a.port}/"
    print(f"메인 서버 대역: 받는 주소 POST http://127.0.0.1:{a.port}/vms/ir")
    print(f"                확인 화면  {url}   (Ctrl+C로 종료)")
    if not a.no_browser:
        threading.Timer(1.0, open_dashboard, args=(url,)).start()
    try:
        ThreadingHTTPServer(("0.0.0.0", a.port), H).serve_forever()
    except KeyboardInterrupt:
        print("\n종료합니다.")
