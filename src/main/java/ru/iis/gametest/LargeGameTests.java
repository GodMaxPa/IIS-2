package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.LvContent;
import ru.iis.lv.item.ProgrammedCircuitItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;
import ru.iis.multi.logic.LargeBoilerLogic;
import ru.iis.multi.logic.LargeTurbineLogic;

/** Большие котлы (D88) и большая паровая турбина (D89). */
final class LargeGameTests {
    private LargeGameTests() {}

    static final BlockPos CTRL = new BlockPos(4, 1, 2), BUS = new BlockPos(3, 1, 2), WATER = new BlockPos(5, 1, 2),
            FUEL_HATCH = new BlockPos(4, 1, 4), STEAM = new BlockPos(4, 2, 2), PIPE = new BlockPos(4, 3, 3);

    /**
     * Котёл 3×5×3: контроллер (4,1,2) лицом на север, середина (4,·,3). Низ: шина топлива, люк воды (каждый тик доливается),
     * люк жидкого топлива (4,1,4), обслуживание, глушитель лицом на восток, 3 топки. Над контроллером — выходной люк пара.
     */
    static LargeBoilerBlockEntity build(GameTestHelper h, LargeBoilerLogic.Tier tier) {
        LargeBoilerBlockEntity.Casings c = MultiContent.boilerCasings(tier);
        BlockPos center = CTRL.south();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos col = center.offset(dx, 0, dz);
                h.setBlock(col, c.firebox());
                for (int k = 1; k <= 4; k++) h.setBlock(col.above(k), dx == 0 && dz == 0 && k < 4 ? c.pipe() : c.casing());
            }
        }
        h.setBlock(BUS, MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.NORTH));
        h.setBlock(WATER, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.NORTH));
        h.setBlock(FUEL_HATCH, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.SOUTH));
        h.setBlock(new BlockPos(3, 1, 4), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.SOUTH));
        h.setBlock(new BlockPos(5, 1, 4), MultiGameTests.hatch(HatchType.MUFFLER, Direction.EAST));
        h.setBlock(STEAM, MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.NORTH));
        h.setBlock(CTRL, MultiContent.LARGE_BOILERS.get(tier).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        LargeBoilerBlockEntity b = h.getBlockEntity(CTRL, LargeBoilerBlockEntity.class);
        b.maintenance().setMask(63);
        FluidHatchBlockEntity water = h.getBlockEntity(WATER, FluidHatchBlockEntity.class);
        h.onEachTick(() -> water.tank().setAmount(Fluids.WATER, HatchType.TANK));
        return b;
    }

    private static int steam(GameTestHelper h) {
        FluidHatchBlockEntity out = h.getBlockEntity(STEAM, FluidHatchBlockEntity.class);
        return out.tank().amount() > 0 && out.tank().resource().getFluid().isSame(IISFluids.STEAM.get()) ? out.tank().amount() : 0;
    }

    /** Уголь в шине → котёл разгорается (КПД растёт порциями), в выходной люк идёт пар, уголь убывает. */
    static void bronzeBoilerCoal(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.BRONZE);
        ItemBusBlockEntity bus = h.getBlockEntity(BUS, ItemBusBlockEntity.class);
        bus.setItem(0, new ItemStack(Items.COAL, 64));
        h.succeedWhen(() -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.assertTrue(h.getBlockState(CTRL).getValue(MachineBlock.ACTIVE), "boiler not burning");
            h.assertTrue(bus.getItem(0).getCount() < 64, "coal not used");
            h.assertTrue(b.efficiency() > 0, "efficiency did not grow");
            h.assertTrue(steam(h) > 0, "no steam in the output hatch");
        });
    }

    /** Полужидкое топливо: 8000 л креозота из люка → 128 тиков горения (8 × 16, D88 dense). */
    static void bronzeBoilerCreosote(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.BRONZE);
        FluidHatchBlockEntity fuel = h.getBlockEntity(FUEL_HATCH, FluidHatchBlockEntity.class);
        fuel.tank().setAmount(IISFluids.CREOSOTE.get(), 9000);
        h.succeedWhen(() -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.assertTrue(fuel.tank().amount() == 1000, "creosote left " + fuel.tank().amount());
            h.assertTrue(b.logic().progressTimeStash > 0 || b.maxProgress() > 0, "creosote did not burn");
        });
    }

    /** Схема 8 в контроллере горячего стального котла: 600 − 8 × 25 = 400 EU/t (800 л/т пара вместо 1200). */
    static void boilerCircuitThrottle(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.STEEL);
        ItemBusBlockEntity bus = h.getBlockEntity(BUS, ItemBusBlockEntity.class);
        bus.setItem(0, new ItemStack(Items.COAL, 64));
        b.setItem(0, ProgrammedCircuitItem.withConfig(new ItemStack(LvContent.PROGRAMMED_CIRCUIT.get()), 8));
        h.onEachTick(() -> {
            if (b.efficiency() < 10000 && b.maxProgress() > 0) setEfficiency(b, 10000);
        });
        h.succeedWhen(() -> {
            h.assertTrue(b.logic().circuitConfig == 8, "circuit not read: " + b.logic().circuitConfig);
            h.assertTrue(b.logic().eut == 400, "steel boiler at circuit 8 must give 400 EU/t, got " + b.logic().eut);
        });
    }

    private static void setEfficiency(MultiblockControllerBlockEntity b, int e) {
        try {
            var f = MultiblockControllerBlockEntity.class.getDeclaredField("efficiency");
            f.setAccessible(true);
            f.setInt(b, e);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Без трубчатого корпуса в середине, с двумя топками или с чужим блоком в стенке форма не собирается. */
    static void boilerStructureRules(GameTestHelper h) {
        LargeBoilerBlockEntity b = build(h, LargeBoilerLogic.Tier.BRONZE);
        BlockPos firebox = new BlockPos(3, 1, 3);
        h.runAtTickTime(25, () -> {
            h.assertTrue(b.formed(), "boiler not formed");
            h.setBlock(PIPE, MultiContent.BRONZE_PLATED_BRICKS.get());
        });
        h.runAtTickTime(50, () -> {
            h.assertFalse(b.formed(), "formed without a pipe casing");
            h.setBlock(PIPE, MultiContent.BRONZE_PIPE_CASING.get());
            h.setBlock(firebox, MultiGameTests.hatch(HatchType.INPUT_BUS, Direction.NORTH)); // топок осталось 2
        });
        h.runAtTickTime(75, () -> {
            h.assertFalse(b.formed(), "formed with two fireboxes");
            h.setBlock(firebox, MultiContent.BRONZE_FIREBOX_CASING.get());
        });
        h.runAtTickTime(100, () -> {
            h.assertTrue(b.formed(), "three fireboxes must be enough");
            h.setBlock(STEAM, Blocks.STONE);
        });
        h.runAtTickTime(125, () -> {
            h.assertFalse(b.formed(), "formed with stone in the casing");
            h.succeed();
        });
    }

    // --- турбина ----------------------------------------------------------------------------------------------------
    static final BlockPos T_CTRL = new BlockPos(4, 2, 1), T_STEAM = new BlockPos(4, 3, 2), T_WATER = new BlockPos(3, 2, 2),
            T_DYNAMO = new BlockPos(4, 2, 4);

    /**
     * Турбина 3×3×4: контроллер (4,2,1) лицом на север (перед ним 3×3 воздуха), тело — z = 1…4. Вход пара сверху, выход воды
     * слева, обслуживание снизу (второй слой), динамо-люк dynamo в середине задней стороны. Пар доливается каждый тик.
     */
    static LargeTurbineBlockEntity turbine(GameTestHelper h, HatchType dynamo) {
        for (int x = 3; x <= 5; x++)
            for (int y = 1; y <= 3; y++)
                for (int z = 1; z <= 4; z++) h.setBlock(new BlockPos(x, y, z), MultiContent.TURBINE_CASING.get());
        h.setBlock(new BlockPos(4, 2, 2), Blocks.AIR);
        h.setBlock(new BlockPos(4, 2, 3), Blocks.AIR);
        h.setBlock(T_STEAM, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.UP));
        h.setBlock(T_WATER, MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.WEST));
        h.setBlock(new BlockPos(4, 1, 3), MultiGameTests.hatch(HatchType.MAINTENANCE, Direction.DOWN));
        h.setBlock(T_DYNAMO, MultiGameTests.hatch(dynamo, Direction.SOUTH));
        h.setBlock(T_CTRL, MultiContent.LARGE_STEAM_TURBINE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        LargeTurbineBlockEntity t = h.getBlockEntity(T_CTRL, LargeTurbineBlockEntity.class);
        t.maintenance().setMask(63);
        t.setItem(0, MultiContent.ROTORS.get(LargeTurbineLogic.Size.SMALL).get().create("steel"));
        FluidHatchBlockEntity steam = h.getBlockEntity(T_STEAM, FluidHatchBlockEntity.class);
        h.onEachTick(() -> steam.tank().setAmount(IISFluids.STEAM.get(), HatchType.TANK));
        return t;
    }

    /** Стальной малый ротор, люк MV: выход растёт к 105 EU/t, энергия идёт в динамо-люк, из пара — дистиллированная вода. */
    static void turbineMakesPower(GameTestHelper h) {
        LargeTurbineBlockEntity t = turbine(h, HatchType.DYNAMO_MV);
        DynamoHatchBlockEntity dyn = h.getBlockEntity(T_DYNAMO, DynamoHatchBlockEntity.class);
        FluidHatchBlockEntity water = h.getBlockEntity(T_WATER, FluidHatchBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(t.formed(), "turbine not formed");
            h.assertTrue(t.eut() == 105, "turbine EU/t " + t.eut());
            h.assertTrue(dyn.energy.stored > DynamoHatchBlockEntity.MIN_STORED, "dynamo got " + dyn.energy.stored);
            h.assertTrue(water.tank().amount() > 0, "no distilled water");
        });
    }

    /** Без воздуха перед лицом, без динамо-люка или без ротора турбина не работает. */
    static void turbineStructureRules(GameTestHelper h) {
        LargeTurbineLogic.Size small = LargeTurbineLogic.Size.SMALL;
        LargeTurbineBlockEntity t = turbine(h, HatchType.DYNAMO_MV);
        BlockPos front = new BlockPos(3, 3, 0);
        h.runAtTickTime(25, () -> {
            h.assertTrue(t.formed(), "turbine not formed");
            h.setBlock(front, Blocks.STONE);
        });
        h.runAtTickTime(50, () -> {
            h.assertFalse(t.formed(), "formed with a block in front of the rotor");
            h.setBlock(front, Blocks.AIR);
            h.setBlock(T_DYNAMO, MultiContent.TURBINE_CASING.get());
        });
        h.runAtTickTime(75, () -> {
            h.assertFalse(t.formed(), "formed without a dynamo hatch");
            h.setBlock(T_DYNAMO, MultiGameTests.hatch(HatchType.DYNAMO, Direction.SOUTH));
            t.setItem(0, ItemStack.EMPTY);
        });
        h.runAtTickTime(200, () -> {
            h.assertTrue(t.formed(), "turbine with an LV dynamo not formed");
            h.assertTrue(t.maxProgress() == 0 && t.eut() == 0, "turbine runs without a rotor");
            h.assertTrue(MultiContent.ROTORS.get(small).get().create("steel").getMaxDamage() == 51200, "steel small rotor durability");
            h.succeed();
        });
    }

    // --- детали турбины (D90) ---------------------------------------------------------------------------------------
    /** Сборщик MV: 4 стальные лопасти + длинный стержень магналия → стальной малый ротор (100 EU/t, прочность 51200). */
    static void assemblerMakesSmallRotor(GameTestHelper h) {
        ru.iis.lv.block.ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), ru.iis.lv.LvMachineType.ASSEMBLER_MV, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("turbine_blade_steel"), 4));
        m.setItem(1, new ItemStack(ru.iis.command.SelfTest.item("long_rod_magnalium")));
        h.succeedWhen(() -> {
            ItemStack o = m.getItem(m.type().inputs);
            h.assertTrue(o.getItem() instanceof ru.iis.multi.TurbineRotorItem r && r.size == LargeTurbineLogic.Size.SMALL, "rotor: " + o);
            h.assertTrue("steel".equals(o.get(MultiContent.ROTOR_MATERIAL.get())), "rotor material " + o.get(MultiContent.ROTOR_MATERIAL.get()));
            h.assertTrue(o.getMaxDamage() == 51200, "rotor durability " + o.getMaxDamage());
        });
    }

    /** Гравёр MV: кремниевая пластина + рубиновая линза (красная) → пластина логических микросхем; линза остаётся (D91). */
    static void engraverMakesIlcWafer(GameTestHelper h) {
        ru.iis.lv.block.ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), ru.iis.lv.LvMachineType.LASER_ENGRAVER_MV, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        m.setItem(0, new ItemStack(ru.iis.command.SelfTest.item("silicon_wafer")));
        m.setItem(1, new ItemStack(ru.iis.command.SelfTest.item("lens_ruby")));
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(2).is(ru.iis.command.SelfTest.item("wafer_ilc")), "output " + m.getItem(2));
            h.assertTrue(m.getItem(1).is(ru.iis.command.SelfTest.item("lens_ruby")), "lens consumed: " + m.getItem(1));
        });
    }
}
