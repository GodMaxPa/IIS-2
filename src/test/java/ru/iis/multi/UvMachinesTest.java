package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.CableSpecs;
import ru.iis.lv.LvMachineType;
import ru.iis.multi.block.EbfBlockEntity;
import ru.iis.multi.block.FusionReactorBlockEntity;
import ru.iis.multi.block.HatchType;
import ru.iis.multi.block.ProcessingArrayBlockEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Уровень UV (D121) по GT5-II: Machine_UV_*, Tier.ELECTRIC[8], GT_MetaTileEntity_BasicMachine_GT_Recipe для mTier 8, makeWires:1507,
 * GT_MachineRecipeLoader:453, 523-533, 590-597, 1051-1052, 1063-1089, 2070-2359, 2436-2464, 2566-2593.
 */
class UvMachinesTest {
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

    private static int fluidIn(JsonObject r, int i) {
        return r.getAsJsonArray("fluid_inputs").get(i).getAsJsonObject().get("amount").getAsInt();
    }

    @Test
    void everyZpmMachineHasAUvTwin() {
        for (LvMachineType zpm : LvMachineType.values()) {
            if (zpm.tier != VoltageTier.ZPM) continue;
            LvMachineType uv = LvMachineType.valueOf(zpm.name().replace("_ZPM", "_UV"));
            assertEquals(VoltageTier.UV, uv.tier, uv.name());
            assertEquals(zpm.id + "_uv", uv.blockId());
            assertEquals(zpm.recipeMap, uv.recipeMap, uv.name());
            assertEquals(zpm.inputs, uv.inputs, uv.name());
            assertEquals(zpm.outputs, uv.outputs, uv.name());
        }
        assertEquals(524288, LvMachineType.MACERATOR_UV.voltage());
        assertEquals(32000, LvMachineType.COMPRESSOR_UV.tank);
        assertEquals(64000, LvMachineType.MIXER_UV.tank);
        assertEquals(80000, LvMachineType.FLUID_CANNER_UV.tank);
    }

    @Test
    void uvCraftsFollowTheTierTable() throws IOException {
        assertEquals("iis:hull_uv", key("lv/macerator_uv", "M"));
        assertEquals("#iis:wires/4x/naquadah_alloy", key("lv/macerator_uv", "W"));
        assertEquals("iis:electric_motor_uv", key("lv/macerator_uv", "E"));
        assertEquals("#c:circuits/superconductor", key("lv/macerator_uv", "C"));
        assertEquals("#iis:wires/16x/superconductor", key("lv/polarizer_uv", "Z"));   // X.COIL_ELECTRIC
        assertEquals("#iis:wires/2x/naquadah_alloy", key("lv/electric_furnace_uv", "C"));  // X.COIL_HEATING
        assertEquals("iis:reinforced_glass", key("lv/electrolyzer_uv", "G"));        // X.GLASS
        assertEquals("#c:circuits/infinite", key("lv/circuit_assembler_uv", "C"));   // X.BETTER_CIRCUIT
        assertEquals("iis:casing_uv", key("lv/hull_uv", "M"));
        assertEquals("#c:plates/osmium", key("lv/hull_uv", "H"));
        assertEquals("#iis:wires/4x/naquadah_alloy", key("lv/hull_uv", "C"));
        assertEquals("#c:plates/osmium", key("lv/casing_uv", "P"));
        assertEquals("#iis:wires/16x/naquadah_alloy", key("lv/battery_buffer_4x4_uv", "W"));
        assertEquals("iis:hull_zpm", key("lv/transformer_zpm", "M"));
        assertEquals("iis:chip_hpic", key("lv/transformer_uv", "K"));
        assertEquals("#iis:wires/4x/naquadah_alloy", key("multi/hatch_energy_uv", "C"));
        assertEquals("iis:reinforced_glass", key("multi/hatch_input_uv", "G"));
    }

    @Test
    void hatchesCoilAndStoresFollowGt() {
        assertEquals(524288, HatchType.ENERGY_UV.voltage);
        assertEquals(81, HatchType.INPUT_BUS_UV.slots());
        assertEquals(9001, EbfBlockEntity.NAQUADAH_ALLOY_HEAT);
        assertEquals(320006000L, FusionReactorBlockEntity.STORE_MARK2);
        assertEquals(640010000L, FusionReactorBlockEntity.STORE_MARK3);
        assertEquals(70, ProcessingArrayBlockEntity.ADVANCED_MIN_CASINGS);
        assertEquals(64, ProcessingArrayBlockEntity.ADVANCED_MAX_MACHINES);
    }

    @Test
    void naquadahAlloyWireMatchesMakeWires() {
        boolean found = false;
        for (CableSpecs.Spec s : CableSpecs.ALL) {
            if (s.id().equals("cable_naquadah_alloy_1x")) {
                found = true;
                assertEquals(524288, s.voltage());
                assertEquals(2, s.amperage());
                assertEquals(4, s.loss());
            }
        }
        assertTrue(found);
    }

    @Test
    void alloysAndGraviStar() throws IOException {
        JsonObject os = recipe("processing/blast/osmiridium_ingots");
        assertEquals(3, count(os, 0));
        assertEquals(1920, os.get("eut").getAsInt());
        assertEquals(2900, os.get("temp").getAsInt());
        JsonObject naq = recipe("processing/blast/naquadah_alloy_ingots");
        assertEquals(30720, naq.get("eut").getAsInt());
        assertEquals(7200, naq.get("temp").getAsInt());
        assertEquals(288, fluidIn(recipe("processing/autoclave/gravi_star"), 0));
    }

