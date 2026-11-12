package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.iis.bio.BioContent;
import ru.iis.bio.block.ItemChannelBlock;
import ru.iis.bio.block.ItemChannelBlockEntity;
import ru.iis.bio.block.LivingStoreBlockEntity;
import ru.iis.bio.block.StationBlockEntity;
import ru.iis.bio.item.BioItems;
import ru.iis.bio.item.Butchery;
import ru.iis.bio.mutant.Mutants;
import ru.iis.bio.production.Station;

/** Черновик Био Б-2 (D148): мутанты, перенос предметов, руда. */
final class BioDraftGameTests {
    private BioDraftGameTests() {}

    /**
     * Мутант — настоящее животное: без наблюдения и без площадки вивария воздействие не идёт и ничего не расходует;
     * после воздействия и срока проявления овца становится рабочей линией, продукт собирается один раз за цикл,
     * повторное воздействие невозможно.
     */
    static void mutantSheepLine(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        Sheep sheep = h.spawn(EntityType.SHEEP, new BlockPos(4, 2, 4));
        Mutants.Profile p = Mutants.Profile.SHEEP_LONGFIBER;
        h.assertTrue(Mutants.expose(l, sheep, p).endsWith("observe_first"), "exposure without observation");
        sheep.setData(Mutants.DATA.get(), new Mutants.Data("", 0, 0));
        h.assertTrue(Mutants.expose(l, sheep, p).endsWith("no_pad"), "exposure without a vivarium pad");
        h.assertTrue(Mutants.expose(l, sheep, Mutants.Profile.PIG_FLESHY).endsWith("wrong_species"), "pig agent on a sheep");
        l.setBlock(h.absolutePos(new BlockPos(5, 1, 4)), BioContent.VIVARIUM_PAD.get().defaultBlockState(), 3);
        h.assertTrue(Mutants.expose(l, sheep, p).endsWith("exposed"), "exposure near the pad");
        h.assertTrue(Mutants.expose(l, sheep, p).endsWith("already"), "second exposure must not stack");
        h.assertTrue(Mutants.working(sheep) == null && Mutants.collect(l, sheep).isEmpty(), "no product while manifesting");
        // срок проявления истёк
        sheep.setData(Mutants.DATA.get(), new Mutants.Data(p.id, 1, l.getGameTime()));
        Mutants.advance(l, sheep);
        h.assertTrue(Mutants.working(sheep) == p, "working line after manifestation");
        h.assertTrue(sheep.hasCustomName(), "the line is visible by name");
        ItemStack got = Mutants.collect(l, sheep);
        h.assertTrue(got.is(BioItems.LONG_FIBER.get()) && got.getCount() == 3, "product " + got);
        h.assertTrue(Mutants.collect(l, sheep).isEmpty(), "the product regrows, no second collection");
        h.succeed();
    }

    /** Каждый предмет таблицы рецептов Био существует в игре (D150: «толстая жила» была в рецептах, но не в реестре). */
    static void recipeItemsExist(GameTestHelper h) {
        java.util.Set<String> missing = new java.util.TreeSet<>();
        for (ru.iis.bio.production.BioRecipe r : ru.iis.bio.production.BioRecipe.all()) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            r.items().forEach(i -> ids.add(i.key()));
            r.outItems().forEach(o -> ids.add(o.item()));
            if (!r.tool().isEmpty()) ids.add(r.tool());
            for (String id : ids) {
                if (id.startsWith("#") || id.equals("food")) continue;
                net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.parse(id.contains(":") ? id : "iis:" + id);
                if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) missing.add(id + " (" + r.id() + ")");
            }
        }
        h.assertTrue(missing.isEmpty(), "bio recipes name items that do not exist: " + missing);
        h.succeed();
    }

    /** Рабочая мясистая свинья даёт свою тушу; обычная — среднюю. */
    static void mutantPigCarcass(GameTestHelper h) {
        Pig plain = h.spawn(EntityType.PIG, new BlockPos(3, 2, 4));
        Pig fleshy = h.spawn(EntityType.PIG, new BlockPos(5, 2, 4));
        fleshy.setData(Mutants.DATA.get(), new Mutants.Data(Mutants.Profile.PIG_FLESHY.id, 2, 0));
        ItemStack a = Butchery.carcassFor(plain), b = Butchery.carcassFor(fleshy);
        h.assertTrue(a != null && a.is(BioItems.CARCASS_MEDIUM.get()), "plain pig carcass " + a);
        h.assertTrue(b != null && b.is(BioItems.CARCASS_FLESHY.get()), "fleshy pig carcass " + b);
        h.succeed();
    }

    /**
     * Перистальтический канал: хранилище → канал → канал → разрыхлитель. Руда доходит до органа, посторонний предмет орган не принимает
     * и он остаётся в канале; число предметов сохраняется.
     */
    static void channelFeedsOrgan(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos store = h.absolutePos(new BlockPos(2, 1, 4)), c1 = h.absolutePos(new BlockPos(3, 1, 4)), c2 = h.absolutePos(new BlockPos(4, 1, 4)),
                organ = h.absolutePos(new BlockPos(5, 1, 4));
        l.setBlock(store, BioContent.LIVING_STORE.get().defaultBlockState(), 3);
        // ось X растёт на восток в координатах теста только без поворота структуры: направление берём из разности позиций
        Direction dir = Direction.getApproximateNearest(c1.getX() - store.getX(), 0, c1.getZ() - store.getZ());
        l.setBlock(c1, BioContent.ITEM_CHANNEL.get().defaultBlockState().setValue(ItemChannelBlock.FACING, dir), 3);
        l.setBlock(c2, BioContent.ITEM_CHANNEL.get().defaultBlockState().setValue(ItemChannelBlock.FACING, dir), 3);
        l.setBlock(organ, BioContent.STATIONS2.get(Station.LOOSENER).get().defaultBlockState(), 3);
        LivingStoreBlockEntity s = (LivingStoreBlockEntity) l.getBlockEntity(store);
        ItemChannelBlockEntity a = (ItemChannelBlockEntity) l.getBlockEntity(c1), b = (ItemChannelBlockEntity) l.getBlockEntity(c2);
        StationBlockEntity o = (StationBlockEntity) l.getBlockEntity(organ);
        ItemStack ore = new ItemStack(ru.iis.command.SelfTest.item("ore_chalcopyrite"), 6);
        s.setItem(0, ore.copy());
        for (int i = 0; i < 12; i++) {
            a.step();
            b.step();
        }
        int inOrgan = o.logic.getItem(0).getCount() + o.logic.getItem(1).getCount() + o.logic.getItem(2).getCount();
        int total = s.getItem(0).getCount() + a.getItem(0).getCount() + b.getItem(0).getCount() + inOrgan;
        h.assertTrue(total == 6, "items are conserved: " + total);
        h.assertTrue(o.logic.getItem(0).is(ore.getItem()), "ore reached the loosener: " + o.logic.getItem(0));
        // посторонний предмет: орган не берёт, канал держит
        s.setItem(1, new ItemStack(Items.DIRT, 2));
        for (int i = 0; i < 12; i++) {
            a.step();
            b.step();
        }
        h.assertTrue(b.getItem(0).is(Items.DIRT) && b.getItem(0).getCount() == 2, "dirt stays in the last channel: " + b.getItem(0));
        for (int i = 0; i < Station.LOOSENER.inputs; i++) h.assertTrue(!o.logic.getItem(i).is(Items.DIRT), "dirt in the organ");
        h.succeed();
    }
}
