package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.LvMachineType;
import ru.iis.machine.Overclock;
import ru.iis.multi.block.HatchType;
import ru.iis.multi.logic.BlastLogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** HV-машины, детали, люки и катушка из кантала (D93) по GT5-II: Machine_HV_*, Tier.ELECTRIC[3], :92, :217, :239. */
class HvMachinesTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject recipe(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    private static String key(String rel, String letter) throws IOException {
        return recipe(rel).getAsJsonObject("key").get(letter).getAsString();
    }

    @Test
    void everyMvMachineHasAnHvTwin() {
        for (LvMachineType mv : LvMachineType.values()) {
            if (mv.tier != VoltageTier.MV) continue;
            LvMachineType hv = LvMachineType.valueOf(mv.name().replace("_MV", "_HV"));
            assertEquals(VoltageTier.HV, hv.tier, hv.name());
            assertEquals(mv.id + "_hv", hv.blockId());
            assertEquals(mv.recipeMap, hv.recipeMap, hv.name());
            assertEquals(mv.inputs, hv.inputs, hv.name());
            // GT: у измельчителя HV два выхода (побочные продукты), у смесителя HV бак 24000
            assertEquals(mv == LvMachineType.MACERATOR_MV ? 2 : mv.outputs, hv.outputs, hv.name());
            assertEquals(mv == LvMachineType.COMPRESSOR_MV ? 12000 : mv == LvMachineType.MIXER_MV ? 24000 : mv == LvMachineType.FLUID_CANNER_MV ? 48000 : mv == LvMachineType.FILTER_MV ? 32000 : mv.tank, hv.tank, hv.name());
        }
        assertEquals(512, LvMachineType.MACERATOR_HV.voltage());
    }

    @Test
    void hvCraftingResolvesTier3Parts() throws IOException {
        assertEquals("iis:hull_hv", key("lv/macerator_hv", "M"));
        assertEquals("iis:electric_motor_hv", key("lv/macerator_hv", "E"));
        assertEquals("#c:circuits/advanced", key("lv/macerator_hv", "C"));
        assertEquals("#iis:cables/1x/gold", key("lv/macerator_hv", "W"));
        assertEquals("iis:grinder_diamond", key("lv/macerator_hv", "G"));            // craftingGrinder
        assertEquals("#iis:wires/2x/kanthal", key("lv/electric_furnace_hv", "C"));    // X.COIL_HEATING
        assertEquals("#iis:wires/4x/kanthal", key("lv/alloy_smelter_hv", "C"));
        assertEquals("#iis:rotors/steel", key("lv/mixer_hv", "R"));
        assertEquals("#iis:wires/1x/electrum", key("lv/electrolyzer_hv", "I"));
        assertEquals("iis:pipe_medium_plastic", key("lv/chemical_reactor_hv", "G"));
        assertEquals("#iis:wires/4x/copper", key("lv/polarizer_hv", "Z"));            // X.COIL_ELECTRIC
        assertEquals("iis:emitter_hv", key("lv/laser_engraver_hv", "E"));
        // улучшенная схема уровня HV — Data (D97)
        assertEquals("#c:circuits/data", key("lv/circuit_assembler_hv", "C"));
        assertEquals("iis:electric_piston_hv", key("lv/robot_arm_hv", "P"));
        assertEquals("minecraft:emerald", key("lv/emitter_hv", "Q"));
        assertEquals("iis:pipe_medium_stainless_steel", key("lv/electric_pump_hv", "P"));
    }

    @Test
    void hvOverclock() {
        // GT calculateOverclockedNess: до 16 EU/t — EU × 4^(tier-1), время ÷ 2^(tier-1): 2 × 400 → 32 × 100; 30 × 400 → 480 × 25
        assertEquals(new Overclock.Result(32, 100), Overclock.basicMachine(2, 400, VoltageTier.HV, 1));
    }

    @Test
    void hatchesAndKanthal() throws IOException {
        assertEquals(512, HatchType.ENERGY_HV.voltage);
        assertTrue(HatchType.DYNAMO_HV.dynamo());
        assertFalse(HatchType.ENERGY_HV.dynamo());
        assertEquals(3, BlastLogic.machineTier(HatchType.ENERGY_HV.voltage));
        assertEquals("#iis:cables/1x/gold", key("multi/hatch_energy_hv", "C"));
        assertEquals("iis:hull_hv", key("multi/hatch_dynamo_hv", "M"));
        assertEquals("#iis:wires/2x/kanthal", key("multi/coil_kanthal", "P"));
        JsonObject coil = recipe("processing/assembler/coil_kanthal");   // GT_MachineRecipeLoader:1382
        assertEquals(8, coil.get("circuit").getAsInt());
        assertEquals(8, coil.getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        JsonObject mix = recipe("processing/mixer/kanthal_dust");          // :1771 Fe + Al + Cr -> 3
        assertEquals(3, mix.getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(300, mix.get("duration").getAsInt());
    }
}
