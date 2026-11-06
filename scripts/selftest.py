"""Launch a development client with the Keyset self-test and report whether every screen rendered.

usage: python scripts/selftest.py <minecraft-version> <fabric|neoforge> [--offline] [--shots]

With --shots the game also saves a screenshot of each screen in the two sizes of its real window to
build/selftest-shots/<version>-<loader>/, so the result can be looked at.

The game starts with -Dkeyset.selftest=true: once the title screen is up, Keyset opens each of its screens at many
interface sizes, scrolls them, and exits. The result is read from the game log. This starts a real Minecraft window,
so it needs a desktop session and the version's assets in the Gradle cache.
"""
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

repository = Path(__file__).resolve().parents[1]


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if len(args) != 2 or args[1] not in ('fabric', 'neoforge'):
        raise SystemExit(__doc__)
    version, loader = args
    project = repository if version == '26.3' else repository / 'ports' / version
    run_dir = project / loader / 'run'
    log = run_dir / 'logs' / 'latest.log'
    if log.exists():
        log.unlink()
    command = [str(repository / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))]
    if project != repository:
        command += ['-p', str(project)]
    command += [f':{loader}:runClient']
    if '--offline' in sys.argv:
        command.append('--offline')
    options = '-Dkeyset.selftest=true'
    shots = '--shots' in sys.argv
    if shots:
        options += ' -Dkeyset.selftest.window=true -Dkeyset.selftest.shots=true -Dkeyset.selftest.frames=8'
        shutil.rmtree(run_dir / 'screenshots', ignore_errors=True)
    env = dict(os.environ, JAVA_TOOL_OPTIONS=options)
    started = time.time()
    result = subprocess.run(command, cwd=repository, env=env, capture_output=True, text=True, timeout=900)
    elapsed = time.time() - started
    text = log.read_text(encoding='utf-8', errors='replace') if log.exists() else ''
    opened = len(re.findall(r'KEYSET-SELFTEST open', text))
    done = re.search(r'KEYSET-SELFTEST DONE failures=(\d+)', text)
    failures = re.findall(r'KEYSET-SELFTEST FAIL .*', text)
    keyset_errors = [line for line in text.splitlines()
                     if '/ERROR]' in line and ('keyset' in line.lower() or 'Keyset' in line)]
    crashes = sorted((run_dir / 'crash-reports').glob('crash-*.txt'), key=lambda p: p.stat().st_mtime) \
        if (run_dir / 'crash-reports').is_dir() else []
    fresh_crash = [c for c in crashes if c.stat().st_mtime >= started]
    ok = bool(done) and done.group(1) == '0' and not failures and not keyset_errors and not fresh_crash \
        and result.returncode == 0
    if shots and (run_dir / 'screenshots').is_dir():
        target = repository / 'build' / 'selftest-shots' / f'{version}-{loader}'
        target.mkdir(parents=True, exist_ok=True)
        for shot in (run_dir / 'screenshots').iterdir():
            shutil.copyfile(shot, target / (shot.name + '.png'))
        print(f'screenshots: {target}')
    print(f'{"PASS" if ok else "FAIL"} {version} {loader}: {opened} screens opened in {elapsed:.0f}s, '
          f'exit {result.returncode}, self-test {"finished" if done else "did not finish"}')
    for line in failures + keyset_errors:
        print('  ' + line)
    for crash in fresh_crash:
        print(f'  crash report: {crash}')
    if not done:
        print('\n'.join(result.stdout.splitlines()[-15:]))
        print('\n'.join(result.stderr.splitlines()[-15:]))
    sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
