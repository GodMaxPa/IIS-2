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

/** Генераторы, аккумуляторные буферы HV и трансформатор EV↔HV (D94) по GT5-II: GT_Loader_MetaTileEntities:197–567, 1163–1187. */
class HvPowerTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject key(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject().getAsJsonObject("key");
    }

    @Test
    void hvGenerators() {
        // SteamTurbine.getCapacity = 24000 × mTier; дизель и газовая турбина — 16000
        assertEquals(72000, GeneratorType.STEAM_TURBINE_HV.tank);
        assertEquals(16000, GeneratorType.DIESEL_HV.tank);
        assertEquals(512, GeneratorType.GAS_TURBINE_HV.voltage());
        assertTrue(GeneratorType.STEAM_TURBINE_HV.steam());
        // MachineStats.cfg ИИС: SteamTurbine.efficiency.tier.3 = 9, Diesel/GasTurbine.efficiency.tier.3 = 85
        assertEquals(9, GeneratorLogic.steamTurbineLitersPerOperation(VoltageTier.HV.tier()));
        assertEquals(85, GeneratorLogic.combustionEfficiency(VoltageTier.HV.tier()));
        // getPollution: BASE × 2^(mTier − 1)
        assertEquals(2, GeneratorType.DIESEL.pollution());
        assertEquals(8, GeneratorType.DIESEL_HV.pollution());
        assertEquals(4, GeneratorType.GAS_TURBINE_HV.pollution());
        assertEquals(0, GeneratorType.STEAM_TURBINE_HV.pollution());
        // maxEUStore = V × 40 + 512
        assertEquals(512 * 40 + 512, new GeneratorLogic(512, 512).capacity());
    }

    @Test
    void hvCrafting() throws IOException {
        JsonObject diesel = key("lv/diesel_generator_hv");
        assertEquals("iis:hull_hv", diesel.get("M").getAsString());
        assertEquals("#c:gears/stainless_steel", diesel.get("G").getAsString());
        assertEquals("#iis:rotors/steel", key("lv/gas_turbine_hv").get("R").getAsString());
        assertEquals("iis:pipe_medium_stainless_steel", key("lv/steam_turbine_hv").get("P").getAsString());
        assertEquals("#iis:wires/16x/gold", key("lv/battery_buffer_4x4_hv").get("W").getAsString());
        assertEquals("#iis:wires/8x/gold", key("lv/battery_buffer_3x3_hv").get("W").getAsString());
        JsonObject tr = key("lv/transformer_hv");
        assertEquals("#iis:cables/1x/aluminium", tr.get("C").getAsString());
        assertEquals("#iis:cables/1x/gold", tr.get("B").getAsString());
        assertEquals("iis:coil_small", tr.get("K").getAsString());
        assertEquals("#iis:bolts/steel", key("lv/coil_small").get("D").getAsString());
    }
}
