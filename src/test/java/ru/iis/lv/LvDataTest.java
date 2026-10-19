package ru.iis.lv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Java-каталоги LV совпадают с тем, что сгенерировал tools/gen/lv.py (провода, машины). */
class LvDataTest {
    private static JsonArray read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/iis/" + name))).getAsJsonArray();
    }

    @Test
    void cablesMatchGenerator() throws IOException {
        JsonArray json = read("cables.json");
        List<CableSpecs.Spec> java = CableSpecs.ALL;
        assertEquals(json.size(), java.size());
        for (int i = 0; i < java.size(); i++) {
            JsonObject j = json.get(i).getAsJsonObject();
            CableSpecs.Spec s = java.get(i);
            assertEquals(j.get("id").getAsString(), s.id());
            assertEquals(j.get("voltage").getAsLong(), s.voltage(), s.id());
            assertEquals(j.get("amperage").getAsLong(), s.amperage(), s.id());
            assertEquals(j.get("loss").getAsLong(), s.loss(), s.id());
            assertEquals(j.get("insulated").getAsBoolean(), s.insulated(), s.id());
            assertEquals(j.get("thickness").getAsDouble(), s.thickness(), 1e-9, s.id());
        }
    }

    @Test
    void machinesMatchGenerator() throws IOException {
        JsonArray json = read("lv_machines.json");
        assertEquals(json.size(), LvMachineType.values().length);
        for (JsonElement e : json) {
            JsonObject j = e.getAsJsonObject();
            LvMachineType t = LvMachineType.valueOf(j.get("type").getAsString().toUpperCase());
            assertEquals(j.get("id").getAsString(), t.blockId());
            assertEquals(j.get("inputs").getAsInt(), t.inputs, t.id);
            assertEquals(j.get("outputs").getAsInt(), t.outputs, t.id);
            assertEquals(j.get("tank").getAsInt(), t.tank, t.id);
            assertEquals(j.get("gui").getAsString(), "lv/" + t.gui, t.id);
            String map = j.get("recipeMap").isJsonNull() ? null : j.get("recipeMap").getAsString();
            assertEquals(map, t.recipeMap == null ? null : t.recipeMap.id, t.id);
            assertTrue(t.recipeMap == null || t.inputs <= t.recipeMap.maxInputs, t.id);
        }
    }

    @Test
    void tinCableCarriesOneAmpLv() {
        CableSpecs.Spec tin = CableSpecs.ALL.stream().filter(s -> s.id().equals("cable_tin_1x")).findFirst().orElseThrow();
        assertEquals(32, tin.voltage());
        assertEquals(1, tin.amperage());
        assertEquals(1, tin.loss());
        CableSpecs.Spec lead4 = CableSpecs.ALL.stream().filter(s -> s.id().equals("wire_lead_4x")).findFirst().orElseThrow();
        assertEquals(8, lead4.amperage());
        assertEquals(4, lead4.loss());
    }
}
