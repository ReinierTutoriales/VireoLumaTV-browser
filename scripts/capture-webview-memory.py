#!/usr/bin/env python3
"""Capture app and its attributed WebView renderer memory on a disposable CI emulator."""
import json
from pathlib import Path
import re
import subprocess
import sys


def renderer_pids(processes, package, app_pid, process_name=None):
    """Only accept a sandbox service explicitly bound to this app process."""
    lines = processes.splitlines()
    pids = set()
    owner = f'<=Proc{{{app_pid}:{process_name or package}/'
    for current, following in zip(lines, lines[1:]):
        if ('Proc #' not in current or owner not in following or
                f'{package}/org.chromium.content.app.SandboxedProcessService' not in following):
            continue
        match = re.search(r'\b(\d+):\S*sandboxed_process\S*', current)
        if match:
            pids.add(int(match.group(1)))
    return sorted(pids)


def total_pss_kib(meminfo):
    match = re.search(r'(?m)^\s*(?:TOTAL PSS|TOTAL):\s*([\d,]+)', meminfo)
    return int(match.group(1).replace(',', '')) if match else None


def capture(report, package, app_pid, phase):
    def adb(*args):
        return subprocess.run(['adb', 'shell', *args], capture_output=True, text=True,
                              timeout=30, check=True).stdout

    report.mkdir(parents=True, exist_ok=True)
    app_pid = int(app_pid)
    process_name = package + (':incognito' if phase == 'private-home' else '')
    assert app_pid in [int(pid) for pid in adb('pidof', process_name).split()], 'App process changed during capture'
    processes = adb('dumpsys', 'activity', 'processes')
    (report / f'{phase}-processes.txt').write_text(processes)
    pids = renderer_pids(processes, package, app_pid, process_name)
    measurements = {}
    for role, pid in [('app', app_pid)] + [('renderer', pid) for pid in pids]:
        meminfo = adb('dumpsys', 'meminfo', str(pid))
        (report / f'{phase}-{role}-{pid}-meminfo.txt').write_text(meminfo)
        measurements[str(pid)] = {'role': role, 'total_pss_kib': total_pss_kib(meminfo)}
    result = {'phase': phase, 'android_api': adb('getprop', 'ro.build.version.sdk').strip(),
              'mem_total_kib': int(re.search(r'MemTotal:\s*(\d+)', adb('cat', '/proc/meminfo')).group(1)),
              'app_pid': app_pid, 'attributed_renderer_pids': pids, 'measurements': measurements,
              'limits': 'Idle Home snapshot; no video, page-load latency or physical-TV performance inference.'}
    (report / f'{phase}-memory.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))


if __name__ == '__main__':
    capture(Path(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4])
