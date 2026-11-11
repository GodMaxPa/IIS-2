"""Smoke test of the dedicated dev server on any OS (Windows twin of scripts/smoke-server.sh, D147).

Starts `gradlew runServer` on a fresh world, waits for it to load, runs /iis, /iis voltages, /iis selftest, /tick query and /iis scan 4,
stops the server and checks the log: mod loaded, selftest passed, GT ores generated and vanilla ores absent, no broken data files.
Run from the repository root with JDK 25 in JAVA_HOME:   python scripts/smoke_server.py   (exit code 0 = passed; log in build/smoke-server.log)
"""
import os
import re
import shutil
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG = os.path.join(ROOT, 'build', 'smoke-server.log')


def read_log():
    with open(LOG, 'rb') as f:
        return f.read().decode('utf-8', 'replace')


def wait(proc, pattern, timeout, since=0):
    """Wait until the log (from offset `since`) matches; returns the match or None when the server died or time ran out."""
    end = time.time() + timeout
    while time.time() < end:
        m = re.search(pattern, read_log()[since:])
        if m:
            return m
        if proc.poll() is not None:
            return re.search(pattern, read_log()[since:])
        time.sleep(1)
    return None


def main():
    os.makedirs(os.path.join(ROOT, 'run'), exist_ok=True)
    os.makedirs(os.path.join(ROOT, 'build'), exist_ok=True)
    with open(os.path.join(ROOT, 'run', 'eula.txt'), 'w') as f:
        f.write('eula=true\n')
    shutil.rmtree(os.path.join(ROOT, 'run', 'world'), ignore_errors=True)   # the test world is not kept between runs
    cmd = ['cmd', '/c', os.path.join(ROOT, 'gradlew.bat')] if os.name == 'nt' else ['./gradlew']
    out = open(LOG, 'wb')
    proc = subprocess.Popen(cmd + ['runServer', '--no-daemon', '--console=plain'], cwd=ROOT, stdin=subprocess.PIPE, stdout=out, stderr=subprocess.STDOUT)
    started = time.time()

    def send(line):
        proc.stdin.write((line + '\n').encode())
        proc.stdin.flush()

    ok = True
    if not wait(proc, r'Done \(', 900):
        print('FAIL: server did not start')
        print(read_log()[-3000:])
        proc.kill()
        return 1
    print(f'server started in {time.time() - started:.0f} s')
    send('iis')
    send('iis voltages')
    send('iis selftest')
    wait(proc, r'SELFTEST (PASSED|FAILED)', 60)
    time.sleep(15)                      # idle ticks for the tick statistics
    send('tick query')
    time.sleep(2)
    mark = len(read_log())
    send('iis scan 4')                  # 81 chunks with generation
    wait(proc, r'IIS scan:', 240, mark)
    time.sleep(2)
    send('tick query')
    time.sleep(2)
    send('stop')
    try:
        proc.wait(180)
    except subprocess.TimeoutExpired:
        print('FAIL: server did not stop')
        proc.kill()
        ok = False
    out.close()
    log = read_log()

    def check(cond, good, bad):
        nonlocal ok
        print(('OK: ' + good) if cond else ('FAIL: ' + bad))
        ok = ok and bool(cond)

    check('[IIS] Ideal Industrial 2.0 loaded' in log, 'mod loaded', 'load marker missing')
    check('ZPM — 131072 EU/t' in log or 'ZPM' in log and '131072 EU/t' in log, '/iis voltages works', '/iis voltages output missing')
    for line in re.findall(r'\]:   ((?:PASS|FAIL) .*)', log):
        print('   ' + line.strip())
    check('SELFTEST PASSED' in log, 'selftest', 'selftest failed')
    scan = re.search(r'IIS scan: .*', log)
    check(scan and re.search(r'IIS ore blocks=[1-9]', scan.group(0)), 'GT ore veins generated', 'no IIS ores found by /iis scan')
    check(scan and re.search(r'small ore blocks=[1-9]', scan.group(0)), 'small ores generated', 'no small ores')
    check(scan and re.search(r'vanilla ore blocks=0\s*$', scan.group(0).strip()), 'no vanilla ores', 'vanilla ores present: ' + (scan.group(0) if scan else ''))
    for line in re.findall(r'(Average time per tick: [^\r\n]*|P50: [^\r\n]*)', log):
        print('   tick: ' + line.strip())
    broken = re.findall(r"(Couldn't parse data file '[^']+'[^\r\n]{0,200}|Parsing error loading recipe [^\r\n]{0,200}|Couldn't load tag [^\r\n]{0,200})", log)
    check(not broken, 'no broken data files', f'{len(broken)} broken data files')
    for b in broken[:20]:
        print('   ' + b)
    errs = [l for l in log.splitlines() if re.search(r'Exception|/ERROR\]', l)
            and not re.search(r'yggdrasil|minecraftservices|server\.properties|NoSuchFileException', l)]
    if errs:
        print(f'WARN: {len(errs)} error lines in the log, first ones:')
        seen = set()
        for l in errs:
            k = re.sub(r'\d+', 'N', l)[:160]
            if k not in seen:
                seen.add(k)
                print('   ' + l[:260])
            if len(seen) >= 25:
                break
    print('SMOKE TEST PASSED' if ok else 'SMOKE TEST FAILED')
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
