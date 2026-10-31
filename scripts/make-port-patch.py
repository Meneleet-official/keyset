"""Regenerate ports/<version>/patches from full-file port overrides.

Usage: python scripts/make-port-patch.py <port-version> <edited-generated-dir>
Compares the files in <edited-generated-dir>/{java,resources} with common/src/client and writes
unified diffs into ports/<port-version>/patches/{java,resources}. Only files that differ get a patch.
"""
import difflib
import sys
from pathlib import Path

repository = Path(__file__).resolve().parents[1]


def lines(path: Path):
    return path.read_bytes().decode('utf-8').replace('\r\n', '\n').split('\n')


def main(version: str, edited: Path):
    for kind in ('java', 'resources'):
        base = repository / 'common/src/client' / kind
        source = edited / kind
        if not source.is_dir():
            continue
        out = repository / 'ports' / version / 'patches' / kind
        for file in sorted(p for p in source.rglob('*') if p.is_file()):
            relative = file.relative_to(source).as_posix()
            upstream = base / relative
            if not upstream.is_file():
                continue
            diff = list(difflib.unified_diff(lines(upstream), lines(file), f'a/{relative}', f'b/{relative}', lineterm=''))
            if not diff:
                continue
            target = out / (relative + '.patch')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text('\n'.join(diff) + '\n', encoding='utf-8', newline='\n')
            print('patch', target.relative_to(repository))


if __name__ == '__main__':
    main(sys.argv[1], Path(sys.argv[2]))
