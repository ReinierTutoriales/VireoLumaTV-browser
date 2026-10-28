#!/usr/bin/env python3
"""Check the built package against the source tree; this is not an authorship determination."""
import argparse
import hashlib
import json
import re
import struct
import subprocess
import zipfile
from pathlib import Path

REMOVED_ASSETS = (
    'pages/home/favicons/bing.png', 'pages/home/favicons/custom.png',
    'pages/home/favicons/ddg.png', 'pages/home/favicons/google.png',
    'pages/home/favicons/startpage.png', 'pages/home/favicons/yahoo.png',
    'pages/home/favicons/yandex.png', 'pages/home/ic_not_available.svg',
    'pages/home/mic.svg', 'pages/home/more.svg', 'pages/home/search.svg', 'pages/ic_warning.svg',
)
PACKAGED_SOURCES = (
    'generic_injects.js', 'pages/home/index.html', 'pages/warning-certificate.html',
    'licenses/NOTICE.md', 'licenses/TV-Bro-LICENSE.md',
)


def class_names(dex):
    """Read defined classes, rather than confusing URLs or notices with class descriptors."""
    if not dex.startswith(b'dex\n') or len(dex) < 112:
        raise ValueError('Invalid DEX header')
    string_count, string_offset = struct.unpack_from('<II', dex, 56)
    type_count, type_offset = struct.unpack_from('<II', dex, 64)
    class_count, class_offset = struct.unpack_from('<II', dex, 96)
    strings = []
    for index in range(string_count):
        offset = struct.unpack_from('<I', dex, string_offset + index * 4)[0]
        while dex[offset] & 128: offset += 1  # UTF-16 length, encoded as unsigned LEB128.
        offset += 1
        end = dex.index(b'\0', offset)
        strings.append(dex[offset:end].decode('utf-8', errors='replace'))
    types = [strings[struct.unpack_from('<I', dex, type_offset + index * 4)[0]] for index in range(type_count)]
    return [types[struct.unpack_from('<I', dex, class_offset + index * 32)[0]] for index in range(class_count)]


def audit(apk, aapt2, root):
    issues = []
    checks = {}
    with zipfile.ZipFile(apk) as package:
        names = package.namelist()
        dex_files = [name for name in names if re.fullmatch(r'classes\d*\.dex', name)]
        if not dex_files: issues.append('No DEX files in APK')
        classes = [name for path in dex_files for name in class_names(package.read(path))]
        old_classes = [name for name in classes if name.startswith('Lcom/phlox/tvwebbrowser/')]
        checks['defined_classes'] = len(classes)
        checks['upstream_namespace_classes'] = old_classes
        if old_classes: issues.append('Original TV Bro class namespace is still packaged')
        retained = [name for name in REMOVED_ASSETS if 'assets/' + name in names]
        checks['removed_assets_still_packaged'] = retained
        if retained: issues.append('Removed assets are still packaged')
        checks['source_assets'] = {}
        for path in PACKAGED_SOURCES:
            source = root / 'app/src/main/assets' / path
            if not source.is_file() or 'assets/' + path not in names:
                issues.append('Required source or packaged asset missing: ' + path)
                continue
            binary = package.read('assets/' + path)
            matches = binary == source.read_bytes()
            checks['source_assets'][path] = {'sha256': hashlib.sha256(binary).hexdigest(), 'matches_source': matches}
            if not matches: issues.append('Packaged asset differs from reviewed source: ' + path)
    badging = subprocess.check_output([str(aapt2), 'dump', 'badging', str(apk)], text=True)
    resources = subprocess.check_output([str(aapt2), 'dump', 'resources', str(apk)], text=True)
    checks['package_name'] = re.search(r"package: name='([^']+)'", badging).group(1)
    if checks['package_name'] != 'com.reiniertutoriales.vireolumatv': issues.append('Unexpected applicationId')
    for removed in ('anim/infinite_fadeinout_anim', 'menu/menu_link'):
        if removed in resources: issues.append('Retired compiled resource: ' + removed)
    checks['upstream_credit_resource_present'] = 'string/based_on_tv_bro_sources' in resources
    if not checks['upstream_credit_resource_present']: issues.append('Required upstream credit is missing')
    checks['issues'] = issues
    return {'apk': apk.name, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
            'scope': 'Package contents and source consistency; not proof of independent authorship or runtime validation',
            'checks': checks}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--aapt2', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = audit(args.apk, args.aapt2, Path(__file__).resolve().parent.parent)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if report['checks']['issues']: raise SystemExit(1)


if __name__ == '__main__': main()
