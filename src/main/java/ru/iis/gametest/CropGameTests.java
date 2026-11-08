package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import ru.iis.crop.CropBlockEntity;
import ru.iis.crop.CropCard;
import ru.iis.crop.CropContent;
import ru.iis.crop.CropMachineBlockEntity;
import ru.iis.crop.CropSeedItem;
import ru.iis.crop.CropnalyzerItem;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;

import java.util.List;

/** Грядки IC2 с селекцией (D139). */
final class CropGameTests {
    private CropGameTests() {}

    private static CropBlockEntity crop(GameTestHelper h, BlockPos p) {
        h.setBlock(p.below(), Blocks.FARMLAND);
        h.setBlock(p, CropContent.CROP.get());
        return h.getBlockEntity(p, CropBlockEntity.class);
    }

    private static CropCard card(String id) {
        CropCard c = CropCard.byId(id);
        if (c == null) throw new IllegalStateException("нет культуры " + id);
        return c;
    }

    /** Семена пшеницы сажаются на грядку, культура дорастает до 7, урожай — пшеница, после сбора размер 2; при сломе — мешочек семян. */
    static void plantGrowHarvest(GameTestHelper h) {
        h.assertTrue(CropCard.ALL.size() >= 80, "культур " + CropCard.ALL.size());
        CropBlockEntity be = crop(h, new BlockPos(2, 2, 2));
        ItemStack seeds = new ItemStack(Items.WHEAT_SEEDS, 3);
        h.assertTrue(be.plantBase(seeds) && seeds.getCount() == 2, "семена пшеницы не сажаются");
        h.assertTrue(be.card() == card("wheat") && be.size() == 1, "не пшеница");
        for (int i = 0; i < 2000 && be.size() < 7; i++) {
            be.nutrients = 100;
            be.water = 200;
            be.tick(h.getLevel());
        }
        h.assertTrue(be.size() == 7, "пшеница не выросла: " + be.size() + ", очки " + be.growthPoints + ", условия " + be.humidity + "/" + be.soil + "/" + be.air);
        int got = 0;
        for (int i = 0; i < 20; i++) {
            be.setCrop(card("wheat"), 7);
            for (ItemStack s : be.harvest(h.getLevel().getRandom())) {
                h.assertTrue(s.is(Items.WHEAT), "урожай " + s);
                got += s.getCount();
            }
            h.assertTrue(be.size() == 2, "после сбора " + be.size());
        }
        h.assertTrue(got >= 10, "за 20 сборов пшеницы " + got);
        be.growth = 5;
        be.setCrop(card("wheat"), 7);
        boolean seed = false;
        for (int i = 0; i < 40 && !seed; i++) {
            for (ItemStack s : be.pick(h.getLevel().getRandom(), true)) seed |= s.is(CropContent.CROP_SEED.get()) && CropSeedItem.seed(s).growth() == 5;
        }
        h.assertTrue(seed, "спелая пшеница не даёт мешочек с её свойствами");
        h.succeed();
    }

    /** Двойная грядка между двумя спелыми пшеницами даёт потомка, свойства — около среднего; самый частый потомок — пшеница. */
    static void crossbreeding(GameTestHelper h) {
        CropBlockEntity mid = crop(h, new BlockPos(3, 2, 3));
        CropBlockEntity a = crop(h, new BlockPos(2, 2, 3)), b = crop(h, new BlockPos(4, 2, 3));
        for (CropBlockEntity p : new CropBlockEntity[]{a, b}) {
            p.setCrop(card("wheat"), 7);
            p.growth = 10;
            p.gain = 10;
            p.resistance = 10;
        }
        mid.makeCross();
        h.assertTrue(mid.cross(), "не двойная");
        for (int i = 0; i < 3000 && mid.card() == null; i++) {
            mid.weedEx = 100;
            mid.tick(h.getLevel());
        }
        h.assertTrue(mid.card() != null && mid.size() == 1, "скрещивания нет");
        h.assertTrue(Math.abs(mid.growth - 10) <= 2 && Math.abs(mid.gain - 10) <= 2 && Math.abs(mid.resistance - 10) <= 2,
                "свойства потомка " + mid.growth + "/" + mid.gain + "/" + mid.resistance);
        List<CropBlockEntity> parents = List.of(a, b);
        java.util.Map<CropCard, Integer> count = new java.util.HashMap<>();
        for (int i = 0; i < 300; i++) count.merge(pickChild(h, parents), 1, Integer::sum);
        int wheat = count.getOrDefault(card("wheat"), 0);
        h.assertTrue(wheat < 300 && count.entrySet().stream().allMatch(e -> e.getKey() == card("wheat") || e.getValue() < wheat), "пшеница из пшениц не самая частая: " + wheat + " из 300");
        h.assertTrue(CropCard.ratio(card("ferru"), card("cyprium")) > CropCard.ratio(card("ferru"), card("wheat")), "металлы не ближе друг к другу");
        h.succeed();
    }

