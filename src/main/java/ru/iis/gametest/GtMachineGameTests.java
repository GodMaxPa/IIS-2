package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import ru.iis.auto.AutoContent;
import ru.iis.auto.MuscleBlockEntity;
import ru.iis.auto.WellBlockEntity;
import ru.iis.lv.LvContent;
import ru.iis.lv.item.ProspectorScannerItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.world.UndergroundFluids;
import ru.iis.world.ore.OreBlocks;
import ru.iis.world.ore.OreHost;

/** Машины GT из книги квестов ИИС 1 (D133): колодец, шахтокопатель, добытчик малых руд, сканер старателя, ломтерезка, трансформатор MAX. */
final class GtMachineGameTests {
    private GtMachineGameTests() {}

    private static void stone(GameTestHelper h, int y0, int y1) {
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) for (int y = y0; y <= y1; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }

    /** Колодец берёт жидкость месторождения чанка, если это вода или нефть; иначе остаётся пустым. */
    static void wellFills(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4);
        ChunkPos cp = ChunkPos.containing(h.absolutePos(at));
        Fluid f = UndergroundFluids.prime(h.getLevel(), cp, 5_000_000);
        h.setBlock(at, AutoContent.WELL.get());
        boolean allowed = f != null && WellBlockEntity.allowed(f);
        h.runAtTickTime(200, () -> {
            var be = (WellBlockEntity) h.getBlockEntity(at, WellBlockEntity.class);
            var tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
            int amount = tag.getIntOr("Amount", 0);
            h.assertTrue(allowed ? amount > 0 : amount == 0, "колодец: жидкость " + f + ", набрано " + amount);
            h.succeed();
        });
    }

    /** Шахтокопатель на силе 64 копает ствол под собой и перед собой, ставит лестницу, булыжник уходит в выход. */
    static void diggerDigs(GameTestHelper h) {
        stone(h, 1, 6);
        BlockPos m = new BlockPos(4, 7, 4);
        h.setBlock(m, AutoContent.PRIMITIVE_MINE_DIGGER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        var be = (MuscleBlockEntity) h.getBlockEntity(m, MuscleBlockEntity.class);
        be.setItem(0, new ItemStack(LvContent.PRIMITIVE_DRILL.get()));
        be.setItem(1, new ItemStack(Items.LADDER, 16));
        be.setItem(2, new ItemStack(Items.COBBLESTONE, 16));
        h.runAtTickTime(1, () -> {
            for (int i = 0; i < 8; i++) be.work(h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE));
        });
        h.runAtTickTime(120, () -> {
            BlockPos below = m.below(), front = m.north().below();
            h.assertTrue(h.getBlockState(below).isAir(), "под машиной не выкопано: " + h.getBlockState(below));
            h.assertTrue(h.getBlockState(front).is(Blocks.LADDER), "нет лестницы перед машиной: " + h.getBlockState(front));
            int cobble = 0;
            for (int i = MuscleBlockEntity.INPUTS; i < MuscleBlockEntity.SLOTS; i++) if (be.getItem(i).is(Items.COBBLESTONE)) cobble += be.getItem(i).getCount();
            h.assertTrue(cobble > 0, "камень не попал в выход");
            h.assertTrue(be.getItem(0).getDamageValue() > 0, "бур не изнашивается");
            h.succeed();
        });
    }

    /** Добытчик малых руд находит малую руду в квадрате 7 × 7 под собой и заделывает дыру булыжником. */
    static void minerFindsSmallOre(GameTestHelper h) {
        stone(h, 1, 6);
        String mat = OreBlocks.allSmall().keySet().iterator().next();
        BlockState small = OreBlocks.getSmall(mat, OreHost.STONE);
        BlockPos ore = new BlockPos(6, 4, 2);
        h.setBlock(ore, small);
        BlockPos m = new BlockPos(4, 7, 4);
        h.setBlock(m, AutoContent.PRIMITIVE_SMALL_ORE_MINER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        var be = (MuscleBlockEntity) h.getBlockEntity(m, MuscleBlockEntity.class);
        be.setItem(0, new ItemStack(LvContent.PRIMITIVE_DRILL.get()));
        be.setItem(1, new ItemStack(Items.COBBLESTONE, 8));
        h.runAtTickTime(1, () -> {
            for (int i = 0; i < 8; i++) be.work(h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE));
        });
        h.runAtTickTime(300, () -> {
            h.assertTrue(h.getBlockState(ore).is(Blocks.COBBLESTONE), "малая руда не добыта: " + h.getBlockState(ore));
            boolean got = false;
            for (int i = MuscleBlockEntity.INPUTS; i < MuscleBlockEntity.SLOTS; i++) got |= !be.getItem(i).isEmpty();
            h.assertTrue(got, "выход пуст");
            h.succeed();
        });
    }

    /** Сканер старателя видит руду в своём чанке. */
    static void prospectorSeesOre(GameTestHelper h) {
        String mat = OreBlocks.all().keySet().iterator().next();
        BlockPos ore = new BlockPos(2, 2, 2);
        h.setBlock(ore, OreBlocks.get(mat, OreHost.STONE));
        var lines = ProspectorScannerItem.scanOres(h.getLevel(), h.absolutePos(ore), 0);
        h.assertTrue(lines.size() >= 2 && !lines.get(1).getString().equals(net.minecraft.network.chat.Component.translatable("item.iis.prospector.nothing").getString()), "сканер: " + lines);
        h.succeed();
    }

    /** D135: вещи Ender IO 2.3 из ИИС 1. Банк конденсаторов отдаёт энергию электролампе, она горит; простая лампа горит от красного сигнала; буфер принимает предметы через возможность. */
    static void eioLegacyBlocks(GameTestHelper h) {
        BlockPos bank = new BlockPos(2, 2, 2), lamp = new BlockPos(3, 2, 2), basic = new BlockPos(6, 2, 2), lever = new BlockPos(6, 2, 3), buffer = new BlockPos(2, 2, 6);
        h.setBlock(bank, ru.iis.compat.AeioContent.CAP_BANK_BASIC.get());
        h.setBlock(lamp, ru.iis.compat.AeioContent.ELECTRIC_LIGHT.get());
        h.setBlock(basic, ru.iis.compat.AeioContent.BASIC_LIGHT.get());
        h.setBlock(buffer, ru.iis.compat.AeioContent.ITEM_BUFFER.get());
        var be = (ru.iis.compat.EioBlocks.PoweredEntity) h.getBlockEntity(bank, ru.iis.compat.EioBlocks.PoweredEntity.class);
        be.energy.set(50_000);
        h.setBlock(lever, Blocks.REDSTONE_BLOCK);
        var items = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, h.absolutePos(buffer), Direction.UP);
        h.assertTrue(items != null && items.size() == 9, "у буфера нет 9 ячеек для труб");
        h.runAtTickTime(10, () -> {
            h.assertTrue(h.getBlockState(lamp).getValue(ru.iis.compat.EioBlocks.LIT), "электролампа не горит от банка");
            h.assertTrue(be.energy.getAmountAsLong() < 50_000, "банк не отдал энергию");
            h.assertTrue(h.getBlockState(basic).getValue(ru.iis.compat.EioBlocks.LIT), "простая лампа не горит от сигнала");
            for (String id : new String[]{"aeio/item_buffer", "aeio/capacitor_bank_basic", "aeio/electric_light", "aeio/basic_light", "processing/bender/spring_small_red_alloy"}) {
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.fromNamespaceAndPath("iis", id));
                h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "нет рецепта " + id);
            }
            h.succeed();
        });
    }

    /** D136: компактная логика вместо Project Red: моды загружены, рецепты ИИС 1 на их детали разобрались, свои рецепты заменены. */
    static void compactLogic(GameTestHelper h) {
        var mods = net.neoforged.fml.ModList.get();
        for (String m : new String[]{"tinyredstone", "tinygates", "little_big_redstone"}) h.assertTrue(mods.isLoaded(m), "мод не загружен: " + m);
        var recipes = h.getLevel().getServer().getRecipeManager();
        for (String id : new String[]{"iis:moremods/logic/tiny_silicon", "iis:moremods/logic/redstone_panel", "iis:moremods/logic/redstone_circuit_board"}) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse(id));
            h.assertTrue(recipes.byKey(key).isPresent(), "нет рецепта " + id);
        }
        for (String id : new String[]{"tinyredstone:silicon_compound", "tinyredstone:redstone_panel", "little_big_redstone:redstone_circuit_board"}) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse(id));
            h.assertTrue(recipes.byKey(key).isEmpty(), "рецепт мода не выключен: " + id);
        }
        h.succeed();
    }

    /** D137: магии нет. Библиотека крепости даёт книги без чар и без зачарованных книг; рецепта стола нет; у библиотекаря нет книжных сделок; зомби с зачарованным мечом его теряет. */
    static void noMagic(GameTestHelper h) {
        var level = h.getLevel();
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Identifier.parse("minecraft:chests/stronghold_library"));
        var table = level.getServer().reloadableRegistries().getLootTable(key);
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, h.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        int items = 0;
        for (int i = 0; i < 40; i++) {
            for (ItemStack s : table.getRandomItems(params)) {
                items++;
                h.assertTrue(!ru.iis.world.NoMagic.magic(s), "в добыче магия: " + s);
                h.assertTrue(!s.has(net.minecraft.core.component.DataComponents.ENCHANTMENTS) || s.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS).isEmpty(), "в добыче чары: " + s);
            }
        }
        h.assertTrue(items > 0, "библиотека пуста");
        var recipe = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse("minecraft:enchanting_table"));
        h.assertTrue(level.getServer().getRecipeManager().byKey(recipe).isEmpty(), "рецепт стола зачарования остался");
        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.VILLAGER_TRADE, Identifier.parse("minecraft:librarian/level_1"));
        var trades = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TRADE).get(tag);
        h.assertTrue(trades.isPresent(), "нет списка сделок библиотекаря");
        for (var t : trades.get()) h.assertTrue(!t.getRegisteredName().contains("enchanted_book"), "сделка с зачарованной книгой: " + t.getRegisteredName());
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        ItemStack sword = new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        var sharp = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
        sword.enchant(sharp, 3);
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, sword);
        zombie.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(4, 2, 4)));
        level.addFreshEntity(zombie);
        h.assertTrue(zombie.getMainHandItem().getEnchantments().isEmpty(), "у зомби остались чары");
        zombie.discard();
        h.succeed();
    }

    /** Ломтерезка и трансформатор MAX есть, рецепты разобрались. */
    static void slicerAndMaxTransformer(GameTestHelper h) {
        for (String id : new String[]{"slicer_lv", "slicer_uv", "adjustable_transformer_max", "well", "primitive_mine_digger", "primitive_small_ore_miner"}) {
            h.assertTrue(BuiltInRegistries.BLOCK.containsKey(Identifier.fromNamespaceAndPath("iis", id)), "нет блока " + id);
        }
        var recipes = h.getLevel().getServer().getRecipeManager();
        for (String id : new String[]{"processing/slicer/sliced_bread", "processing/slicer/raw_fries", "processing/assembler/adjustable_transformer_max", "processing/assembler/prospector_uv",
                "auto/well", "auto/primitive_mine_digger", "auto/primitive_small_ore_miner", "gt/primitive_drill", "gt/gear_stone", "gt/slicer_blade_flat", "food/dough", "processing/fluid_solidifier/cheese"}) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.fromNamespaceAndPath("iis", id));
            h.assertTrue(recipes.byKey(key).isPresent(), "нет рецепта " + id);
        }
        h.succeed();
    }
}
