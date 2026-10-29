#!/usr/bin/env python3
"""Capture reproducible TV diagnostics without clearing app data or changing system settings."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import time


def invoke(arguments, timeout=30):
    try:
        run = subprocess.run(arguments, capture_output=True, text=True, timeout=timeout)
        return {'command': arguments, 'returncode': run.returncode, 'stdout': run.stdout, 'stderr': run.stderr}
    except (OSError, subprocess.TimeoutExpired) as error:
        return {'command': arguments, 'returncode': None, 'stdout': '', 'stderr': str(error)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb', help='ADB executable or path to adb.exe')
    parser.add_argument('--serial', help='Authorized device; required when several are connected')
    parser.add_argument('--package', default='com.reiniertutoriales.vireolumatv')
    parser.add_argument('--phase', required=True, help='Label such as baseline-one-tab or candidate-video')
    parser.add_argument('--source-sha', required=True, help='Source SHA of the installed APK, supplied by the tester')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--renderer-pid', action='append', type=int, default=[],
                        help='Renderer PID attributed using the saved activity connections; can be repeated')
    parser.add_argument('--settle-seconds', type=int, default=30)
    parser.add_argument('--cold-start', action='store_true',
                        help='Explicitly stop and relaunch the app before measuring (does not clear its data)')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+', args.package):
        parser.error('Invalid package name')
    if not re.fullmatch(r'[A-Za-z0-9_-]+', args.phase): parser.error('Use a simple phase label')
    if not re.fullmatch(r'[0-9a-fA-F]{7,40}', args.source_sha): parser.error('Invalid source SHA')
    if not 0 <= args.settle_seconds <= 60: parser.error('settle-seconds must be between 0 and 60')
    if any(pid <= 0 for pid in args.renderer_pid): parser.error('Renderer PIDs must be positive')
    devices = invoke([args.adb, 'devices'])
    authorized = [line.split()[0] for line in devices['stdout'].splitlines()
                  if len(line.split()) >= 2 and line.split()[1] == 'device']
    serial = args.serial or (authorized[0] if len(authorized) == 1 else None)
    if serial not in authorized: parser.error('Select exactly one connected and authorized ADB device with --serial')
    adb = [args.adb, '-s', serial, 'shell']
    report = {'captured_utc': datetime.now(timezone.utc).isoformat(), 'serial': serial,
              'phase': args.phase, 'source_sha_supplied_by_tester': args.source_sha,
              'package': args.package, 'settle_seconds': args.settle_seconds,
              'renderer_pids_supplied_by_tester': args.renderer_pid, 'captures': {}}
    captures = report['captures']

    def capture(name, *command, timeout=30):
        result = invoke(adb + list(command), timeout)
        captures[name] = result
        return result['stdout'].strip() if result['returncode'] == 0 else ''

    if args.cold_start:
        capture('force-stop', 'am', 'force-stop', args.package)
        capture('launch', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-c',
                'android.intent.category.LEANBACK_LAUNCHER', '-n', args.package + '/.activity.main.MainActivity', timeout=60)
    capture('gfx-reset', 'dumpsys', 'gfxinfo', args.package, 'reset')
    time.sleep(args.settle_seconds)
    for prop in ('ro.product.model', 'ro.build.fingerprint', 'ro.build.version.sdk', 'ro.config.low_ram'):
        capture(prop, 'getprop', prop)
    capture('ui-size', 'wm', 'size')
    capture('ui-density', 'wm', 'density')
    capture('display', 'dumpsys', 'display')
    capture('webview-provider', 'dumpsys', 'webviewupdate')
    capture('installed-package', 'dumpsys', 'package', args.package)
    capture('processes', 'ps', '-A')
    capture('activity-connections', 'dumpsys', 'activity', 'processes', args.package)
    process_groups = {}
    for name in (args.package, args.package + ':incognito'):
        pids = capture('pidof-' + name, 'pidof', name)
        process_groups[name] = [int(pid) for pid in pids.split() if pid.isdigit()]
        for pid in process_groups[name]: capture('meminfo-' + str(pid), 'dumpsys', 'meminfo', str(pid))
    for pid in args.renderer_pid: capture('renderer-meminfo-' + str(pid), 'dumpsys', 'meminfo', str(pid))
    report['app_processes'] = process_groups
    capture('native-ui-frames', 'dumpsys', 'gfxinfo', args.package, 'framestats')
    report['limits'] = [
        'Renderer ownership and supplied source SHA require tester verification.',
        'No aggregate memory total is inferred from unrelated WebView processes.',
        'gfxinfo measures native window frames, not all Chromium rendering or video frames.',
        'The low_ram property is recorded as a property, not claimed as ActivityManager.isLowRamDevice().',
        'The settle interval measures the current screen; it is not an automated navigation benchmark.',
    ]
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    args.output.mkdir(parents=True, exist_ok=True)
    target = args.output / (args.phase + '-' + stamp + '.json')
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(target)
    if not process_groups[args.package]: print('Main process was not running; see the recorded process state.')


if __name__ == '__main__': main()
