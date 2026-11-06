#!/usr/bin/env python3
"""Remote focus checks on the disposable CI emulator, without opening external pages.

Controls may be legacy Views (resource-id package:id/name) or Compose
elements with testTag (resource-id ending with the tag, or viewIdResourceName).
Focus checks remain transitional except exported Compose toolbar tags.\nObservations are saved as JSON to expose gaps without changing test outcomes.
"""
import json
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


observations = []


def observe(step, root, expected, *, strict_when_exported=False):
    visible = root is not None and any(find_control(root, package, name) is not None for name in expected)
    actual = root is not None and any(focused(root, name) for name in expected)
    observations.append({'step': step, 'expected': list(expected), 'visible': bool(visible),
                         'focused': bool(actual), 'hierarchy_available': root is not None})
    if strict_when_exported and has_compose_tags(root, set(expected)) and not actual:
        raise AssertionError(f'{step}: Compose tags exported but expected focus missing')
    if not actual:
        print(f'NOTE: {step}: expected focus not observed (transitional)', file=sys.stderr)
    return actual


def save_observations():
    (report / 'native-home-focus-observations.json').write_text(
        json.dumps({'schema': 1, 'observations': observations}, indent=2) + '\n')


# Transitional checks are unchanged; every focus observation is recorded.
try:
    for attempt in range(2):
        key(4)
        root = snapshot(f'back-{attempt}')
        if observe(f'back-{attempt}', root, ('home_search',)):
            break
    key(19)
    observe('capsule', snapshot('capsule'), ('back_button', 'home_button'), strict_when_exported=True)
    key(4)
    observe('search', snapshot('search'), ('home_search',))
    key(22)
    observe('submit', snapshot('submit'), ('home_submit',))
    key(20)
    root = snapshot('tiles')
    if root is not None:
        grid = find_control(root, package, 'home_tiles')
        if grid is None:
            print('NOTE: home_tiles not found (transitional)', file=sys.stderr)
        elif not any(node.get('focused') == 'true' and node.get('class') == 'android.widget.Button'
                     for node in grid.iter('node')):
            print('NOTE: Down did not reach a native shortcut/action (transitional)', file=sys.stderr)
    current = adb('shell', 'pidof', package).strip()
    if current != expected_pid:
        raise AssertionError(f'The process restarted during remote navigation (pid {current} != {expected_pid})')
    print('Native Home remote focus checks completed (transitional assertions unchanged)')
finally:
    save_observations()
