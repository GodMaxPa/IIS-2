package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import ru.iis.fluid.GtFluids;
import ru.iis.fluid.IISFluids;
import ru.iis.multi.MultiContent;

/**
 * Большая паровая турбина высокого давления (GT_MetaTileEntity_LargeTurbine_HPSteam, D105): форма паровой турбины на
 * титановых турбинных корпусах. Работает на перегретом паре (1 л = 1 EU, до 125 % оптимума ротора), отработанный пар —
 * в выходные люки. Обычный пар во входных люках просто пропадает. Без загрязнения.
 */
public class LargeHpTurbineBlockEntity extends LargeTurbineBlockEntity {
    public LargeHpTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.LARGE_HP_TURBINE_BE.get(), pos, state);
    }

    @Override
    protected Block casing() {
        return MultiContent.TURBINE_CASING_TITANIUM.get();
    }

    /** GT fluidIntoPower турбины ВД. */
    @Override
    protected int targetPower(ServerLevel sl) {
        Fluid sh = GtFluids.get("superheated_steam").source().get();
        int opt = optFlow(), remaining = (int) (opt * 1.25f), total = 0;
        for (BlockPos p : parts.inputHatches) {
            if (remaining <= 0) break;
            if (!(sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h) || h.tank().amount() <= 0) continue;
            Fluid f = h.tank().resource().getFluid();
            if (f.isSame(sh)) {
                int flow = Math.min(h.tank().amount(), remaining);
                h.tank().drainInternal(flow);
                setStoredFluid(h.tank().amount());
                remaining -= flow;
                total += flow;
            } else if (f.isSame(IISFluids.STEAM.get())) {
                h.tank().drainInternal(h.tank().amount());
            }
        }
        if (total > 0) addFluidOutput(sl, IISFluids.STEAM.get(), total);
        int eu = Math.min(opt, total);
        if (total > 0 && total != opt) {
            float eff = total > opt ? 1f : 1f - Math.abs((total - (float) opt) / opt);
            eu = Math.max(1, (int) ((long) (eu * eff) * baseEff() / 10000L));
        } else {
            eu = (int) ((long) eu * baseEff() / 10000L);
        }
        return eu;
    }
}