    private static CropCard pickChild(GameTestHelper h, List<CropBlockEntity> parents) {
        int total = 0;
        int[] acc = new int[CropCard.ALL.size()];
        for (CropCard c : CropCard.ALL) {
            for (CropBlockEntity p : parents) total += CropCard.ratio(c, p.card());
            acc[c.index()] = total;
        }
        int s = h.getLevel().getRandom().nextInt(total);
        for (CropCard c : CropCard.ALL) if (acc[c.index()] > s) return c;
        return null;
    }

    /** Пустая грядка зарастает сорняком, гербицид от этого бережёт; совок убирает сорняк и даёт его мешочек; сорняк захватывает соседа. */
    static void weeds(GameTestHelper h) {
        CropBlockEntity be = crop(h, new BlockPos(2, 2, 2));
        CropBlockEntity guarded = crop(h, new BlockPos(6, 2, 6));
        for (int i = 0; i < 1500 && be.card() == null; i++) {
            be.tick(h.getLevel());
            guarded.weedEx = 100;
            guarded.tick(h.getLevel());
        }
        h.assertTrue(be.card() == CropCard.weed(), "сорняк не вырос");
        h.assertTrue(guarded.card() == null, "сорняк вырос под гербицидом");
        ItemStack w = be.weedOut();
        h.assertTrue(w != null && CropSeedItem.seed(w).crop().equals("weed") && be.card() == null, "совок не убрал сорняк");
        CropBlockEntity n = crop(h, new BlockPos(3, 2, 2));
        n.setCrop(card("wheat"), 3);
        be.setCrop(CropCard.weed(), 4);
        be.growth = 31;
        for (int i = 0; i < 400 && n.card() != CropCard.weed(); i++) {
            for (Direction d : Direction.Plane.HORIZONTAL) h.setBlock(new BlockPos(2, 2, 2).relative(d).below(), d == Direction.EAST ? Blocks.FARMLAND : Blocks.STONE);
            be.tick(h.getLevel());
            be.setCrop(CropCard.weed(), 4);
        }
        h.assertTrue(n.card() == CropCard.weed(), "сорняк не перешёл на соседа");
        h.succeed();
    }

