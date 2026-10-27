package ru.iis.multi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Детали турбины и синяя сталь по GT5 (D90): рецепты в сгенерированных данных. */
class TurbinePartsDataTest {
    private static final Path GEN = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject json(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(GEN.resolve(rel))).getAsJsonObject();
    }

    @Test
    void blueSteelChainByHand() throws IOException {
        // GT_CraftingRecipeLoader:389 — 8 синей стали: розовое золото, латунь, 2 стали, 4 чёрной стали
        JsonObject r = json("dust/mix_blue_steel.json");
        assertEquals(8, r.getAsJsonObject("result").get("count").getAsInt());
        JsonArray in = r.getAsJsonArray("ingredients");
        assertEquals(8, in.size());
        long black = in.asList().stream().filter(e -> e.getAsString().equals("#c:dusts/black_steel")).count();
        assertEquals(4, black);
        // GT_MachineRecipeLoader:1778 — чёрная бронза в смесителе: 3 меди + 2 электрума
        JsonObject m = json("processing/mixer/black_bronze_dust.json");
        assertEquals(500, m.get("duration").getAsInt());
        assertEquals(5, m.getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt());
    }

    @Test
    void turbineCasingAndRotor() throws IOException {
        JsonObject c = json("multi/turbine_casing.json");
        assertEquals("#c:frames/blue_steel", c.getAsJsonObject("key").get("F").getAsString());
        JsonObject a = json("processing/assembler/turbine_casing.json");
        assertEquals(6, a.getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        JsonObject rotor = json("processing/assembler/turbine_small_steel.json");
        assertEquals(100, rotor.get("eut").getAsInt());
        assertEquals(51200, rotor.getAsJsonArray("outputs").get(0).getAsJsonObject().getAsJsonObject("components")
                .get("minecraft:max_damage").getAsInt());
        JsonObject blade = json("part/turbine_blade_steel.json");
        assertEquals("#iis:double_plates/steel", blade.getAsJsonObject("key").get("P").getAsString());
        JsonObject bend = json("processing/bender/double_plate_steel.json");
        assertEquals(96, bend.get("eut").getAsInt());
    }

    @Test
    void hvCasing() throws IOException {
        assertEquals("#c:plates/stainless_steel", json("lv/casing_hv.json").getAsJsonObject("key").get("P").getAsString());
        assertEquals(8, json("processing/assembler/casing_hv.json").getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
    }
}
