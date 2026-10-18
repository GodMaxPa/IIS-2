#!/usr/bin/env bash
# Дымовой тест: запускает выделенный сервер с модом, ждёт загрузки мира,
# выполняет /iis и /iis voltages, останавливает сервер и проверяет лог.
# Использование: scripts/smoke-server.sh   (из корня проекта, нужен JDK 25 в JAVA_HOME)
set -u
cd "$(dirname "$0")/.."
mkdir -p run
echo "eula=true" > run/eula.txt
# Мир для теста не сохраняем между запусками
rm -rf run/world
LOG=build/smoke-server.log
mkdir -p build
: > "$LOG"

FIFO=$(mktemp -u); mkfifo "$FIFO"
( ./gradlew runServer --no-daemon --console=plain < "$FIFO" > "$LOG" 2>&1 ) &
GRADLE_PID=$!
exec 3>"$FIFO"

deadline=$((SECONDS + 900))
until grep -q 'Done (' "$LOG"; do
  if ! kill -0 $GRADLE_PID 2>/dev/null || [ $SECONDS -gt $deadline ]; then
    echo "FAIL: server did not start"; tail -50 "$LOG"; exit 1
  fi
  sleep 3
done
echo "iis" >&3
echo "iis voltages" >&3
echo "iis selftest" >&3
for i in $(seq 1 20); do grep -qE 'SELFTEST (PASSED|FAILED)' "$LOG" && break; sleep 2; done
echo "iis scan 4" >&3
# скан 81 чанка с генерацией занимает время
for i in $(seq 1 60); do grep -q 'IIS scan:' "$LOG" && break; sleep 3; done
sleep 2
echo "stop" >&3
wait $GRADLE_PID
exec 3>&-; rm -f "$FIFO"

ok=1
grep -q '\[IIS\] Ideal Industrial 2.0 loaded' "$LOG" && echo "OK: mod loaded" || { echo "FAIL: load marker missing"; ok=0; }
grep -q 'ZPM — 131072 EU/t' "$LOG" && echo "OK: /iis voltages works" || { echo "FAIL: /iis voltages output missing"; ok=0; }
grep -E '\]:   (PASS|FAIL) ' "$LOG" | sed 's/.*]:  /   /'
if grep -q 'SELFTEST PASSED' "$LOG"; then echo "OK: selftest"; else echo "FAIL: selftest"; ok=0; fi
if grep -qE 'IIS scan: .*IIS ore blocks=[1-9]' "$LOG"; then echo "OK: GT ore veins generated"; else echo "FAIL: no IIS ores found by /iis scan"; ok=0; fi
if grep -qE 'IIS scan: .*small ore blocks=[1-9]' "$LOG"; then echo "OK: small ores generated"; else echo "FAIL: no small ores"; ok=0; fi
if grep -qE 'IIS scan: .*vanilla ore blocks=0$' "$LOG"; then echo "OK: no vanilla ores"; else echo "FAIL: vanilla ores present"; grep 'IIS scan:' "$LOG"; ok=0; fi
grep -A100 'IIS scan:' "$LOG" | grep -E '^\[.*\]:   ' | sed 's/.*]:  /   /' | head -40
ERRS=$(grep -iE 'Exception|ERROR' "$LOG" | grep -vE 'yggdrasil|minecraftservices|server.properties|NoSuchFileException' || true)
[ -n "$ERRS" ] && { echo "WARN: errors in log:"; echo "$ERRS" | head -20; }
[ $ok = 1 ] && echo "SMOKE TEST PASSED" || { echo "SMOKE TEST FAILED"; exit 1; }
