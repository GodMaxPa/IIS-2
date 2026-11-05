package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import ru.iis.auto.*;
import ru.iis.command.SelfTest;
import ru.iis.energy.VoltageTier;
import ru.iis.fluid.GtFluids;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.BatteryBufferBlockEntity;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.lv.item.BatteryItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;
import ru.iis.world.ore.OreBlocks;
import ru.iis.world.ore.OreHost;

/** Автоматика, хранилища, автобур и помпа, настраиваемый трансформатор, зарядник, блоки металлов, кислород в газовой турбине (D108). */
final class AutoGameTests {
    private AutoGameTests() {}

    private static final BlockPos M = new BlockPos(4, 1, 4);

    private static AutomationBlockEntity automation(GameTestHelper h, AutomationType kind) {
        h.setBlock(M, AutoContent.AUTOMATION.get(kind).get(0).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        AutomationBlockEntity be = h.getBlockEntity(M, AutomationBlockEntity.class);
        h.onEachTick(() -> be.energy.stored = be.energy.capacity);
        return be;
    }

    /** Сзади (юг) — сундук. */
    private static ChestBlockEntity chest(GameTestHelper h, BlockPos p) {
        h.setBlock(p, Blocks.CHEST);
        return h.getBlockEntity(p, ChestBlockEntity.class);
    }

    private static int count(ChestBlockEntity c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    /** Сундук-буфер: стопки уходят назад в сундук, энергия тратится по предмету; сверху — сигнала нет. */
    static void chestBufferMovesItems(GameTestHelper h) {
        AutomationBlockEntity be = automation(h, AutomationType.CHEST_BUFFER);
        ChestBlockEntity c = chest(h, M.south());
        be.setItem(5, new ItemStack(Items.COBBLESTONE, 32));
        be.setItem(9, new ItemStack(Items.COBBLESTONE, 16));
        h.succeedWhen(() -> h.assertTrue(count(c, Items.COBBLESTONE) == 48, "moved " + count(c, Items.COBBLESTONE)));
    }

    /** Размер порции 8: из 12 булыжников уходят ровно 8, остаток 4 меньше порции и остаётся. */
    static void bufferTargetStackSize(GameTestHelper h) {
        AutomationBlockEntity be = automation(h, AutomationType.CHEST_BUFFER);
        ChestBlockEntity c = chest(h, M.south());
        be.targetStack = 8;
        be.setItem(0, new ItemStack(Items.COBBLESTONE, 12));
        h.runAtTickTime(100, () -> {
            h.assertTrue(count(c, Items.COBBLESTONE) == 8, "moved " + count(c, Items.COBBLESTONE));
            h.assertTrue(be.getItem(0).getCount() == 4, "left " + be.getItem(0).getCount());
            h.succeed();
        });
    }

    /** Фильтр: снаружи принимает только образец; с инверсией — всё, кроме образца. */
    static void filterAcceptsSampleOnly(GameTestHelper h) {
        AutomationBlockEntity be = automation(h, AutomationType.FILTER);
        be.setItem(9, new ItemStack(Items.IRON_INGOT));
        h.assertTrue(be.canPlaceItemThroughFace(0, new ItemStack(Items.IRON_INGOT), Direction.NORTH), "sample refused");
        h.assertTrue(!be.canPlaceItemThroughFace(0, new ItemStack(Items.GOLD_INGOT), Direction.NORTH), "other accepted");
        h.assertTrue(!be.canPlaceItemThroughFace(0, new ItemStack(Items.IRON_INGOT), Direction.SOUTH), "accepted from the back");
        be.filterInvert = true;
        h.assertTrue(!be.canPlaceItemThroughFace(0, new ItemStack(Items.IRON_INGOT), Direction.NORTH), "inverted: sample accepted");
        h.assertTrue(be.canPlaceItemThroughFace(0, new ItemStack(Items.GOLD_INGOT), Direction.NORTH), "inverted: other refused");
        h.succeed();
    }

    /** Фильтр по типу: вид «слитки» принимает только слитки (с вида «руды» — руды GT). */
    static void typeFilterChecksKind(GameTestHelper h) {
        AutomationBlockEntity be = automation(h, AutomationType.TYPE_FILTER);
        be.typeIndex = AutomationBlockEntity.TYPE_PREFIXES.indexOf("ingot");
        h.assertTrue(be.canPlaceItemThroughFace(0, new ItemStack(SelfTest.item("ingot_tin")), Direction.NORTH), "ingot refused");
        h.assertTrue(!be.canPlaceItemThroughFace(0, new ItemStack(SelfTest.item("dust_tin")), Direction.NORTH), "dust accepted");
        be.typeIndex = 0;
        h.assertTrue(be.canPlaceItemThroughFace(0, new ItemStack(OreBlocks.get("chalcopyrite", OreHost.STONE).getBlock()), Direction.NORTH), "ore refused");
        h.succeed();
    }

    /** Регулятор: образец 5 булыжников, цель — слот 3 сундука: туда приходит ровно по 5. */
    static void regulatorFillsTargetSlot(GameTestHelper h) {
        AutomationBlockEntity be = automation(h, AutomationType.REGULATOR);
        ChestBlockEntity c = chest(h, M.south());
        be.setItem(9, new ItemStack(Items.COBBLESTONE, 5));
        be.targetSlots[0] = 3;
        be.setItem(0, new ItemStack(Items.COBBLESTONE, 12));
        h.runAtTickTime(100, () -> {
            h.assertTrue(c.getItem(3).is(Items.COBBLESTONE) && c.getItem(3).getCount() == 10, "slot 3: " + c.getItem(3));
            h.assertTrue(be.getItem(0).getCount() == 2, "left " + be.getItem(0).getCount());
            h.succeed();
        });
    }

    /** Распределитель: по 2 предмета на восток и 1 на запад, по кругу. */
    static void distributorSplitsItems(GameTestHelper h) {
        AutomationBlockEntity be = automation(h, AutomationType.ITEM_DISTRIBUTOR);
        ChestBlockEntity east = chest(h, M.east()), west = chest(h, M.west());
        be.itemsPerSide[Direction.EAST.get3DDataValue()] = 2;
        be.itemsPerSide[Direction.WEST.get3DDataValue()] = 1;
        be.setItem(0, new ItemStack(Items.COBBLESTONE, 6));
        h.succeedWhen(() -> {
            h.assertTrue(count(east, Items.COBBLESTONE) == 4, "east " + count(east, Items.COBBLESTONE));
            h.assertTrue(count(west, Items.COBBLESTONE) == 2, "west " + count(west, Items.COBBLESTONE));
        });
    }

    /** Квантовый сундук: копит больше стопки, отдаёт в выходной слот, чужой предмет не принимает. */
    static void quantumChestStores(GameTestHelper h) {
        h.setBlock(M, AutoContent.QUANTUM_CHESTS.get(0).get());
        QuantumChestBlockEntity be = h.getBlockEntity(M, QuantumChestBlockEntity.class);
        int[] fed = {0};
        h.onEachTick(() -> {
            if (fed[0] < 4 && be.getItem(0).isEmpty()) {
                be.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                fed[0]++;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(fed[0] == 4 && be.total() == 256, "total " + be.total());
            h.assertTrue(be.getItem(1).is(Items.COBBLESTONE) && be.getItem(1).getCount() == 64, "output " + be.getItem(1));
            h.assertTrue(!be.canPlaceItemThroughFace(0, new ItemStack(Items.DIRT), Direction.UP), "foreign item accepted");
        });
    }

    /** Квантовый резервуар: большой запас и не сливает вниз сам. */
    static void quantumTankHolds(GameTestHelper h) {
        h.setBlock(M, ru.iis.fluid.FluidContainers.TANKS.get("quantum_tank_lv").get());
        h.setBlock(M.below(), ru.iis.fluid.FluidContainers.TANKS.get("universal_tank_bronze").get());
        var tank = h.getBlockEntity(M, ru.iis.fluid.UniversalTankBlockEntity.class);
        var under = h.getBlockEntity(M.below(), ru.iis.fluid.UniversalTankBlockEntity.class);
        h.assertTrue(tank.tank.capacity() == 1602000, "capacity " + tank.tank.capacity());
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            tank.tank.insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(Fluids.WATER), 100000, tx);
            tx.commit();
        }
        h.runAtTickTime(40, () -> {
            h.assertTrue(tank.tank.amount() == 100000, "amount " + tank.tank.amount());
            h.assertTrue(under.tank.amount() == 0, "drained down " + under.tank.amount());
            h.succeed();
        });
    }

    /** Автобур LV: труба на один блок вниз, руда на этом слое — в выходной слот; блок руды исчез. */
    static void minerMinesOre(GameTestHelper h) {
        BlockPos p = new BlockPos(8, 4, 8), ore = new BlockPos(10, 3, 10);
        h.setBlock(p, AutoContent.MINERS.get(0).get());
        h.setBlock(ore, OreBlocks.get("chalcopyrite", OreHost.STONE));
        BasicDrillerBlockEntity be = h.getBlockEntity(p, BasicDrillerBlockEntity.class);
        h.onEachTick(() -> be.energy.stored = be.energy.capacity);
        be.setItem(0, new ItemStack(MultiContent.MINING_PIPE.get(), 8));
        h.succeedWhen(() -> {
            h.assertBlockPresent(MultiContent.MINING_PIPE_TIP.get(), p.below());
            h.assertBlockPresent(Blocks.AIR, ore);
            h.assertTrue(!be.getItem(BasicDrillerBlockEntity.OUT).isEmpty(), "no ore mined");
        });
    }

    /** Помпа LV: источник под ней — 1000 л воды в баке, на его месте наконечник трубы. */
    static void pumpTakesSource(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 3, 4);
        h.setBlock(p, AutoContent.PUMPS.get(0).get());
        h.setBlock(p.below(), Blocks.WATER);
        BasicDrillerBlockEntity be = h.getBlockEntity(p, BasicDrillerBlockEntity.class);
        h.onEachTick(() -> be.energy.stored = be.energy.capacity);
        be.setItem(0, new ItemStack(MultiContent.MINING_PIPE.get(), 2));
        h.succeedWhen(() -> {
            h.assertBlockPresent(MultiContent.MINING_PIPE_TIP.get(), p.below());
            h.assertTrue(be.tank().amount() == 1000 && be.tank().resource().getFluid().isSame(Fluids.WATER), "water " + be.tank().amount());
        });
    }

