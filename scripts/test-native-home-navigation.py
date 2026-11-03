#!/usr/bin/env python3
"""Remote focus checks on the disposable CI emulator, without opening external pages."""
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

report = Path(sys.argv[1])
package = sys.argv[2]
expected_pid = sys.argv[3]


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True)


def key(code):
    adb('shell', 'input', 'keyevent', str(code))


def snapshot(label):
    path = '/sdcard/native-home-focus.xml'
    adb('shell', 'rm', '-f', path)
    # Some images terminate the dumper after writing a complete XML file.
    subprocess.run(['adb', 'shell', 'uiautomator', 'dump', path], check=False, capture_output=True, timeout=25)
    text = adb('shell', 'cat', path)
    (report / f'cold-focus-{label}.xml').write_text(text)
    return ET.fromstring(text)


def focused(root, name):
    return any(node.get('resource-id') == f'{package}:id/{name}' and node.get('focused') == 'true'
               for node in root.iter('node'))


# The cold-start capsule may be open or closed. Back closes it within two presses.
for attempt in range(2):
    key(4)
    root = snapshot(f'back-{attempt}')
    if focused(root, 'home_search'):
        break
assert focused(root, 'home_search'), 'Closing the capsule did not return focus to native Home'
key(19)
root = snapshot('capsule')
assert focused(root, 'back_button') or focused(root, 'home_button'), 'Up did not open actionable capsule controls'
key(4)
assert focused(snapshot('search'), 'home_search'), 'Back did not restore search focus'
key(22)
assert focused(snapshot('submit'), 'home_submit'), 'Right from search did not reach Submit'
key(20)
root = snapshot('tiles')
grid = next(node for node in root.iter('node') if node.get('resource-id') == f'{package}:id/home_tiles')
assert any(node.get('focused') == 'true' and node.get('class') == 'android.widget.Button'
           for node in grid.iter('node')), 'Down did not reach a native shortcut/action'
assert adb('shell', 'pidof', package).strip() == expected_pid, 'The process restarted during remote navigation'
print('Native Home remote focus passed: capsule, search, Submit and shortcut grid; no external navigation')
