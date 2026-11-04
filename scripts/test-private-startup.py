#!/usr/bin/env python3
"""Open private mode from the real menu and check its separate process on the CI emulator."""
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
    raise AssertionError(f'No fresh window hierarchy for {label}')


def tap(node):
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    assert match, 'The menu control has no screen bounds'
    x1, y1, x2, y2 = map(int, match.groups())
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


assert adb('shell', 'pidof', package) == normal_pid
for attempt in range(2):
    adb('shell', 'input', 'keyevent', '4')
    root = snapshot(f'reset-{attempt}')
    if any(node.get('resource-id') == f'{package}:id/home_search' and node.get('focused') == 'true'
           for node in root.iter('node')):
        break
else:
    raise AssertionError('Home search did not regain focus before opening private mode')
adb('shell', 'input', 'keyevent', '19')  # Up from Home opens the capsule.
root = snapshot('capsule')
menu = next(node for node in root.iter('node') if node.get('resource-id') == f'{package}:id/menu_button')
tap(menu)
for attempt in range(4):
    root = snapshot(f'menu-{attempt}')
    private = next((node for node in root.iter('node') if node.get('text') == 'Incognito mode'), None)
    if private is not None:
        break
    time.sleep(1)
else:
    raise AssertionError('Private mode is missing from the menu')
tap(private)

private_pid = ''
for attempt in range(20):
    result = subprocess.run(['adb', 'shell', 'pidof', private_process], capture_output=True, text=True)
    private_pid = result.stdout.strip()
    if private_pid:
        break
    time.sleep(1)
assert private_pid, 'The private process did not start'
for attempt in range(8):
    root = snapshot(f'home-{attempt}')
    if any('Private browsing doesn' in node.get('text', '') for node in root.iter('node')):
        adb('shell', 'input', 'keyevent', '4')  # Dismiss the first private-mode explanation.
        continue
    if any(node.get('resource-id') == f'{package}:id/home_search' for node in root.iter('node')):
        break
    time.sleep(1)
else:
    raise AssertionError('Private Home did not render')
assert adb('shell', 'pidof', private_process) == private_pid, 'Private process restarted'
assert not subprocess.run(['adb', 'shell', 'pidof', package], capture_output=True).stdout.strip(), \
    'Normal process was retained after entering private mode'
print(f'Private Home started in isolated process {private_pid}')
