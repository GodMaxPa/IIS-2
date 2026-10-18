#!/usr/bin/env bash
# Скриншоты для владельца без его участия: сервер готовит мир, клиент под виртуальным дисплеем
# (Xvfb + программный OpenGL Mesa) загружает его и снимает экран.
#   scripts/showcase.sh wall   — стена из всех блоков руд IIS в небе
#   scripts/showcase.sh vein   — жилы, построенные алгоритмом GT5 в небе (форма слоёв видна целиком)
# Результат: build/showcase/<режим>.png. Нужен JDK 25 в JAVA_HOME.
set -u
MODE=${1:-wall}
cd "$(dirname "$0")/.."
OUT=build/showcase; mkdir -p "$OUT" run
echo "eula=true" > run/eula.txt
rm -rf run/world run/saves/showcase
LOG="$OUT/server-$MODE.log"

FIFO=$(mktemp -u); mkfifo "$FIFO"
( ./gradlew runServer --no-daemon --console=plain < "$FIFO" > "$LOG" 2>&1 ) & PID=$!
exec 3>"$FIFO"
until grep -q 'Done (' "$LOG"; do kill -0 $PID 2>/dev/null || { echo "server failed"; tail -30 "$LOG"; exit 1; }; sleep 3; done

common() {
  echo 'gamerule advance_time false'; echo 'gamerule advance_weather false'; echo 'gamerule respawn_radius 0'
  echo 'time set noon'; echo 'weather clear'
}

if [ "$MODE" = wall ]; then
  { common; python3 -I - <<'PY'
import json
mats = json.load(open('src/main/resources/iis/materials.json', encoding='utf-8'))
Y, Z, W = 200, 16, 14
print('forceload add -32 -32 32 32')
print(f'fill -20 {Y-1} -3 20 {Y-1} {Z} minecraft:smooth_stone')
print(f'setworldspawn 0 {Y} 0 0 0')
rows = 0
for host, prefix in (('stone', ''), ('deepslate', 'deepslate_')):
    ms = [m for m in mats if host in m['hosts']]
    for i, m in enumerate(ms):
        print(f"setblock {-W // 2 + i % W} {Y + 12 - rows - i // W} {Z} iis:{prefix}ore_{m['id']}")
    rows += (len(ms) + W - 1) // W
print('save-all flush')
PY
  } >&3
  sleep 8
else
  # Жила медного типа ИИС, построенная алгоритмом GT5 в небе (команда /iis vein), вид сбоку и сверху
  { common
    echo 'forceload add -64 -64 64 64'
    echo 'fill -3 209 -48 3 209 -42 minecraft:glass'
    echo 'iis vein copper 0 200 0'
    echo 'iis vein iron 0 200 60'
    echo 'setworldspawn 0 210 -45 0 25'
    echo 'save-all flush'
  } >&3
  sleep 10
fi
echo stop >&3; wait $PID; exec 3>&-; rm -f "$FIFO"
mkdir -p run/saves && cp -r run/world run/saves/showcase

cat > run/options.txt <<'OPT'
onboardAccessibility:false
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
pauseOnLostFocus:false
fov:0.25
guiScale:2
renderDistance:8
lang:ru_ru
OPT
export LIBGL_ALWAYS_SOFTWARE=1
xvfb-run -a -s "-screen 0 1600x900x24" bash -c '
  ./gradlew runClient -Pshowcase --no-daemon --console=plain > '"$OUT"'/client-'"$MODE"'.log 2>&1 &
  for i in $(seq 1 120); do grep -qE "Changing view distance|joined the game|Loaded [0-9]+ advancements" '"$OUT"'/client-'"$MODE"'.log && break; sleep 5; done
  sleep 45
  import -window root '"$OUT"'/'"$MODE"'.png
  pkill -f "net.neoforged.devlaunch"; sleep 5
'
ls -la "$OUT/$MODE.png" 2>/dev/null || { echo "no screenshot"; tail -40 "$OUT/client-$MODE.log"; exit 1; }