    /** Ферру дорастает до последней стадии только над железом (CropNeedBlockBelow); анализатор открывает мешочек за 10 000 EU. */
    static void metalNeedsBlock(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 2, 2);
        CropBlockEntity be = crop(h, p);
        be.setCrop(card("ferru"), 3);
        h.assertTrue(!be.canGrow(h.getLevel(), card("ferru")), "растёт без железа");
        h.setBlock(p.below(2), Blocks.IRON_BLOCK);
        h.assertTrue(be.canGrow(h.getLevel(), card("ferru")), "не растёт над блоком железа");
        h.setBlock(p.below(2), Blocks.GOLD_BLOCK);
        h.assertTrue(!be.canGrow(h.getLevel(), card("ferru")), "растёт над золотом");
        h.setBlock(p.below(2), Blocks.IRON_ORE);
        h.assertTrue(be.canGrow(h.getLevel(), card("ferru")), "не растёт над железной рудой");
        ItemStack seed = CropSeedItem.of(card("ferru"), 3, 4, 5, 0);
        ItemStack analyzer = new ItemStack(CropContent.CROPNALYZER.get());
        CropnalyzerItem.setCharge(analyzer, 5000);
        h.assertTrue(CropnalyzerItem.scan(seed, analyzer, false) == 3 && CropnalyzerItem.charge(analyzer) == 4000, "анализ на 5000 EU: " + CropSeedItem.seed(seed).scan());
        CropnalyzerItem.setCharge(analyzer, 9000);
        h.assertTrue(CropnalyzerItem.scan(seed, analyzer, false) == 4, "до 4 не дошло");
        h.succeed();
    }

    /** Crop-Matron кладёт удобрение, воду и гербицид; сборщик собирает спелую культуру в свой инвентарь и убирает сорняк. */
    static void machines(GameTestHelper h) {
        CropBlockEntity c1 = crop(h, new BlockPos(2, 2, 2)), c2 = crop(h, new BlockPos(3, 2, 2));
        c1.setCrop(card("wheat"), 3);
        h.setBlock(new BlockPos(5, 2, 5), CropContent.CROP_MATRON.get());
        CropMachineBlockEntity m = h.getBlockEntity(new BlockPos(5, 2, 5), CropMachineBlockEntity.class);
        m.setItem(0, new ItemStack(CropContent.FERTILIZER.get(), 4));
        m.setItem(1, new ItemStack(CropContent.WEED_EX.get()));
        m.setItem(2, new ItemStack(Items.WATER_BUCKET));
        h.setBlock(new BlockPos(6, 2, 6), CropContent.CROP_HARVESTER.get());
        CropMachineBlockEntity hv = h.getBlockEntity(new BlockPos(6, 2, 6), CropMachineBlockEntity.class);
        h.onEachTick(() -> {
            m.energy.stored = m.energy.capacity;
            hv.energy.stored = hv.energy.capacity;
        });
        h.runAtTickTime(260, () -> {
            h.assertTrue(c1.nutrients >= 80 && c2.nutrients >= 80, "удобрения нет: " + c1.nutrients + "/" + c2.nutrients);
            h.assertTrue(c1.water > 0 && c1.weedEx > 0, "воды или гербицида нет: " + c1.water + "/" + c1.weedEx);
            h.assertTrue(m.getItem(0).getCount() <= 2 && m.getItem(2).is(Items.BUCKET), "удобрение " + m.getItem(0) + ", ведро " + m.getItem(2));
            c1.setCrop(card("wheat"), 7);
            c2.setCrop(CropCard.weed(), 3);
        });
        h.runAtTickTime(560, () -> {
            h.assertTrue(c1.size() == 2, "сборщик не собрал: размер " + c1.size());
            h.assertTrue(c2.card() == null, "сорняк остался");
            h.succeed();
        });
    }

    /** Органический репликатор IV копирует мешочек с анализом 4 за UU-материю; без анализа не берёт; рецепты и предметы на месте. */
    static void organicReplicator(GameTestHelper h) {
        ElectricMachineBlockEntity r = LvGameTests.machine(h, new BlockPos(2, 2, 2), LvMachineType.ORGANIC_REPLICATOR_IV, Direction.NORTH);
        r.setItem(0, CropSeedItem.of(card("wheat"), 1, 1, 1, 4));
        r.tank().setAmount(ru.iis.fluid.GtFluids.get("uumatter").source().get(), 100);
        h.onEachTick(() -> r.energy.stored = r.energy.capacity);
        for (String id : new String[]{"crop/crop_sticks", "crop/fertilizer", "crop/weed_ex", "crop/cropnalyzer", "crop/crop_lens", "crop/weeding_trowel", "crop/crop_matron",
                "crop/crop_harvester", "lv/organic_replicator_lv", "lv/organic_replicator_uv", "processing/fluid_extractor/crop_oil_berry",
                "processing/chemical_reactor/crop_coppon_fiber_copper", "processing/autoclave/crop_tine_twig_tin"}) {
            var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("iis", id));
            h.assertTrue(h.getLevel().recipeAccess().byKey(key).isPresent(), "нет рецепта " + id);
        }
        for (String id : new String[]{"uum_berry", "uua_berry", "oil_berry", "tine_twig", "crop_lens", "organic_replicator_uv"}) {
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("iis", id)), "нет предмета " + id);
        }
        h.runAtTickTime(200, () -> {
            ItemStack out = r.getItem(1);
            h.assertTrue(out.is(CropContent.CROP_SEED.get()) && CropSeedItem.seed(out).crop().equals("wheat"), "копии нет: " + out);
            h.assertTrue(r.getItem(0).is(CropContent.CROP_SEED.get()), "образец пропал");
            h.assertTrue(r.tank().amount() == 100 - 14, "UU-материи осталось " + r.tank().amount());
            h.succeed();
        });
    }
}
