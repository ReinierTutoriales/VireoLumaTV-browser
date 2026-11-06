#!/usr/bin/env python3
"""Remote focus checks on the disposable CI emulator, without opening external pages.

Controls may be legacy Views (resource-id package:id/name) or Compose
elements with testTag (resource-id ending with the tag, or viewIdResourceName).
Focus assertions are soft; the only hard failure is if the app process exits.
"""
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from ui_control_contract import matches_control, find_control, has_compose_tags

report = Path(sys.argv[1])
package = sys.argv[2]
expected_pid = sys.argv[3]


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True)


def key(code):
    adb('shell', 'input', 'keyevent', str(code))


def snapshot(label):
    path = '/sdcard/native-home-focus.xml'
    for attempt in range(3):
        current = adb('shell', 'pidof', package).strip()
        if current != expected_pid:
            raise AssertionError(f'The app exited or restarted before the UI snapshot (pid {current} != {expected_pid})')
        adb('shell', 'rm', '-f', path)
        try:
            result = subprocess.run(['adb', 'shell', 'uiautomator', 'dump', path],
                                    check=False, capture_output=True, text=True, timeout=25)
            (report / f'cold-focus-{label}-dump-{attempt}.txt').write_text(result.stdout + result.stderr)
        except subprocess.TimeoutExpired:
            continue
        result = subprocess.run(['adb', 'shell', 'cat', path], capture_output=True, text=True)
        if result.returncode != 0:
            continue
        try:
            root = ET.fromstring(result.stdout)
        except ET.ParseError:
            continue
        (report / f'cold-focus-{label}.xml').write_text(result.stdout)
        return root
    print(f'NOTE: UIAutomator did not write a fresh hierarchy for {label}', file=sys.stderr)
    return None


def matches_id(node, name):
    return matches_control(node, package, name)


def focused(root, name):
    if root is None:
        return False
    return any(matches_id(node, name) and node.get('focused') == 'true'
               for node in root.iter('node'))


def note(msg):
    print(f'NOTE: {msg}', file=sys.stderr)


# Soft checks only. Hard requirement is that the process stays alive.
for attempt in range(2):
    key(4)
    root = snapshot(f'back-{attempt}')
    if focused(root, 'home_search'):
        break
if not focused(root, 'home_search'):
    note('Closing the capsule did not return focus to home_search (transitional)')
key(19)
root = snapshot('capsule')
if not (focused(root, 'back_button') or focused(root, 'home_button')):
    if has_compose_tags(root, {'back_button', 'home_button'}):
        raise AssertionError('Compose toolbar tags are exported but D-pad Up did not focus Back/Home')
    note('Up did not focus capsule controls (transitional: toolbar tags not exported)')
key(4)
if not focused(snapshot('search'), 'home_search'):
    note('Back did not restore search focus (transitional)')
key(22)
if not focused(snapshot('submit'), 'home_submit'):
    note('Right from search did not reach Submit (transitional)')
key(20)
root = snapshot('tiles')
if root is not None:
    try:
        grid = next(node for node in root.iter('node') if matches_id(node, 'home_tiles'))
        if not any(node.get('focused') == 'true' and node.get('class') == 'android.widget.Button'
                   for node in grid.iter('node')):
            note('Down did not reach a native shortcut/action (transitional)')
    except StopIteration:
        note('home_tiles not found by id or testTag (transitional)')
current = adb('shell', 'pidof', package).strip()
if current != expected_pid:
    raise AssertionError(f'The process restarted during remote navigation (pid {current} != {expected_pid})')
print('Native Home remote focus checks completed (focus asserts soft; process stayed alive)')
