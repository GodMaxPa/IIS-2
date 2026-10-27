package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Кремний, пластины и микросхемы по GT5 (D91): рецепты в сгенерированных данных. */
class SiliconDataTest {
    private static final Path GEN = Path.of("src/generated/resources/data/iis/recipe/processing");

    private static JsonObject json(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(GEN.resolve(rel))).getAsJsonObject();
    }

    private static int outCount(JsonObject r) {
        return r.getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt();
    }

    @Test
    void bouleWafersAndChips() throws IOException {
        JsonObject boule = json("blast/silicon_boule.json");   // GT_MachineRecipeLoader:620
        assertEquals(1784, boule.get("temp").getAsInt());
        assertEquals(9000, boule.get("duration").getAsInt());
        assertEquals(32, boule.getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(16, outCount(json("cutter/silicon_wafer.json")));
        assertEquals(400, json("cutter/silicon_wafer.json").get("duration").getAsInt());          // вода: время × 2
        assertEquals(200, json("cutter/silicon_wafer_lubricant.json").get("duration").getAsInt());
        assertEquals("#iis:crafting_lens/red", json("laser_engraver/wafer_ilc.json").getAsJsonArray("keep").get(0).getAsString());
        assertEquals("#iis:crafting_lens/cyan", json("laser_engraver/wafer_ram.json").getAsJsonArray("keep").get(0).getAsString());
        assertEquals(8, outCount(json("cutter/chip_ilc.json")));
        assertEquals(32, outCount(json("cutter/chip_ram.json")));
    }

    @Test
    void siliconSources() throws IOException {
        assertEquals(8, json("electrolyzer/sand_silicon_dioxide.json").getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals("iis:dust_silicon", json("electrolyzer/decompose_silicon_dioxide.json").getAsJsonArray("outputs").get(0)
                .getAsJsonObject().get("id").getAsString());
    }
}
