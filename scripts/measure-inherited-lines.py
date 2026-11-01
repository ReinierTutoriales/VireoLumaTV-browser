#!/usr/bin/env python3
"""Counts application source lines last written outside this project, per git blame.

Each line is attributed with `git blame -w -C` (whitespace ignored, code moved or copied between
files followed). Lines whose author is not one of PROJECT_AUTHORS are reported as external.
Blank lines, lone brackets, package/import lines and comments are not counted on either side.

The result measures text attribution only. It is not a legal assessment: a line rewritten by hand
still counts as own here even when its structure follows earlier code.

Usage: measure-inherited-lines.py [--rev REV] [--top N] [--tsv FILE]
"""
import argparse
import re
import subprocess
import sys
from collections import Counter

PROJECT_AUTHORS = {
    '86568548+reiniertutoriales@users.noreply.github.com',
    'reinierlazo2012@gmail.com',
}
SOURCE_ROOT = 'app/src/main/'
EXTENSIONS = ('.kt', '.xml', '.js', '.html', '.css')
SKIP = re.compile(r'^(|[{}()\[\];,]+|(import|package) .*|//.*|/?\*.*|<!--.*|-->)$')


def git(*args):
    return subprocess.run(('git',) + args, check=True, capture_output=True, text=True).stdout


def attribute(rev, path):
    """(external, counted) lines of one file."""
    external = counted = 0
    author = None
    for line in git('blame', '-w', '-C', '--line-porcelain', rev, '--', path).splitlines():
        if line.startswith('author-mail '):
            author = line[len('author-mail '):].strip('<>').lower()
        elif line.startswith('\t'):
            if SKIP.match(line[1:].strip()):
                continue
            counted += 1
            if author not in PROJECT_AUTHORS:
                external += 1
    return external, counted


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--rev', default='HEAD')
    parser.add_argument('--top', type=int, default=15)
    parser.add_argument('--tsv', help='write per-file results here')
    options = parser.parse_args()
    paths = [p for p in git('ls-tree', '-r', '--name-only', options.rev, '--', SOURCE_ROOT).split()
             if p.endswith(EXTENSIONS)]
    rows = [(path,) + attribute(options.rev, path) for path in paths]
    by_kind = Counter()
    for path, external, counted in rows:
        kind = 'layout' if '/res/layout' in path else path.rsplit('.', 1)[1]
        if '/res/values' in path: kind = 'values'
        by_kind[kind, 'external'] += external
        by_kind[kind, 'counted'] += counted
    external = sum(r[1] for r in rows)
    counted = sum(r[2] for r in rows)
    print(f'revision {git("rev-parse", "--short", options.rev).strip()}: {len(rows)} files, '
          f'{external} of {counted} counted lines external ({100 * external / max(counted, 1):.1f} %)')
    for kind in sorted({k for k, _ in by_kind}):
        print(f'  {kind:8} {by_kind[kind, "external"]:6} / {by_kind[kind, "counted"]}')
    print('largest:')
    for path, ext, cnt in sorted(rows, key=lambda r: -r[1])[:options.top]:
        if ext: print(f'  {ext:5} / {cnt:5}  {path}')
    if options.tsv:
        with open(options.tsv, 'w') as out:
            out.write('path\texternal\tcounted\n')
            for row in rows: out.write('\t'.join(map(str, row)) + '\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
