#!/usr/bin/env bash
# Скриншоты для владельца без его участия: сервер готовит мир, клиент под виртуальным дисплеем
# (Xvfb + программный OpenGL Mesa) загружает его и снимает экран.
#   scripts/showcase.sh wall   — стена из всех блоков руд IIS в небе
#   scripts/showcase.sh items  — рамки с предметами: цепочка руда -> бронза, инструменты GT
#   scripts/showcase.sh vein   — жилы, построенные алгоритмом GT5 в небе (форма слоёв видна целиком)
#   scripts/showcase.sh steam  — все машины эпохи пара в работе: котлы, паровые машины, трубы, доменная печь
#   scripts/showcase.sh steamgui — GUI парового дробителя и JEI (нужен xdotool)
#   scripts/showcase.sh lv     — эпоха LV: 13 машин на оловянном кабеле от паровой турбины, генераторы, буферы, коксовая печь
#   scripts/showcase.sh lvgui  — GUI дробителя LV и JEI (нужен xdotool)
#   scripts/showcase.sh ebf    — электрическая доменная печь в работе; ebfgui — её экран и JEI
#   scripts/showcase.sh wrench — сетка гаечного ключа на грани машины
#   scripts/showcase.sh map    — JourneyMap: миникарта и полная карта с сеткой рудных чанков
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
sleep 5  # консоль сервера иногда не готова сразу после «Done»

common() {
  echo 'gamerule advance_time false'; echo 'gamerule advance_weather false'; echo 'gamerule respawn_radius 0'
  echo 'time set noon'; echo 'weather clear'
}

if [ "$MODE" = items ]; then
  # Рамки с предметами: цепочка руда -> бронза и инструменты GT
  { common; python3 -I - <<'PY'
rows = [
  ['ore_chalcopyrite', 'crushed_chalcopyrite', 'purified_chalcopyrite', 'impure_dust_chalcopyrite', 'dust_chalcopyrite',
   'minecraft:copper_ingot', 'dust_copper', 'ingot_tin', 'dust_tin', 'dust_bronze', 'ingot_bronze', 'plate_bronze'],
  ['rod_bronze', 'bolt_bronze', 'screw_bronze', 'ring_bronze', 'pickaxe_head_bronze', 'axe_head_bronze', 'saw_head_bronze',
   'sword_head_bronze', 'small_ore_copper', 'small_ore_ruby', 'gem_ruby', 'gem_sapphire'],
  ['mortar_flint', 'mortar_bronze', 'hammer_bronze', 'file_bronze', 'saw_bronze', 'wrench_bronze', 'screwdriver_bronze',
   'knife_bronze', 'pickaxe_bronze', 'axe_bronze', 'shovel_bronze', 'sword_bronze'],
]
Y, Z = 200, 6
print('forceload add -32 -32 32 32')
print(f'fill -10 {Y-1} -3 10 {Y-1} {Z+1} minecraft:smooth_stone')
print(f'fill -8 {Y} {Z+1} 8 {Y+4} {Z+1} minecraft:white_concrete')
print(f'setworldspawn 0 {Y} 0 0 10')
for r, row in enumerate(rows):
    for i, it in enumerate(row):
        iid = it if ':' in it else 'iis:' + it
        x = 6 - i  # игрок смотрит на +Z, поэтому +X у него слева
        print(f'summon minecraft:item_frame {x} {Y + 3 - r} {Z} {{Facing:2b,Fixed:1b,Invisible:0b,Item:{{id:"{iid}",count:1}}}}')
print('save-all flush')
PY
  } >&3
  sleep 8
