package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.iis.IIS;
import ru.iis.bio.BioContent;
import ru.iis.bio.block.*;
import ru.iis.bio.core.ColonyState;
import ru.iis.bio.core.TissueGraph;
import ru.iis.bio.core.TissueGraph.Square;
import ru.iis.bio.world.ShuttleSpawner;
import ru.iis.bio.world.ShuttleTemplate;
import ru.iis.bio.production.Station;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Игровые тесты биопути: челнок, кормление через пасть, рост ткани, голод, разрыв и смерть ткани, снег, тревога. */
final class BioGameTests {
    private BioGameTests() {}

    private static BlockState tissue(Direction... faces) {
        BlockState s = BioContent.TISSUE.get().defaultBlockState();
        for (Direction d : faces) s = s.setValue(MultifaceBlock.getFaceProperty(d), true);
        return s;
    }

    private static void useFood(GameTestHelper h, BlockPos abs, ItemStack stack) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ServerLevel l = h.getLevel();
        l.getBlockState(abs).useItemOn(stack, l, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5), Direction.UP, abs, false));
    }

    /** Ядро в центре пола и цепочка ткани на восток по полу: первая клетка прикреплена к ядру. */
    private static ColonyCoreBlockEntity coreWithChain(GameTestHelper h, int length, BlockPos[] cellsOut) {
        ServerLevel l = h.getLevel();
        BlockPos corePos = h.absolutePos(new BlockPos(4, 1, 4));
        l.setBlock(corePos, BioContent.CORE.get().defaultBlockState(), 3);
        ColonyCoreBlockEntity core = (ColonyCoreBlockEntity) l.getBlockEntity(corePos);
        for (int i = 0; i < length; i++) {
            BlockPos cell = h.absolutePos(new BlockPos(5 + i, 1, 4));
            l.setBlock(cell, i == 0 ? tissue(Direction.WEST, Direction.DOWN) : tissue(Direction.DOWN), 3);
            core.adoptTile(cell);
            if (cellsOut != null) cellsOut[i] = cell;
        }
        return core;
    }

    private static boolean isTissue(ServerLevel l, BlockPos p) {
        return l.getBlockState(p).is(BioContent.TISSUE.get());
    }

    /**
     * Челнок 10×7×5 строится (корпус, остекление, 3 двигателя, ядро и пасть вделаны в крышу снаружи, на обшивке уже есть спящее
     * заражение) → пасть связана с ядром → стейки → ткань растёт, не разрывается (каждый квадрат цепочкой достаёт до ядра)
     * и не лезет на пасть.
     */
    static void shuttleFeedAndGrow(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(3, 1, 3));
        BlockPos corePos = ShuttleSpawner.build(l, origin.getX(), origin.getZ(), origin.getY(), 0, false,
                origin.getX() + 5.5, origin.getZ() + 12.0, 12345L);
        int hull = 0, windows = 0, engines = 0;
        for (int dx = -1; dx < 12; dx++)
            for (int dy = -3; dy < 6; dy++)
                for (int dz = -1; dz < 9; dz++) {
                    BlockState s = l.getBlockState(origin.offset(dx, dy, dz));
                    if (s.is(BioContent.SHUTTLE_HULL.get())) hull++;
                    if (s.is(BioContent.SHUTTLE_WINDOW.get())) windows++;
                    if (s.is(BioContent.SHUTTLE_ENGINE.get())) engines++;
                }
        h.assertTrue(hull >= 60, "hull blocks: " + hull);
        h.assertTrue(windows >= 5, "window blocks: " + windows);
        h.assertTrue(engines == 3, "engine blocks: " + engines);
        h.assertTrue(l.getBlockState(corePos).is(BioContent.CORE.get()), "no core");
        BlockPos mawPos = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (l.getBlockState(corePos.relative(d)).is(BioContent.MAW.get())) mawPos = corePos.relative(d);
        }
        h.assertTrue(mawPos != null, "no maw next to the core");
        h.assertTrue(l.getBlockState(corePos.above()).isAir() || l.getBlockState(corePos.above()).is(BioContent.TISSUE.get()), "the core must sit on the outside of the hull");
        h.assertTrue(l.getBlockState(corePos.below()).isAir(), "the cabin is hollow under the core");
        ColonyCoreBlockEntity core = (ColonyCoreBlockEntity) l.getBlockEntity(corePos);
        h.assertTrue(core.tileCount() >= 6, "the hull must come pre-infected, tissue squares: " + core.tileCount());
        h.assertTrue(!core.colony().awake(), "hearth must start dormant");
        h.assertTrue(l.getBlockState(corePos).getValue(ColonyCoreBlock.MOOD) == CoreMood.DORMANT, "dormant look");

        final int seeded = core.tileCount();
        ItemStack beef = new ItemStack(Items.COOKED_BEEF, 16);
        useFood(h, mawPos, beef);
        h.assertTrue(beef.getCount() == 15, "maw did not take the food: " + beef.getCount());
        h.assertTrue(core.pocket.getItem(0).is(Items.COOKED_BEEF), "the maw must pass the food into the digestive pocket");
        h.assertTrue(l.getBlockState(mawPos).getValue(FeedingMawBlock.CHEWING), "maw should chew");
        for (int i = 0; i < 8; i++) useFood(h, mawPos, beef);

        h.succeedWhen(() -> {
            Set<Square> squares = new HashSet<>();
            for (int dx = -8; dx <= 8; dx++)
                for (int dy = -4; dy <= 6; dy++)
                    for (int dz = -8; dz <= 8; dz++) {
                        BlockPos p = corePos.offset(dx, dy, dz);
                        BlockState st = l.getBlockState(p);
                        if (!st.is(BioContent.TISSUE.get())) continue;
                        h.assertTrue(core.claims(p), "tissue outside the territory at " + p);
                        for (Direction d : Direction.values()) {
                            if (!MultifaceBlock.hasFace(st, d)) continue;
                            h.assertTrue(!l.getBlockState(p.relative(d)).is(BioContent.MAW.get()), "tissue grew on the maw at " + p);
                            squares.add(new Square(p.getX(), p.getY(), p.getZ(), d.ordinal()));
                        }
                    }
            h.assertTrue(squares.size() >= seeded + 3, "tissue grew from " + seeded + " to " + squares.size() + ", nutrition " + core.colony().nutrition());
            Map<Square, Integer> dist = TissueGraph.distances(squares, s ->
                    s.x() + TissueGraph.DX[s.face()] == corePos.getX() && s.y() + TissueGraph.DY[s.face()] == corePos.getY()
                            && s.z() + TissueGraph.DZ[s.face()] == corePos.getZ());
            h.assertTrue(dist.size() == squares.size(), "torn tissue: " + (squares.size() - dist.size()) + " of " + squares.size() + " squares are cut off from the core");
            IIS.LOGGER.info("[IIS gametest] bio_shuttle_feed_grow: {} squares, nutrition {}, mood {}", squares.size(), (int) core.colony().nutrition(), core.colony().mood());
        });
    }

    /** Без питания ткань увядает (вид STARVING) и отступает с самого дальнего конца, пока не уйдёт вся. */
    static void starvationRetreat(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos[] cells = new BlockPos[3];
        ColonyCoreBlockEntity core = coreWithChain(h, 3, cells);
        core.colony().restore(new ColonyState.Snapshot(0, true, 4, 29, 0, 0));
        AtomicBoolean sawStarving = new AtomicBoolean();
        AtomicBoolean sawFarEndFirst = new AtomicBoolean();
        h.succeedWhen(() -> {
            for (BlockPos p : cells) {
                BlockState s = l.getBlockState(p);
                if (s.is(BioContent.TISSUE.get()) && s.getValue(BioTissueBlock.VITALITY) == Vitality.STARVING) sawStarving.set(true);
            }
            // отступление идёт с дальнего конца: корень стоит, пока не ушла вся цепочка за ним
            if (!isTissue(l, cells[2]) && isTissue(l, cells[0])) sawFarEndFirst.set(true);
            h.assertTrue(core.tileCount() == 0, "tiles left: " + core.tileCount());
            for (BlockPos p : cells) h.assertTrue(!isTissue(l, p), "tissue still stands at " + p);
            h.assertTrue(sawStarving.get(), "tissue never looked starving");
            h.assertTrue(sawFarEndFirst.get(), "tissue did not retreat from the far end");
        });
    }

    /** Ядро сломали — вся его ткань отмирает сразу. */
    static void coreRemovedKillsTissue(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos[] cells = new BlockPos[2];
        coreWithChain(h, 2, cells);
        l.setBlock(h.absolutePos(new BlockPos(4, 1, 4)), Blocks.AIR.defaultBlockState(), 3);
        for (BlockPos p : cells) h.assertTrue(!isTissue(l, p), "tissue survived the core at " + p);
        h.succeed();
    }

    /** Пасть без колонии не принимает еду. */
    static void mawNeedsColony(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos mawPos = h.absolutePos(new BlockPos(4, 1, 4));
        l.setBlock(mawPos, BioContent.MAW.get().defaultBlockState(), 3);
        ItemStack beef = new ItemStack(Items.COOKED_BEEF, 4);
        useFood(h, mawPos, beef);
        h.assertTrue(beef.getCount() == 4, "a disconnected maw ate " + (4 - beef.getCount()));
        h.succeed();
    }

    /** Повреждение ткани тревожит колонию: ядро меняет вид на «встревожено». */
    static void damageAlarms(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos[] cells = new BlockPos[2];
        ColonyCoreBlockEntity core = coreWithChain(h, 2, cells);
        BlockPos corePos = core.getBlockPos();
        core.feed(100);
        h.runAfterDelay(30, () -> l.setBlock(cells[1], Blocks.AIR.defaultBlockState(), 3)); // как будто сломал игрок
        h.succeedWhen(() -> h.assertTrue(l.getBlockState(corePos).getValue(ColonyCoreBlock.MOOD) == CoreMood.ALARMED,
                "core mood " + l.getBlockState(corePos).getValue(ColonyCoreBlock.MOOD)));
    }

    /** Разорвали цепочку — оторванная часть отмирает, а часть у ядра остаётся. */
    static void tornTissueDies(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos[] cells = new BlockPos[4];
        coreWithChain(h, 4, cells);
        h.runAfterDelay(30, () -> l.setBlock(cells[1], Blocks.AIR.defaultBlockState(), 3));
        h.succeedWhen(() -> {
            h.assertTrue(!isTissue(l, cells[2]) && !isTissue(l, cells[3]), "the cut-off tissue is still alive");
            h.assertTrue(isTissue(l, cells[0]), "the tissue at the core must stay");
        });
    }

    /** Ткань пробивается сквозь снег: слой растаял, на его месте — ткань на прикреплённой грани. */
    static void tissueMeltsSnow(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos corePos = h.absolutePos(new BlockPos(4, 1, 4));
        l.setBlock(corePos, BioContent.CORE.get().defaultBlockState(), 3);
        ColonyCoreBlockEntity core = (ColonyCoreBlockEntity) l.getBlockEntity(corePos);
        List<BlockPos> around = List.of(corePos.north(), corePos.south(), corePos.east(), corePos.west(), corePos.above());
        for (BlockPos p : around) l.setBlock(p, Blocks.SNOW.defaultBlockState(), 3);
        core.feed(300);
        h.succeedWhen(() -> {
            boolean grown = false;
            for (BlockPos p : around) grown |= isTissue(l, p);
            h.assertTrue(grown, "the tissue did not push through the snow");
        });
    }

    // --- срез Б-1 (D70) -----------------------------------------------------------------------------------------------
    private static ColonyCoreBlockEntity core(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, BioContent.CORE.get().defaultBlockState(), 3);
        return (ColonyCoreBlockEntity) h.getLevel().getBlockEntity(abs);
    }

    private static StationBlockEntity station(GameTestHelper h, BlockPos rel, net.minecraft.world.level.block.Block block) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, block.defaultBlockState(), 3);
        return (StationBlockEntity) h.getLevel().getBlockEntity(abs);
    }

    private static ItemStack bio(net.minecraft.world.item.Item item, int n) {
        return new ItemStack(item, n);
    }

    private static int count(StationBlockEntity st, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < st.logic.getContainerSize(); i++) if (st.logic.getItem(i).is(item)) n += st.logic.getItem(i).getCount();
        return n;
    }

    private static int fluid(ru.iis.bio.production.StationLogic logic, ru.iis.bio.fluid.BioFluids.Def d) {
        int n = 0;
        for (var t : logic.tanks) if (t.fluid() == d.source().get()) n += t.amount();
        return n;
    }

    /** Карман: масса растений + масса мяса + 500 воды → 350 N0 (в запас колонии) + 50 ростовой фракции + 100 стока (L01). */
    static void pocketL01(GameTestHelper h) {
        ColonyCoreBlockEntity c = core(h, new BlockPos(4, 1, 4));
        c.pocket.setItem(0, bio(ru.iis.bio.item.BioItems.PLANT_MASS.get(), 1));
        c.pocket.setItem(1, bio(ru.iis.bio.item.BioItems.MEAT_MASS.get(), 1));
        c.pocket.tanks.get(0).fill(net.minecraft.world.level.material.Fluids.WATER, 500, false);
        h.succeedWhen(() -> {
            h.assertTrue(fluid(c.pocket, ru.iis.bio.fluid.BioFluids.PRIMARY) == 50, "primary fraction " + fluid(c.pocket, ru.iis.bio.fluid.BioFluids.PRIMARY));
            h.assertTrue(fluid(c.pocket, ru.iis.bio.fluid.BioFluids.RUNOFF) == 100, "runoff " + fluid(c.pocket, ru.iis.bio.fluid.BioFluids.RUNOFF));
            h.assertTrue(c.colony().awake() && c.colony().nutrition() > 340 && c.colony().nutrition() <= 350, "N0 " + c.colony().nutrition());
            h.assertTrue(c.pocket.tanks.get(0).amount() == 0, "water left " + c.pocket.tanks.get(0).amount());
        });
    }

    /** Взрослая свинья, убитая ножом Био, оставляет среднюю тушу вместо свинины. */
    static void knifeCarcass(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ru.iis.bio.item.BioItems.KNIFE.get()));
        var pig = h.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(4, 1, 4));
        pig.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(player), 1000f);
        h.succeedWhen(() -> {
            h.assertItemEntityPresent(ru.iis.bio.item.BioItems.CARCASS_MEDIUM.get(), new BlockPos(4, 1, 4), 3.0);
            h.assertItemEntityNotPresent(Items.PORKCHOP, new BlockPos(4, 1, 4), 3.0);
        });
    }

    /** Площадка с ножом: средняя туша → 4 мясные массы + 2 соединительных волокна + 2 костных фрагмента; нож изнашивается. */
    static void butcherCarcass(GameTestHelper h) {
        StationBlockEntity b = station(h, new BlockPos(4, 1, 4), BioContent.BUTCHER.get());
        b.logic.setItem(0, bio(ru.iis.bio.item.BioItems.CARCASS_MEDIUM.get(), 1));
        b.logic.setItem(Station.BUTCHER.toolIndex(), new ItemStack(ru.iis.bio.item.BioItems.KNIFE.get()));
        h.succeedWhen(() -> {
            h.assertTrue(count(b, ru.iis.bio.item.BioItems.MEAT_MASS.get()) == 4, "meat mass " + count(b, ru.iis.bio.item.BioItems.MEAT_MASS.get()));
            h.assertTrue(count(b, ru.iis.bio.item.BioItems.CONNECTIVE_FIBER.get()) == 2, "connective fiber");
            h.assertTrue(count(b, ru.iis.bio.item.BioItems.BONE_FRAGMENT.get()) == 2, "bone fragments");
            h.assertTrue(b.logic.getItem(Station.BUTCHER.toolIndex()).getDamageValue() == 1, "knife wear");
        });
    }

    /** Сосуд на горящем очаге: 2 соединительных волокна + 250 воды → гель + 250 стока; без жара не идёт. */
    static void potGel(GameTestHelper h) {
        BlockPos hearthAbs = h.absolutePos(new BlockPos(4, 1, 4));
        h.getLevel().setBlock(hearthAbs, BioContent.HEARTH.get().defaultBlockState(), 3);
        var hearth = (HearthBlockEntity) h.getLevel().getBlockEntity(hearthAbs);
        StationBlockEntity pot = station(h, new BlockPos(4, 2, 4), BioContent.POT.get());
        pot.logic.setItem(0, bio(ru.iis.bio.item.BioItems.CONNECTIVE_FIBER.get(), 2));
        pot.logic.tanks.get(0).fill(net.minecraft.world.level.material.Fluids.WATER, 250, false);
        h.runAfterDelay(40, () -> {
            h.assertTrue(pot.logic.status() == ru.iis.bio.production.StationLogic.Status.NO_HEAT, "pot must wait for heat, " + pot.logic.status());
            hearth.addFuel();
            hearth.addFuel();
        });
        h.succeedWhen(() -> {
            h.assertTrue(count(pot, ru.iis.bio.item.BioItems.GEL.get()) == 1, "gel " + count(pot, ru.iis.bio.item.BioItems.GEL.get()));
            h.assertTrue(fluid(pot.logic, ru.iis.bio.fluid.BioFluids.RUNOFF) == 250, "runoff");
            h.assertTrue(hearth.heat() == 2 * HearthBlockEntity.TICKS_PER_FUEL - 120 * 20, "heat used " + hearth.heat());
        });
    }

    /** Мерный сосуд: 600 N0 + 200 гидролизата + 150 связующего + 50 взвеси → 1 000 G0 (L10). */
    static void mixerG0(GameTestHelper h) {
        StationBlockEntity m = station(h, new BlockPos(4, 1, 4), BioContent.MIXER.get());
        m.logic.tanks.get(0).fill(ru.iis.bio.fluid.BioFluids.N0.source().get(), 600, false);
        m.logic.tanks.get(1).fill(ru.iis.bio.fluid.BioFluids.HYDROLYSATE.source().get(), 200, false);
        m.logic.tanks.get(2).fill(ru.iis.bio.fluid.BioFluids.BINDER.source().get(), 150, false);
        m.logic.tanks.get(3).fill(ru.iis.bio.fluid.BioFluids.SUSPENSION.source().get(), 50, false);
        h.succeedWhen(() -> {
            h.assertTrue(fluid(m.logic, ru.iis.bio.fluid.BioFluids.G0) == 1000, "G0 " + fluid(m.logic, ru.iis.bio.fluid.BioFluids.G0));
            h.assertTrue(fluid(m.logic, ru.iis.bio.fluid.BioFluids.N0) == 0 && fluid(m.logic, ru.iis.bio.fluid.BioFluids.SUSPENSION) == 0, "inputs not consumed");
        });
    }

    /** Ручная подложка у колонии: подложка + неразвитая культура + 50 ростовой фракции → мягкая ткань той же линии; питается N0 колонии. */
    static void substrateTissue(GameTestHelper h) {
        ColonyCoreBlockEntity c = core(h, new BlockPos(4, 1, 4));
        c.feed(500);
        h.onEachTick(() -> { if (c.colony().nutrition() < 300) c.feed(500); }); // колония тратит N0 и на рост ткани
        StationBlockEntity s = station(h, new BlockPos(5, 1, 4), BioContent.SUBSTRATE.get());
        s.logic.setItem(0, bio(ru.iis.bio.item.BioItems.GROWTH_SUBSTRATE.get(), 1));
        ItemStack culture = bio(ru.iis.bio.item.BioItems.PRIMITIVE_CULTURE.get(), 1);
        culture.set(ru.iis.bio.item.BioItems.LINEAGE.get(), c.lineage());
        s.logic.setItem(1, culture);
        s.logic.tanks.get(0).fill(ru.iis.bio.fluid.BioFluids.PRIMARY.source().get(), 50, false);
        h.succeedWhen(() -> {
            ItemStack out = s.logic.getItem(Station.SUBSTRATE.inputs);
            h.assertTrue(out.is(ru.iis.bio.item.BioItems.SOFT_TISSUE.get()), "no soft tissue: " + out);
            h.assertTrue(c.lineage().equals(out.get(ru.iis.bio.item.BioItems.LINEAGE.get())), "lineage lost");
        });
    }

    /** Подложка без колонии не работает. */
    static void substrateNeedsColony(GameTestHelper h) {
        StationBlockEntity s = station(h, new BlockPos(4, 1, 4), BioContent.SUBSTRATE.get());
        s.logic.setItem(0, bio(ru.iis.bio.item.BioItems.GROWTH_SUBSTRATE.get(), 1));
        s.logic.setItem(1, bio(ru.iis.bio.item.BioItems.PRIMITIVE_CULTURE.get(), 1));
        s.logic.tanks.get(0).fill(ru.iis.bio.fluid.BioFluids.PRIMARY.source().get(), 50, false);
        h.runAfterDelay(60, () -> {
            h.assertTrue(s.logic.status() == ru.iis.bio.production.StationLogic.Status.NO_COLONY, "status " + s.logic.status());
            h.assertTrue(s.logic.getItem(0).getCount() == 1, "inputs must stay");
            h.succeed();
        });
    }

    /** Ростовое ложе у колонии выращивает пасть-кормушку (A02) из каркаса, тканей, мембраны, рёбер, посева и 75 G0. */
    static void bedGrowsMaw(GameTestHelper h) {
        ColonyCoreBlockEntity c = core(h, new BlockPos(4, 1, 4));
        c.feed(900);
        h.onEachTick(() -> { if (c.colony().nutrition() < 300) c.feed(500); }); // колония тратит N0 и на рост ткани
        StationBlockEntity bed = station(h, new BlockPos(5, 1, 4), BioContent.BED.get());
        bed.logic.setItem(0, bio(ru.iis.bio.item.BioItems.WOVEN_FRAME.get(), 1));
        bed.logic.setItem(1, bio(ru.iis.bio.item.BioItems.SOFT_TISSUE.get(), 2));
        bed.logic.setItem(2, bio(ru.iis.bio.item.BioItems.LIVING_MEMBRANE.get(), 1));
        bed.logic.setItem(3, bio(ru.iis.bio.item.BioItems.BONE_RIB.get(), 2));
        bed.logic.setItem(4, bio(ru.iis.bio.item.BioItems.BASE_SEED.get(), 1));
        bed.logic.tanks.get(0).fill(ru.iis.bio.fluid.BioFluids.G0.source().get(), 75, false);
        h.succeedWhen(() -> {
            ItemStack out = bed.logic.getItem(Station.BED.inputs);
            h.assertTrue(out.is(BioContent.MAW_ITEM.get()), "no maw: " + out + ", status " + bed.logic.status());
            h.assertTrue(bed.logic.tanks.get(0).amount() == 0, "G0 left");
        });
    }

    /** Нож по ткани колонии: клетка снимается, в руки — неразвитая культура этой линии. */
    static void scrapeCulture(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos[] cells = new BlockPos[2];
        ColonyCoreBlockEntity c = coreWithChain(h, 2, cells);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack knife = new ItemStack(ru.iis.bio.item.BioItems.KNIFE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, knife);
        BlockState before = l.getBlockState(cells[1]);
        before.useItemOn(knife, l, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(cells[1]), Direction.UP, cells[1], false));
        boolean got = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (st.is(ru.iis.bio.item.BioItems.PRIMITIVE_CULTURE.get()) && c.lineage().equals(st.get(ru.iis.bio.item.BioItems.LINEAGE.get()))) got = true;
        }
        h.assertTrue(got, "no primitive culture with the colony line");
        h.assertTrue(!isTissue(l, cells[1]), "the scraped cell is still there");
        h.assertTrue(knife.getDamageValue() == 1, "knife wear " + knife.getDamageValue());
        h.succeed();
    }
}
