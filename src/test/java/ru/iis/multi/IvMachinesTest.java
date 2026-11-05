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

/**
 * Уровень IV (D103) по GT5-II: Machine_IV_*, Tier.ELECTRIC[5], GT_MetaTileEntity_BasicMachine_GT_Recipe для mTier 5,
 * GT_MetaGenerated_Item_01:498–689 (детали IV, датчики), GT_MachineRecipeLoader:1031, 2994, 3452, 3501 (ванадий-галлий,
 * силикон, бутадиен-стирольный каучук).
 */
class IvMachinesTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject recipe(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    private static String key(String rel, String letter) throws IOException {
        return recipe(rel).getAsJsonObject("key").get(letter).getAsString();
    }

    @Test
    void everyEvMachineHasAnIvTwin() {
        for (LvMachineType ev : LvMachineType.values()) {
            if (ev.tier != VoltageTier.EV) continue;
            String ivName = ev.name().replace("_EV", "_IV");
            LvMachineType iv = LvMachineType.valueOf(ivName);
            assertEquals(VoltageTier.IV, iv.tier, iv.name());
            assertEquals(ev.id + "_iv", iv.blockId());
            assertEquals(ev.recipeMap, iv.recipeMap, iv.name());
            assertEquals(ev.inputs, iv.inputs, iv.name());
            // GT: у измельчителя IV четыре выхода, у смесителя бак 40000, у наливщика 80000
            assertEquals(ev == LvMachineType.MACERATOR_EV ? 4 : ev.outputs, iv.outputs, iv.name());
            int tank = ev == LvMachineType.MIXER_EV ? 40000 : ev == LvMachineType.FLUID_CANNER_EV ? 80000 : ev == LvMachineType.FILTER_EV ? 48000 : ev.tank;
            assertEquals(tank, iv.tank, iv.name());
        }
        assertEquals(8192, LvMachineType.MACERATOR_IV.voltage());
    }

    @Test
    void ivCraftingResolvesTier5Parts() throws IOException {
        assertEquals("iis:hull_iv", key("lv/macerator_iv", "M"));
        assertEquals("#c:circuits/elite", key("lv/macerator_iv", "C"));
        assertEquals("#iis:cables/1x/platinum", key("lv/macerator_iv", "W"));
        assertEquals("#iis:wires/2x/tungsten_steel", key("lv/electric_furnace_iv", "C"));
        assertEquals("#c:rods/vanadium_gallium", key("lv/polarizer_iv", "S"));
        assertEquals("#iis:wires/1x/osmium", key("lv/electrolyzer_iv", "I"));
        assertEquals("iis:emitter_iv", key("lv/laser_engraver_iv", "E"));
        assertEquals("iis:electric_pump_iv", key("lv/distillery_iv", "P"));
        assertEquals("#iis:rings/styrene_butadiene_rubber", key("lv/electric_pump_iv_sbr", "O"));
        assertEquals("#c:plates/silicone", key("lv/conveyor_module_iv_silicone", "R"));
        assertEquals("#iis:wires/16x/annealed_copper", key("lv/electric_motor_iv", "W"));
        assertEquals("minecraft:ender_eye", key("lv/emitter_iv", "Q"));
        assertEquals("#c:rods/osmium", key("lv/sensor_iv", "S"));
        assertEquals("#c:gems/quartzite", key("lv/sensor_lv", "Q"));
        assertEquals("iis:casing_iv", key("lv/hull_iv", "M"));
        assertEquals("#iis:cables/1x/tungsten", key("lv/hull_iv", "C"));
        assertEquals("#iis:wires/16x/tungsten", key("lv/battery_buffer_4x4_iv", "W"));
    }

    @Test
    void hatchesMvToIvFollowGt() {
        // GT_MetaTileEntity_Hatch#getSlots, GT_MetaTileEntity_Hatch_Input#countCapacity, muffler 0.7^(tier - 1)
        assertEquals(4, ru.iis.multi.block.HatchType.INPUT_BUS.slots());
        assertEquals(9, ru.iis.multi.block.HatchType.INPUT_BUS_MV.slots());
        assertEquals(36, ru.iis.multi.block.HatchType.OUTPUT_BUS_IV.slots());
        assertEquals(16000, ru.iis.multi.block.HatchType.INPUT_HATCH.tank());
        assertEquals(32000, ru.iis.multi.block.HatchType.INPUT_HATCH_HV.tank());
        assertEquals(64000, ru.iis.multi.block.HatchType.OUTPUT_HATCH_EV.tank());
        assertEquals(128000, ru.iis.multi.block.HatchType.INPUT_HATCH_IV.tank());
        assertSame(ru.iis.multi.block.HatchType.MUFFLER, ru.iis.multi.block.HatchType.MUFFLER_IV.kind());
        assertSame(ru.iis.multi.block.HatchType.ENERGY_EV, ru.iis.multi.block.HatchType.ENERGY_EV.kind());
        assertEquals(4, ru.iis.multi.block.HatchType.ENERGY_EV.tier);
        assertEquals(2400, ru.iis.pollution.PollutionLogic.mufflerOutput(10000, 5));   // (int) (10000 × 0.7⁴)
    }

    @Test
    void ivMaterialChains() throws IOException {
        JsonObject vg = recipe("processing/blast/vanadium_gallium_ingots");
        assertEquals(4500, vg.get("temperature") != null ? vg.get("temperature").getAsInt() : vg.get("temp").getAsInt());
        assertEquals(480, vg.get("eut").getAsInt());
        JsonObject silicone = recipe("processing/chemical_reactor/gt_3452");
        assertEquals("iis:molten_silicone", silicone.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(1296, silicone.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("amount").getAsInt());
        JsonObject sbr = recipe("processing/chemical_reactor/gt_3501");
        assertEquals("iis:molten_styrenebutadienerubber", sbr.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("id").getAsString());
        JsonObject pgs = recipe("processing/centrifuge/platinum_group_sludge");
        assertEquals(6, pgs.getAsJsonArray("outputs").size());
        assertEquals(6000, pgs.getAsJsonArray("outputs").get(5).getAsJsonObject().get("chance").getAsInt());
        assertEquals(4500, ru.iis.multi.block.EbfBlockEntity.TUNGSTENSTEEL_HEAT);
    }
}
