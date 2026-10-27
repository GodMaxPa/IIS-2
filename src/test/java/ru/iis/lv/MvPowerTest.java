package ru.iis.lv;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.energy.VoltageTier;
import ru.iis.energy.logic.GeneratorLogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Генераторы MV, буферы MV и буферы LV 3×3/4×4 (D95) по GT5-II: GT_Loader_MetaTileEntities:489–566, 1164–1186. */
class MvPowerTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject key(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject().getAsJsonObject("key");
    }

    @Test
    void mvGenerators() {
        assertEquals(48000, GeneratorType.STEAM_TURBINE_MV.tank);   // 24000 × mTier
        assertEquals(16000, GeneratorType.DIESEL_MV.tank);
        assertEquals(128, GeneratorType.GAS_TURBINE_MV.voltage());
        // MachineStats.cfg ИИС: SteamTurbine.efficiency.tier.2 = 8, Diesel/GasTurbine.efficiency.tier.2 = 90
        assertEquals(8, GeneratorLogic.steamTurbineLitersPerOperation(VoltageTier.MV.tier()));
        assertEquals(90, GeneratorLogic.combustionEfficiency(VoltageTier.MV.tier()));
        assertEquals(4, GeneratorType.DIESEL_MV.pollution());
        assertEquals(2, GeneratorType.GAS_TURBINE_MV.pollution());
    }

    @Test
    void mvCrafting() throws IOException {
        JsonObject diesel = key("lv/diesel_generator_mv");
        assertEquals("iis:hull_mv", diesel.get("M").getAsString());
        assertEquals("#c:circuits/good", diesel.get("C").getAsString());
        assertEquals("#c:gears/aluminium", diesel.get("G").getAsString());
        assertEquals("#iis:rotors/bronze", key("lv/gas_turbine_mv").get("R").getAsString());
        assertEquals("iis:pipe_medium_steel", key("lv/steam_turbine_mv").get("P").getAsString());
        assertEquals("#iis:wires/1x/copper", key("lv/battery_buffer_1x1_mv").get("W").getAsString());
        assertEquals("#iis:wires/16x/copper", key("lv/battery_buffer_4x4_mv").get("W").getAsString());
        assertEquals("#iis:wires/8x/tin", key("lv/battery_buffer_3x3_lv").get("W").getAsString());
        assertEquals("#iis:wires/16x/tin", key("lv/battery_buffer_4x4_lv").get("W").getAsString());
    }
}
