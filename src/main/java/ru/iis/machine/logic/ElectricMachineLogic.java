package ru.iis.machine.logic;

import ru.iis.energy.logic.EnergyStorage;

/**
 * Рабочий цикл одноблочной электрической машины GT5-II (GT_MetaTileEntity_BasicMachine#onPostTick), без Minecraft.
 *
 * Правила оригинала:
 *  - запас EU = 64 × V уровня, «минимальный запас» = 16 × V; новый рецепт начинается при запасе ≥ половины минимального;
 *  - рецепт ищется только среди рецептов до V уровня EU/t, затем разгон (Overclock.basicMachine);
 *  - каждый тик работы списывается EU/t; не хватило — «заикание»: прогресс теряется, 100 тиков простоя
 *    (machines.constant_need_of_energy=true в ИИС);
 *  - ампераж на входе зависит от потребления: (EU/t × 2 − 1) / V + 1.
 */
public final class ElectricMachineLogic {
    public static final int STUTTER_TICKS = 100;

    public interface Env {
        /** Найти рецепт, проверить место под результат, забрать входы. {EU/t, тики} уже после разгона, или null. */
        int[] tryStart();

        /** Положить результат. */
        void finish();
    }

    public final long voltage;
    public int progress, maxProgress, eut;
    public boolean stuttering, active;

    public ElectricMachineLogic(long voltage) {
        this.voltage = voltage;
    }

    public long capacity() {
        return voltage * 64;
    }

    public long minimumStored() {
        return voltage * 16;
    }

    public long maxAmperesIn() {
        return (eut * 2L - 1) / voltage + 1;
    }

    public void tick(boolean checkRecipe, EnergyStorage energy, Env env) {
        boolean succeeded = false;
        if (maxProgress > 0) {
            active = true;
            if (progress < 0 || energy.drain(eut)) {
                if (++progress >= maxProgress) {
                    env.finish();
                    eut = 0;
                    progress = 0;
                    maxProgress = 0;
                    stuttering = false;
                    succeeded = true;
                }
                if (progress > 5) stuttering = false;
            } else if (!stuttering) {
                progress = -STUTTER_TICKS;
                stuttering = true;
            }
        } else {
            active = false;
        }
        if (maxProgress <= 0 && (checkRecipe || succeeded) && energy.stored >= minimumStored() / 2) {
            int[] r = env.tryStart();
            if (r != null) {
                eut = r[0];
                maxProgress = Math.max(1, r[1]);
                progress = 0;
            }
        }
        energy.maxAmperesIn = maxAmperesIn();
    }
}
