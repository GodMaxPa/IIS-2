package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import ru.iis.fluid.GtFluids;
import ru.iis.lv.Fuels;
import ru.iis.multi.MultiContent;

/**
 * Большая газовая турбина (GT_MetaTileEntity_LargeTurbine_Gas, D104): форма паровой турбины на турбинных корпусах из
 * нержавеющей стали. Топливо — первый горючий газ во входных люках (EU на литр — как у газовых турбин GT); оптимальный
 * поток = оптимум ротора / EU на литр, берётся до 125 %; меньше оптимума — КПД падает пропорционально. 8 загрязнения за тик
 * (нужен глушитель). С кислородом — разгон ИИС, см. {@link #targetPower}.
 */
public class LargeGasTurbineBlockEntity extends LargeTurbineBlockEntity {
    public static final int POLLUTION = 8;
    /** MachineStats.cfg: LargeTurbineGas.oxygenFactor. */
    public static final float OXYGEN_FACTOR = 28f;
    private int oxygenConsume, prevOxygenConsume;
    private boolean boosted;

    public LargeGasTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.LARGE_GAS_TURBINE_BE.get(), pos, state);
    }

    @Override
    protected Block casing() {
        return MultiContent.TURBINE_CASING_STAINLESS.get();
    }

    /**
     * GT_MetaTileEntity_LargeTurbine_Gas#fluidIntoPower. На полной скорости турбина забирает кислород (EU/t / 2 / 28 л/т, не меньше 1): пока его
     * хватает, скорость растёт на 5 единиц за тик до 15000, а при нехватке падает. Скорость выше 100 % поднимает оптимальный поток топлива
     * (при 150 % — вдвое), вместе с самой скоростью это даёт до ×3 выхода при ×2 расхода; загрязнение при разгоне вдвое больше.
     */
    @Override
    protected int targetPower(ServerLevel sl) {
        float fuelOverload = 0;
        if (efficiency >= 10000) {
            fuelOverload = (efficiency - 10000) / 5000f;
            oxygenConsume = Math.max(1, (int) (eut / (2 * OXYGEN_FACTOR)));
            if (efficiency < 12550) oxygenConsume = (int) (oxygenConsume * (float) Math.sqrt(fuelOverload));
            oxygenConsume = Math.max(1, oxygenConsume);
            if (prevOxygenConsume <= oxygenConsume) {
                prevOxygenConsume = oxygenConsume;
            } else {
                prevOxygenConsume--;
                oxygenConsume = prevOxygenConsume;
            }
            int real = drainOxygen(sl, oxygenConsume);
            boosted = real > 0 || efficiency > 10000;
            if (!boosted) {
                oxygenConsume = 0;
            } else {
                int delta = 5;
                if (oxygenConsume > real) {
                    delta = -6;
                    if (efficiency > 12550) delta -= (efficiency - 12550) / 10;
                }
                efficiency = Math.min(efficiency + delta, 15000);
                oxygenConsume = real;
            }
        } else {
            boosted = false;
            oxygenConsume = 0;
        }
        Fluid fuel = null;
        int value = 0;
        for (BlockPos p : parts.inputHatches) {
            if (sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h && h.tank().amount() > 0) {
                int v = Fuels.power("gas", h.tank().resource().getFluid());
                if (v > 0) {
                    fuel = h.tank().resource().getFluid();
                    value = v;
                    break;
                }
            }
        }
        if (fuel == null) return 0;
        int opt = Math.max(1, optFlow() / value);
        if (fuelOverload > 0) opt += Math.round(opt * fuelOverload);
        int remaining = (int) (opt * 1.25f), total = 0;
        for (BlockPos p : parts.inputHatches) {
            if (remaining <= 0) break;
            if (!(sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h) || h.tank().amount() <= 0 || !h.tank().resource().getFluid().isSame(fuel)) continue;
            int flow = Math.min(h.tank().amount(), remaining);
            h.tank().drainInternal(flow);
            setStoredFluid(h.tank().amount());
            remaining -= flow;
            total += flow;
        }
        int eu = Math.min(opt, total) * value;
        if (total != opt) {
            float eff = total > opt ? 1f : Math.max(0f, 1f - Math.abs((total - (float) opt) / opt));
            eu = Math.max(1, (int) ((long) (eu * eff) * baseEff() / 10000L));
        } else {
            eu = (int) ((long) eu * baseEff() / 10000L);
        }
        return eu;
    }

    /** GT depleteInputUpTo: сколько есть, до нужного количества, из всех входных люков. */
    private int drainOxygen(ServerLevel sl, int amount) {
        Fluid oxygen = GtFluids.get("oxygen").source().get();
        int left = amount;
        for (BlockPos p : parts.inputHatches) {
            if (left <= 0) break;
            if (!(sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h) || h.tank().amount() <= 0 || !h.tank().resource().getFluid().isSame(oxygen)) continue;
            int n = Math.min(left, h.tank().amount());
            h.tank().drainInternal(n);
            left -= n;
        }
        return amount - left;
    }

    /** GT getMaxEfficiency: max(скорость, 100 %) минус неисправности. */
    @Override
    protected int efficiencyCap() {
        return Math.max(efficiency, 10000) - (10000 - maintenance.maxEfficiency());
    }

    /** Для тестов: разгон до полной скорости занимает 1000 тиков. */
    public void forceSpeed(int speed) {
        efficiency = speed;
    }

    public boolean boosted() {
        return boosted;
    }

    @Override
    protected boolean runningTick(ServerLevel sl) {
        if (!super.runningTick(sl)) return false;
        if (!polluteEnvironment(sl, boosted ? POLLUTION * 2 : POLLUTION)) {
            stop(true);
            return false;
        }
        return true;
    }
}
