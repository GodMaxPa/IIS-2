package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.LvMachineType;
import ru.iis.machine.logic.ElectricMachineLogic;
import ru.iis.machine.Overclock;
import ru.iis.multi.block.HatchType;
import ru.iis.multi.logic.BlastLogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** MV-машины, люк и трансформатор MV (D82) по GT5-II: GT_Loader_MetaTileEntities Machine_MV_*, :196, :238. */
class MvMachinesTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject recipe(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    @Test
    void everyLvMachineHasAnMvTwin() {
        for (LvMachineType lv : LvMachineType.values()) {
            if (lv.tier != VoltageTier.LV) continue;
            LvMachineType mv = LvMachineType.valueOf(lv.name() + "_MV");
            assertEquals(VoltageTier.MV, mv.tier, mv.name());
            assertEquals(lv.id + "_mv", mv.blockId());
            assertEquals(lv.recipeMap, mv.recipeMap, mv.name());
            assertEquals(lv.inputs, mv.inputs, mv.name());
            assertEquals(lv.outputs, mv.outputs, mv.name());
            assertEquals(lv.gui, mv.gui, mv.name());
            // GT: бак MV-смесителя 16000 (у LV 8000), остальные — как у LV
            assertEquals(lv == LvMachineType.MIXER ? 16000 : lv.tank, mv.tank, mv.name());
        }
        assertEquals(128, LvMachineType.MACERATOR_MV.voltage());
    }

    @Test
    void mvCraftingResolvesTier2Parts() throws IOException {
        JsonObject mac = recipe("lv/macerator_mv").getAsJsonObject("key");
        assertEquals("iis:hull_mv", mac.get("M").getAsString());
        assertEquals("iis:electric_motor_mv", mac.get("E").getAsString());
        assertEquals("iis:electric_piston_mv", mac.get("P").getAsString());
        assertEquals("#c:circuits/good", mac.get("C").getAsString());
        assertEquals("#iis:cables/1x/copper", mac.get("W").getAsString());
        assertEquals("minecraft:diamond", mac.get("G").getAsString());
        // X.COIL_HEATING у MV — 2x константан, X.ROTOR — бронзовый, у электролизёра MV — серебряный провод
        assertEquals("#iis:wires/2x/cupronickel", recipe("lv/electric_furnace_mv").getAsJsonObject("key").get("C").getAsString());
        assertEquals("#iis:wires/4x/cupronickel", recipe("lv/alloy_smelter_mv").getAsJsonObject("key").get("C").getAsString());
        assertEquals("#iis:rotors/bronze", recipe("lv/mixer_mv").getAsJsonObject("key").get("R").getAsString());
        assertEquals("#iis:wires/1x/silver", recipe("lv/electrolyzer_mv").getAsJsonObject("key").get("I").getAsString());
        assertEquals("#iis:wires/2x/copper", recipe("lv/polarizer_mv").getAsJsonObject("key").get("Z").getAsString());
        JsonObject tr = recipe("lv/transformer_mv").getAsJsonObject("key");
        assertEquals("#iis:cables/1x/gold", tr.get("C").getAsString());
        assertEquals("#iis:cables/1x/copper", tr.get("B").getAsString());
        assertEquals("#iis:cables/1x/copper", recipe("multi/hatch_energy_mv").getAsJsonObject("key").get("C").getAsString());
    }

    @Test
    void mvOverclock() {
        // руда в дробителе: 2 EU/t × 400 → на MV 8 EU/t × 200; 30 EU/t × 400 → 120 EU/t × 200
        assertEquals(new Overclock.Result(8, 200), Overclock.basicMachine(2, 400, VoltageTier.MV, 1));
        assertEquals(new Overclock.Result(120, 200), Overclock.basicMachine(30, 400, VoltageTier.MV, 1));
    }

    @Test
    void energyHatchesAndFluidOutput() {
        assertEquals(32, HatchType.ENERGY.voltage);
        assertEquals(128, HatchType.ENERGY_MV.voltage);
        assertTrue(HatchType.ENERGY_MV.energy());
        assertFalse(HatchType.INPUT_BUS.energy());
        // один люк MV = уровень 2 (рецепты до 128 EU/t), как два люка LV
        assertEquals(2, BlastLogic.machineTier(HatchType.ENERGY_MV.voltage));
        // GT drain(1000 * (int) Math.pow(1.5, mTier)): LV 1000, MV 2000, HV 3000
        assertEquals(1000, ElectricMachineLogic.fluidOutputPerOperation(VoltageTier.LV));
        assertEquals(2000, ElectricMachineLogic.fluidOutputPerOperation(VoltageTier.MV));
        assertEquals(3000, ElectricMachineLogic.fluidOutputPerOperation(VoltageTier.HV));
    }
}
