package ru.iis.bio.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Рецепт ручного места или органа Био (чистые данные, без Minecraft; D70). Таблица — `iis/bio_recipes.json`, её пишет
 * tools/gen/bio.py по спецификации v0.7 §22 (числа — временный тестовый набор, Р1). Ключи предметов: id ({@code iis:gel}),
 * тег ({@code #minecraft:planks}) или {@code food} — любая еда, кроме подготовленных масс.
 *
 * @param station  код места (см. {@link Station})
 * @param mode     режим места (у кармана ядра: 0 питание, 1 гидролизат, 2 почкование)
 * @param tool     инструмент в слоте инструмента (износ 1 за партию) или пустая строка
 * @param heat     нужен ли жар очага под сосудом
 * @param upkeep   поддержание живой части во время работы, мБ N0 за тик (берётся у ядра колонии)
 * @param lineage  переносить ли линию колонии с посева на выход
 */
public record BioRecipe(String id, String station, int mode, List<In> items, List<FluidIn> fluids, List<Out> outItems,
                        List<FluidOut> outFluids, int seconds, String tool, boolean heat, double upkeep, boolean lineage) {

    public record In(String key, int count) {}

    public record Out(String item, int count) {}

    public record FluidIn(String fluid, int amount) {}

    /** {@code perNutrition > 0}: выход = perNutrition × очки сытости съеденной еды (для сырой пищи в кармане). */
    public record FluidOut(String fluid, int amount, int perNutrition) {}

    public int ticks() {
        return seconds * 20;
    }

    // --- загрузка ---------------------------------------------------------------------------------------------------
    private static List<BioRecipe> all;

    public static synchronized List<BioRecipe> all() {
        if (all == null) {
            try (InputStream in = BioRecipe.class.getResourceAsStream("/iis/bio_recipes.json")) {
                all = in == null ? List.of() : parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("cannot read iis/bio_recipes.json", e);
            }
        }
        return all;
    }

    /** Используется ли жидкость во входах рецептов места (любого режима): трубы и сосуды подают только её. */
    public static boolean usesFluid(String station, net.minecraft.world.level.material.Fluid f) {
        for (BioRecipe r : all()) {
            if (!r.station().equals(station)) continue;
            for (FluidIn in : r.fluids()) if (ru.iis.bio.fluid.BioFluids.byKey(in.fluid()) == f) return true;
        }
        return false;
    }

    public static List<BioRecipe> forStation(String station, int mode) {
        List<BioRecipe> r = new ArrayList<>();
        for (BioRecipe b : all()) if (b.station.equals(station) && b.mode == mode) r.add(b);
        return r;
    }

    public static List<BioRecipe> parse(String json) {
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        List<BioRecipe> out = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            List<In> items = new ArrayList<>();
            for (JsonElement x : arr(o, "items")) items.add(new In(x.getAsJsonObject().get("key").getAsString(), x.getAsJsonObject().get("count").getAsInt()));
            List<FluidIn> fluids = new ArrayList<>();
            for (JsonElement x : arr(o, "fluids")) fluids.add(new FluidIn(x.getAsJsonObject().get("fluid").getAsString(), x.getAsJsonObject().get("amount").getAsInt()));
            List<Out> outItems = new ArrayList<>();
            for (JsonElement x : arr(o, "out_items")) outItems.add(new Out(x.getAsJsonObject().get("item").getAsString(), x.getAsJsonObject().get("count").getAsInt()));
            List<FluidOut> outFluids = new ArrayList<>();
            for (JsonElement x : arr(o, "out_fluids")) {
                JsonObject f = x.getAsJsonObject();
                outFluids.add(new FluidOut(f.get("fluid").getAsString(), f.has("amount") ? f.get("amount").getAsInt() : 0,
                        f.has("per_nutrition") ? f.get("per_nutrition").getAsInt() : 0));
            }
            out.add(new BioRecipe(o.get("id").getAsString(), o.get("station").getAsString(), o.has("mode") ? o.get("mode").getAsInt() : 0,
                    List.copyOf(items), List.copyOf(fluids), List.copyOf(outItems), List.copyOf(outFluids), o.get("seconds").getAsInt(),
                    o.has("tool") ? o.get("tool").getAsString() : "", o.has("heat") && o.get("heat").getAsBoolean(),
                    o.has("upkeep") ? o.get("upkeep").getAsDouble() : 0.0, o.has("lineage") && o.get("lineage").getAsBoolean()));
        }
        return Collections.unmodifiableList(out);
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
    }
}
