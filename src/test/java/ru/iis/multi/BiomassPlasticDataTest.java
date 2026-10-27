package ru.iis.multi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Биомасса → пластик и клей, сборщик схем до схемы HV, корпус HV (D92): рецепты в сгенерированных данных сверены с GT5. */
class BiomassPlasticDataTest {
    private static final Path DATA = Path.of("src/generated/resources/data/iis/recipe");
    private static final Path GEN = DATA.resolve("processing");

    private static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    private static JsonObject rec(String rel) throws IOException {
        return json(GEN.resolve(rel));
    }

    private static JsonObject fin(JsonObject r) {
        return r.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject();
    }

    private static JsonObject fout(JsonObject r) {
        return r.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject();
    }

    @Test
    void bioChaffAndPlantBall() throws IOException {
        // IIS 1 config/ic2/macerator.ini через GT_ModHandler#addIC2RecipesToGT: 300 тиков, 2 EU/t
        JsonObject leaves = rec("macerator/bio_chaff_leaves.json");
        assertEquals(8, leaves.getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(300, leaves.get("duration").getAsInt());
        assertEquals(2, leaves.get("eut").getAsInt());
        assertEquals(16, rec("macerator/bio_chaff_wheat_seeds.json").getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(1, rec("macerator/bio_chaff_plant_ball.json").getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        // у тростника и пшеницы в GT свои рецепты измельчителя, биоотходов из них нет
        assertFalse(Files.exists(GEN.resolve("macerator/bio_chaff_wheat.json")));
        assertEquals("minecraft:sugar", rec("macerator/sugar_cane.json").getAsJsonArray("outputs").get(0).getAsJsonObject().get("id").getAsString());
        JsonObject ball = rec("compressor/plant_ball_saplings.json");
        assertEquals(8, ball.getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals("iis:plant_ball", ball.getAsJsonArray("outputs").get(0).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void breweryBiomass() throws IOException {
        // GT_RecipeAdder#addBrewingRecipe:784 — 750 л → 750 л, 128 тиков, 4 EU/t
        for (String w : new String[]{"water", "distilled_water"}) {
            JsonObject r = rec("brewery/biomass_" + w + ".json");
            assertEquals(750, fin(r).get("amount").getAsInt());
            assertEquals("iis:biomass", fout(r).get("id").getAsString());
            assertEquals(750, fout(r).get("amount").getAsInt());
            assertEquals(128, r.get("duration").getAsInt());
            assertEquals(4, r.get("eut").getAsInt());
        }
        // дистиллятор (импорт chem.py): 40 л биомассы → 12 л этанола
        assertEquals("iis:bioethanol", fout(rec("distillery/gt_713.json")).get("id").getAsString());
    }

    @Test
    void polymerization() throws IOException {
        // GT_RecipeAdder#addDefaultPolymerizationRecipes:273-276
        JsonObject o2 = rec("chemical_reactor/plastic_oxygen_cell.json");
        assertEquals(144, fin(o2).get("amount").getAsInt());
        assertEquals(216, fout(o2).get("amount").getAsInt());
        assertEquals("iis:molten_plastic", fout(o2).get("id").getAsString());
        assertEquals(1, o2.get("circuit").getAsInt());
        assertEquals(1500, fout(rec("chemical_reactor/plastic_ethylene_cell_oxygen.json")).get("amount").getAsInt());
        assertEquals(1000, fout(rec("chemical_reactor/plastic_ethylene_cell_air.json")).get("amount").getAsInt());
        assertEquals(288, fout(rec("chemical_reactor/plastic_air_cell.json")).get("amount").getAsInt());
        assertTrue(Files.exists(GEN.resolve("fluid_solidifier/plate_plastic.json")));
    }

    @Test
    void glueAndPhenolicBoard() throws IOException {
        JsonObject resin = rec("centrifuge/sticky_resin.json");   // :1887
        assertEquals(100, fout(resin).get("amount").getAsInt());
        assertEquals(1000, resin.getAsJsonArray("outputs").get(1).getAsJsonObject().get("chance").getAsInt());
        JsonObject board = rec("assembler/circuit_board_phenolic.json");   // :444
        assertEquals(100, fin(board).get("amount").getAsInt());
        assertEquals("iis:mold_plate", board.getAsJsonArray("keep").get(0).getAsString());
        JsonObject craft = json(DATA.resolve("lv/circuit_board_phenolic.json"));   // :442
        assertEquals(8, craft.getAsJsonObject("result").get("count").getAsInt());
        // древесная масса: бревно → 6 + 80 % ещё одна (ProcessingLog:24)
        JsonArray logOut = rec("macerator/log_oak.json").getAsJsonArray("outputs");
        assertEquals(6, logOut.get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(8000, logOut.get(1).getAsJsonObject().get("chance").getAsInt());
    }

    @Test
    void circuitAssemblerChain() throws IOException {
        // :555-564, в ИИС 1 только паяльный сплав (useonlygoodsolderingmaterials=true) — 72 л
        JsonObject adv = rec("circuit_assembler/circuit_advanced.json");
        assertEquals(800, adv.get("duration").getAsInt());
        assertEquals(28, adv.get("eut").getAsInt());
        assertEquals(5, adv.getAsJsonArray("inputs").size());
        assertEquals("iis:molten_solderingalloy", fin(adv).get("ingredient").getAsString());
        assertEquals(72, fin(adv).get("amount").getAsInt());
        assertEquals(400, rec("circuit_assembler/circuit_integrated_good.json").get("duration").getAsInt());
        assertEquals(200, rec("circuit_assembler/circuit_basic.json").get("duration").getAsInt());
        JsonObject tr = rec("assembler/transistor.json");   // :487
        assertEquals(8, tr.getAsJsonArray("outputs").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(144, fin(tr).get("amount").getAsInt());
    }

    @Test
    void hullHvAndControllers() throws IOException {
        JsonObject hull = rec("assembler/hull_hv.json");   // :1407
        assertEquals(288, fin(hull).get("amount").getAsInt());
        assertEquals("#iis:cables/1x/gold", hull.getAsJsonArray("inputs").get(0).getAsJsonObject().get("ingredient").getAsString());
        assertEquals("#c:plates/plastic", json(DATA.resolve("lv/hull_hv.json")).getAsJsonObject("key").get("P").getAsString());
        assertEquals("#c:circuits/advanced", json(DATA.resolve("multi/large_boiler_steel.json")).getAsJsonObject("key").get("C").getAsString());
        assertEquals("iis:hull_hv", json(DATA.resolve("multi/large_steam_turbine.json")).getAsJsonObject("key").get("M").getAsString());
    }
}
