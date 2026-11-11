#!/usr/bin/env python3
"""Generate native Android brand resources from the approved SVG outlines."""
import argparse
import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'app/src/main/res'
ANDROID = 'http://schemas.android.com/apk/res/android'


def generate():
    master = ET.parse(ROOT / 'assets/brand/logo.svg').getroot()
    ns = {'s': 'http://www.w3.org/2000/svg'}
    background = master.find('s:rect', ns).attrib['fill']
    paths = {p.attrib['id']: p.attrib['d'] for p in master.findall('s:path', ns)}
    foreground = master.find('s:path', ns).attrib['fill']
    assert set(paths) == {'bird', 'letters'}
    assert all(p.attrib['fill'] == foreground for p in master.findall('s:path', ns))

    def points(data):
        values = list(map(float, re.findall(r'-?\d+(?:\.\d+)?', data)))
        return list(zip(values[::2], values[1::2]))

    def fit(data, x, y, width, height, circle=None):
        pts = points(data)
        left, right = min(p[0] for p in pts), max(p[0] for p in pts)
        top, bottom = min(p[1] for p in pts), max(p[1] for p in pts)
        cx, cy = (left + right) / 2, (top + bottom) / 2
        scale = min(width / (right - left), height / (bottom - top))
        if circle is not None:
            scale = min(scale, circle / max(math.hypot(px - cx, py - cy) for px, py in pts))
        return (f'    <group android:scaleX="{scale:.8f}" android:scaleY="{scale:.8f}" '
                f'android:translateX="{x + width / 2 - cx * scale:.8f}" '
                f'android:translateY="{y + height / 2 - cy * scale:.8f}">\n'
                f'        <path android:fillColor="@color/brand_foreground" android:pathData="{data}"/>\n'
                '    </group>\n')

    def vector(width, height, content, opaque=False):
        backdrop = (f'    <path android:fillColor="@color/brand_background" '
                    f'android:pathData="M0,0H{width}V{height}H0Z"/>\n') if opaque else ''
        return (f'<?xml version="1.0" encoding="utf-8"?>\n'
                f'<vector xmlns:android="{ANDROID}" android:width="{width}dp" android:height="{height}dp" '
                f'android:viewportWidth="{width}" android:viewportHeight="{height}">\n'
                + backdrop + content + '</vector>\n')

    lockup = paths['bird'] + ' ' + paths['letters']
    logo = vector(80, 80, fit(lockup, 8, 8, 64, 64), True)
    banner = fit(paths['bird'], 16, 15, 58, 60) + fit(paths['letters'], 82, 29, 64, 32)
    adaptive_banner = fit(paths['bird'], 56, 37.5, 58, 60) + fit(paths['letters'], 122, 51.5, 64, 32)
    result = {
        'values/brand.xml': ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                             f'    <color name="brand_background">{background}</color>\n'
                             f'    <color name="brand_foreground">{foreground}</color>\n</resources>\n'),
        'drawable/brand_logo.xml': logo,
        'drawable/brand_symbol.xml': vector(24, 24, fit(paths['bird'], 2, 2, 20, 20)),
        'drawable/brand_icon_foreground.xml': vector(108, 108, fit(lockup, 21, 21, 66, 66, circle=32)),
        'drawable/brand_banner_foreground.xml': vector(240, 135, adaptive_banner),
        'mipmap-anydpi/ic_launcher.xml': logo,
        'mipmap-anydpi/tv_banner.xml': vector(160, 90, banner, True),
    }
    for name, resource in [('ic_launcher', 'brand_icon_foreground'), ('tv_banner', 'brand_banner_foreground')]:
        result[f'mipmap-anydpi-v26/{name}.xml'] = (
            f'<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="{ANDROID}">\n'
            f'    <background android:drawable="@color/brand_background"/>\n'
            f'    <foreground android:drawable="@drawable/{resource}"/>\n</adaptive-icon>\n')
    return {RES / path: content for path, content in result.items()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    stale = []
    for path, expected in generate().items():
        if args.check:
            if not path.is_file() or path.read_text() != expected:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(expected)
    if stale:
        raise SystemExit('Regenerate brand resources: ' + ', '.join(stale))
    print('Brand resources match the approved vector master.' if args.check else 'Brand resources generated.')


if __name__ == '__main__':
    main()
