#!/usr/bin/env python3
"""Diagnostic: YouTube fullscreen, then remote Back, repeated; records what the page and the app do.

Usage: diagnose-fullscreen-back.py APK REPORT_DIR [CYCLES]
Needs a rooted emulator (google_apis image) and websocket-client.
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

import websocket

APK, REPORT = sys.argv[1], sys.argv[2]
CYCLES = int(sys.argv[3]) if len(sys.argv) > 3 else 8
PKG = "com.reiniertutoriales.vireolumatv"
WATCH = "https://m.youtube.com/watch?v=aqz-KE-bpKQ"
os.makedirs(REPORT, exist_ok=True)
log_lines = []


def note(*parts):
    line = " ".join(str(p) for p in parts)
    print(line, flush=True)
    log_lines.append(line)


def adb(*args, timeout=30, check=False):
    r = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode:
        raise SystemExit(f"adb {' '.join(args)} failed: {r.stderr}")
    return r.stdout


def pid():
    return adb("shell", "pidof", PKG).strip()


def enable_page_debugging():
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.activity.main.MainActivity", "-d", "https://example.invalid/")
    time.sleep(6)
    adb("shell", "am", "force-stop", PKG)
    path = f"/data/data/{PKG}/shared_prefs/main.xml"
    xml = adb("shell", "cat", path)
    if "web_engine_debug" in xml:
        xml = re.sub(r'<boolean name="web_engine_debug" value="\w+" />', '<boolean name="web_engine_debug" value="true" />', xml)
    else:
        xml = xml.replace("</map>", '    <boolean name="web_engine_debug" value="true" />\n</map>')
    local = os.path.join(REPORT, "main.xml")
    with open(local, "w") as f:
        f.write(xml)
    uid = adb("shell", "stat", "-c", "%u", path).strip()
    adb("push", local, path, check=True)
    adb("shell", "chown", f"{uid}:{uid}", path)


class Page:
    def __init__(self):
        self.ws = None
        self.next_id = 0

    def connect(self):
        app = pid()
        adb("forward", "--remove-all")
        adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{app}", check=True)
        for _ in range(30):
            try:
                targets = json.load(urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5))
                pages = [t for t in targets if t.get("type") == "page" and "youtube" in t.get("url", "")]
                if pages:
                    self.ws = websocket.create_connection(pages[0]["webSocketDebuggerUrl"], timeout=45, suppress_origin=True)
                    return pages[0]["url"]
            except Exception as error:  # noqa: BLE001 - diagnostic retries
                note("devtools not ready:", error)
            time.sleep(2)
        raise SystemExit("no YouTube page target")

    def eval(self, expression, gesture=False):
        self.next_id += 1
        self.ws.send(json.dumps({"id": self.next_id, "method": "Runtime.evaluate", "params": {
            "expression": expression, "userGesture": gesture, "awaitPromise": True, "returnByValue": True}}))
        while True:
            message = json.loads(self.ws.recv())
            if message.get("id") == self.next_id:
                result = message.get("result", {})
                if "exceptionDetails" in result:
                    return {"error": result["exceptionDetails"].get("text")}
                return result.get("result", {}).get("value")


PROBE = """(function(){
  if (!window.__fs) {
    window.__fs = [];
    var t0 = performance.now();
    var push = function(kind, extra){ window.__fs.push([Math.round(performance.now()-t0), kind, location.href.slice(0,80), history.length, !!document.fullscreenElement, extra||'']); };
    document.addEventListener('fullscreenchange', function(){ push('fullscreenchange', document.fullscreenElement ? (document.fullscreenElement.id || document.fullscreenElement.tagName) : ''); }, true);
    window.addEventListener('popstate', function(){ push('popstate'); }, true);
    var ps = history.pushState, rs = history.replaceState, bk = history.back;
    history.pushState = function(){ push('pushState', String(arguments[2]||'').slice(0,60)); return ps.apply(this, arguments); };
    history.replaceState = function(){ push('replaceState', String(arguments[2]||'').slice(0,60)); return rs.apply(this, arguments); };
    history.back = function(){ push('history.back'); return bk.apply(this, arguments); };
    var rf = Element.prototype.requestFullscreen;
    Element.prototype.requestFullscreen = function(){ push('requestFullscreen', this.id || this.tagName); return rf.apply(this, arguments); };
    var ef = Document.prototype.exitFullscreen;
    Document.prototype.exitFullscreen = function(){ push('exitFullscreen'); return ef.apply(this, arguments); };
  }
  return true;
})()"""

ENTER = """(async function(){
  var limit = function(p, ms){ return Promise.race([Promise.resolve(p).catch(function(e){ return 'rejected: ' + e; }), new Promise(function(r){ setTimeout(function(){ r('pending'); }, ms); })]); };
  var v = document.querySelector('video');
  var played = v ? await limit((v.muted = true, v.play()), 3000) : 'no video';
  var button = document.querySelector('.fullscreen-icon, button.ytp-fullscreen-button, [aria-label*="ull screen"]');
  if (button) { button.click(); await limit(null, 1500); await new Promise(function(r){ setTimeout(r, 1500); }); }
  var requested = '';
  if (!document.fullscreenElement) {
    var target = document.querySelector('#player-container-id') || document.querySelector('#movie_player') || v;
    if (target) requested = String(await limit(target.requestFullscreen(), 3000));
  }
  return {button: !!button, requested: requested, played: String(played), fullscreen: !!document.fullscreenElement, playing: !!(v && !v.paused)};
})()"""


def app_state():
    """Visibility and focus of the capsule from the live view hierarchy (no idle wait, unlike uiautomator)."""
    text = adb("shell", "dumpsys", "activity", "top", timeout=20)
    state = {}
    for name in ("controls_overlay", "back_button", "home_button", "page_container"):
        m = re.search(r"\{[0-9a-f]+ ([VIG])\S* (\S+) [^}]*app:id/" + name + r"\b", text)
        if m:
            state[name] = m.group(1) + ("F" if m.group(2)[1:2] == "F" else "")
    state["focused"] = [m.group(2) or m.group(1) for m in
                        re.finditer(r"(\w+)\{[0-9a-f]+ \S{9} .F\S* [^}]*?(?:#\w+ (\S+))?\}", text)][:3]
    return state


SCENARIOS = ["double-back", "quick", "double-back", "after-ok", "long-back", "double-back", "after-move", "after-play"]


def scenario_before(name):
    """What the viewer did in fullscreen before Back."""
    if name == "quick":
        return
    if name == "after-ok":  # OK with the pointer shows the player's controls.
        adb("shell", "input", "keyevent", "KEYCODE_DPAD_CENTER")
        time.sleep(1.5)
    elif name == "after-play":
        time.sleep(20)
    elif name == "after-move":  # Pointer moved over the video, then left still.
        for key in ("KEYCODE_DPAD_UP", "KEYCODE_DPAD_UP", "KEYCODE_DPAD_RIGHT"):
            adb("shell", "input", "keyevent", key)
        time.sleep(1)


def main():
    adb("install", "-r", "--no-incremental", APK, timeout=180, check=True)
    enable_page_debugging()
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.activity.main.MainActivity", "-d", WATCH, timeout=60)
    time.sleep(25)
    page = Page()
    note("page:", page.connect())
    results = []
    for cycle in range(1, CYCLES + 1):
        page.eval(PROBE)
        page.eval("window.__fs.length = 0")
        entered = page.eval(ENTER, gesture=True)
        time.sleep(0.4 if SCENARIOS[(cycle - 1) % len(SCENARIOS)] == 'quick' else 3)
        before = {"page_fullscreen": page.eval("!!document.fullscreenElement"), "app": app_state()}
        scenario = SCENARIOS[(cycle - 1) % len(SCENARIOS)]
        note(f"cycle {cycle} {scenario}: app pid {pid() or 'none'}")
        scenario_before(scenario)
        before["scenario"] = scenario
        before["app_after_setup"] = app_state()
        if scenario == "long-back":
            adb("shell", "input", "keyevent", "--longpress", "KEYCODE_BACK")
        else:
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
        if scenario == "double-back":
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
        samples = []
        for wait in (0.5, 1.0, 2.0, 4.0):
            time.sleep(wait - (samples[-1]["t"] if samples else 0))
            samples.append({"t": wait, "page_fullscreen": page.eval("!!document.fullscreenElement"),
                            "url": page.eval("location.href"), "app": app_state()})
        events = page.eval("window.__fs")
        final = samples[-1]
        ok = final["app"].get("controls_overlay", "").startswith("V") and not final["page_fullscreen"]
        result = {"cycle": cycle, "entered": entered, "before": before, "after": samples, "events": events, "ok": ok}
        results.append(result)
        note(f"cycle {cycle}: ok={ok} entered={entered} before={before} final={final} events={events}")
        with open(os.path.join(REPORT, f"cycle-{cycle}.png"), "wb") as f:
            f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=20).stdout)
        # Close the capsule (if it opened) to return to the page for the next cycle.
        if final["app"].get("controls_overlay", "").startswith("V"):
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)
        if not page.eval("location.href", False) or "watch" not in (page.eval("location.href") or ""):
            note("left the watch page; reopening")
            page.eval(f"location.href = {json.dumps(WATCH)}")
            time.sleep(15)
            page.connect()
    with open(os.path.join(REPORT, "results.json"), "w") as f:
        json.dump(results, f, indent=1)
    with open(os.path.join(REPORT, "logcat.txt"), "w") as f:
        f.write(adb("logcat", "-d", timeout=60))
    with open(os.path.join(REPORT, "summary.txt"), "w") as f:
        f.write("\n".join(log_lines))
    failed = [r["cycle"] for r in results if not r["ok"]]
    note(f"capsule shown after Back in {len(results) - len(failed)}/{len(results)} cycles; failed: {failed}")


if __name__ == "__main__":
    try:
        main()
    finally:
        # Keep the evidence even when a cycle breaks: app process and logcat at that moment.
        note("app pid at end:", pid() or "none")
        with open(os.path.join(REPORT, "logcat.txt"), "w") as f:
            f.write(adb("logcat", "-d", timeout=60))
        with open(os.path.join(REPORT, "summary.txt"), "w") as f:
            f.write("\n".join(log_lines))
