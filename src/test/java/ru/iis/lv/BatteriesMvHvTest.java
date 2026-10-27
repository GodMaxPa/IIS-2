package ru.iis.lv;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Батарейки и корпуса MV/HV (D96) по GT5-II: GT_MetaGenerated_Item_01:402–471, GT_MachineRecipeLoader:1428–1431. */
class BatteriesMvHvTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject json(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    private static int inCount(JsonObject r, int i) {
        return r.getAsJsonArray("inputs").get(i).getAsJsonObject().get("count").getAsInt();
    }

    @Test
    void canning() throws IOException {
        // 2 / 8 / 32 пыли в корпус LV / MV / HV, 100 / 400 / 1600 тиков, 2 EU/t
        assertEquals(2, inCount(json("processing/canner/battery_lithium_lv"), 0));
        JsonObject mv = json("processing/canner/battery_lithium_mv");
        assertEquals(8, inCount(mv, 0));
        assertEquals(400, mv.get("duration").getAsInt());
        assertEquals("iis:battery_hull_mv", mv.getAsJsonArray("inputs").get(1).getAsJsonObject().get("ingredient").getAsString());
        JsonObject hv = json("processing/canner/battery_sodium_hv");
        assertEquals(32, inCount(hv, 0));
        assertEquals(1600, hv.get("duration").getAsInt());
        assertEquals(2, hv.get("eut").getAsInt());
        assertEquals("iis:battery_hull_hv", json("processing/extractor/hull_from_battery_cadmium_hv").getAsJsonArray("outputs")
                .get(0).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void hulls() throws IOException {
        JsonObject craft = json("lv/battery_hull_mv");
        assertEquals("#iis:cables/1x/copper", craft.getAsJsonObject("key").get("C").getAsString());
        assertFalse(Files.exists(RECIPES.resolve("lv/battery_hull_hv.json")));   // в GT у большого корпуса нет крафта
        JsonObject hv = json("processing/assembler/battery_hull_hv");
        assertEquals(9, inCount(hv, 1));
        assertEquals(1296, hv.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(3200, hv.get("duration").getAsInt());
        assertEquals(4, hv.get("eut").getAsInt());
        assertEquals(432, json("processing/assembler/battery_hull_mv").getAsJsonArray("fluid_inputs").get(0).getAsJsonObject()
                .get("amount").getAsInt());
    }
}
