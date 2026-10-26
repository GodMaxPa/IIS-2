package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.iis.bio.BioContent;
import ru.iis.bio.block.ColonyCoreBlockEntity;
import ru.iis.bio.block.HearthBlockEntity;
import ru.iis.bio.block.StationBlockEntity;
import ru.iis.bio.block.VesselBlock;
import ru.iis.bio.block.VesselBlockEntity;
import ru.iis.bio.fluid.BioFluids;
import ru.iis.bio.item.BioItems;
import ru.iis.bio.production.Station;
import ru.iis.fluid.CapsuleItem;
import ru.iis.fluid.FluidContainers;
import ru.iis.fluid.FluidContent;
import ru.iis.fluid.GtFluids;
import ru.iis.fluid.UniversalTankBlockEntity;
import ru.iis.machine.IISMachines;
import ru.iis.machine.block.PipeBlock;

/** Игровые тесты жидкостей (D72): жидкости GT, капсулы, резервуары, трубы Кибера, сосуды Био, очаг, отжим и фильтр. */
final class FluidGameTests {
    private FluidGameTests() {}

    static UniversalTankBlockEntity tank(GameTestHelper h, BlockPos rel, String id) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, FluidContainers.TANKS.get(id).get().defaultBlockState(), 3);
        return (UniversalTankBlockEntity) h.getLevel().getBlockEntity(abs);
    }

    static int insert(ResourceHandler<FluidResource> handler, Fluid f, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int moved = handler.insert(FluidResource.of(f), amount, tx);
            tx.commit();
            return moved;
        }
    }

    /** Жидкости GT5 зарегистрированы (сотни), у расплава железа есть цвет и температура GT. */
    static void gtFluidsRegistered(GameTestHelper h) {
        h.assertTrue(GtFluids.all().size() > 400, "GT fluids: " + GtFluids.all().size());
        GtFluids.Def iron = GtFluids.get("molten_iron");
        h.assertTrue(iron != null && iron.source().get().getFluidType().getTemperature() == 1811, "molten iron");
        h.succeed();
    }

    /** Капсула 1000 мБ → резервуар и обратно; обычная не берёт неполную порцию, универсальная берёт. */
    static void capsulesAndTank(GameTestHelper h) {
        UniversalTankBlockEntity t = tank(h, new BlockPos(4, 1, 4), "universal_tank_bronze");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        CapsuleItem capsule = (CapsuleItem) FluidContainers.CAPSULE.get();
        ItemStack full = CapsuleItem.withContent(new ItemStack(capsule), FluidContent.of(Fluids.WATER, CapsuleItem.CAPACITY));
        player.setItemInHand(InteractionHand.MAIN_HAND, full);
        h.assertTrue(capsule.transfer(h.getLevel(), player, InteractionHand.MAIN_HAND, full, t.tank), "fill tank");
        h.assertTrue(t.tank.amount() == 1000 && t.tank.fluid() == Fluids.WATER, "tank " + t.tank.amount());
        h.assertTrue(CapsuleItem.content(player.getMainHandItem()).isEmpty(), "capsule must be empty");
        // в резервуаре 300 — обычная капсула не набирает, универсальная набирает 300
        UniversalTankBlockEntity t2 = tank(h, new BlockPos(6, 1, 4), "universal_tank_steel");
        insert(t2.tank, Fluids.LAVA, 300);
        ItemStack empty = new ItemStack(capsule);
        player.setItemInHand(InteractionHand.MAIN_HAND, empty);
        h.assertTrue(!capsule.transfer(h.getLevel(), player, InteractionHand.MAIN_HAND, empty, t2.tank), "normal capsule must not take 300");
        CapsuleItem uni = (CapsuleItem) FluidContainers.UNIVERSAL_CAPSULE.get();
        ItemStack ue = new ItemStack(uni);
        player.setItemInHand(InteractionHand.MAIN_HAND, ue);
        h.assertTrue(uni.transfer(h.getLevel(), player, InteractionHand.MAIN_HAND, ue, t2.tank), "universal capsule takes 300");
        FluidContent c = CapsuleItem.content(player.getMainHandItem());
        h.assertTrue(c.amount() == 300 && c.fluidValue() == Fluids.LAVA && t2.tank.amount() == 0, "universal capsule " + c);
        h.succeed();
    }

    /** Резервуар сливает вниз: верхний наполняет нижний. */
    static void tankDrainsDown(GameTestHelper h) {
        UniversalTankBlockEntity low = tank(h, new BlockPos(4, 1, 4), "universal_tank_bronze");
        UniversalTankBlockEntity top = tank(h, new BlockPos(4, 2, 4), "universal_tank_bronze");
        insert(top.tank, Fluids.WATER, 5000);
        h.succeedWhen(() -> h.assertTrue(low.tank.amount() == 5000 && top.tank.amount() == 0, "low " + low.tank.amount() + " top " + top.tank.amount()));
    }

    /** Резервуар → медная труба → резервуар: жидкость доходит через трубу. */
    static void tankPipeTank(GameTestHelper h) {
        UniversalTankBlockEntity top = tank(h, new BlockPos(4, 3, 4), "universal_tank_bronze");
        BlockState pipe = IISMachines.PIPES.get("pipe_small_copper").get().defaultBlockState()
                .setValue(PipeBlock.PROPS.get(Direction.UP), true).setValue(PipeBlock.PROPS.get(Direction.DOWN), true);
        h.setBlock(new BlockPos(4, 2, 4), pipe);
        UniversalTankBlockEntity low = tank(h, new BlockPos(4, 1, 4), "universal_tank_bronze");
        insert(top.tank, Fluids.WATER, 2000);
        h.succeedWhen(() -> h.assertTrue(low.tank.amount() >= 1000, "through the pipe: " + low.tank.amount()));
    }

    /** Расплав железа (1811 K) в деревянной трубе (предел 350 K): огонь рядом или сгоревшая труба. */
    static void hotFluidBurnsPipe(GameTestHelper h) {
        BlockPos rel = new BlockPos(4, 2, 4);
        h.setBlock(rel, IISMachines.PIPES.get("pipe_small_wood").get().defaultBlockState().setValue(PipeBlock.PROPS.get(Direction.UP), true));
        Fluid iron = GtFluids.get("molten_iron").source().get();
        h.onEachTick(() -> {
            var cap = h.getLevel().getCapability(Capabilities.Fluid.BLOCK, h.absolutePos(rel), Direction.UP);
            if (cap != null) insert(cap, iron, 10);
        });
        h.succeedWhen(() -> {
            boolean fire = !h.getBlockState(rel).is(IISMachines.PIPES.get("pipe_small_wood").get());
            for (Direction d : Direction.values()) fire |= h.getBlockState(rel.relative(d)).is(Blocks.FIRE);
            h.assertTrue(fire, "no fire from a hot fluid in a wooden pipe");
        });
    }

    private static ColonyCoreBlockEntity fedCore(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, BioContent.CORE.get().defaultBlockState(), 3);
        ColonyCoreBlockEntity c = (ColonyCoreBlockEntity) h.getLevel().getBlockEntity(abs);
        c.colony().setNutrition(1000);
        return c;
    }

    private static VesselBlockEntity vessel(GameTestHelper h, BlockPos rel, Direction... connect) {
        return part(h, rel, BioContent.VESSELS.get("capillary_bundle").get().defaultBlockState(), connect);
    }

    private static VesselBlockEntity part(GameTestHelper h, BlockPos rel, BlockState st, Direction... connect) {
        for (Direction d : connect) st = st.setValue(VesselBlock.PROPS.get(d), true);
        h.setBlock(rel, st);
        return (VesselBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    /** Ядро → 3 капиллярных пучка → резервуар: ядро качает (ULV, напор ≤ 0,050 атм), тратит N0 на работу. */
    static void corePumpsNetwork(GameTestHelper h) {
        ColonyCoreBlockEntity core = fedCore(h, new BlockPos(1, 1, 4));
        VesselBlockEntity first = vessel(h, new BlockPos(2, 1, 4), Direction.WEST, Direction.EAST);
        VesselBlockEntity mid = vessel(h, new BlockPos(3, 1, 4), Direction.WEST, Direction.EAST);
        vessel(h, new BlockPos(4, 1, 4), Direction.WEST, Direction.EAST);
        UniversalTankBlockEntity t = tank(h, new BlockPos(5, 1, 4), "universal_tank_bronze");
        double n0 = core.colony().nutrition();
        h.onEachTick(() -> insert(mid.fluidHandler(Direction.UP), Fluids.WATER, 40));
        h.succeedWhen(() -> {
            h.assertTrue(t.tank.amount() >= 500, "water pumped by the core: " + t.tank.amount());
            h.assertTrue(first.pressure > 0 && first.pressure <= 0.050 + 1e-6, "core head " + first.pressure);
            h.assertTrue(core.colony().nutrition() < n0, "pump work and upkeep must use N0");
        });
    }

    /** Без насоса по ровной сети жидкость не идёт: ядро есть (питание), но не вплотную к сосудам. */
    static void flatNetworkWithoutPump(GameTestHelper h) {
        fedCore(h, new BlockPos(1, 1, 1));
        VesselBlockEntity a = vessel(h, new BlockPos(3, 1, 4), Direction.EAST);
        vessel(h, new BlockPos(4, 1, 4), Direction.WEST, Direction.EAST);
        UniversalTankBlockEntity t = tank(h, new BlockPos(5, 1, 4), "universal_tank_bronze");
        insert(a.fluidHandler(Direction.UP), Fluids.WATER, 40);
        h.runAfterDelay(60, () -> {
            h.assertTrue(t.tank.amount() == 0, "no pump, no flow: " + t.tank.amount());
            h.succeed();
        });
    }

    /** Самотёк: сосуд над резервуаром сливается вниз без насоса (0,0968 атм на блок). */
    static void gravityDrain(GameTestHelper h) {
        fedCore(h, new BlockPos(1, 1, 1));
        VesselBlockEntity v = vessel(h, new BlockPos(4, 2, 4), Direction.DOWN);
        UniversalTankBlockEntity t = tank(h, new BlockPos(4, 1, 4), "universal_tank_bronze");
        h.onEachTick(() -> insert(v.fluidHandler(Direction.UP), Fluids.WATER, 40));
        h.succeedWhen(() -> h.assertTrue(t.tank.amount() >= 300, "gravity flow: " + t.tank.amount()));
    }

    /** Столб из трёх ULV-сосудов с водой: внизу 0,24 атм — выше разрывных 0,080, нижний лопается. */
    static void tallUlvColumnBursts(GameTestHelper h) {
        fedCore(h, new BlockPos(1, 1, 1));
        VesselBlockEntity low = vessel(h, new BlockPos(4, 1, 4), Direction.UP);
        VesselBlockEntity midV = vessel(h, new BlockPos(4, 2, 4), Direction.UP, Direction.DOWN);
        VesselBlockEntity top = vessel(h, new BlockPos(4, 3, 4), Direction.DOWN);
        for (VesselBlockEntity v : new VesselBlockEntity[]{low, midV, top}) insert(v.fluidHandler(Direction.NORTH), Fluids.WATER, 40);
        h.succeedWhen(() -> h.assertTrue(h.getBlockState(new BlockPos(4, 1, 4)).isAir(), "the bottom ULV vessel must burst"));
    }

    private static BlockState valve(Direction facing) {
        return BioContent.CHECK_VALVE.get().defaultBlockState().setValue(ru.iis.bio.block.CheckValveBlock.FACING, facing);
    }

    /** Обратный клапан по течению пропускает. */
    static void checkValveForward(GameTestHelper h) {
        fedCore(h, new BlockPos(1, 1, 4));
        vessel(h, new BlockPos(2, 1, 4), Direction.WEST, Direction.EAST);
        part(h, new BlockPos(3, 1, 4), valve(Direction.EAST), Direction.WEST, Direction.EAST);
        VesselBlockEntity after = vessel(h, new BlockPos(4, 1, 4), Direction.WEST, Direction.EAST);
        VesselBlockEntity before = (VesselBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 1, 4)));
        UniversalTankBlockEntity t = tank(h, new BlockPos(5, 1, 4), "universal_tank_bronze");
        h.onEachTick(() -> insert(before.fluidHandler(Direction.UP), Fluids.WATER, 40));
        h.succeedWhen(() -> h.assertTrue(t.tank.amount() >= 200 && after.flow > 0, "forward valve: " + t.tank.amount()));
    }

    /** Обратный клапан против течения не пропускает. */
    static void checkValveBackward(GameTestHelper h) {
        fedCore(h, new BlockPos(1, 1, 4));
        VesselBlockEntity before = vessel(h, new BlockPos(2, 1, 4), Direction.WEST, Direction.EAST);
        part(h, new BlockPos(3, 1, 4), valve(Direction.WEST), Direction.WEST, Direction.EAST);
        vessel(h, new BlockPos(4, 1, 4), Direction.WEST, Direction.EAST);
        UniversalTankBlockEntity t = tank(h, new BlockPos(5, 1, 4), "universal_tank_bronze");
        h.onEachTick(() -> insert(before.fluidHandler(Direction.UP), Fluids.WATER, 40));
        h.runAfterDelay(100, () -> {
            h.assertTrue(t.tank.amount() == 0, "reversed valve must block: " + t.tank.amount());
            h.succeed();
        });
    }

    /** Закрытый зажим перекрывает сеть; открытый — пропускает. */
    static void clampClosesAndOpens(GameTestHelper h) {
        fedCore(h, new BlockPos(1, 1, 4));
        VesselBlockEntity before = vessel(h, new BlockPos(2, 1, 4), Direction.WEST, Direction.EAST);
        BlockPos clampPos = new BlockPos(3, 1, 4);
        BlockState closed = BioContent.CLAMP.get().defaultBlockState().setValue(ru.iis.bio.block.ClampBlock.LEVEL, 4);
        part(h, clampPos, closed, Direction.WEST, Direction.EAST);
        vessel(h, new BlockPos(4, 1, 4), Direction.WEST, Direction.EAST);
        UniversalTankBlockEntity t = tank(h, new BlockPos(5, 1, 4), "universal_tank_bronze");
        h.onEachTick(() -> insert(before.fluidHandler(Direction.UP), Fluids.WATER, 40));
        h.runAfterDelay(80, () -> {
            h.assertTrue(t.tank.amount() == 0, "closed clamp must block: " + t.tank.amount());
            h.setBlock(clampPos, h.getBlockState(clampPos).setValue(ru.iis.bio.block.ClampBlock.LEVEL, 0));
        });
        h.succeedWhen(() -> h.assertTrue(h.getTick() > 80 && t.tank.amount() > 0, "open clamp passes: " + t.tank.amount()));
    }

    /** Живой резервуар: в режиме «приём» сеть его наполняет, в «выдаче» — он питает сеть. */
    static void livingTankReceiveAndSupply(GameTestHelper h) {
        fedCore(h, new BlockPos(1, 1, 4));
        VesselBlockEntity v = vessel(h, new BlockPos(2, 1, 4), Direction.WEST, Direction.EAST);
        h.setBlock(new BlockPos(3, 1, 4), BioContent.LIVING_TANK.get());
        var lt = (ru.iis.bio.block.LivingTankBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3, 1, 4)));
        h.onEachTick(() -> {
            if (!lt.supplying()) insert(v.fluidHandler(Direction.UP), Fluids.WATER, 40);
        });
        h.runAfterDelay(200, () -> {
            h.assertTrue(lt.tank().amount() > 0, "receiving living tank must fill: " + lt.tank().amount());
            lt.toggle();
            vessel(h, new BlockPos(4, 1, 4), Direction.WEST, Direction.EAST, Direction.NORTH);
            fedCore(h, new BlockPos(4, 1, 3));
        });
        UniversalTankBlockEntity[] out = new UniversalTankBlockEntity[1];
        h.runAfterDelay(201, () -> out[0] = tank(h, new BlockPos(5, 1, 4), "universal_tank_bronze"));
        h.succeedWhen(() -> h.assertTrue(out[0] != null && out[0].tank.amount() > 0, "supplying living tank feeds the network"));
    }

    private static ru.iis.bio.block.MusclePumpBlockEntity musclePump(GameTestHelper h, BlockPos rel, Direction facing) {
        h.setBlock(rel, BioContent.MUSCLE_PUMP.get().defaultBlockState().setValue(ru.iis.bio.block.MusclePumpBlock.FACING, facing));
        var p = (ru.iis.bio.block.MusclePumpBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
        p.n1().fill(BioFluids.N1.source().get(), 250, false);
        p.e1().fill(BioFluids.E1.source().get(), 250, false);
        return p;
    }

    /** Мышечный насос LV: резервуар → насос → узкий волокнистый сосуд → резервуар; тратит E1. */
    static void musclePumpMovesFluid(GameTestHelper h) {
        fedCore(h, new BlockPos(1, 1, 1));
        UniversalTankBlockEntity src = tank(h, new BlockPos(2, 1, 4), "universal_tank_bronze");
        insert(src.tank, Fluids.WATER, 8000);
        var pump = musclePump(h, new BlockPos(3, 1, 4), Direction.EAST);
        part(h, new BlockPos(4, 1, 4), BioContent.VESSELS.get("narrow_fibrous_vessel").get().defaultBlockState(), Direction.WEST, Direction.EAST);
        UniversalTankBlockEntity dst = tank(h, new BlockPos(5, 1, 4), "universal_tank_bronze");
        h.succeedWhen(() -> {
            h.assertTrue(dst.tank.amount() >= 1000, "pumped " + dst.tank.amount());
            h.assertTrue(pump.e1().amount() < 250, "pump work must use E1");
        });
    }

    /** LV-насос напрямую в ULV-сосуд: напор 0,200 атм выше разрывных 0,080 — сосуд лопается. */
    static void lvPumpBurstsUlvVessel(GameTestHelper h) {
        fedCore(h, new BlockPos(1, 1, 1));
        UniversalTankBlockEntity src = tank(h, new BlockPos(2, 1, 4), "universal_tank_bronze");
        insert(src.tank, Fluids.WATER, 8000);
        musclePump(h, new BlockPos(3, 1, 4), Direction.EAST);
        vessel(h, new BlockPos(4, 1, 4), Direction.WEST);
        h.succeedWhen(() -> h.assertTrue(h.getBlockState(new BlockPos(4, 1, 4)).isAir(), "ULV vessel must burst under an LV pump"));
    }

    /** F06: 600 N1 + 300 концентрата + 100 эмульсии → 1 000 E1 за 30 с; буферы корпуса тратятся (N1/E1 по 12 мБ). */
    static void glandMakesE1(GameTestHelper h) {
        BlockPos abs = h.absolutePos(new BlockPos(4, 1, 4));
        h.getLevel().setBlock(abs, BioContent.GLAND.get().defaultBlockState(), 3);
        StationBlockEntity g = (StationBlockEntity) h.getLevel().getBlockEntity(abs);
        g.logic.tanks.get(0).fill(BioFluids.N1.source().get(), 600, false);
        g.logic.tanks.get(1).fill(BioFluids.CONCENTRATE.source().get(), 300, false);
        g.logic.tanks.get(2).fill(BioFluids.EMULSION.source().get(), 100, false);
        var n1 = g.logic.tanks.get(Station.GLAND.n1Buffer());
        var e1 = g.logic.tanks.get(Station.GLAND.e1Buffer());
        n1.fill(BioFluids.N1.source().get(), 250, false);
        e1.fill(BioFluids.E1.source().get(), 250, false);
        h.succeedWhen(() -> {
            h.assertTrue(fluid(g, BioFluids.E1) - e1.amount() == 1000, "E1 product " + (fluid(g, BioFluids.E1) - e1.amount()));
            h.assertTrue(e1.amount() <= 239 && e1.amount() >= 236, "E1 buffer " + e1.amount());
        });
    }

    /** Гнездо без N1/E1 в буферах стоит (нет питания). */
    static void nestNeedsBuffers(GameTestHelper h) {
        BlockPos abs = h.absolutePos(new BlockPos(4, 1, 4));
        h.getLevel().setBlock(abs, BioContent.NEST.get().defaultBlockState(), 3);
        StationBlockEntity n = (StationBlockEntity) h.getLevel().getBlockEntity(abs);
        h.runAfterDelay(20, () -> {
            h.assertTrue(n.logic.status() == ru.iis.bio.production.StationLogic.Status.NO_NUTRIENT, "nest status " + n.logic.status());
            h.succeed();
        });
    }

    /** Очаг жжёт топливо из своего слота, как печь: уголь → жар, слот пуст. */
    static void hearthFuelSlot(GameTestHelper h) {
        BlockPos abs = h.absolutePos(new BlockPos(4, 1, 4));
        h.getLevel().setBlock(abs, BioContent.HEARTH.get().defaultBlockState(), 3);
        HearthBlockEntity hearth = (HearthBlockEntity) h.getLevel().getBlockEntity(abs);
        hearth.setItem(0, new ItemStack(Items.COAL));
        h.assertTrue(hearth.takeHeat(), "hearth must burn coal from the slot");
        h.assertTrue(hearth.getItem(0).isEmpty() && hearth.heat() > 1000, "coal used, heat " + hearth.heat());
        h.succeed();
    }

    private static int fluid(StationBlockEntity st, BioFluids.Def d) {
        int n = 0;
        for (var t : st.logic.tanks) if (t.fluid() == d.source().get()) n += t.amount();
        return n;
    }

    private static StationBlockEntity station(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        BlockPos abs = h.absolutePos(new BlockPos(4, 1, 4));
        h.getLevel().setBlock(abs, block.defaultBlockState(), 3);
        return (StationBlockEntity) h.getLevel().getBlockEntity(abs);
    }

    /** W08: 2 растительные массы + 1000 воды → 800 экстракта + 200 стока + растительный остаток (L02, 80 с). */
    static void pressExtract(GameTestHelper h) {
        StationBlockEntity p = station(h, BioContent.PRESS.get());
        p.logic.setItem(0, new ItemStack(BioItems.PLANT_MASS.get(), 2));
        p.logic.tanks.get(0).fill(Fluids.WATER, 1000, false);
        h.succeedWhen(() -> {
            h.assertTrue(fluid(p, BioFluids.EXTRACT) == 800 && fluid(p, BioFluids.RUNOFF) == 200, "extract " + fluid(p, BioFluids.EXTRACT));
            boolean residue = false;
            for (int i = 0; i < p.logic.getContainerSize(); i++) residue |= p.logic.getItem(i).is(BioItems.PLANT_RESIDUE.get());
            h.assertTrue(residue, "plant residue");
        });
    }

    /** W10: 1000 воды → 950 W0 + 50 стока, прокладка изнашивается (L08, 40 с). */
    static void filterW0(GameTestHelper h) {
        StationBlockEntity f = station(h, BioContent.FILTER.get());
        f.logic.setItem(Station.FILTER.toolIndex(), new ItemStack(BioItems.FILTER_PAD.get()));
        f.logic.tanks.get(0).fill(Fluids.WATER, 1000, false);
        h.succeedWhen(() -> {
            h.assertTrue(fluid(f, BioFluids.W0) == 950 && fluid(f, BioFluids.RUNOFF) == 50, "W0 " + fluid(f, BioFluids.W0));
            h.assertTrue(f.logic.getItem(Station.FILTER.toolIndex()).getDamageValue() == 1, "pad wear");
        });
    }

    /** Стенд-эталон (D74), построенный в небе тестового мира, работает: обе сети сосудов, медная линия, места Био, LV-органы, пар. */
    static void standWorks(GameTestHelper h) {
        int y0 = 150;
        var level = h.getLevel();
        ru.iis.stand.StandBuilder.build(level, y0);
        h.succeedWhen(() -> {
            var ulvTank = (ru.iis.bio.block.LivingTankBlockEntity) level.getBlockEntity(new BlockPos(11, y0, 28));
            var lvTank = (ru.iis.bio.block.LivingTankBlockEntity) level.getBlockEntity(new BlockPos(9, y0, 34));
            var pipeTank = (UniversalTankBlockEntity) level.getBlockEntity(new BlockPos(42, y0, 32));
            var probe = (VesselBlockEntity) level.getBlockEntity(new BlockPos(10, y0, 28));
            h.assertTrue(ulvTank != null && ulvTank.tank().amount() > 0, "ULV network → living tank: "
                    + (probe == null || probe.summary == null ? "no summary" : probe.summary.describe().getString() + " p=" + probe.pressure)
                    + " tank=" + (ulvTank == null ? "missing" : ulvTank.describe().getString()));
            var probe2 = (VesselBlockEntity) level.getBlockEntity(new BlockPos(7, y0, 34));
            h.assertTrue(lvTank != null && lvTank.tank().amount() > 0, "LV pump network → living tank: "
                    + (probe2 == null || probe2.summary == null ? "no summary" : probe2.summary.describe().getString()));
            h.assertTrue(pipeTank != null && pipeTank.tank.amount() > 0, "copper pipe line → tank");
            for (BlockPos p : new BlockPos[]{new BlockPos(2, y0, 14), new BlockPos(5, y0, 14), new BlockPos(8, y0 + 1, 14), new BlockPos(11, y0, 14),
                    new BlockPos(14, y0, 14), new BlockPos(17, y0, 14), new BlockPos(2, y0, 20), new BlockPos(6, y0, 20), new BlockPos(10, y0, 20),
                    new BlockPos(7, y0, 6), new BlockPos(9, y0, 6)}) {
                StationBlockEntity st = (StationBlockEntity) level.getBlockEntity(p);
                h.assertTrue(st != null && (st.logic.busy() || produced(st)),
                        "stand station at " + p + " must be working: " + (st == null ? "missing" : st.logic.status()));
            }
            var mac = (ru.iis.machine.block.SteamMachineBlockEntity) level.getBlockEntity(new BlockPos(68, y0 + 1, 2));
            h.assertTrue(mac != null && !mac.getItem(mac.getContainerSize() - 1).isEmpty(), "steam macerator output");
        });
    }

    /** Место уже выдало продукт (бак продукта или выходной слот не пусты). */
    private static boolean produced(StationBlockEntity st) {
        Station s = st.logic.station;
        for (int k = s.inTanks.length; k < s.inTanks.length + s.outTanks.length; k++) if (st.logic.tanks.get(k).amount() > 0) return true;
        for (int i = s.inputs; i < s.inputs + s.outputs; i++) if (!st.logic.getItem(i).isEmpty()) return true;
        return false;
    }
}
