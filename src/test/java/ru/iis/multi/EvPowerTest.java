package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.multi.block.HatchType;
import ru.iis.multi.logic.BlastLogic;
import ru.iis.multi.logic.LargeTurbineLogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Питание EV (D99): люки EV, трансформатор IV↔EV, буферы EV по GT5-II (GT_Loader_MetaTileEntities:198, 208, 218, 229, 240, 272, 491–568). */
class EvPowerTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject key(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject().getAsJsonObject("key");
    }

    @Test
    void largeRotorNeedsEvDynamo() {
        // большой стальной ротор: optFlow = 3 × 6 × 50 = 900 л/т, baseEff = (50 + 10 × (5 + 2)) × 100 = 12 000 → 540 EU/t
        LargeTurbineLogic.Size large = LargeTurbineLogic.Size.LARGE;
        int opt = LargeTurbineLogic.optimalFlow(large, 6.0f);
        int eu = LargeTurbineLogic.steamPower(opt, opt, LargeTurbineLogic.baseEfficiency(large, 2));
        assertEquals(540, eu);
        assertTrue(eu > HatchType.DYNAMO_HV.voltage, "an HV dynamo hatch explodes");
        assertTrue(eu <= HatchType.DYNAMO_EV.voltage, "an EV dynamo hatch holds it");
        assertTrue(HatchType.DYNAMO_EV.dynamo());
        assertEquals(2048, HatchType.ENERGY_EV.voltage);
        assertEquals(4, BlastLogic.machineTier(HatchType.ENERGY_EV.voltage));
    }

    @Test
    void evCrafting() throws IOException {
        assertEquals("iis:hull_ev", key("multi/hatch_dynamo_ev").get("M").getAsString());
        assertEquals("#iis:cables/1x/aluminium", key("multi/hatch_energy_ev").get("C").getAsString());
        JsonObject tr = key("lv/transformer_ev");
        assertEquals("#iis:cables/1x/tungsten", tr.get("C").getAsString());
        assertEquals("#iis:cables/1x/aluminium", tr.get("B").getAsString());
        assertEquals("iis:coil_small", tr.get("K").getAsString());
        assertEquals("#iis:wires/16x/aluminium", key("lv/battery_buffer_4x4_ev").get("W").getAsString());
        assertEquals("#iis:wires/1x/aluminium", key("lv/battery_buffer_1x1_ev").get("W").getAsString());
    }
}
