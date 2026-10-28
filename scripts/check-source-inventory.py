"""Check tracked-file coverage; classifications do not establish independent authorship."""
import csv
from collections import Counter
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
INVENTORY = ROOT / 'docs/audit/SOURCE_INVENTORY.tsv'
LABELS = {'PROPIO', 'REESCRITO', 'REGENERADO', 'MODIFICADO', 'IDENTICO', 'IDENTICO_BLOB', 'ELIMINADO'}


def main():
    with INVENTORY.open(encoding='utf-8', newline='') as source:
        rows = list(csv.DictReader(source, delimiter='\t'))
    tracked = set(subprocess.check_output(
        ['git', 'ls-files', '-z'], cwd=ROOT).decode('utf-8').split('\0')) - {''}
    counts = Counter(row['ruta'] for row in rows)
    errors = [f'Duplicate path: {path}' for path, count in counts.items() if count > 1]
    errors.extend(f'Unclassified tracked file: {path}' for path in sorted(tracked - counts.keys()))
    for row in rows:
        path, label = row['ruta'], row['clasificacion']
        if label not in LABELS:
            errors.append(f'Unknown classification: {path}: {label}')
        if path in tracked and label == 'ELIMINADO':
            errors.append(f'Tracked file marked deleted: {path}')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'Inventory covers {len(tracked)}/{len(tracked)} tracked files; authorship remains a separate review.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
