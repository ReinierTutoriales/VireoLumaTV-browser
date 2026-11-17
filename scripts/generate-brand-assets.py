#!/usr/bin/env python3
"""Generate Android resources and transparent README logos from the approved SVG."""
import argparse
import math
import re
import struct
import xml.etree.ElementTree as ET
import zlib
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

    def fit_params(data, x, y, width, height, circle=None):
        pts = points(data)
        left, right = min(p[0] for p in pts), max(p[0] for p in pts)
        top, bottom = min(p[1] for p in pts), max(p[1] for p in pts)
        cx, cy = (left + right) / 2, (top + bottom) / 2
        scale = min(width / (right - left), height / (bottom - top))
        if circle is not None:
            scale = min(scale, circle / max(math.hypot(px - cx, py - cy) for px, py in pts))
        return scale, x + width / 2 - cx * scale, y + height / 2 - cy * scale

    def fit(data, x, y, width, height, circle=None):
        scale, tx, ty = fit_params(data, x, y, width, height, circle)
        return (f'    <group android:scaleX="{scale:.8f}" android:scaleY="{scale:.8f}" '
                f'android:translateX="{tx:.8f}" '
                f'android:translateY="{ty:.8f}">\n'
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
    adaptive_banner = fit(paths['bird'], 56, 37.5, 58, 60) + fit(paths['letters'], 122, 51.5, 64, 32)
    result = {
        'values/brand.xml': ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                             f'    <color name="brand_background">{background}</color>\n'
                             f'    <color name="brand_foreground">{foreground}</color>\n</resources>\n'),
        'drawable/brand_logo.xml': logo,
        'drawable/brand_symbol.xml': vector(24, 24, fit(paths['bird'], 2, 2, 20, 20)),
        'drawable/brand_icon_foreground.xml': vector(108, 108, fit(lockup, 21, 21, 66, 66, circle=32)),
        'drawable/brand_banner_foreground.xml': vector(240, 135, adaptive_banner),
    }
    icon_fit = fit_params(lockup, 8, 8, 64, 64)
    for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
        factor = size / 80.0
        s, tx, ty = icon_fit
        result[f'mipmap-{density}/ic_launcher.png'] = render_png(
            size, size, background, foreground, [(lockup, s * factor, tx * factor, ty * factor)],
        )
    bird_fit = fit_params(paths['bird'], 16, 15, 58, 60)
    letters_fit = fit_params(paths['letters'], 82, 29, 64, 32)
    result['mipmap-xhdpi/tv_banner.png'] = render_png(
        320, 180, background, foreground,
        [
            (paths['bird'], bird_fit[0] * 2.0, bird_fit[1] * 2.0, bird_fit[2] * 2.0),
            (paths['letters'], letters_fit[0] * 2.0, letters_fit[1] * 2.0, letters_fit[2] * 2.0),
        ],
    )
    for name, resource in [('ic_launcher', 'brand_icon_foreground'), ('tv_banner', 'brand_banner_foreground')]:
        result[f'mipmap-anydpi-v26/{name}.xml'] = (
            f'<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="{ANDROID}">\n'
            f'    <background android:drawable="@color/brand_background"/>\n'
            f'    <foreground android:drawable="@drawable/{resource}"/>\n</adaptive-icon>\n')
    outputs = {RES / path: content for path, content in result.items()}
    pts = points(lockup)
    left = math.floor(min(x for x, _ in pts)) - 24
    top = math.floor(min(y for _, y in pts)) - 24
    width = math.ceil(max(x for x, _ in pts)) + 24 - left
    height = math.ceil(max(y for _, y in pts)) + 24 - top
    for theme, color in [('light', background), ('dark', foreground)]:
        outlines = ''.join(f'<path fill="{color}" d="{data}"/>\n' for data in paths.values())
        outputs[ROOT / f'assets/brand/logo-readme-{theme}.svg'] = (
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{left} {top} {width} {height}">\n'
            '<title>VireoLumaTV — logotipo con las iniciales VLTV</title>\n'
            + outlines + '</svg>\n')
    return outputs


