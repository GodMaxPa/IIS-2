package ru.iis.multi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.LvMachineType;
import ru.iis.machine.recipe.GtRecipeMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Машины GT5, которых у ИИС 2 не было в LV (D83): экструдер и формы печи сплавов (ProcessingShaping). */
class NewMvMachinesTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/iis/recipe");

    private static JsonObject recipe(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(rel + ".json"))).getAsJsonObject();
    }

    private static void assertRecipe(String rel, String out, int count, int duration, int eut, String keep) throws IOException {
        JsonObject r = recipe(rel);
        JsonObject o = r.getAsJsonArray("outputs").get(0).getAsJsonObject();
        assertEquals(out, o.get("id").getAsString(), rel);
        assertEquals(count, o.get("count").getAsInt(), rel);
        assertEquals(duration, r.get("duration").getAsInt(), rel);
        assertEquals(eut, r.get("eut").getAsInt(), rel);
        assertEquals(keep, r.getAsJsonArray("keep").get(0).getAsString(), rel);
    }

    @Test
    void extruderMachines() {
        assertEquals(GtRecipeMap.EXTRUDER, LvMachineType.EXTRUDER.recipeMap);
        assertEquals(2, LvMachineType.EXTRUDER.inputs);
        assertEquals(1, LvMachineType.EXTRUDER.outputs);
        assertEquals(VoltageTier.MV, LvMachineType.EXTRUDER_MV.tier);
        assertEquals("extruder_mv", LvMachineType.EXTRUDER_MV.blockId());
    }

    @Test
    void extruderRecipesFollowProcessingShaping() throws IOException {
        String p = "processing/extruder/";
        // сталь (1000 K): множитель 16; пластина — масса × 1 тиков, 8 × 16 = 128 EU/t (только MV)
        assertRecipe(p + "plate_from_ingot_steel", "iis:plate_steel", 1, 55, 128, "iis:shape_extruder_plate");
        // иридий: 2 стержня за массу × 2 тиков, 6 × 16 = 96 EU/t
        assertRecipe(p + "rod_from_ingot_iridium", "iis:rod_iridium", 2, 384, 96, "iis:shape_extruder_rod");
        // 2 оловянных слитка → пустая капсула (GT Cell_Empty), 128 тиков, 32 EU/t — доступно и на LV
        assertRecipe(p + "cell_from_ingot_tin", "iis:capsule", 1, 128, 32, "iis:shape_extruder_cell");
        assertRecipe(p + "casing_from_ingot_steel", "iis:steel_item_casing", 2, 32, 48, "iis:shape_extruder_casing");
        assertRecipe(p + "pipe_medium_from_ingot_steel", "iis:pipe_medium_steel", 1, 24, 128, "iis:shape_extruder_pipe_medium");
        assertEquals(3, recipe(p + "pipe_medium_from_ingot_steel").getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
        // слиток из слитка того же материала не делается, из магнитного железа — делается
        assertFalse(Files.exists(RECIPES.resolve(p + "ingot_from_ingot_steel.json")));
        assertRecipe(p + "ingot_from_ingot_iron_magnetic", "minecraft:iron_ingot", 1, 10, 64, "iis:shape_extruder_ingot");
        // стекло (NO_SMASHING): из пыли, бутылка 32 тика 16 EU/t
        assertRecipe(p + "bottle_from_dust_glass", "minecraft:glass_bottle", 1, 32, 16, "iis:shape_extruder_bottle");
    }

    @Test
    void alloySmelterMolds() throws IOException {
        String p = "processing/alloy_smelter/";
        // 2 слитка → пластина за массу × 2 тиков, 2 × 16 = 32 EU/t
        assertRecipe(p + "plate_from_ingot_steel", "iis:plate_steel", 1, 110, 32, "iis:mold_plate");
        assertRecipe(p + "casing_from_ingot_steel", "iis:steel_item_casing", 3, 128, 16, "iis:mold_casing");
        assertRecipe(p + "anvil_from_ingot_iron", "minecraft:anvil", 1, 512, 64, "iis:mold_anvil");
        assertEquals(31, recipe(p + "anvil_from_ingot_iron").getAsJsonArray("inputs").get(0).getAsJsonObject().get("count").getAsInt());
    }
}
