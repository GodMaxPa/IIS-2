package ru.iis.lv;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ru.iis.IIS;
import ru.iis.fluid.IISFluids;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Топливо генераторов: энергия на литр (GT Materials FuelPower; FuelType 0 — дизель, 1 — газ, 3 — полужидкое; ИИС MaterialProperties.cfg).
 * Список из iis/fuels.json (tools/gen/lv.py). Жидкость iis:<имя> учитывается, только когда она уже есть в моде:
 * в эпоху LV таких жидкостей ещё нет — они придут с химией и нефтью.
 */
public final class Fuels {
    private Fuels() {}

    private static Map<String, Map<String, Integer>> table;
    /** id жидкости -> имя в таблице топлив GT. */
    private static final Map<String, String> ALIASES = Map.ofEntries(Map.entry("liquid_light_fuel", "lightfuel"), Map.entry("liquid_heavy_fuel", "heavyfuel"), Map.entry("liquid_sufluriclight_fuel", "sulfuriclightfuel"),
            Map.entry("liquid_sulfuricheavy_fuel", "sulfuricheavyfuel"), Map.entry("gas_gas", "gas"), Map.entry("gas_natural_gas", "natruralgas"), Map.entry("gas_sulfuricgas", "sulfuricgas"),
            Map.entry("liquid_light_oil", "oillight"), Map.entry("liquid_medium_oil", "oilmedium"), Map.entry("liquid_heavy_oil", "oilheavy"), Map.entry("bioethanol", "ethanol"));

    private static synchronized Map<String, Map<String, Integer>> table() {
        if (table == null) {
            table = new HashMap<>();
            try (var in = IIS.class.getResourceAsStream("/iis/fuels.json")) {
                if (in != null) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (String kind : root.keySet()) {
                        Map<String, Integer> m = new HashMap<>();
                        root.getAsJsonObject(kind).entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsInt()));
                        table.put(kind, m);
                    }
                }
            } catch (Exception e) {
                IIS.LOGGER.error("[IIS] fuels.json", e);
            }
        }
        return table;
    }

    /** EU на литр до учёта КПД; 0 — не топливо для этого генератора. */
    public static int fuelPower(GeneratorType type, Fluid fluid) {
        if (fluid == Fluids.EMPTY) return 0;
        if (type.steam()) return fluid.isSame(IISFluids.STEAM.get()) ? 1 : 0;
        return power(type.fuelKind, fluid);
    }

    /** GT mSpecialValue топлива раздела kind ("diesel", "gas", "dense" — полужидкое, D88); 0 — не топливо. */
    public static int power(String kind, Fluid fluid) {
        if (fluid == Fluids.EMPTY) return 0;
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        if (!IIS.MODID.equals(id.getNamespace())) return 0;
        // таблица по именам материалов GT, а часть жидкостей зовётся иначе: без этого нафта, лёгкое топливо, газ и LPG не горели нигде (D130)
        String path = id.getPath();
        Map<String, Integer> map = table().getOrDefault(kind, Map.of());
        Integer v = map.get(path);
        if (v == null) v = map.get(ALIASES.getOrDefault(path, path.startsWith("liquid_") ? path.substring(7) : path));
        return v == null ? 0 : v;
    }
}
