package ru.iis.fluid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Жидкости GT5-II (D72): список, состояние и температура — из исходников IdealIndustrial/GT5Unofficial @ 00021e3
 * (GT_Loader_Item_Block_And_Fluid + GT_Proxy#addFluid, извлечено в reference/original/gt5_fluids.json), имена — из
 * GregTech.lang.ru сборки ИИС. Как в GT, у жидкостей нет блоков и вёдер: их возят капсулы, трубы, резервуары и машины.
 * Состояния GT: 0 твёрдое (дроблёный лёд), 1 жидкость, 2 газ, 3 плазма, 4 расплав. Пар и креозот — свои, в {@link IISFluids}.
 */
public final class GtFluids {
    private GtFluids() {}

    public record Def(String id, int state, int temperature, int color, DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
                      DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing) {
        public boolean gaseous() {
            return state == 2 || state == 3;
        }
    }

    private static final Map<String, Def> ALL = new LinkedHashMap<>();
    private static boolean done;

    /** Зарегистрировать все жидкости (вызывается из {@link IISFluids#register} до регистрации реестров). */
    @SuppressWarnings("unchecked")
    static synchronized void init() {
        if (done) return;
        done = true;
        JsonArray arr;
        try (InputStream in = GtFluids.class.getResourceAsStream("/iis/gt_fluids.json")) {
            if (in == null) return;
            arr = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read iis/gt_fluids.json", e);
        }
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String id = o.get("id").getAsString();
            int state = o.get("state").getAsInt(), temp = o.get("temperature").getAsInt(), color = o.get("color").getAsInt();
            final DeferredHolder<FluidType, FluidType>[] type = new DeferredHolder[1];
            final DeferredHolder<Fluid, BaseFlowingFluid.Source>[] src = new DeferredHolder[1];
            final DeferredHolder<Fluid, BaseFlowingFluid.Flowing>[] flow = new DeferredHolder[1];
            type[0] = IISFluids.TYPES.register(id, () -> new FluidType(properties(id, state, temp)));
            src[0] = IISFluids.FLUIDS.register(id, () -> new BaseFlowingFluid.Source(new BaseFlowingFluid.Properties(type[0], src[0], flow[0])));
            flow[0] = IISFluids.FLUIDS.register(id + "_flowing", () -> new BaseFlowingFluid.Flowing(new BaseFlowingFluid.Properties(type[0], src[0], flow[0])));
            ALL.put(id, new Def(id, state, temp, color, src[0], flow[0]));
        }
    }

    /** GT_Proxy#addFluid: вязкость и плотность по состоянию; плазма светится. */
    private static FluidType.Properties properties(String id, int state, int temp) {
        FluidType.Properties p = FluidType.Properties.create().descriptionId("fluid_type.iis." + id).temperature(temp)
                .canSwim(false).canDrown(false).canExtinguish(false).canHydrate(false).supportsBoating(false).canConvertToSource(false);
        return switch (state) {
            case 0 -> p.viscosity(10000).density(1000);
            case 2 -> p.viscosity(200).density(-100);
            case 3 -> p.viscosity(10).density(-55536).lightLevel(15);
            case 4 -> p.viscosity(1000).density(3000).lightLevel(Math.min(15, Math.max(0, (temp - 600) / 100)));
            default -> p.viscosity(1000).density(1000);
        };
    }

    public static Map<String, Def> all() {
        return Collections.unmodifiableMap(ALL);
    }

    public static Def get(String id) {
        return ALL.get(id);
    }

    /** Цвет для интерфейсов (ARGB) или null, если жидкость не из GT. */
    public static Integer color(Fluid f) {
        if (f == Fluids.WATER) return 0xFF3F76E4;
        if (f == Fluids.LAVA) return 0xFFE85A10;
        for (Def d : ALL.values()) if (d.source().get() == f || d.flowing().get() == f) return 0xFF000000 | d.color();
        return null;
    }
}
