#!/usr/bin/env bash
# Игровые тесты (GameTest): машины ставятся в мир и работают в реальных тиках.
# Использование: scripts/gametest.sh   (из корня проекта, нужен JDK 25 в JAVA_HOME)
set -u
cd "$(dirname "$0")/.."
LOG=build/gametest.log
mkdir -p build
./gradlew runGameTestServer --console=plain > "$LOG" 2>&1
code=$?
grep -E '\[IIS gametest\]|GAME TESTS COMPLETE|required tests passed|failed|Failed' "$LOG" | grep -v '^\s*at ' | tail -30
if [ $code -eq 0 ] && grep -q 'required tests passed' "$LOG"; then echo "GAMETESTS PASSED"; else echo "GAMETESTS FAILED (see $LOG)"; exit 1; fi