    /** Настраиваемый трансформатор EV: выход LV 2 А питает машину LV рядом. */
    static void adjustableTransformerFeedsLv(GameTestHelper h) {
        h.setBlock(M, AutoContent.ADJUSTABLE_TRANSFORMERS.get(0).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        AdjustableTransformerBlockEntity t = h.getBlockEntity(M, AdjustableTransformerBlockEntity.class);
        t.oTier = VoltageTier.LV.tier();
        t.oAmp = 2;
        t.iAmp = 1;
        h.onEachTick(() -> t.energy.stored = t.energy.capacity);
        ElectricMachineBlockEntity m = LvGameTests.machine(h, M.east(), LvMachineType.MACERATOR, Direction.EAST);
        h.succeedWhen(() -> {
            h.assertTrue(t.outputVoltage() == 32 && t.outputAmperes() == 2, "out " + t.outputVoltage() + " V, " + t.outputAmperes() + " A");
            h.assertTrue(m.energy.stored > 0, "LV machine got nothing");
        });
    }

    /** Зарядник LV: батарейка заряжается быстрее обычного буфера: за тик больше V (до 15 × V). */
    static void chargerChargesFast(GameTestHelper h) {
        h.setBlock(M, LvContent.BATTERY_CHARGERS.get(0).get());
        BatteryBufferBlockEntity c = h.getBlockEntity(M, BatteryBufferBlockEntity.class);
        h.assertTrue(c.energy.capacity == 32L * 256 * 16, "capacity " + c.energy.capacity);
        h.onEachTick(() -> c.energy.stored = c.energy.capacity);
        ItemStack bat = new ItemStack(LvContent.BATTERY_LITHIUM_LV.get());
        c.setItem(0, bat);
        h.succeedWhen(() -> {
            long charge = BatteryItem.charge(c.getItem(0));
            h.assertTrue(charge >= 32, "charge " + charge);
        });
    }

    /** Блоки металлов: компрессор собирает 9 слитков стали в блок (тег c:storage_blocks/steel есть — бур рельсоукладчика парсится). */
    static void compressorMakesSteelBlock(GameTestHelper h) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, M, LvMachineType.COMPRESSOR, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        m.setItem(0, new ItemStack(SelfTest.item("ingot_steel"), 9));
        h.succeedWhen(() -> h.assertTrue(m.getItem(1).is(SelfTest.item("block_steel")), "output " + m.getItem(1)));
    }

