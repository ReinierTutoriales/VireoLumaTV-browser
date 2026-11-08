#!/usr/bin/env python3
"""Prints a Markdown table of Macrobenchmark results found under the given directory."""
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
rows = []
for path in sorted(root.rglob('*benchmarkData.json')):
    for bench in json.loads(path.read_text()).get('benchmarks', []):
        name = f"{bench.get('className', '').rsplit('.', 1)[-1]}.{bench.get('name', '')}"
        for metric, values in sorted(bench.get('metrics', {}).items()):
            rows.append((name, metric, values.get('median'), values.get('maximum')))
        for metric, values in sorted(bench.get('sampledMetrics', {}).items()):
            rows.append((name, metric, values.get('P50'), values.get('P90')))

if not rows:
    print('No Macrobenchmark results were produced.')
    sys.exit(0)

print('| Benchmark | Metric | Median / P50 | Max / P90 |')
print('| --- | --- | ---: | ---: |')
for name, metric, middle, high in rows:
    fmt = lambda v: '-' if v is None else f'{v:.1f}'
    print(f'| {name} | {metric} | {fmt(middle)} | {fmt(high)} |')
