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
    void pressHeaterOven() throws IOException {
        assertRecipe("processing/forming_press/coin_doge", "iis:coin_doge", 4, 100, 16, "iis:mold_credit");
        assertRecipe("processing/forming_press/arrow_head_glass_empty", "iis:arrow_head_glass_empty", 1, 64, 4, "iis:mold_arrow");
        JsonObject heat = recipe("processing/fluid_heater/steam_from_water");
        assertEquals(6, heat.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(960, heat.getAsJsonArray("fluid_outputs").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(1, heat.get("circuit").getAsInt());
        assertEquals(32, heat.get("eut").getAsInt());
        assertEquals(0, LvMachineType.FLUID_HEATER.outputs);
        assertEquals(8000, LvMachineType.FLUID_HEATER_MV.tank);
        // духовка GT — та же печь на ванильной плавке, что и электропечь
        assertNull(LvMachineType.ELECTRIC_OVEN.recipeMap);
        assertEquals("#iis:wires/2x/cupronickel", recipe("lv/electric_oven_mv").getAsJsonObject("key").get("C").getAsString());
    }

    private static void assertArc(String form, String out, int count, int duration) throws IOException {
        JsonObject r = recipe("processing/arc_furnace/" + form);
        JsonObject o = r.getAsJsonArray("outputs").get(0).getAsJsonObject();
        assertEquals(out, o.get("id").getAsString(), form);
        assertEquals(count, o.get("count").getAsInt(), form);
        assertEquals(duration, r.get("duration").getAsInt(), form);
        assertEquals(96, r.get("eut").getAsInt(), form);
        JsonObject ox = r.getAsJsonArray("fluid_inputs").get(0).getAsJsonObject();
        assertEquals("iis:oxygen", ox.get("ingredient").getAsString(), form);
        assertEquals(duration, ox.get("amount").getAsInt(), form);
    }

    @Test
    void arcFurnaceFollowsReverseArcSmelting() throws IOException {
        assertEquals(6, LvMachineType.ARC_FURNACE_MV.outputs);
        assertEquals(16000, LvMachineType.ARC_FURNACE.tank);
        // железо и медь в дуге — кованое железо и отожжённая медь; время = масса × количество, не меньше 16
        assertArc("plate_iron", "iis:ingot_wrought_iron", 1, 56);
        assertArc("ingot_iron", "iis:ingot_wrought_iron", 1, 56);
        assertArc("dust_copper", "iis:ingot_annealed_copper", 1, 63);
        // полслитка — 4 самородка (4,5 вниз), ротор 4¼ — 38 самородков, огромная труба — 12 слитков
        assertArc("rod_steel", "iis:nugget_steel", 4, 27);
        assertArc("rotor_tin", "iis:nugget_tin", 38, 501);
        assertArc("pipe_huge_steel", "iis:ingot_steel", 12, 660);
        // горючее — в золу (⅛), не металлы (рубин) — без рецепта; слиток стали в дуге не меняется — без рецепта
        assertArc("plate_wood", "iis:tiny_dust_ash", 1, 16);
        assertFalse(Files.exists(RECIPES.resolve("processing/arc_furnace/gem_ruby.json")));
        assertFalse(Files.exists(RECIPES.resolve("processing/arc_furnace/ingot_steel.json")));
    }

    @Test
    void laserEngraverLensesEmitters() throws IOException {
        assertEquals(GtRecipeMap.LASER_ENGRAVER, LvMachineType.LASER_ENGRAVER_MV.recipeMap);
        // ProcessingLens: пластина → линза + малая кучка пыли (масса / 2, 480 EU/t — HV); изысканный самоцвет → линза + 2 пыли (масса, 24)
        JsonObject plate = recipe("processing/lathe/lens_ruby");
        assertEquals(480, plate.get("eut").getAsInt());
        assertEquals("iis:lens_ruby", plate.getAsJsonArray("outputs").get(0).getAsJsonObject().get("id").getAsString());
        JsonObject exq = recipe("processing/lathe/lens_from_exquisite_ruby");
        assertEquals(24, exq.get("eut").getAsInt());
        assertEquals(2, exq.getAsJsonArray("outputs").get(1).getAsJsonObject().get("count").getAsInt());
        // белые линзы (craftingLensWhite) — по цвету материала; рубин не белый
        String white = Files.readString(RECIPES.getParent().resolve("tags/item/crafting_lens/white.json"));
        assertTrue(white.contains("iis:lens_diamond") && !white.contains("iis:lens_ruby"), white);
        assertRecipe("processing/laser_engraver/chiseled_stone_bricks", "minecraft:chiseled_stone_bricks", 1, 50, 16, "#iis:crafting_lens/white");
        assertRecipe("processing/laser_engraver/feather_ruby", "minecraft:feather", 4, 32, 8, "iis:lens_ruby");
        // излучатель: в сборщике схема 5, 80 тиков, 30 EU/t; на верстаке «SSC/WQS/CWS»
        JsonObject em = recipe("processing/assembler/emitter_mv");
        assertEquals(5, em.get("circuit").getAsInt());
        assertEquals("minecraft:quartz", recipe("lv/emitter_mv").getAsJsonObject("key").get("Q").getAsString());
        assertEquals("iis:emitter_lv", recipe("lv/laser_engraver_lv").getAsJsonObject("key").get("E").getAsString());
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