    /** Газовая турбина с кислородом: на полной скорости берёт кислород, скорость выше 100 %, режим разгона. */
    static void gasTurbineOxygenBoost(GameTestHelper h) {
        for (int x = 3; x <= 5; x++) for (int y = 1; y <= 3; y++) for (int z = 1; z <= 4; z++)
            h.setBlock(new BlockPos(x, y, z), MultiContent.TURBINE_CASING_STAINLESS.get());
        h.setBlock(new BlockPos(4, 2, 2), Blocks.AIR);
        h.setBlock(new BlockPos(4, 2, 3), Blocks.AIR);
        BlockPos fuelPos = new BlockPos(4, 3, 2), oxyPos = new BlockPos(5, 2, 2), ctrl = new BlockPos(4, 2, 1);
        h.setBlock(fuelPos, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.UP));
        h.setBlock(oxyPos, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.EAST));
        h.setBlock(new BlockPos(3, 2, 2), MultiGameTests.hatch(HatchType.MUFFLER, Direction.WEST));
        h.setBlock(new BlockPos(4, 1, 3), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.DOWN));
        h.setBlock(new BlockPos(4, 2, 4), MultiGameTests.hatch(HatchType.DYNAMO_EV, Direction.SOUTH));
        h.setBlock(ctrl, MultiContent.LARGE_GAS_TURBINE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        LargeGasTurbineBlockEntity t = h.getBlockEntity(ctrl, LargeGasTurbineBlockEntity.class);
        t.maintenance().setMask(63);
        t.setItem(0, MultiContent.ROTORS.get(ru.iis.multi.logic.LargeTurbineLogic.Size.SMALL).get().create("steel"));
        FluidHatchBlockEntity fuel = h.getBlockEntity(fuelPos, FluidHatchBlockEntity.class), oxy = h.getBlockEntity(oxyPos, FluidHatchBlockEntity.class);
        boolean[] started = {false};
        h.onEachTick(() -> {
            fuel.tank().setAmount(GtFluids.get("methane").source().get(), 16000);
            oxy.tank().setAmount(GtFluids.get("oxygen").source().get(), 16000);
            if (!started[0] && t.maxProgress() > 0) {
                started[0] = true;
                t.forceSpeed(10000);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(t.boosted(), "not boosted, speed " + t.efficiency());
            h.assertTrue(t.efficiency() > 10000, "speed " + t.efficiency());
        });
    }

    /** Отпугиватель LV: без энергии радиус 16, с энергией 64; режимы запрещают нужные виды мобов. */
    static void monsterRepellentModes(GameTestHelper h) {
        h.setBlock(M, AutoContent.MONSTER_REPELLENTS.get(0).get());
        MonsterRepellentBlockEntity r = h.getBlockEntity(M, MonsterRepellentBlockEntity.class);
        h.assertTrue(r.denies(false, false) && !r.denies(true, false) && !r.denies(false, true), "hostiles mode");
        r.mode = MonsterRepellentBlockEntity.Mode.HOSTILES_NEUTRALS;
        h.assertTrue(r.denies(true, false) && !r.denies(false, true), "neutrals mode");
        r.mode = MonsterRepellentBlockEntity.Mode.EVERYONE;
        h.assertTrue(r.denies(false, true), "everyone mode");
        h.runAtTickTime(5, () -> {
            h.assertTrue(r.range() == 16, "unpowered range " + r.range());
            r.energy.stored = r.energy.capacity;
        });
        h.runAtTickTime(10, () -> {
            h.assertTrue(r.range() == 64, "powered range " + r.range());
            h.succeed();
        });
    }

    /** Микроволновка LV: картошка печётся за 32 тика, а железный слиток взрывает машину. */
    static void microwaveBakesAndExplodes(GameTestHelper h) {
        ElectricMachineBlockEntity food = LvGameTests.machine(h, new BlockPos(2, 1, 2), LvMachineType.MICROWAVE, Direction.NORTH);
        h.onEachTick(() -> food.energy.stored = food.energy.capacity);
        food.setItem(0, new ItemStack(Items.POTATO));
        BlockPos metal = new BlockPos(7, 1, 7);
        ElectricMachineBlockEntity m = LvGameTests.machine(h, metal, LvMachineType.MICROWAVE, Direction.NORTH);
        m.setItem(0, new ItemStack(Items.RAW_IRON));
        m.energy.stored = m.energy.capacity;
        h.succeedWhen(() -> {
            h.assertTrue(food.getItem(1).is(Items.BAKED_POTATO), "food " + food.getItem(1));
            h.assertBlockNotPresent(LvContent.MACHINES.get(LvMachineType.MICROWAVE).get(), metal);
        });
    }

    /** Большой дизель на 100 % КПД: 2048 EU/t в динамо-люк, топливо и смазка тратятся; без смазки стоит. */
    static void combustionEngineRuns(GameTestHelper h) {
        for (int x = 3; x <= 5; x++) for (int y = 1; y <= 3; y++) for (int z = 1; z <= 4; z++) {
            h.setBlock(new BlockPos(x, y, z), z == 1 ? MultiContent.ENGINE_INTAKE_CASING.get() : MultiContent.STABLE_TITANIUM_CASING.get());
        }
        BlockPos ctrl = new BlockPos(4, 2, 1), fuelPos = new BlockPos(3, 2, 2), lubPos = new BlockPos(5, 2, 2), dynamo = new BlockPos(4, 2, 4);
        h.setBlock(new BlockPos(4, 2, 2), MultiContent.GEAR_BOX_TITANIUM.get());
        h.setBlock(new BlockPos(4, 2, 3), MultiContent.GEAR_BOX_TITANIUM.get());
        h.setBlock(fuelPos, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.WEST));
        h.setBlock(lubPos, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.EAST));
        h.setBlock(new BlockPos(4, 1, 2), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.DOWN));
        h.setBlock(new BlockPos(4, 3, 3), MultiGameTests.hatch(HatchType.MUFFLER, Direction.UP));
        h.setBlock(dynamo, MultiGameTests.hatch(HatchType.DYNAMO_EV, Direction.SOUTH));
        h.setBlock(ctrl, MultiContent.COMBUSTION_ENGINE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        CombustionEngineBlockEntity e = h.getBlockEntity(ctrl, CombustionEngineBlockEntity.class);
        e.maintenance().setMask(63);
        FluidHatchBlockEntity fuel = h.getBlockEntity(fuelPos, FluidHatchBlockEntity.class), lub = h.getBlockEntity(lubPos, FluidHatchBlockEntity.class);
        fuel.tank().setAmount(GtFluids.get("fuel").source().get(), 16000);
        lub.tank().setAmount(GtFluids.get("lubricant").source().get(), 1000);
        DynamoHatchBlockEntity dyn = h.getBlockEntity(dynamo, DynamoHatchBlockEntity.class);
        boolean[] started = {false};
        h.onEachTick(() -> {
            if (!started[0] && e.maxProgress() > 0) {
                started[0] = true;
                e.forceEfficiency(10000);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "engine not formed");
            h.assertTrue(e.output() == 2048, "output " + e.output());
            h.assertTrue(dyn.energy.stored > DynamoHatchBlockEntity.MIN_STORED, "dynamo " + dyn.energy.stored);
            h.assertTrue(fuel.tank().amount() < 16000 && lub.tank().amount() < 1000, "fuel " + fuel.tank().amount() + " lube " + lub.tank().amount());
        });
    }
}
