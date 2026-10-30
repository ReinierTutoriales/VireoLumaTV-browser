"""Refresh explicitly, or verify offline, the redistributable first-install filter snapshot."""
import argparse
import datetime
import gzip
import hashlib
import json
import re
import urllib.request
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'app/src/main/assets/adblock/baseline'
LIMIT = 8 * 1024 * 1024
TOTAL = 16 * 1024 * 1024
SOURCES = [
    ('adblock_list_easylist.txt', 'https://easylist-downloads.adblockplus.org/easylist.txt', False),
    ('adblock_list_easyprivacy.txt', 'https://easylist-downloads.adblockplus.org/easyprivacy.txt', False),
    ('adblock_list_easylist_spanish.txt', 'https://easylist-downloads.adblockplus.org/easylistspanish.txt', False),
] + [('adblock_list_ubo_' + name, 'https://ublockorigin.github.io/uAssets/filters/' + name, True)
     for name in ['filters.txt', 'quick-fixes.txt', 'unbreak.txt', 'badware.txt']]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify(directory=OUT):
    manifest = json.loads((directory / 'manifest.json').read_text())
    assert manifest['version'] == 1 and len(manifest['lists']) == 7
    assert [(x['cache'], x['url'], x['ubo']) for x in manifest['lists']] == SOURCES
    size = 0
    for name, info in manifest['files'].items():
        assert re.fullmatch(r'[a-z0-9-]+\.txt\.gz', name), name
        with gzip.open(directory / name, 'rb') as source:
            data = source.read(LIMIT + 1)
        assert len(data) == info['bytes'] <= LIMIT and digest(data) == info['sha256'], name
        data.decode('utf-8')
        size += len(data)
    assert size <= TOTAL
    assert set(p.name for p in directory.glob('*.gz')) == set(manifest['files'])
    print(f"Baseline: {len(manifest['lists'])} lists, {len(manifest['files'])} source files, "
          f"{size} text bytes, {sum(p.stat().st_size for p in directory.glob('*.gz'))} gzip bytes; hashes OK")


def refresh_to(directory):
    directory.mkdir(parents=True, exist_ok=True)
    files, payloads, lists = {}, {}, []

    def fetch(url):
        if url in payloads:
            return payloads[url]
        request = urllib.request.Request(url, headers={'User-Agent': 'VireoLumaTV filter-snapshot-maintenance'})
        with urllib.request.urlopen(request, timeout=30) as response:
            if response.status != 200:
                raise ValueError(f'HTTP {response.status}: {url}')
            data = response.read(LIMIT + 1)
        if len(data) > LIMIT or sum(v[1] for v in payloads.values()) + len(data) > TOTAL:
            raise ValueError('Snapshot exceeds the text budget')
        text = data.decode('utf-8')
        if text.lstrip().startswith('<') or not text.strip():
            raise ValueError(f'Invalid filter text: {url}')
        name = digest(url.encode())[:24] + '.txt.gz'
        payloads[url] = (name, len(data), text)
        files[name] = {'url': url, 'bytes': len(data), 'sha256': digest(data)}
        (directory / name).write_bytes(gzip.compress(data, compresslevel=9, mtime=0))
        return payloads[url]

    for cache, url, ubo in SOURCES:
        name, _, main = fetch(url)
        if ubo:
            assert any(line.startswith('! Title: uBlock') for line in main.splitlines()[:8]), url
        else:
            assert main.startswith('[Adblock Plus') and len(main.splitlines()) >= 100, url
        includes, visited = {}, set()

        def expand(text, depth):
            if depth >= 3:
                return
            for child in re.findall(r'^!#include\s+([^\r\n]+)', text, re.M):
                child = child.strip()
                if not child or '://' in child or child.startswith('/') or '..' in child or child in visited:
                    continue
                visited.add(child)
                child_name, _, child_text = fetch(url.rsplit('/', 1)[0] + '/' + child)
                includes[child] = child_name
                expand(child_text, depth + 1)
        if ubo:
            expand(main, 0)
        lists.append({'cache': cache, 'url': url, 'file': name, 'ubo': ubo, 'includes': includes})
    manifest = {'version': 1, 'fetched_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'license': 'GPL-3.0 (filter data only; retain upstream source headers)',
                'lists': lists, 'files': files}
    (directory / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    for path in directory.glob('*.gz'):
        if path.name not in files:
            path.unlink()
    verify(directory)


def refresh():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='filter-baseline-', dir=OUT.parent) as temporary:
        stage, previous = Path(temporary) / 'next', Path(temporary) / 'previous'
        refresh_to(stage)
        if OUT.exists():
            OUT.rename(previous)
        try:
            stage.rename(OUT)
        except OSError:
            if previous.exists():
                previous.rename(OUT)
            raise


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh', action='store_true', help='Download a complete snapshot explicitly')
    options = parser.parse_args()
    refresh() if options.refresh else verify()
