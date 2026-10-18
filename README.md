# Ideal Industrial 2.0 (ИИС 2.0)

Продолжение **Идеальной Индустриальной Сборки** (Minecraft 1.7.10, GregTech 5-II) на современном Minecraft.
Частный проект для владельца и друзей.

Главный документ проекта — библия (`IIS2_BIBLE.md` в проекте Claude «IIS 2»). Здесь технические детали.

## Платформа (закреплено)

| Компонент | Версия |
|---|---|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.122 |
| Java | 25 (проверено на Amazon Corretto 25.0.4) |
| Gradle | 9.2.1 (через wrapper) |
| ModDevGradle | 2.0.148 |

## Состояние

См. [`docs/PROGRESS.md`](docs/PROGRESS.md). Сейчас: **Срез 2 «Камень и бронза»** — руды, малые руды, формы предметов, инструменты GT и ручная переработка до бронзы готовы.

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

1. Установить NeoForge **26.1.2.122** для Minecraft 26.1.2 (установщик с neoforged.net или через лаунчер).
2. Положить `iis-<версия>.jar` в папку `mods` этой версии.
3. В игре: `/iis`, `/iis voltages`, вкладка творческого режима «ИИС 2.0: Руды». Отладка: `/iis scan [радиус]`, `/iis vein <жила> <x y z>`, `/iis selftest`.

## Документация

- [`AGENTS.md`](AGENTS.md) — правила для ИИ-разработчиков (Claude / Codex)
- [`docs/VISION.md`](docs/VISION.md) — философия и игровые цели
- [`docs/ORIGINAL_MODS.md`](docs/ORIGINAL_MODS.md) — состав оригинальной ИИС и судьба каждого мода
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — устройство кода
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — срезы разработки
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — журнал решений
- [`docs/PROGRESS.md`](docs/PROGRESS.md) — что сделано и что дальше
- [`docs/PORTING_NOTES.md`](docs/PORTING_NOTES.md) — отличия от GT5-II / ИИС 1.7.10
