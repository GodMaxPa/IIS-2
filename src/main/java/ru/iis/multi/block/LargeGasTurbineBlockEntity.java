package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import ru.iis.lv.Fuels;
import ru.iis.multi.MultiContent;

/**
 * Большая газовая турбина (GT_MetaTileEntity_LargeTurbine_Gas, D104): форма паровой турбины на турбинных корпусах из
 * нержавеющей стали. Топливо — первый горючий газ во входных люках (EU на литр — как у газовых турбин GT); оптимальный
 * поток = оптимум ротора / EU на литр, берётся до 125 %; меньше оптимума — КПД падает пропорционально. 8 загрязнения за тик
 * (нужен глушитель). Кислородного разгона ИИС (до 150 % мощности) пока нет.
 */
public class LargeGasTurbineBlockEntity extends LargeTurbineBlockEntity {
    public static final int POLLUTION = 8;

    public LargeGasTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.LARGE_GAS_TURBINE_BE.get(), pos, state);
    }

    @Override
    protected Block casing() {
        return MultiContent.TURBINE_CASING_STAINLESS.get();
    }

    /** GT fluidIntoPower газовой турбины (без кислорода). */
    @Override
    protected int targetPower(ServerLevel sl) {
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
        int opt = Math.max(1, optFlow() / value), remaining = (int) (opt * 1.25f), total = 0;
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

    @Override
    protected boolean runningTick(ServerLevel sl) {
        if (!super.runningTick(sl)) return false;
        if (!polluteEnvironment(sl, POLLUTION)) {
            stop(true);
            return false;
        }
        return true;
    }
}
