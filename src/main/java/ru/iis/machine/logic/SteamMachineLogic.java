package ru.iis.machine.logic;

/**
 * Рабочий цикл паровой машины GT5-II без зависимостей от Minecraft.
 * Перенесено из GT_MetaTileEntity_BasicMachine#onPostTick и GT_MetaTileEntity_BasicMachine_Bronze (GT5-II @ 00021e3).
 *
 * Правила оригинала:
 *  - пар хранится в «единицах»: 2 л пара = 1 единица, запас до 16000 единиц (32000 л);
 *  - рецепт расходует столько единиц за тик, сколько у него EU/t;
 *  - новый рецепт начинается, только если в запасе не меньше 500 единиц (половина «минимального запаса» 1000);
 *  - если пара не хватило, машина «заикается»: прогресс сбрасывается, и 100 тиков она простаивает;
 *  - после каждой операции машина должна выпустить пар через сторону выхода: пока перед ней
 *    твёрдый блок, следующий рецепт не начнётся; существа перед выходом получают ожог.
 */
public final class SteamMachineLogic {
    public static final int MAX_STEAM_UNITS = 16000;
    public static final int MIN_UNITS_TO_START = 500;
    public static final int STUTTER_TICKS = 100;

    public interface Env {
        /** Перед стороной выхода нет блока с хитбоксом. */
        boolean ventClear();

        /** Выпуск пара: звук, частицы, ожог существ перед выходом. */
        void vent();

        /**
         * Найти рецепт, проверить место для результата и забрать ингредиенты.
         * @return {eut, duration} уже с учётом уровня машины, или null, если начать нельзя
         */
        int[] tryStart();

        /** Операция завершена: положить результат. */
        void finish();
    }

    /** Запас пара, единицы (1 единица = 2 л). */
    public int steamUnits;
    public int progress;
    public int maxProgress;
    public int eut;
    public boolean needsVenting;
    public boolean stuttering;
    public boolean active;

    /** @param checkRecipe true, если пора искать рецепт (инвентарь изменился, операция только что закончилась или по таймеру) */
    public void tick(boolean checkRecipe, Env env) {
        boolean succeeded = false;
        if (maxProgress > 0) {
            active = true;
            if (progress < 0 || drain(eut)) {
                if (++progress >= maxProgress) {
                    env.finish();
                    eut = 0;
                    progress = 0;
                    maxProgress = 0;
                    stuttering = false;
                    succeeded = true;
                    needsVenting = true;
                }
                if (progress > 5) stuttering = false;
            } else if (!stuttering) {
                progress = -STUTTER_TICKS;
                stuttering = true;
            }
        } else {
            active = false;
        }
        // allowToCheckRecipe: сначала выпустить пар
        if (needsVenting && env.ventClear()) {
            env.vent();
            needsVenting = false;
        }
        if (!needsVenting && maxProgress <= 0 && (checkRecipe || succeeded) && steamUnits >= MIN_UNITS_TO_START) {
            int[] r = env.tryStart();
            if (r != null) {
                eut = r[0];
                maxProgress = Math.max(1, r[1]);
                progress = 0;
            }
        }
    }

    private boolean drain(int units) {
        if (steamUnits - units >= 0) {
            steamUnits -= units;
            return true;
        }
        return false;
    }

    /** Принять пар (литры). Возвращает принятые литры (всегда чётное число, как в GT5). */
    public int fillSteam(int liters) {
        if (liters <= 1) return 0;
        int units = Math.min(liters / 2, MAX_STEAM_UNITS - steamUnits);
        if (units <= 0) return 0;
        steamUnits += units;
        return units * 2;
    }

    /** Бронза: EU/t рецепта, время ×2. Сталь (высокое давление): EU/t ×3, время как в рецепте. */
    public static int[] scale(boolean highPressure, int recipeEut, int recipeDuration) {
        return highPressure ? new int[]{3 * recipeEut, recipeDuration} : new int[]{recipeEut, 2 * recipeDuration};
    }

    /** Паровые машины GT5 берут только рецепты до 32 EU/t (напряжение LV). */
    public static final int MAX_RECIPE_EUT = 32;
}
