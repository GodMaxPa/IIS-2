package ru.iis.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import ru.iis.energy.WorldEnergy;
import ru.iis.lv.LvContent;
import ru.iis.lv.block.CableBlock;
import ru.iis.machine.recipe.GtRecipe;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** AE2 и Ender IO поверх ИИС 2 (D125). Без этих модов тесты проходят сразу: ИИС 2 от них не зависит. */
final class AeioGameTests {
    private AeioGameTests() {}

    private static boolean absent(GameTestHelper h) {
        if (ModList.get().isLoaded("ae2") && ModList.get().isLoaded("enderio")) return false;
        h.succeed();
        return true;
    }

    private static RecipeHolder<?> recipe(ServerLevel level, String id) {
        return level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).orElse(null);
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
    }

    /** Все рецепты ИИС 1 загрузились, и у каждого все входы можно чем-то заполнить. */
    static void recipesLoaded(GameTestHelper h) {
        if (absent(h)) return;
        ServerLevel level = h.getLevel();
        JsonArray ids;
        try (var in = AeioGameTests.class.getResourceAsStream("/iis/aeio_recipes.json")) {
            ids = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (java.io.IOException e) {
            throw h.assertionException("no recipe list: " + e);
        }
        List<String> missing = new ArrayList<>(), empty = new ArrayList<>();
        for (var e : ids) {
            String id = e.getAsString();
            RecipeHolder<?> r = recipe(level, id);
            if (r == null) {
                missing.add(id);
                continue;
            }
            Recipe<?> v = r.value();
            if (v instanceof GtRecipe g) {
                for (var i : g.inputs()) if (!i.ingredient().isCustom() && i.ingredient().getValues().size() == 0) empty.add(id);
            } else if (v.placementInfo().isImpossibleToPlace()) {
                empty.add(id);
            }
        }
        h.assertTrue(missing.isEmpty(), "not loaded (" + missing.size() + "): " + missing);
        h.assertTrue(empty.isEmpty(), "empty ingredient (" + empty.size() + "): " + empty);
        h.succeed();
    }

    /** recipes.remove и выключенные настройками вещи выключены, замены на месте. */
    static void modRecipesOff(GameTestHelper h) {
        if (absent(h)) return;
        ServerLevel level = h.getLevel();
        for (String id : new String[]{"ae2:network/blocks/controller", "ae2:network/blocks/inscribers", "ae2:network/blocks/crystal_processing_charger", "ae2:network/parts/terminals",
                "enderio:alloy_smelter", "enderio:sag_mill", "enderio:energy_conduit", "enderio:stirling_generator"}) {
            h.assertTrue(recipe(level, id) == null, "still on: " + id);
        }
        for (String id : new String[]{"iis:aeio/ae2/crafting/controller", "iis:aeio/ae2/crafting/terminal", "iis:aeio/ae2/gt/mixer/fluix_water", "iis:aeio/enderio/assembler/energy_conduit", "iis:aeio/enderio/gt/blast/dark_steel"}) {
            h.assertTrue(recipe(level, id) != null, "missing: " + id);
        }
        h.succeed();
    }

    /** Что скрыто в ИИС 1, не видно в просмотрщике рецептов. */
    static void hiddenTag(GameTestHelper h) {
        if (absent(h)) return;
        TagKey<Item> hidden = TagKey.create(Registries.ITEM, Identifier.parse("c:hidden_from_recipe_viewers"));
        for (String id : new String[]{"ae2:inscriber", "ae2:charger", "ae2:growth_accelerator", "ae2:certus_quartz_crystal", "enderio:alloy_smelter", "enderio:dark_steel_ingot"}) {
            h.assertTrue(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)).builtInRegistryHolder().is(hidden), "not hidden: " + id);
        }
        h.assertFalse(BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:controller")).builtInRegistryHolder().is(hidden), "controller hidden");
        h.succeed();
    }

    /** Наши сплавы входят в теги, которые читают рецепты Ender IO. */
    static void alloyTags(GameTestHelper h) {
        if (absent(h)) return;
        for (String[] t : new String[][]{{"c:ingots/conductive_alloy", "iis:ingot_conductive_iron"}, {"c:ingots/pulsating_alloy", "iis:ingot_pulsating_iron"}, {"c:ingots/dark_steel", "iis:ingot_dark_steel"}, {"c:ingots/vibrant_alloy", "iis:ingot_vibrant_alloy"}}) {
            h.assertTrue(BuiltInRegistries.ITEM.getValue(Identifier.parse(t[1])).builtInRegistryHolder().is(TagKey.create(Registries.ITEM, Identifier.parse(t[0]))), t[1] + " not in " + t[0]);
        }
        h.succeed();
    }

    /** Провод подключается к приёмнику AE2 и отдаёт ему энергию, пока тот просит. */
    static void cableFeedsAe2(GameTestHelper h) {
        if (absent(h)) return;
        BlockPos src = new BlockPos(0, 2, 1), cable = src.east(), sink = cable.east(), cell = sink.east();
        ServerLevel level = h.getLevel();
        h.setBlock(cable, LvContent.CABLES.get("cable_copper_1x").get());
        h.setBlock(src, LvContent.CABLES.get("cable_copper_1x").get());
        h.setBlock(sink, block("ae2:energy_acceptor"));
        h.setBlock(cell, block("ae2:energy_cell"));
        BlockPos ctrl = cell.east();
        h.setBlock(ctrl, block("ae2:controller"));
        BlockState cs = level.getBlockState(h.absolutePos(cable));
        h.assertTrue(cs.getValue(CableBlock.PROPS.get(Direction.EAST)), "cable not connected to the acceptor");
        long[] total = {0};
        h.onEachTick(() -> {
            total[0] += WorldEnergy.emit(level, h.absolutePos(src), List.of(Direction.EAST), 32, 1);
            BlockState c = level.getBlockState(h.absolutePos(ctrl));
            var prop = c.getBlock().getStateDefinition().getProperty("state");
            // контроллер загорается, только когда сеть получила энергию
            if (total[0] >= 20 && prop != null && "online".equals(c.getValue(prop).toString())) h.succeed();
        });
    }

    /** Провод питает машину Ender IO с конденсатором: энергия в ней растёт. */
    static void cableFeedsEnderIo(GameTestHelper h) {
        if (absent(h)) return;
        BlockPos src = new BlockPos(1, 2, 1), cable = src.east(), sink = cable.east();
        ServerLevel level = h.getLevel();
        h.setBlock(cable, LvContent.CABLES.get("cable_copper_1x").get());
        h.setBlock(src, LvContent.CABLES.get("cable_copper_1x").get());
        h.setBlock(sink, block("enderio:alloy_smelter"));
        BlockPos abs = h.absolutePos(sink);
        // Конденсатор кладём прямо в его ячейку: у мода нет открытого пути, а зависеть от него при сборке ИИС 2 не должна
        try {
            var be = level.getBlockEntity(abs);
            int slot = (int) be.getClass().getMethod("getCapacitorSlotIndex").invoke(be);
            @SuppressWarnings("unchecked")
            var inv = (net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>) be.getClass().getMethod("getInventory").invoke(be);
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                h.assertTrue(inv.insert(slot, net.neoforged.neoforge.transfer.item.ItemResource.of(BuiltInRegistries.ITEM.getValue(Identifier.parse("enderio:basic_capacitor"))), 1, tx) == 1, "capacitor not taken");
                tx.commit();
            }
        } catch (ReflectiveOperationException e) {
            throw h.assertionException("capacitor: " + e);
        }
        h.onEachTick(() -> {
            WorldEnergy.emit(level, h.absolutePos(src), List.of(Direction.EAST), 32, 1);
            var e = level.getCapability(Capabilities.Energy.BLOCK, abs, Direction.WEST);
            if (e != null && e.getAmountAsLong() > 0) h.succeed();
        });
    }
}