def parse_svg_edges(path_data, scale, tx, ty):
    tokens = re.findall(r'[MLCZmlcz]|-?\d+(?:\.\d+)?', path_data)
    edges = []
    i = 0
    cmd = ''
    cx = cy = sx = sy = 0.0
    def xf(x, y):
        return x * scale + tx, y * scale + ty
    while i < len(tokens):
        tok = tokens[i]
        if tok.isalpha():
            cmd = tok
            i += 1
            if cmd in ('Z', 'z'):
                if (cx, cy) != (sx, sy):
                    edges.append((*xf(cx, cy), *xf(sx, sy)))
                cx, cy = sx, sy
            continue
        if cmd == 'M':
            cx, cy = float(tokens[i]), float(tokens[i + 1])
            sx, sy = cx, cy
            i += 2
            cmd = 'L'
        elif cmd == 'L':
            nx, ny = float(tokens[i]), float(tokens[i + 1])
            edges.append((*xf(cx, cy), *xf(nx, ny)))
            cx, cy = nx, ny
            i += 2
        elif cmd == 'C':
            x1, y1, x2, y2, x3, y3 = map(float, tokens[i:i + 6])
            p0, p1, p2, p3 = xf(cx, cy), xf(x1, y1), xf(x2, y2), xf(x3, y3)
            px, py = p0
            for step in range(1, 9):
                t = step / 8.0
                mt = 1.0 - t
                qx = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
                qy = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
                edges.append((px, py, qx, qy))
                px, py = qx, qy
            cx, cy = x3, y3
            i += 6
        else:
            raise ValueError(f'Unsupported SVG command: {cmd}')
    return edges


def render_png(width, height, bg_hex, fg_hex, layers, ss=4):
    bg = tuple(int(bg_hex.lstrip('#')[j:j + 2], 16) for j in (0, 2, 4))
    fg = tuple(int(fg_hex.lstrip('#')[j:j + 2], 16) for j in (0, 2, 4))
    edges = []
    for path_data, scale, tx, ty in layers:
        edges.extend(parse_svg_edges(path_data, scale * ss, tx * ss, ty * ss))

    sw, sh = width * ss, height * ss
    cov = [0] * (width * height)
    for sy in range(sh):
        y = sy + 0.5
        hits = []
        for x0, y0, x1, y1 in edges:
            if y0 <= y < y1:
                hits.append((x0 + (y - y0) * (x1 - x0) / (y1 - y0), 1))
            elif y1 <= y < y0:
                hits.append((x0 + (y - y0) * (x1 - x0) / (y1 - y0), -1))
        if not hits:
            continue
        hits.sort(key=lambda item: item[0])
        winding = 0
        start_x = 0.0
        row_base = (sy // ss) * width
        for hx, delta in hits:
            prev = winding
            winding += delta
            if prev == 0 and winding != 0:
                start_x = hx
            elif prev != 0 and winding == 0:
                x_a = max(0, int(math.floor(start_x)))
                x_b = min(sw, int(math.ceil(hx)))
                for sx in range(x_a, x_b):
                    if start_x <= sx + 0.5 < hx:
                        cov[row_base + (sx // ss)] += 1

    samples = ss * ss
    half = samples // 2
    raw = bytearray()
    for py in range(height):
        raw.append(0)
        row_off = py * width
        for px in range(width):
            c = cov[row_off + px]
            if c == 0:
                raw.extend(bg)
            elif c == samples:
                raw.extend(fg)
            else:
                inv = samples - c
                raw.append((bg[0] * inv + fg[0] * c + half) // samples)
                raw.append((bg[1] * inv + fg[1] * c + half) // samples)
                raw.append((bg[2] * inv + fg[2] * c + half) // samples)

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    ihdr = struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    stale = []
    for path, expected in generate().items():
        is_bin = isinstance(expected, bytes)
        if args.check:
            ok = path.is_file() and (path.read_bytes() == expected if is_bin else path.read_text() == expected)
            if not ok:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            if is_bin:
                path.write_bytes(expected)
            else:
                path.write_text(expected)
    if stale:
        raise SystemExit('Regenerate brand resources: ' + ', '.join(stale))
    print('Brand resources match the approved vector master.' if args.check else 'Brand resources generated.')


if __name__ == '__main__':
    main()
