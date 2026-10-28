package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.LvMachineType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** EV-версии базовых машин и детали EV (D100) по GT5-II: Machine_EV_*, Tier.ELECTRIC[4], GT_MetaGenerated_Item_01:556, 670. */
class EvMachinesTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static String key(String rel, String letter) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject().getAsJsonObject("key")
                .get(letter).getAsString();
    }

    @Test
    void everyHvMachineHasAnEvTwin() {
        for (LvMachineType hv : LvMachineType.values()) {
            if (hv.tier != VoltageTier.HV) continue;
            LvMachineType ev = LvMachineType.valueOf(hv.name().replace("_HV", "_EV"));
            assertEquals(VoltageTier.EV, ev.tier, ev.name());
            assertEquals(hv.id + "_ev", ev.blockId());
            assertEquals(hv.recipeMap, ev.recipeMap, ev.name());
            assertEquals(hv.inputs, ev.inputs, ev.name());
            // GT: у измельчителя EV три выхода, у смесителя EV бак 32000
            assertEquals(hv == LvMachineType.MACERATOR_HV ? 3 : hv.outputs, ev.outputs, ev.name());
            assertEquals(hv == LvMachineType.MIXER_HV ? 32000 : hv == LvMachineType.FLUID_CANNER_HV ? 64000 : hv.tank, ev.tank, ev.name());
        }
        assertEquals(2048, LvMachineType.MACERATOR_EV.voltage());
    }

    @Test
    void evCraftingResolvesTier4Parts() throws IOException {
        assertEquals("iis:hull_ev", key("lv/macerator_ev", "M"));
        assertEquals("iis:grinder_diamond", key("lv/macerator_ev", "G"));
        assertEquals("#c:circuits/data", key("lv/macerator_ev", "C"));
        assertEquals("#iis:cables/1x/aluminium", key("lv/macerator_ev", "W"));
        assertEquals("#iis:wires/2x/nichrome", key("lv/electric_furnace_ev", "C"));
        assertEquals("#iis:wires/1x/platinum", key("lv/electrolyzer_ev", "I"));
        assertEquals("#iis:wires/8x/annealed_copper", key("lv/polarizer_ev", "Z"));
        assertEquals("#c:circuits/elite", key("lv/circuit_assembler_ev", "C"));
        assertEquals("iis:emitter_ev", key("lv/laser_engraver_ev", "E"));
        assertEquals("iis:electric_pump_ev", key("lv/distillery_ev", "P"));
        assertEquals("iis:pipe_medium_titanium", key("lv/electric_pump_ev", "P"));
        assertEquals("minecraft:ender_pearl", key("lv/emitter_ev", "Q"));
    }
}