elif [ "$MODE" = wall ]; then
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
elif [ "$MODE" = steam ] || [ "$MODE" = steamgui ]; then
  { common; python3 -I - "$MODE" <<'PY'
import sys
mode = sys.argv[1]
Y = 200
print('forceload add -32 -32 32 32')
print(f'fill -12 {Y-1} -14 10 {Y-1} 6 minecraft:smooth_stone')
water = '{id:"minecraft:water",amount:16000}'
steam = 'steam:{stacks:[{id:"iis:steam",amount:32000}]}'
def boiler(x, bid, extra):
    print(f'setblock {x} {Y} 0 iis:{bid}[facing=north]')
    print(f'data merge block {x} {Y} 0 {{water:{{stacks:[{water}]}},temperature:500{extra}}}')
boiler(6, 'boiler_coal_bronze', ',Items:[{Slot:2b,id:"minecraft:coal",count:64}]')
boiler(5, 'boiler_coal_steel', ',Items:[{Slot:2b,id:"minecraft:coal",count:64}]')
boiler(4, 'boiler_lava_steel', ',Items:[{Slot:2b,id:"minecraft:lava_bucket",count:1}]')
boiler(3, 'boiler_solar_bronze', '')
for x, size in ((6, 'small'), (5, 'medium'), (4, 'large')):
    mat = 'bronze' if x == 6 else 'steel'
    print(f'setblock {x} {Y+1} 0 iis:pipe_{size}_{mat}[down=true]')
inputs = {
    'macerator': '{Slot:0b,id:"iis:ore_chalcopyrite",count:64}',
    'forge_hammer': '{Slot:0b,id:"minecraft:stone",count:64}',
    'compressor': '{Slot:0b,id:"minecraft:ice",count:64}',
    'extractor': '{Slot:0b,id:"minecraft:slime_ball",count:64}',
    'alloy_smelter': '{Slot:0b,id:"minecraft:copper_ingot",count:63},{Slot:1b,id:"iis:ingot_tin",count:21}',
    'furnace': '{Slot:0b,id:"minecraft:sand",count:64}',
}
for i, (m, items) in enumerate(inputs.items()):
    x = 1 - i
    for y, tier in ((Y, 'bronze'), (Y + 1, 'steel')):
        print(f'setblock {x} {y} 0 iis:steam_{m}_{tier}[facing=north]')
        print(f'data merge block {x} {y} 0 {{Items:[{items}],{steam}}}')
# Кирпичная доменная печь: куб 3x4x3 из огнеупорных кирпичей, полая шахта, контроллер в передней стенке
print(f'fill -9 {Y-1} 0 -7 {Y+2} 2 iis:firebrick_casing')
print(f'fill -8 {Y} 1 -8 {Y+2} 1 minecraft:air')
print(f'setblock -8 {Y} 0 iis:bricked_blast_furnace[facing=north]')
print(f'data merge block -8 {Y} 0 {{Items:[{{Slot:0b,id:"minecraft:iron_ingot",count:10}},{{Slot:1b,id:"minecraft:coal",count:40}}]}}')
if mode == 'steam':
    print(f'setworldspawn -1 {Y} -11 0 12')
else:
    print(f'setworldspawn 1 {Y} -1 0 60')
print('save-all flush')
PY
  } >&3
  sleep 12
