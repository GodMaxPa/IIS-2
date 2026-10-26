# Ideal Industrial 2.0 (ИИС 2.0)

Продолжение **Идеальной Индустриальной Сборки** (Minecraft 1.7.10, GregTech 5-II) на современном Minecraft.
Частный проект для владельца и друзей.

Главный документ проекта — библия (`IIS2_BIBLE.md` в проекте Claude «IIS 2»). Здесь технические детали.

## Платформа (закреплено)

| Компонент | Версия |
|---|---|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.117 (мод работает и на более новых 26.1.2.x) |
| Java | 25 (проверено на Amazon Corretto 25.0.4) |
| Gradle | 9.2.1 (через wrapper) |
| ModDevGradle | 2.0.148 |

## Состояние

См. [`docs/PROGRESS.md`](docs/PROGRESS.md). Сейчас:
- **Кибер — LV III.** Руды и инструменты GT, пар, LV с LV II, LV III и LV IV (25 LV-машин, жидкости GT, капсулы, химия и расплавы, дистиллятор и химическая ванна), электрическая доменная печь, загрязнение, техобслуживание. Электроинструменты LV: бур, цепная пила, электроключ. Дальше — провода 8x+, покрытия, MV.
- **Био — первые LV-органы.** Личный челнок, колония и ткань, ручная биохимия, сеть сосудов с давлением, насосы и клапаны, формовочное гнездо, питательная железа, мембранный фильтр. Дальше — хитин, мутанты, экология.
- **Дуо** — не начато (этап D концепции).

Что перенесено из GT5-II и чем доказано — [`docs/MIGRATION_STATUS.md`](docs/MIGRATION_STATUS.md).

## Команды

```bash
./gradlew build              # сборка + unit-тесты -> build/libs/iis-<версия>.jar
./gradlew test               # только unit-тесты
scripts/smoke-server.sh      # выделенный сервер: мод грузится, /iis, жилы есть, ванильных руд нет
scripts/showcase.sh wall|vein|items # скриншоты клиента под виртуальным дисплеем -> build/showcase/
python3 -I tools/gen/gen_content.py  # пересоздать контент (руды, предметы, инструменты, рецепты) из reference/
./gradlew runClient          # клиент для разработки (нужен графический компьютер)
```

На Windows вместо `./gradlew` — `gradlew.bat`.

## Как поиграть (владельцу)

1. Установить NeoForge **26.1.2.117 или новее** для Minecraft 26.1.2 (установщик с neoforged.net или через лаунчер).
2. Положить `iis-<версия>.jar` в папку `mods` этой версии.
3. В игре: `/iis`, `/iis voltages`, вкладки творческого режима «ИИС 2.0: …» (руды, материалы, инструменты, машины, LV, многоблочные машины, жидкости, Био). Отладка: `/iis scan [радиус]`, `/iis vein <жила> <x y z>`, `/iis selftest`. Операторам: `/iis shuttle` и `/iis shuttle new` (личный челнок Био), `/iis stand` (построить стенд со всем содержимым).
4. Стендовый мир-эталон: `powershell -ExecutionPolicy Bypass -File scripts\stand.ps1` кладёт его в `saves\IIS2_Stand`.

## Документация

- [`AGENTS.md`](AGENTS.md) — правила для ИИ-разработчиков (Claude / Codex)
- [`docs/VISION.md`](docs/VISION.md) — философия и игровые цели
- [`docs/ORIGINAL_MODS.md`](docs/ORIGINAL_MODS.md) — состав оригинальной ИИС и судьба каждого мода
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — устройство кода
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — срезы разработки
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — журнал решений
- [`docs/PROGRESS.md`](docs/PROGRESS.md) — что сделано и что дальше
- [`docs/PORTING_NOTES.md`](docs/PORTING_NOTES.md) — отличия от GT5-II / ИИС 1.7.10