    @Test
    void crystalChainReachesTheSuperconductorCircuit() throws IOException {
        JsonObject raw = recipe("processing/autoclave/raw_crystal_chip_emerald");
        assertEquals(1000, raw.getAsJsonArray("outputs").get(0).getAsJsonObject().get("chance").getAsInt());
        assertEquals(12000, raw.get("duration").getAsInt());
        assertEquals(10000, recipe("processing/laser_engraver/chip_crystal_cpu").get("eut").getAsInt());
        JsonObject mf = recipe("processing/circuit_assembler/circuit_crystalmainframe");
        assertEquals("iis:circuit_ultimatecrystalcomputer", ingredient(mf, 1));
        assertEquals("#iis:wires/1x/superconductor", ingredient(mf, 5));
        assertEquals(30720, mf.get("eut").getAsInt());
        JsonObject proc = recipe("processing/circuit_assembler/circuit_crystalprocessor");
        assertEquals("iis:circuit_board_multifiberglass", ingredient(proc, 0));
        assertEquals(9600, proc.get("eut").getAsInt());
    }

    @Test
    void wetwareChainReachesTheInfiniteCircuit() throws IOException {
        JsonObject neuro = recipe("processing/assembly_line/chip_neuro_cpu");
        assertEquals("iis:circuit_crystalmainframe", neuro.getAsJsonArray("keep").get(0).getAsString());
        assertEquals(72000, neuro.get("temp").getAsInt());
        assertEquals(3, neuro.getAsJsonArray("fluid_inputs").size());
        JsonObject mf = recipe("processing/assembly_line/circuit_wetwaremainframe");
        assertEquals(300000, mf.get("eut").getAsInt());
        assertEquals(10, mf.getAsJsonArray("inputs").size());
        assertEquals(10000, fluidIn(mf, 1));
    }

    @Test
    void uvPartsKeepGtOrder() throws IOException {
        JsonObject motor = recipe("processing/assembly_line/electric_motor_uv");
        assertEquals("iis:block_neodymium_magnetic", ingredient(motor, 0));
        assertEquals("#iis:long_rods/neutronium", ingredient(motor, 1));
        assertEquals("#iis:wires/1x/superconductor", ingredient(motor, 4));
        assertEquals(100000, motor.get("eut").getAsInt());
        assertEquals(288000, motor.get("temp").getAsInt());
        assertEquals("iis:electric_motor_zpm", motor.getAsJsonArray("keep").get(0).getAsString());
        assertEquals(1296, fluidIn(motor, 0));
        assertEquals(2000, fluidIn(motor, 1));
        assertEquals("iis:pipe_large_high_pressure", ingredient(recipe("processing/assembly_line/electric_pump_uv"), 1));
        assertEquals(2304, fluidIn(recipe("processing/assembly_line/robot_arm_uv"), 0));
        JsonObject field = recipe("processing/assembly_line/field_generator_uv");
        assertEquals("iis:gravi_star", ingredient(field, 2));
        assertEquals(64, count(field, 4));
        assertEquals(14, field.getAsJsonArray("inputs").size());
        JsonObject zpm = recipe("processing/assembly_line/field_generator_zpm");
        assertEquals(4, count(zpm, 2));
        assertEquals(24000, zpm.get("eut").getAsInt());
        assertEquals(14, zpm.getAsJsonArray("inputs").size());
    }

    @Test
    void fusionControllersAndRecipes() throws IOException {
        JsonObject m2 = recipe("processing/assembly_line/fusion_computer_zpm");
        assertEquals("iis:block_europium", m2.getAsJsonArray("keep").get(0).getAsString());
        assertEquals(60000, m2.get("eut").getAsInt());
        JsonObject m3 = recipe("processing/assembly_line/fusion_computer_uv");
        assertEquals("iis:block_americium", m3.getAsJsonArray("keep").get(0).getAsString());
        assertEquals(432000, m3.get("temp").getAsInt());
        JsonObject nt = recipe("processing/fusion/neutronium_from_americium");
        assertEquals(600000000, nt.get("temp").getAsInt());
        assertEquals(1200, nt.get("duration").getAsInt());
        assertEquals(1, nt.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("amount").getAsInt());
    }

    /** Зарядники: GT_Loader_MetaTileEntities:590-594. */
    @Test
    void chargersHaveTheirBatteries() throws IOException {
        assertEquals("#iis:batteries/master", key("auto/battery_charger_ev", "B"));
        assertEquals("iis:energy_orb", key("auto/battery_charger_iv", "B"));
        assertEquals("iis:energy_orb_cluster", key("auto/battery_charger_luv", "B"));
        assertEquals("iis:ultimate_battery", key("auto/battery_charger_uv", "B"));
        assertEquals("#iis:wires/16x/naquadah_alloy", key("auto/battery_charger_uv", "W"));
        assertEquals(16000, fluidIn(recipe("processing/assembly_line/ultimate_battery"), 1));
    }

    /** D113: наквады в выживании всё ещё нет: руда не появилась, UV закрыт ею. */
    @Test
    void naquadahStillHasNoOre() {
        assertFalse(Files.exists(GEN.resolve("assets/iis/items/ore_naquadah.json")));
    }
}