elif [ "$MODE" = lv ] || [ "$MODE" = lvgui ]; then
  { common; python3 -I - "$MODE" <<'PY'
import sys
mode = sys.argv[1]
Y = 200
print('forceload add -32 -32 32 32')
print(f'fill -16 {Y-1} -14 12 {Y-1} 8 minecraft:smooth_stone')
water = '{id:"minecraft:water",amount:16000}'
machines = [  # (машина, предметы), макератор стоит на x=1 (на него смотрит режим lvgui)
    ('ore_washer', '{Slot:0b,id:"iis:crushed_chalcopyrite",count:64}'),
    ('mixer', '{Slot:0b,id:"iis:dust_copper",count:48},{Slot:1b,id:"iis:dust_tin",count:16}'),
    ('assembler', '{Slot:0b,id:"iis:wire_tin_1x",count:64}'),
    ('polarizer', '{Slot:0b,id:"iis:rod_iron",count:64}'),
    ('lathe', '{Slot:0b,id:"minecraft:iron_ingot",count:64}'),
    ('bender', '{Slot:0b,id:"minecraft:iron_ingot",count:64}'),
    ('wiremill', '{Slot:0b,id:"iis:ingot_tin",count:64}'),
    ('macerator', '{Slot:0b,id:"iis:ore_chalcopyrite",count:64}'),
    ('electric_furnace', '{Slot:0b,id:"minecraft:sand",count:64}'),
    ('alloy_smelter', '{Slot:0b,id:"minecraft:copper_ingot",count:63},{Slot:1b,id:"iis:ingot_tin",count:21}'),
    ('compressor', '{Slot:0b,id:"minecraft:ice",count:64}'),
    ('extractor', '{Slot:0b,id:"minecraft:slime_ball",count:64}'),
    ('forge_hammer', '{Slot:0b,id:"minecraft:stone",count:64}'),
]
x0 = -6
for i, (m, items) in enumerate(machines):
    x = x0 + i
    print(f'setblock {x} {Y} 0 iis:{m}_lv[facing=north]')
    tank = f',tank:{{stacks:[{water}]}}' if m in ('ore_washer', 'mixer') else ''
    print(f'data merge block {x} {Y} 0 {{Items:[{items}],eu:2048L{tank}}}')
# кабель за машинами (z=1): все соединены с соседями и с машиной спереди (север)
xe = x0 + len(machines)
for x in range(x0, xe + 1):
    west = 'true' if x > x0 else 'false'
    north = 'true' if x < xe else 'false'
    print(f'setblock {x} {Y} 1 iis:cable_tin_1x[west={west},east=true,north={north}]')
print(f'setblock {xe + 1} {Y} 1 iis:steam_turbine_lv[facing=west]')
print(f'data merge block {xe + 1} {Y} 1 {{tank:{{stacks:[{{id:"iis:steam",amount:24000}}]}}}}')
# витрина: генераторы, буферы, трансформатор, провода разных материалов
for j, b in enumerate(['diesel_generator_lv[facing=north]', 'gas_turbine_lv[facing=north]', 'battery_buffer_1x1_lv[facing=north]',
                       'battery_buffer_2x2_lv[facing=north]', 'transformer_lv[facing=north]', 'transformer_lv[facing=north,step_up=true]',
                       'casing_lv', 'hull_lv']):
    print(f'setblock {x0 + j} {Y} -3 iis:{b}')
for j, w in enumerate(['wire_red_alloy_1x', 'wire_tin_2x', 'wire_lead_4x', 'wire_copper_1x', 'cable_tin_1x', 'cable_lead_2x',
                       'cable_copper_4x', 'cable_annealed_copper_1x']):
    print(f'setblock {x0 + 9 + j} {Y} -3 iis:{w}[east=true,west=true]')
# коксовая печь 3x3x3 с пустой серединой и контроллером спереди
print(f'fill -12 {Y} -1 -10 {Y+2} 1 iis:coke_oven_bricks')
print(f'setblock -11 {Y+1} 0 minecraft:air')
print(f'setblock -11 {Y+1} -1 iis:coke_oven[facing=north]')
print(f'data merge block -11 {Y+1} -1 {{Items:[{{Slot:0b,id:"minecraft:coal",count:64}}]}}')
# каучуковое дерево
print(f'setblock 9 {Y-1} -4 minecraft:dirt')
print(f'place feature iis:rubber_tree 9 {Y} -4')
if mode == 'lv':
    print(f'setworldspawn -1 {Y+1} -8 0 20')
else:
    print(f'setworldspawn 1 {Y} -1 0 60')
print('save-all flush')
PY
  } >&3
  sleep 12
elif [ "$MODE" = ebf ] || [ "$MODE" = ebfgui ]; then
  { common; python3 -I - "$MODE" <<'PY'
import sys
mode = sys.argv[1]
Y = 200
print('forceload add -32 -32 32 32')
print(f'fill -8 {Y-1} -8 8 {Y-1} 8 minecraft:smooth_stone')
# печь: середина (0,·,1), контроллер (0,Y,0) лицом на север
print(f'fill -1 {Y} 0 1 {Y} 2 iis:heat_proof_casing')
print(f'fill -1 {Y+1} 0 1 {Y+2} 2 iis:coil_cupronickel')
print(f'fill 0 {Y+1} 1 0 {Y+2} 1 minecraft:air')
print(f'fill -1 {Y+3} 0 1 {Y+3} 2 iis:heat_proof_casing')
print(f'setblock 0 {Y+3} 1 iis:hatch_muffler_lv[facing=up]')
print(f'setblock -1 {Y} 1 iis:hatch_energy_lv[facing=west]')
print(f'setblock 0 {Y} 2 iis:hatch_energy_lv[facing=south]')
print(f'setblock 1 {Y} 1 iis:hatch_input_bus_lv[facing=east]')
print(f'setblock -1 {Y} 2 iis:hatch_output_bus_lv[facing=west]')
print(f'setblock 1 {Y} 0 iis:hatch_maintenance[facing=north]')
print(f'setblock 0 {Y} 0 iis:electric_blast_furnace[facing=north]')
print(f'data merge block 0 {Y} 0 {{maintenance:63}}')
print(f'data merge block 1 {Y} 1 {{Items:[{{Slot:0b,id:"iis:gem_ruby",count:64}}]}}')
print(f'data merge block -1 {Y} 1 {{eu:768L}}')
print(f'data merge block 0 {Y} 2 {{eu:768L}}')
# питание: две паровые турбины прямо к люкам
print(f'setblock -2 {Y} 1 iis:steam_turbine_lv[facing=east]')
print(f'data merge block -2 {Y} 1 {{tank:{{stacks:[{{id:"iis:steam",amount:24000}}]}}}}')
print(f'setblock 0 {Y} 3 iis:steam_turbine_lv[facing=north]')
print(f'data merge block 0 {Y} 3 {{tank:{{stacks:[{{id:"iis:steam",amount:24000}}]}}}}')
if mode == 'ebf':
    print(f'setworldspawn 3 {Y+1} -5 25 15')
else:
    print(f'setworldspawn 0 {Y} -2 0 37')
print('save-all flush')
PY
  } >&3
  sleep 12
