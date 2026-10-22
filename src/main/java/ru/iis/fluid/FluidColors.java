package ru.iis.fluid;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Цвета жидкостей для интерфейсов и капсул (ARGB): жидкости GT и Био регистрируют свои сюда (Био зависит от этого пакета, не наоборот). */
public final class FluidColors {
    private FluidColors() {}

    private record Entry(Supplier<? extends Fluid> fluid, int argb) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static synchronized void register(Supplier<? extends Fluid> fluid, int argb) {
        ENTRIES.add(new Entry(fluid, argb));
    }

    public static int argb(Fluid f) {
        if (f == Fluids.EMPTY) return 0x00000000;
        if (f == Fluids.WATER || f == Fluids.FLOWING_WATER) return 0xFF3F76E4;
        if (f == Fluids.LAVA || f == Fluids.FLOWING_LAVA) return 0xFFE85A10;
        Integer gt = GtFluids.color(f);
        if (gt != null) return gt;
        for (Entry e : ENTRIES) if (e.fluid.get() == f) return e.argb;
        if (f == IISFluids.STEAM.get()) return 0xFFC8C8C8;
        if (f == IISFluids.CREOSOTE.get()) return 0xFF5A3C14;
        return 0xFF909090;
    }
}
