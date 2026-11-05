package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ru.iis.IIS;
import ru.iis.lv.Fuels;
import ru.iis.multi.MultiContent;

/**
 * Большой плазменный генератор (GT_MetaTileEntity_LargeTurbine_Plasma, D121): форма паровой турбины на вольфрамостальных турбинных корпусах.
 * Топливо — первая плазма во входных люках (EU на литр — как у плазменных генераторов), оптимальный поток = оптимум ротора × 40 / EU на литр,
 * берётся до 125 %, меньше оптимума — КПД падает пропорционально. Остывшая жидкость (то, что было плазмой, или её расплав) уходит в выходной люк.
 */
public class LargePlasmaTurbineBlockEntity extends LargeTurbineBlockEntity {
    public LargePlasmaTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.LARGE_PLASMA_TURBINE_BE.get(), pos, state);
    }

    @Override
    protected Block casing() {
        return MultiContent.TURBINE_CASING_TUNGSTENSTEEL.get();
    }

    /** GT fluidIntoPower. */
    @Override
    protected int targetPower(ServerLevel sl) {
        Fluid fuel = null;
        int value = 0;
        for (BlockPos p : parts.inputHatches) {
            if (sl.getBlockEntity(p) instanceof FluidHatchBlockEntity h && h.tank().amount() > 0) {
                int v = Fuels.power("plasma", h.tank().resource().getFluid());
                if (v > 0) {
                    fuel = h.tank().resource().getFluid();
                    value = v;
                    break;
                }
            }
        }
        if (fuel == null) return 0;
        long optBase = optFlow() * 40L;
        int opt = (int) ((optBase + value - 1) / value);
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
        Fluid cold = cooled(fuel);
        if (cold != Fluids.EMPTY && total > 0) addFluidOutput(sl, cold, total);
        long eu = (long) Math.min(opt, total) * value;
        if (total != opt) {
            float eff = total > opt ? 1f : Math.max(0f, 1f - Math.abs((total - (float) opt) / opt));
            eu = Math.max(1L, (long) (eu * eff) * baseEff() / 10000L);
        } else {
            eu = eu * baseEff() / 10000L;
        }
        return (int) Math.min(Integer.MAX_VALUE, eu);
    }

    /** plasma_x в x или molten_x. */
    public static Fluid cooled(Fluid plasma) {
        String path = BuiltInRegistries.FLUID.getKey(plasma).getPath();
        if (!path.startsWith("plasma_")) return Fluids.EMPTY;
        String name = path.substring("plasma_".length());
        Fluid f = BuiltInRegistries.FLUID.getValue(Identifier.fromNamespaceAndPath(IIS.MODID, name));
        if (f != Fluids.EMPTY) return f;
        return BuiltInRegistries.FLUID.getValue(Identifier.fromNamespaceAndPath(IIS.MODID, "molten_" + name));
    }
}
