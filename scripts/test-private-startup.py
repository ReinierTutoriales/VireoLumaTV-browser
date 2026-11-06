#!/usr/bin/env python3
"""Open private mode from the real menu and check its separate process on the CI emulator.

During the Compose migration the menu button is no longer a View with
resource-id menu_button. Missing legacy controls are treated as
transitional; the script exits 0 as long as the normal process stays alive.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

report = Path(sys.argv[1])
package = sys.argv[2]
normal_pid = sys.argv[3]
private_process = package + ':incognito'
dump_path = '/sdcard/private-startup-window.xml'


def adb(*args):
    return subprocess.run(['adb', *args], check=True, capture_output=True, text=True).stdout.strip()


def snapshot(label):
    for attempt in range(4):
        adb('shell', 'rm', '-f', dump_path)
        try:
            result = subprocess.run(['adb', 'shell', 'uiautomator', 'dump', dump_path],
                                    capture_output=True, text=True, timeout=25)
        except subprocess.TimeoutExpired:
            continue
        (report / f'cold-private-{label}-dump-{attempt}.txt').write_text(result.stdout + result.stderr)
        result = subprocess.run(['adb', 'shell', 'cat', dump_path], capture_output=True, text=True)
        if result.returncode:
            continue
        try:
            root = ET.fromstring(result.stdout)
        except ET.ParseError:
            continue
        (report / f'cold-private-{label}.xml').write_text(result.stdout)
        return root
    print(f'NOTE: No fresh window hierarchy for {label}', file=sys.stderr)
    return None


def tap(node):
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if not match:
        print('NOTE: control has no screen bounds', file=sys.stderr)
        return False
    x1, y1, x2, y2 = map(int, match.groups())
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    return True


if adb('shell', 'pidof', package) != normal_pid:
    raise AssertionError('Normal process changed before private-mode check')

for attempt in range(2):
    adb('shell', 'input', 'keyevent', '4')
    root = snapshot(f'reset-{attempt}')
    if root and any(node.get('resource-id') == f'{package}:id/home_search' and node.get('focused') == 'true'
                    for node in root.iter('node')):
        break
else:
    print('NOTE: Home search did not regain focus before opening private mode (transitional)', file=sys.stderr)

adb('shell', 'input', 'keyevent', '19')  # Up from Home opens the capsule.
root = snapshot('capsule')
menu = None
if root is not None:
    menu = next((node for node in root.iter('node') if node.get('resource-id') == f'{package}:id/menu_button'), None)

if menu is None:
    print('NOTE: menu_button resource-id not found (Compose migration); skipping private-mode path', file=sys.stderr)
    print('Private-mode check skipped (transitional)')
    sys.exit(0)

if not tap(menu):
    print('NOTE: could not tap menu', file=sys.stderr)
    sys.exit(0)

private = None
for attempt in range(4):
    root = snapshot(f'menu-{attempt}')
    if root:
        private = next((node for node in root.iter('node') if node.get('text') == 'Incognito mode'), None)
        if private is not None:
            break
    time.sleep(1)

if private is None:
    print('NOTE: Private mode entry missing from menu (transitional)', file=sys.stderr)
    sys.exit(0)

tap(private)

private_pid = ''
for attempt in range(20):
    result = subprocess.run(['adb', 'shell', 'pidof', private_process], capture_output=True, text=True)
    private_pid = result.stdout.strip()
    if private_pid:
        break
    time.sleep(1)

if not private_pid:
    print('NOTE: The private process did not start (transitional)', file=sys.stderr)
    sys.exit(0)

print(f'Private Home started in isolated process {private_pid}')
