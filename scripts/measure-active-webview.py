#!/usr/bin/env python3
"""Collect repeatable emulator snapshots with pages served from the CI host."""
import http.server
import json
from pathlib import Path
import re
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

report = Path(sys.argv[1])
package = sys.argv[2]
pid = sys.argv[3]
origin = 'http://10.0.2.2:18764'
requests = []


class Fixture(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        requests.append(self.path)
        if self.path == '/clip.mp4':
            body = Path('scripts/fixtures/memory-video.mp4').read_bytes()
            mime = 'video/mp4'
        elif self.path in ('/a', '/b'):
            label = 'A' if self.path == '/a' else 'B'
            body = (f'<!doctype html><meta name="viewport" content="width=device-width">'
                    f'<title>Memory fixture {label}</title><h1>Memory fixture {label}</h1>'
                    '<script>for(let i=0;i<400;i++){const n=document.createElement("p");'
                    'n.textContent="Local test content "+i;document.body.append(n)}</script>').encode()
            mime = 'text/html; charset=utf-8'
        elif self.path == '/video':
            body = (b'<!doctype html><meta name="viewport" content="width=device-width">'
                    b'<title>Memory fixture video</title><h1>Memory fixture video</h1>'
                    b'<button onclick="document.querySelector(\'video\').play()">Play sample</button>'
                    b'<video controls loop width="640" src="/clip.mp4"></video>'
                    b'<script>document.querySelector("video").onplaying=()=>'
                    b'{document.querySelector("button").textContent="Playing sample"}</script>')
            mime = 'text/html; charset=utf-8'
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass


def adb(*args):
    return subprocess.run(['adb', 'shell', *args], check=True, capture_output=True,
                          text=True, timeout=30).stdout.strip()


def snapshot(label):
    remote = '/sdcard/memory-fixture.xml'
    for attempt in range(3):
        assert pid in adb('pidof', package).split(), 'App restarted during memory scenario'
        adb('rm', '-f', remote)
        subprocess.run(['adb', 'shell', 'uiautomator', 'dump', remote],
                       capture_output=True, text=True, timeout=30)
        output = subprocess.run(['adb', 'shell', 'cat', remote], capture_output=True,
                                text=True, timeout=30)
        if output.returncode:
            continue
        try:
            root = ET.fromstring(output.stdout)
        except ET.ParseError:
            continue
        (report / f'memory-{label}.xml').write_text(output.stdout)
        return root
    raise AssertionError(f'No fresh UI hierarchy for {label}')


def wait_text(label, value):
    for attempt in range(5):
        root = snapshot(f'{label}-{attempt}')
        found = next((node for node in root.iter('node') if node.get('text') == value), None)
        if found is not None:
            return found
        time.sleep(1)
    raise AssertionError(f'{value} was not visible in {label}')


def wait_selected_tab(label, title):
    for attempt in range(5):
        root = snapshot(f'{label}-{attempt}')
        parent = {child: node for node in root.iter('node') for child in node}
        for node in root.iter('node'):
            if node.get('text') != title:
                continue
            current = node
            while current in parent:
                current = parent[current]
                if current.get('selected') == 'true' and current.get('clickable') == 'true':
                    return
        time.sleep(1)
    raise AssertionError(f'{title} was not the selected tab')


def tap(node):
    bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    assert bounds, 'Target has no bounds'
    x1, y1, x2, y2 = map(int, bounds.groups())
    adb('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def navigate(path, title):
    adb('am', 'start', '-n', f'{package}/.activity.main.MainActivity',
        '-a', 'android.intent.action.VIEW', '-d', origin + path)
    wait_text(path.strip('/'), title)
    wait_selected_tab(path.strip('/'), title)
    assert path in requests, f'Local fixture {path} was not requested'


def sample(label):
    for index in range(3):
        subprocess.run([sys.executable, 'scripts/capture-webview-memory.py', str(report),
                        package, pid, f'normal-{label}-{index}'], check=True, timeout=90)
        time.sleep(1)


server = http.server.ThreadingHTTPServer(('0.0.0.0', 18764), Fixture)
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
try:
    navigate('/a', 'Memory fixture A')
    sample('page-a')
    navigate('/b', 'Memory fixture B')
    sample('page-b')
    tap(wait_text('tab-a', 'Memory fixture A'))
    wait_selected_tab('return-a', 'Memory fixture A')
    sample('tab-a-return')
    navigate('/video', 'Memory fixture video')
    tap(wait_text('video-play', 'Play sample'))
    wait_text('video-playing', 'Playing sample')
    assert '/clip.mp4' in requests, 'The video file was not requested'
    sample('video-playing')
    adb('am', 'start', '-n', f'{package}/.activity.main.MainActivity',
        '-a', 'android.intent.action.VIEW', '-d', 'https://vireolumatv.invalid/appcontent/home/')
    wait_text('home-return', 'DuckDuckGo')
    sample('home-return')
finally:
    server.shutdown()
    (report / 'memory-fixture-requests.json').write_text(json.dumps(requests, indent=2) + '\n')