elif [ "$MODE" = map ]; then
  # Карта JourneyMap со слоем «сетка рудных чанков» (спавн на поверхности)
  { common
    echo 'setworldspawn 8 100 8'
    echo 'save-all flush'
  } >&3
  sleep 8
elif [ "$MODE" = wrench ]; then
  # Сетка ключа GT на грани буфера (ключ в руку кладёт повторяющийся командный блок)
  { common
    echo 'forceload add -16 -16 16 16'
    echo 'fill -6 199 -6 6 199 6 minecraft:smooth_stone'
    echo 'setblock 0 200 0 minecraft:smooth_stone'
    echo 'setblock 0 201 0 iis:battery_buffer_2x2_lv[facing=north]'
    echo 'setblock 1 201 0 iis:transformer_lv[facing=north]'
    echo 'setblock -1 201 0 iis:macerator_lv[facing=north]'
    echo 'setblock 0 198 0 minecraft:repeating_command_block{auto:1b,Command:"item replace entity @a weapon.mainhand with iis:wrench_steel"}'
    echo 'setworldspawn 0 200 -2 0 0'
    echo 'save-all flush'
  } >&3
  sleep 10
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
version:4790
onboardAccessibility:false
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
pauseOnLostFocus:false
fov:0.25
guiScale:2
renderDistance:8
lang:ru_ru
key_key.use:key.keyboard.v
OPT
export LIBGL_ALWAYS_SOFTWARE=1
xvfb-run -a -s "-screen 0 1600x900x24" bash -c '
  ./gradlew runClient -Pshowcase --no-daemon --console=plain > '"$OUT"'/client-'"$MODE"'.log 2>&1 &
  for i in $(seq 1 120); do grep -qE "Changing view distance|joined the game|Loaded [0-9]+ advancements" '"$OUT"'/client-'"$MODE"'.log && break; sleep 5; done
  sleep 45
  if [ "'"$MODE"'" = steamgui ] || [ "'"$MODE"'" = lvgui ] || [ "'"$MODE"'" = ebfgui ]; then
    xdotool key v; sleep 12  # «использовать» на V: щелчок мыши под Xvfb не доходит до игры
    import -window root '"$OUT"'/'"$MODE"'-screen.png
    xdotool mousemove 746 350; sleep 2; xdotool key u; sleep 8
    import -window root '"$OUT"'/'"$MODE"'-jei.png
    xdotool key Escape; sleep 1; xdotool key Escape; sleep 1
  fi
  if [ "'"$MODE"'" = map ]; then
    sleep 40; import -window root '"$OUT"'/map-minimap.png
    xdotool key j; sleep 6; xdotool key Escape; sleep 3; xdotool key j; sleep 10
    import -window root '"$OUT"'/map-full.png
    xdotool key Escape; sleep 2
  fi
  import -window root '"$OUT"'/'"$MODE"'.png
  pkill -f "net.neoforged.devlaunch"; sleep 5
'
ls -la "$OUT/$MODE.png" 2>/dev/null || { echo "no screenshot"; tail -40 "$OUT/client-$MODE.log"; exit 1; }
