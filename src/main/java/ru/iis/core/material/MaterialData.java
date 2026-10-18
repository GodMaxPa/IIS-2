package ru.iis.core.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.iis.world.vein.VeinDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Загрузка встроенных в мод данных (материалы, жилы). Читается один раз при старте,
 * потому что блоки регистрируются до загрузки датапаков.
 */
public final class MaterialData {
    private MaterialData() {}

    private static List<IISMaterial> materials;
    private static List<VeinDefinition> veins;
    private static List<FormItem> items;
    private static List<ToolDef> tools;
    private static Map<String, String> forms;
    private static List<SmallOre> smallOres;

    /** Малая руда GT5 (GT_Worldgen_GT_Ore_SmallPieces): сколько вкраплений на чанк и на каких высотах. */
    public record SmallOre(String name, int minY, int maxY, int amount, String material, Set<String> dimensions) {}

    public static synchronized List<SmallOre> smallOres() {
        if (smallOres == null) {
            List<SmallOre> out = new ArrayList<>();
            for (JsonElement e : read("/iis/small_ores.json")) {
                JsonObject o = e.getAsJsonObject();
                Set<String> dims = new HashSet<>();
                o.getAsJsonArray("dimensions").forEach(d -> dims.add(d.getAsString()));
                out.add(new SmallOre(o.get("name").getAsString(), o.get("minY").getAsInt(), o.get("maxY").getAsInt(),
                        o.get("amount").getAsInt(), o.get("material").getAsString(), Set.copyOf(dims)));
            }
            smallOres = List.copyOf(out);
        }
        return smallOres;
    }

    /** Предмет-форма материала (дроблёная руда, пыль, слиток...), который регистрирует мод. */
    public record FormItem(String id, String prefix, String material) {}

    /** Инструмент GT. Повреждение в единицах 1 = 50 единиц GT5. */
    public record ToolDef(String id, String tool, String material, String handle, int maxDamage, int blockDamage,
                          int craftDamage, float speed, int quality, String kind) {}

    public static synchronized List<FormItem> items() {
        if (items == null) {
            List<FormItem> out = new ArrayList<>();
            for (JsonElement e : read("/iis/items.json")) {
                JsonObject o = e.getAsJsonObject();
                out.add(new FormItem(o.get("id").getAsString(), o.get("prefix").getAsString(), o.get("material").getAsString()));
            }
            items = List.copyOf(out);
        }
        return items;
    }

    public static synchronized List<ToolDef> tools() {
        if (tools == null) {
            List<ToolDef> out = new ArrayList<>();
            for (JsonElement e : read("/iis/tools.json")) {
                JsonObject o = e.getAsJsonObject();
                out.add(new ToolDef(o.get("id").getAsString(), o.get("tool").getAsString(), o.get("material").getAsString(),
                        o.get("handle").getAsString(), o.get("maxDamage").getAsInt(), o.get("blockDamage").getAsInt(),
                        o.get("craftDamage").getAsInt(), o.get("speed").getAsFloat(), o.get("quality").getAsInt(), str(o, "kind")));
            }
            tools = List.copyOf(out);
        }
        return tools;
    }

    /** Форма/материал -> ID предмета (свой или ванильный), например "dust/redstone" -> "minecraft:redstone". */
    public static synchronized Map<String, String> forms() {
        if (forms == null) {
            Map<String, String> out = new HashMap<>();
            readObject("/iis/forms.json").entrySet().forEach(en -> out.put(en.getKey(), en.getValue().getAsString()));
            forms = Map.copyOf(out);
        }
        return forms;
    }

    static JsonObject readObject(String path) {
        try (InputStream in = MaterialData.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }

    public static synchronized List<IISMaterial> materials() {
        if (materials == null) materials = parseMaterials(read("/iis/materials.json"));
        return materials;
    }

    public static synchronized List<VeinDefinition> veins() {
        if (veins == null) veins = parseVeins(read("/iis/veins.json"));
        return veins;
    }

    static JsonArray read(String path) {
        try (InputStream in = MaterialData.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }

    static List<IISMaterial> parseMaterials(JsonArray arr) {
        List<IISMaterial> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            List<String> hosts = new ArrayList<>();
            o.getAsJsonArray("hosts").forEach(h -> hosts.add(h.getAsString()));
            List<String> smallHosts = new ArrayList<>();
            o.getAsJsonArray("smallHosts").forEach(h -> smallHosts.add(h.getAsString()));
            List<String> subTags = new ArrayList<>();
            o.getAsJsonArray("subTags").forEach(h -> subTags.add(h.getAsString()));
            IISMaterial m = new IISMaterial(o.get("id").getAsString(), o.get("gtName").getAsString(),
                    o.get("en").getAsString(), o.get("ru").getAsString(), o.get("color").getAsInt(),
                    o.get("iconSet").getAsString(), o.get("harvestLevel").getAsInt(), hosts, smallHosts, str(o, "formula"),
                    o.get("directSmelting").getAsString(), o.get("oreMultiplier").getAsInt(), subTags);
            if (!ids.add(m.id())) throw new IllegalStateException("Duplicate material " + m.id());
            out.add(m);
        }
        return List.copyOf(out);
    }

    static List<VeinDefinition> parseVeins(JsonArray arr) {
        List<VeinDefinition> out = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            Set<String> dims = new HashSet<>();
            o.getAsJsonArray("dimensions").forEach(d -> dims.add(d.getAsString()));
            out.add(new VeinDefinition(o.get("name").getAsString(), o.get("minY").getAsInt(), o.get("maxY").getAsInt(),
                    o.get("weight").getAsInt(), o.get("density").getAsInt(), o.get("size").getAsInt(),
                    str(o, "primary"), str(o, "secondary"), str(o, "between"), str(o, "sporadic"), dims));
        }
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
