package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.CableSpecs;
import ru.iis.lv.LvMachineType;
import ru.iis.multi.block.EbfBlockEntity;
import ru.iis.multi.block.HatchType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Уровень ZPM (D112) по GT5-II: Machine_ZPM_*, Tier.ELECTRIC[7], GT_MetaTileEntity_BasicMachine_GT_Recipe для mTier 7, makeWires:1505, 1510,
 * GT_MachineRecipeLoader:452, 516, 520-521, 585-588, 2058-2294, 2310-2323, GT_CraftingRecipeLoader:334, 409.
 */
class ZpmMachinesTest {
    private static final Path GEN = Path.of("src/generated/resources");
    private static final Path RECIPES = GEN.resolve("data/iis/recipe");

    private static JsonObject recipe(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    private static String key(String rel, String letter) throws IOException {
        return recipe(rel).getAsJsonObject("key").get(letter).getAsString();
    }

    private static String ingredient(JsonObject r, int i) {
        return r.getAsJsonArray("inputs").get(i).getAsJsonObject().get("ingredient").getAsString();
    }

    private static int count(JsonObject r, int i) {
        return r.getAsJsonArray("inputs").get(i).getAsJsonObject().get("count").getAsInt();
    }

    @Test
    void everyLuvMachineHasAZpmTwin() {
        for (LvMachineType luv : LvMachineType.values()) {
            if (luv.tier != VoltageTier.LUV) continue;
            LvMachineType zpm = LvMachineType.valueOf(luv.name().replace("_LUV", "_ZPM"));
            assertEquals(VoltageTier.ZPM, zpm.tier, zpm.name());
            assertEquals(luv.id + "_zpm", zpm.blockId());
            assertEquals(luv.recipeMap, zpm.recipeMap, zpm.name());
            assertEquals(luv.inputs, zpm.inputs, zpm.name());
            assertEquals(luv.outputs, zpm.outputs, zpm.name());
            int tank = luv == LvMachineType.COMPRESSOR_LUV ? 28000 : luv == LvMachineType.MIXER_LUV ? 56000 : luv == LvMachineType.FILTER_LUV ? 64000 : luv.tank;
            assertEquals(tank, zpm.tank, zpm.name());
        }
        assertEquals(131072, LvMachineType.MACERATOR_ZPM.voltage());
    }

    @Test
    void zpmCraftsFollowTheTierTable() throws IOException {
        assertEquals("iis:hull_zpm", key("lv/macerator_zpm", "M"));
        assertEquals("#iis:cables/1x/naquadah", key("lv/macerator_zpm", "W"));
        assertEquals("iis:electric_motor_zpm", key("lv/macerator_zpm", "E"));
        assertEquals("#c:circuits/ultimate", key("lv/macerator_zpm", "C"));
        assertEquals("#iis:wires/8x/superconductor", key("lv/polarizer_zpm", "Z"));   // X.COIL_ELECTRIC
        assertEquals("#iis:wires/2x/naquadah", key("lv/electric_furnace_zpm", "C"));  // X.COIL_HEATING
        assertEquals("iis:reinforced_glass", key("lv/electrolyzer_zpm", "G"));        // X.GLASS
        assertEquals("iis:casing_zpm", key("lv/hull_zpm", "M"));
        assertEquals("#c:plates/iridium", key("lv/hull_zpm", "H"));
        assertEquals("#c:plates/polytetrafluoroethylene", key("lv/hull_zpm", "P"));
        assertEquals("#c:plates/iridium", key("lv/casing_zpm", "P"));
        assertEquals("#iis:wires/16x/naquadah", key("lv/battery_buffer_4x4_zpm", "W"));
        assertEquals("iis:hull_luv", key("lv/transformer_luv", "M"));
        assertEquals("#iis:cables/1x/naquadah", key("lv/transformer_luv", "C"));
        assertEquals("iis:reinforced_glass", key("multi/hatch_input_zpm", "G"));
        assertEquals("#iis:cables/1x/naquadah", key("multi/hatch_energy_zpm", "C"));
    }

    /** D121: лучшая схема ZPM, Superconductor, появилась с кристальной цепочкой. */
    @Test
    void theZpmCircuitAssemblerTakesTheSuperconductorCircuit() throws IOException {
        assertEquals("#c:circuits/superconductor", key("lv/circuit_assembler_zpm", "C"));
    }

    /** D17: жилы наквады только на Марсе и астероидах, ждут среза «Космос». Руды и добычи нет, только рецепты; решение меняет владелец. */
    @Test
    void naquadahHasNoOreYet() {
        assertFalse(Files.exists(GEN.resolve("assets/iis/items/ore_naquadah.json")));
        assertTrue(Files.exists(GEN.resolve("assets/iis/items/ingot_naquadah.json")));
    }

    @Test
    void hatchesAndCoilsFollowGt() {
        assertEquals(131072, HatchType.ENERGY_ZPM.voltage);
        assertEquals(64, HatchType.INPUT_BUS_ZPM.slots());
        assertEquals(5400, EbfBlockEntity.HSSG_HEAT);
        assertEquals(7200, EbfBlockEntity.NAQUADAH_HEAT);
    }

    @Test
    void wiresMatchMakeWires() {
        boolean naquadah = false;
        for (CableSpecs.Spec s : CableSpecs.ALL) {
            if (s.id().equals("cable_naquadah_1x")) {
                naquadah = true;
                assertEquals(131072, s.voltage());
                assertEquals(2, s.amperage());
                assertEquals(2, s.loss());
            }
            assertNotEquals("cable_superconductor_1x", s.id(), "superconductor wire is insulated by itself");
            if (s.id().equals("wire_superconductor_8x")) assertEquals(32, s.amperage());
        }
        assertTrue(naquadah);
    }

    /** D114: четыре рецепта сверхпроводника и воздух → жидкий воздух → аргон (GT_CraftingRecipeLoader:331-334, GT_MachineRecipeLoader:1231, 1833-1834). */
    @Test
    void airAndSuperconductorHaveSources() throws IOException {
        for (String wire : new String[]{"naquadah", "yttrium_barium_cuprate", "niobium_titanium", "vanadium_gallium"}) {
            assertEquals("#iis:wires/1x/" + wire, key("wire/superconductor_" + wire, "C"));
        }
        assertEquals(2000, recipe("processing/compressor/air_1").getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(53, count(recipe("processing/centrifuge/liquid_air"), 0));
        assertEquals(4, recipe("processing/centrifuge/liquid_air").getAsJsonArray("outputs").size());
        assertEquals(4000, LvMachineType.COMPRESSOR.tank);
    }

    @Test
    void quantumChainReachesUltimate() throws IOException {
        JsonObject mf = recipe("processing/circuit_assembler/circuit_quantummainframe");
        assertEquals("iis:circuit_masterquantumcomputer", ingredient(mf, 1));
        assertEquals(4, count(mf, 1));
        assertEquals(7680, mf.get("eut").getAsInt());
        assertEquals(1600, mf.get("duration").getAsInt());
        JsonObject proc = recipe("processing/circuit_assembler/circuit_quantumprocessor");
        assertEquals("iis:circuit_board_fiberglass", ingredient(proc, 0));
        assertEquals("iis:chip_quantum_cpu", ingredient(proc, 1));
        assertEquals(2400, proc.get("eut").getAsInt());
        assertEquals(50, recipe("processing/chemical_reactor/wafer_quantum_cpu_radon").getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(2, count(recipe("processing/chemical_reactor/wafer_quantum_cpu_eye"), 1));
        assertEquals(5, recipe("processing/cutter/chip_quantum_cpu").getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals("#c:circuits/ultimate", key("lv/scanner_luv", "C"));
    }

    @Test
    void assemblyLineRecipesKeepGtOrder() throws IOException {
        JsonObject motor = recipe("processing/assembly_line/electric_motor_zpm");
        assertEquals("#iis:long_rods/neodymium_magnetic", ingredient(motor, 0));
        assertEquals(2, count(motor, 0));
        assertEquals("#iis:long_rods/hsse", ingredient(motor, 1));
        assertEquals("#iis:cables/4x/vanadium_gallium", ingredient(motor, 8));
        assertEquals(24000, motor.get("eut").getAsInt());
        assertEquals(144000, motor.get("temp").getAsInt());
        assertEquals("iis:electric_motor_luv", motor.getAsJsonArray("keep").get(0).getAsString());
        assertEquals(288, motor.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(750, motor.getAsJsonArray("fluid_inputs").get(1).getAsJsonObject().get("amount").getAsInt());
        assertEquals("iis:pipe_medium_high_pressure", ingredient(recipe("processing/assembly_line/electric_pump_zpm"), 1));
        assertEquals(1152, recipe("processing/assembly_line/robot_arm_zpm").getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        JsonObject field = recipe("processing/assembly_line/field_generator_luv");
        assertEquals("iis:quantum_star", ingredient(field, 2));
        assertEquals("iis:circuit_masterquantumcomputer", ingredient(field, 4));
        assertEquals(6000, field.get("eut").getAsInt());
    }
}
