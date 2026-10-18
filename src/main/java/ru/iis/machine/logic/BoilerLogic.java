package ru.iis.machine.logic;

/**
 * Логика малых паровых котлов GT5-II без зависимостей от Minecraft.
 * Перенесено из GT_MetaTileEntity_Boiler{,_Bronze,_Steel,_Lava,_Solar}#onPostTick (GT5-II @ 00021e3).
 *
 * Порядок действий за тик тот же, что в оригинале:
 *  1) температура не ниже 20; раз в (lossTimer+1) тиков остывание;
 *  2) отдача пара соседям (делает блок, через {@link Env#pushSteam()});
 *  3) раз в waterPeriod тиков при температуре > 100: расход воды и выработка пара;
 *     если воды не было, а потом она появилась, котёл взрывается (сохранённая особенность GT5);
 *  4) переполнение пара: звук и сброс до 3/4 ёмкости;
 *  5) дозаправка топливом, если запас энергии кончился;
 *  6) нагрев раз в heatPeriod тиков, пока не достигнут предел температуры.
 *
 * Особенность GT5 (оставлена): топливо тратится только на нагрев. Горячий котёл на пределе
 * температуры почти не расходует топливо, а пар даёт одинаково при любой температуре выше 100.
 */
public final class BoilerLogic {

    /** Параметры вида котла (числа из классов GT5). */
    public enum Kind {
        //           loss  water cap    maxT  heatP cost step solar
        COAL_BRONZE(45,   25,   16000, 500,  12,   1,   1,   false),
        COAL_STEEL (40,   10,   32000, 1000, 12,   2,   1,   false),
        LAVA_STEEL (20,   10,   32000, 1000, 8,    3,   1,   false),
        SOLAR_BRONZE(45,  25,   32000, 500,  12,   5,   5,   true);

        /** Остывание на {@link #step} после стольких тиков (счётчик должен превысить это число). */
        public final int lossTimerLimit;
        /** Период расхода воды и выработки пара, тики. */
        public final int waterPeriod;
        /** Ёмкость баков воды и пара, л. */
        public final int capacity;
        public final int maxTemperature;
        public final int heatPeriod;
        /** Сколько единиц топливной энергии стоит один шаг нагрева. */
        public final int heatCost;
        /** На сколько градусов меняется температура за шаг нагрева или остывания. */
        public final int step;
        public final boolean solar;

        Kind(int lossTimerLimit, int waterPeriod, int capacity, int maxTemperature, int heatPeriod, int heatCost, int step, boolean solar) {
            this.lossTimerLimit = lossTimerLimit;
            this.waterPeriod = waterPeriod;
            this.capacity = capacity;
            this.maxTemperature = maxTemperature;
            this.heatPeriod = heatPeriod;
            this.heatCost = heatCost;
            this.step = step;
            this.solar = solar;
        }
    }

    /** Что происходит снаружи котла. Реализует блок-сущность (или тест). */
    public interface Env {
        /** Отдать пар соседям: блок сам вызывает {@link #drainSteam}. */
        void pushSteam();

        /** Котёл пуст по энергии и хочет топлива. Вернуть добавленную энергию (0, если топлива нет). */
        int refuel();

        /** Сколько пара получается из 1 л воды в этом цикле (уголь: 150; лава: зависит от слота побочки). */
        default int steamPerWater() {
            return 150;
        }

        /** Тратится ли вода в этом цикле (лавовый котёл тратит её с вероятностью, равной КПД). */
        default boolean consumesWater() {
            return true;
        }

        /** Вызывается при каждом шаге нагрева (лавовый котёл иногда даёт грязную каменную пыль). */
        default void onHeatStep() {}
    }

    public enum Result { NONE, OVERFLOW, EXPLODE }

    /** Особенность солнечного котла GT5: после CALCIFICATION_TIME циклов выход падает с 150 до 50. */
    public static final int CALCIFICATION_TIME = 43200;
    public static final int SOLAR_OUTPUT = 150, SOLAR_MIN_OUTPUT = 50;

    public final Kind kind;
    public int temperature = 20;
    public int lossTimer;
    public int water;
    public int steam;
    public int energy;
    public boolean hadNoWater;
    /** Солнечный котёл: число рабочих циклов (накипь). Сбрасывается, если котёл сломать и поставить заново. */
    public int runTime;
    public boolean active;

    public BoilerLogic(Kind kind) {
        this.kind = kind;
    }

    /**
     * Один серверный тик.
     * @param tick номер тика мира (GT использует остаток от деления на периоды)
     */
    public Result tick(long tick, Env env) {
        if (temperature <= 20) {
            temperature = 20;
            lossTimer = 0;
        }
        if (++lossTimer > kind.lossTimerLimit) {
            temperature -= kind.step;
            lossTimer = 0;
        }
        if (steam > 0) env.pushSteam();
        Result result = Result.NONE;
        if (tick % kind.waterPeriod == 0) {
            if (temperature > 100) {
                if (water <= 0) {
                    hadNoWater = true;
                } else {
                    if (hadNoWater) return Result.EXPLODE;
                    int out;
                    if (kind.solar) {
                        water -= 1;
                        runTime++;
                        out = solarOutput(runTime);
                    } else {
                        if (env.consumesWater()) water -= 1;
                        out = env.steamPerWater();
                    }
                    steam += out;
                }
            } else {
                hadNoWater = false;
            }
        }
        int limit = kind.capacity;
        if (steam > limit) {
            steam = limit * 3 / 4;
            result = Result.OVERFLOW;
        }
        if (energy <= 0 && (!kind.solar || tick % 256 == 0)) {
            energy += env.refuel();
        }
        if (temperature < kind.maxTemperature && energy > 0 && tick % kind.heatPeriod == 0) {
            energy -= kind.heatCost;
            temperature += kind.step;
            env.onHeatStep();
        }
        active = energy > 0;
        return result;
    }

    /** Солнечный котёл GT5: выход за цикл после накопления накипи. */
    public static int solarOutput(int runTime) {
        if (runTime > CALCIFICATION_TIME) {
            return Math.max(SOLAR_MIN_OUTPUT, SOLAR_OUTPUT - (runTime - CALCIFICATION_TIME) / (CALCIFICATION_TIME / 150));
        }
        return SOLAR_OUTPUT;
    }

    /**
     * Энергия солнца за 256 тиков (GT5): ясный день — 40, иначе 5; в дождь при тёмном небе или без вида на небо — 0.
     * В грозу дозаправки нет вовсе.
     */
    public static int solarEnergy(boolean seesSky, boolean raining, boolean thundering, boolean dark, boolean day) {
        if (thundering) return 0;
        if ((raining && dark) || !seesSky) return 0;
        return !raining && day ? 40 : 5;
    }

    /** Лавовый котёл GT5 (SmallLavaBoilerEfficiencyLoss=true): КПД падает по мере заполнения слота побочки. */
    public static double lavaEfficiency(int byproductCount, int maxStack) {
        if (byproductCount <= 0) return 1.0;
        return 1.0 - (double) byproductCount / (double) maxStack;
    }

    public static int lavaSteam(double efficiency) {
        return 100 + (int) Math.round(200 * efficiency);
    }

    /** Забрать пар (для отдачи соседям). Возвращает забранное. */
    public int drainSteam(int amount) {
        int d = Math.min(amount, steam);
        steam -= d;
        return d;
    }

    /** Залить воду. Возвращает принятое. */
    public int fillWater(int amount) {
        int f = Math.min(amount, kind.capacity - water);
        water += Math.max(0, f);
        return Math.max(0, f);
    }
}
