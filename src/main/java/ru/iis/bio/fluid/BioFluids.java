package ru.iis.bio.fluid;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;
import ru.iis.IIS;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Жидкости Био первого среза (спецификация владельца v0.7 §22.5, D70): живут только в органах и ручных ёмкостях —
 * без блока в мире и ведра, переносятся мерным ковшом. Вода — ванильная.
 */
public final class BioFluids {
    private BioFluids() {}

    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, IIS.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, IIS.MODID);

    /** Жидкость Био: id, цвет для интерфейса (ARGB), вязкость. */
    public record Def(String id, int color, DeferredHolder<Fluid, BaseFlowingFluid.Source> source, DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing) {}

    public static final Map<String, Def> ALL = new LinkedHashMap<>();

    private static Def fluid(String id, int color, int viscosity) {
        final DeferredHolder<FluidType, FluidType>[] type = new DeferredHolder[1];
        final DeferredHolder<Fluid, BaseFlowingFluid.Source>[] src = new DeferredHolder[1];
        final DeferredHolder<Fluid, BaseFlowingFluid.Flowing>[] flow = new DeferredHolder[1];
        type[0] = TYPES.register("bio_" + id, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid_type.iis.bio_" + id).density(1050).viscosity(viscosity).temperature(300)
                .canSwim(false).canDrown(false).canExtinguish(false).canHydrate(false).supportsBoating(false).canConvertToSource(false)));
        src[0] = FLUIDS.register("bio_" + id, () -> new BaseFlowingFluid.Source(new BaseFlowingFluid.Properties(type[0], src[0], flow[0])));
        flow[0] = FLUIDS.register("bio_" + id + "_flowing", () -> new BaseFlowingFluid.Flowing(new BaseFlowingFluid.Properties(type[0], src[0], flow[0])));
        Def d = new Def(id, color, src[0], flow[0]);
        ru.iis.fluid.FluidColors.register(src[0], color);
        ALL.put(id, d);
        return d;
    }

    /** Питательная среда N0 — основное питание колонии и ULV-органов. */
    public static final Def N0 = fluid("n0", 0xFFC0625A, 1100);
    /** Первичная ростовая фракция — стартовый «рост» до G0. */
    public static final Def PRIMARY = fluid("primary_fraction", 0xFFE89AAE, 1200);
    /** Органический сток — отход большинства процессов. */
    public static final Def RUNOFF = fluid("runoff", 0xFF6A5838, 1300);
    public static final Def HYDROLYSATE = fluid("hydrolysate", 0xFFA83F36, 1200);
    public static final Def SUSPENSION = fluid("suspension", 0xFFD6CFBC, 1400);
    public static final Def BINDER = fluid("binder", 0xFFDCC48A, 2000);
    /** Ростовая среда G0 — выращивание тканей и ULV-органов. */
    public static final Def G0 = fluid("g0", 0xFF9C3868, 1500);
    // остальные смеси ULV–LV спецификации v0.7 §22.5 (D72)
    public static final Def EXTRACT = fluid("extract", 0xFF8FB050, 1100);
    public static final Def EMULSION = fluid("emulsion", 0xFFE8D2A0, 1600);
    public static final Def CONCENTRATE = fluid("concentrate", 0xFFD89A30, 1500);
    /** Отфильтрованная вода W0. */
    public static final Def W0 = fluid("w0", 0xFF6EA8E0, 1000);
    public static final Def N1 = fluid("n1", 0xFFD07868, 1100);
    /** Энергетическая среда E1. */
    public static final Def E1 = fluid("e1", 0xFFE8B040, 1200);
    public static final Def G1 = fluid("g1", 0xFFB04880, 1500);
    // черновик Б-2 (D148): пищеварительная кислота рудного желудка, кислый сток, фермент HV, ростовая среда MV
    public static final Def ACID = fluid("digestive_acid", 0xFFB8D040, 1100);
    public static final Def ACID_RUNOFF = fluid("acid_runoff", 0xFF7A8A30, 1300);
    public static final Def ENZYME = fluid("enzyme_solution", 0xFF60C8A0, 1200);
    public static final Def G2 = fluid("g2", 0xFF8040A0, 1500);

    /** Порядок для синхронизации интерфейса: 0 — пусто, 1 — вода, дальше жидкости Био. */
    public static List<Fluid> syncOrder() {
        List<Fluid> l = new ArrayList<>();
        l.add(Fluids.EMPTY);
        l.add(Fluids.WATER);
        for (Def d : ALL.values()) l.add(d.source().get());
        return l;
    }

    public static int color(Fluid f) {
        return ru.iis.fluid.FluidColors.argb(f);
    }

    /** Жидкость по ключу рецепта: {@code water} или id Био ({@code n0}, {@code g0} …). */
    public static @Nullable Fluid byKey(String key) {
        if ("water".equals(key)) return Fluids.WATER;
        Def d = ALL.get(key);
        return d == null ? null : d.source().get();
    }

    public static Identifier still(Def d) {
        return Identifier.fromNamespaceAndPath(IIS.MODID, "block/fluid/bio_" + d.id());
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
    }
}
