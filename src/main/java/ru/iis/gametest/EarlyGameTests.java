package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import ru.iis.world.EarlyGame;

/** Правки владельца 08.11 (D142). */
final class EarlyGameTests {
    private EarlyGameTests() {}

    /** Каменные инструменты слабее кремниевых, кремниевые как ванильные каменные; рецепты ИИС 1 на месте, ванильные выключены. */
    static void toolsAndRecipes(GameTestHelper h) {
        ItemStack stone = new ItemStack(Items.STONE_PICKAXE), flint = new ItemStack(EarlyGame.FLINT_PICKAXE.get());
        h.assertTrue(stone.getMaxDamage() == EarlyGame.STONE_DURABILITY && flint.getMaxDamage() == 131, "прочность камня/кремня " + stone.getMaxDamage() + "/" + flint.getMaxDamage());
        var s = Blocks.STONE.defaultBlockState();
        h.assertTrue(stone.getDestroySpeed(s) == EarlyGame.STONE_SPEED && flint.getDestroySpeed(s) == 4.0F, "скорость камня/кремня " + stone.getDestroySpeed(s) + "/" + flint.getDestroySpeed(s));
        h.assertTrue(flint.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()) && !flint.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()), "кремень добывает не как камень");
        var recipes = h.getLevel().recipeAccess();
        for (String id : new String[]{"iis:wood/stick_saw", "iis:wood/chest_logs", "iis:wood/oak_slab_saw", "iis:stone/stone_slab_saw", "iis:vanilla/hopper", "iis:vanilla/iron_bars",
                "iis:tool/flint_pickaxe", "iis:vanilla/flint_from_gravel", "minecraft:stick"}) {
            h.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).isPresent(), "нет рецепта " + id);
        }
        for (String id : new String[]{"minecraft:oak_slab", "minecraft:hopper", "minecraft:iron_bars"}) {
            h.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).isEmpty(), "ванильный рецепт не выключен: " + id);
        }
        var stick = recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse("minecraft:stick"))).get().value();
        h.assertTrue(stick instanceof net.minecraft.world.item.crafting.ShapedRecipe sr && sr.assemble(net.minecraft.world.item.crafting.CraftingInput.of(1, 2,
                java.util.List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS)))).getCount() == 2, "две доски дают не 2 палки");
        h.succeed();
    }

    /** Вода не бесконечная: между двумя источниками третий не появляется. */
    static void finiteWater(GameTestHelper h) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 3; z++) h.setBlock(new BlockPos(x + 1, 1, z + 1), Blocks.STONE);
        for (int x = 0; x < 3; x++) h.setBlock(new BlockPos(x + 2, 1, 2), Blocks.AIR);
        h.setBlock(new BlockPos(2, 1, 2), Blocks.WATER);
        h.setBlock(new BlockPos(4, 1, 2), Blocks.WATER);
        h.runAtTickTime(60, () -> {
            var st = h.getBlockState(new BlockPos(3, 1, 2));
            h.assertTrue(!(st.getFluidState().isSource()), "между источниками вырос третий");
            h.succeed();
        });
    }
}
