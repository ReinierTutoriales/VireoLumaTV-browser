#!/usr/bin/env python3
"""Check that memory capture never attributes a different app's renderer."""
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location('memory_capture', Path(__file__).with_name('capture-webview-memory.py'))
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)

processes = '''
    Proc # 1: fg     5214:com.google.android.webview:sandboxed_process0:service (service)
        com.reiniertutoriales.vireolumatv/org.chromium.content.app.SandboxedProcessService0:0<=Proc{5168:com.reiniertutoriales.vireolumatv/u0a193}
    Proc # 2: fg     6012:com.google.android.webview:sandboxed_process1:service (service)
        another.browser/org.chromium.content.app.SandboxedProcessService1:0<=Proc{6000:another.browser/u0a194}
'''
assert capture.renderer_pids(processes, 'com.reiniertutoriales.vireolumatv', 5168) == [5214]
assert capture.renderer_pids(processes, 'com.reiniertutoriales.vireolumatv', 6000) == []
assert capture.renderer_pids('', 'com.reiniertutoriales.vireolumatv', 5168) == []
private = '''
    Proc # 1: fore  6523:com.android.chrome:sandboxed_process0/u0a87i4 (service)
        com.reiniertutoriales.vireolumatv/org.chromium.content.app.SandboxedProcessService0<=Proc{6470:com.reiniertutoriales.vireolumatv:incognito/u0a87}
'''
assert capture.renderer_pids(private, 'com.reiniertutoriales.vireolumatv', 6470,
                             'com.reiniertutoriales.vireolumatv:incognito') == [6523]
assert capture.total_pss_kib(' TOTAL PSS:  123,456\n') == 123456
assert capture.total_pss_kib('               TOTAL:  106043       TOTAL SWAP PSS: 0\n') == 106043
assert capture.total_pss_kib('no total') is None
print('Renderer ownership and PSS parsing passed')
