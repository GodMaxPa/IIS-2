package ru.iis.multi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.CableSpecs;
import ru.iis.lv.LvMachineType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Уровень LuV (D110) по GT5-II: Machine_LuV_*, Tier.ELECTRIC[6], GT_MetaTileEntity_BasicMachine_GT_Recipe для mTier 6, makeWires:1500–1503,
 * ProcessingWire:155–168 (кабели выше EV: фольга и силикон), GT_MachineRecipeLoader:570, 579–583, 2046–2281.
 */
class LuvMachinesTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

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
    void everyIvMachineHasALuvTwin() {
        for (LvMachineType iv : LvMachineType.values()) {
            if (iv.tier != VoltageTier.IV) continue;
            LvMachineType luv = LvMachineType.valueOf(iv.name().replace("_IV", "_LUV"));
            assertEquals(VoltageTier.LUV, luv.tier, luv.name());
            assertEquals(iv.id + "_luv", luv.blockId());
            assertEquals(iv.recipeMap, luv.recipeMap, luv.name());
            assertEquals(iv.inputs, luv.inputs, luv.name());
            assertEquals(iv.outputs, luv.outputs, luv.name());
            int tank = iv == LvMachineType.MIXER_IV ? 48000 : iv == LvMachineType.FLUID_CANNER_IV ? 96000 : iv == LvMachineType.FILTER_IV ? 56000 : iv.tank;
            assertEquals(tank, luv.tank, luv.name());
        }
        assertEquals(32768, LvMachineType.MACERATOR_LUV.voltage());
    }

    @Test
    void luvCraftsFollowTheTierTable() throws IOException {
        assertEquals("iis:hull_luv", key("lv/macerator_luv", "M"));
        assertEquals("#iis:cables/1x/niobium_titanium", key("lv/macerator_luv", "W"));
        assertEquals("iis:electric_motor_luv", key("lv/macerator_luv", "E"));
        assertEquals("#c:circuits/master", key("lv/macerator_luv", "C"));
        assertEquals("iis:casing_luv", key("lv/hull_luv", "M"));
        assertEquals("#iis:cables/1x/vanadium_gallium", key("lv/hull_luv", "C"));
        assertEquals("#c:plates/chrome", key("lv/casing_luv", "P"));
        assertEquals("#iis:wires/16x/vanadium_gallium", key("lv/battery_buffer_4x4_luv", "W"));
        assertEquals("iis:chip_pic", key("lv/transformer_iv", "K"));
    }

    @Test
    void theIvCircuitAssemblerTakesMaster() throws IOException {
        assertEquals("#c:circuits/master", key("lv/circuit_assembler_iv", "C"));
        assertEquals("iis:hull_iv", key("lv/circuit_assembler_iv", "M"));
        assertFalse(Files.exists(RECIPES.resolve("lv/circuit_assembler_luv.json")), "the Ultimate circuit is not ported");
    }

    /** До D110 кабели платины и вольфрама нельзя было изолировать, и уровень IV не собирался. */
    @Test
    void highTierCablesTakeFoilAndSilicone() throws IOException {
        for (String m : new String[]{"platinum", "tungsten", "tungsten_steel", "osmium", "vanadium_gallium", "niobium_titanium", "yttrium_barium_cuprate"}) {
            JsonObject r = recipe("processing/assembler/cable_" + m + "_1x");
            assertEquals("#iis:wires/1x/" + m, ingredient(r, 0));
            assertEquals("#iis:foils/" + m, ingredient(r, 1), m);
            assertEquals("iis:molten_silicone", r.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("ingredient").getAsString());
            assertEquals(72, r.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
            assertEquals(24, r.get("circuit").getAsInt());
        }
        JsonObject big = recipe("processing/assembler/cable_tungsten_8x");
        assertEquals(3, count(big, 1), "cost = 8 / 4 + 1");
    }

    /** Без этих рецептов (нашёл tools/check_reach.py) цепочка от руды рвалась ещё на LV и MV: кованое железо, кольца, морозильник, стеклянная пыль. */
    @Test
    void bootstrapRecipesExist() throws IOException {
        assertEquals("minecraft:iron_nugget", recipe("smelting/nugget_iron_wrought").get("ingredient").getAsString());
        assertEquals("#c:rods/tin", key("part/ring_tin", "X"));
        assertEquals("iis:frost_proof_casing", key("multi/vacuum_freezer", "M"));
        assertEquals("iis:electric_pump_hv", key("multi/vacuum_freezer", "P"));
        assertEquals("minecraft:glass", ingredient(recipe("processing/macerator/glass"), 0));
        assertEquals(4, recipe("dust/to_small_gallium").getAsJsonObject("result").get("count").getAsInt());
        assertEquals(18, recipe("dust/to_tiny_gallium").getAsJsonObject("result").get("count").getAsInt());
    }

    /** D111: GT_MachineRecipeLoader:1827, 1829, 1890; HardRadonRecipe по умолчанию выключен: 100 л, рецепта на 2 л нет. */
    @Test
    void radonComesFromUraniumThroughPlutonium() throws IOException {
        JsonObject centrifuge = recipe("processing/centrifuge/uranium");
        assertEquals(320, centrifuge.get("eut").getAsInt());
        JsonArray outs = centrifuge.getAsJsonArray("outputs");
        assertEquals("iis:tiny_dust_plutonium", outs.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals(200, outs.get(1).getAsJsonObject().get("chance").getAsInt());
        JsonObject radon = recipe("processing/chemical_reactor/gt_1829");
        assertEquals(6, count(radon, 0));
        assertEquals(100, radon.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertFalse(Files.exists(RECIPES.resolve("processing/chemical_reactor/gt_1827.json")), "the hard radon recipe is off");
    }

    @Test
    void fluidFilterChain() throws IOException {
        assertEquals("iis:glass_fiber", ingredient(recipe("processing/assembler/fluid_filter"), 2));
        assertEquals(32, count(recipe("processing/assembler/fluid_filter"), 2));
        assertEquals(7, count(recipe("processing/mixer/borosilicate_glass"), 1));
    }

    @Test
    void luvWiresMatchMakeWires() {
        for (CableSpecs.Spec s : CableSpecs.ALL) {
            if (s.id().equals("wire_hssg_1x") || s.id().equals("wire_vanadium_gallium_1x")) {
                assertEquals(32768, s.voltage());
                assertEquals(4, s.amperage());
                assertEquals(4, s.loss());
            }
            if (s.id().equals("cable_yttrium_barium_cuprate_4x")) {
                assertEquals(16, s.amperage());
                assertEquals(4, s.loss());
            }
        }
    }

    @Test
    void masterCircuitChain() throws IOException {
        JsonObject master = recipe("processing/circuit_assembler/circuit_master");
        assertEquals("#c:frames/aluminium", ingredient(master, 0));
        assertEquals("iis:circuit_elitenanocomputer", ingredient(master, 1));
        assertEquals(4, count(master, 1));
        assertEquals(1920, master.get("eut").getAsInt());
        assertEquals(1600, master.get("duration").getAsInt());
        assertEquals(288, master.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        JsonObject stick = recipe("processing/circuit_assembler/data_stick");
        assertEquals("iis:chip_nand", ingredient(stick, 2));
        assertEquals(32, count(stick, 2));
        JsonObject nand = recipe("processing/laser_engraver/wafer_nand");
        assertTrue(nand.get("cleanroom").getAsBoolean());
        JsonObject boule = recipe("processing/blast/silicon_boule_glowstone");
        assertEquals(2484, boule.get("temp").getAsInt());
        assertEquals(8000, boule.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
    }

    @Test
    void assemblyLineRecipesKeepGtOrder() throws IOException {
        JsonObject motor = recipe("processing/assembly_line/electric_motor_luv");
        assertEquals("#iis:long_rods/neodymium_magnetic", ingredient(motor, 0));
        assertEquals("#iis:long_rods/hssg", ingredient(motor, 1));
        assertEquals(64, count(motor, 2));
        assertEquals("#iis:cables/1x/yttrium_barium_cuprate", ingredient(motor, 6));
        assertEquals(600, motor.get("duration").getAsInt());
        assertEquals(6000, motor.get("eut").getAsInt());
        assertEquals(144000, motor.get("temp").getAsInt());
        assertEquals("iis:electric_motor_iv", motor.getAsJsonArray("keep").get(0).getAsString());
        JsonArray fluids = motor.getAsJsonArray("fluid_inputs");
        assertEquals(144, fluids.get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals("iis:lubricant", fluids.get(1).getAsJsonObject().get("ingredient").getAsString());
        JsonObject arm = recipe("processing/assembly_line/robot_arm_luv");
        assertEquals("#c:circuits/master", ingredient(arm, 5));
        assertEquals(9, arm.getAsJsonArray("inputs").size());
        assertTrue(recipe("processing/assembly_line/conveyor_module_luv").getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("ingredient").getAsString().contains("styrenebutadienerubber"));
    }
}
