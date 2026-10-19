package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import ru.iis.IIS;
import ru.iis.command.SelfTest;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.GeneratorType;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.*;
import ru.iis.lv.item.BatteryItem;
import ru.iis.machine.block.MachineBlock;

/** Игровые тесты среза LV: энергия по проводам, опасности, буфер, коксовая печь, каучуковое дерево. */
final class LvGameTests {
    private LvGameTests() {}

    // --- helpers -----------------------------------------------------------------------------------------------
    static ElectricMachineBlockEntity machine(GameTestHelper h, BlockPos p, LvMachineType t, Direction facing) {
        h.setBlock(p, LvContent.MACHINES.get(t).get().defaultBlockState().setValue(MachineBlock.FACING, facing));
        return h.getBlockEntity(p, ElectricMachineBlockEntity.class);
    }

    static GeneratorBlockEntity generator(GameTestHelper h, BlockPos p, GeneratorType t, Direction facing) {
        h.setBlock(p, LvContent.GENERATORS.get(t).get().defaultBlockState().setValue(MachineBlock.FACING, facing));
        return h.getBlockEntity(p, GeneratorBlockEntity.class);
    }

    /** Провод с соединениями по текущим соседям (как при установке игроком). */
    static void cable(GameTestHelper h, BlockPos p, String id) {
        BlockState s = LvContent.CABLES.get(id).get().defaultBlockState();
        for (Direction d : Direction.values()) {
            s = s.setValue(CableBlock.PROPS.get(d), CableBlock.canConnect(h.getBlockState(p.relative(d)), d.getOpposite()));
        }
        h.setBlock(p, s);
    }

    static TransformerBlockEntity transformer(GameTestHelper h, BlockPos p, Direction facing, boolean up) {
        h.setBlock(p, LvContent.TRANSFORMER.get().defaultBlockState().setValue(TransformerBlock.FACING, facing).setValue(TransformerBlock.STEP_UP, up));
        return h.getBlockEntity(p, TransformerBlockEntity.class);
    }

    // --- tests --------------------------------------------------------------------------------------------------
    /** Паровая турбина → 3 оловянных кабеля → дробитель LV: руда халькопирита → 2 дроблёной. */
    static void turbinePowersMacerator(GameTestHelper h) {
        GeneratorBlockEntity gen = generator(h, new BlockPos(1, 1, 4), GeneratorType.STEAM_TURBINE, Direction.EAST);
        gen.tank().setAmount(IISFluids.STEAM.get(), 24000);
        ElectricMachineBlockEntity m = machine(h, new BlockPos(5, 1, 4), LvMachineType.MACERATOR, Direction.NORTH);
        for (int x = 2; x <= 4; x++) cable(h, new BlockPos(x, 1, 4), "cable_tin_1x");
        m.setItem(0, new ItemStack(SelfTest.item("ore_chalcopyrite")));
        h.succeedWhen(() -> {
            ItemStack o = m.getItem(1);
            h.assertTrue(o.is(SelfTest.item("crushed_chalcopyrite")) && o.getCount() == 2, "expected 2 crushed chalcopyrite, got " + o);
            h.assertTrue(gen.tank().amount() < 24000, "turbine used no steam");
            IIS.LOGGER.info("[IIS gametest] turbine_powers_macerator done at tick {}, steam left {}", h.getTick(), gen.tank().amount());
        });
    }

    /** Повышающий трансформатор шлёт 128 В по медному кабелю в машину LV — машина взрывается. */
    static void overvoltageExplodes(GameTestHelper h) {
        BlockPos mp = new BlockPos(6, 1, 4);
        machine(h, mp, LvMachineType.MACERATOR, Direction.NORTH);
        TransformerBlockEntity t = transformer(h, new BlockPos(2, 1, 4), Direction.EAST, true);
        for (int x = 3; x <= 5; x++) cable(h, new BlockPos(x, 1, 4), "cable_copper_1x");
        t.energy.stored = t.energy.capacity;
        h.succeedWhen(() -> h.assertTrue(!(h.getBlockState(mp).getBlock() instanceof ElectricMachineBlock), "machine survived 128 V"));
    }

    /** 128 В в оловянный кабель (предел 32 В): кабель перегревается и сгорает. */
    static void cableBurns(GameTestHelper h) {
        TransformerBlockEntity t = transformer(h, new BlockPos(2, 1, 4), Direction.EAST, true);
        BlockPos c = new BlockPos(3, 1, 4);
        cable(h, c, "cable_tin_1x");
        cable(h, c.east(), "cable_tin_1x");
        h.onEachTick(() -> {
            if (!t.isRemoved()) t.energy.stored = t.energy.capacity;
        });
        h.succeedWhen(() -> {
            BlockState s = h.getBlockState(c);
            h.assertTrue(s.isAir() || s.getBlock() instanceof BaseFireBlock, "cable still there: " + s);
        });
    }

    /** Заряженная машина рядом с водой взрывается (GT: в половине каждых 1024 тиков, шанс 1/3 за тик). */
    static void waterExplodes(GameTestHelper h) {
        BlockPos mp = new BlockPos(4, 1, 4);
        ElectricMachineBlockEntity m = machine(h, mp, LvMachineType.MACERATOR, Direction.NORTH);
        h.setBlock(mp.east(), Blocks.WATER);
        h.onEachTick(() -> {
            if (!m.isRemoved()) m.energy.stored = m.energy.capacity;
        });
        h.succeedWhen(() -> h.assertTrue(!(h.getBlockState(mp).getBlock() instanceof ElectricMachineBlock), "machine next to water did not explode"));
    }

    /** Свинья на голом оловянном проводе под током получает урон. */
    static void bareWireShocks(GameTestHelper h) {
        GeneratorBlockEntity gen = generator(h, new BlockPos(1, 1, 4), GeneratorType.STEAM_TURBINE, Direction.EAST);
        gen.tank().setAmount(IISFluids.STEAM.get(), 24000);
        machine(h, new BlockPos(5, 1, 4), LvMachineType.MACERATOR, Direction.NORTH);
        for (int x = 2; x <= 4; x++) cable(h, new BlockPos(x, 1, 4), "wire_tin_1x");
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(3, 1, 4));
        float start = pig.getHealth();
        h.succeedWhen(() -> h.assertTrue(pig.getHealth() < start || pig.isDeadOrDying(), "pig was not shocked, health " + pig.getHealth()));
    }

    /** Буфер с запасом выше 2/3 заряжает батарейку и отдаёт энергию машине. */
    static void bufferChargesBattery(GameTestHelper h) {
        BlockPos bp = new BlockPos(2, 1, 4);
        h.setBlock(bp, LvContent.BATTERY_BUFFER_1X1.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        BatteryBufferBlockEntity b = h.getBlockEntity(bp, BatteryBufferBlockEntity.class);
        ElectricMachineBlockEntity m = machine(h, new BlockPos(4, 1, 4), LvMachineType.MACERATOR, Direction.NORTH);
        cable(h, new BlockPos(3, 1, 4), "cable_tin_1x");
        b.setItem(0, new ItemStack(LvContent.BATTERY_LV.get()));
        h.onEachTick(() -> b.energy.stored = b.energy.capacity);
        h.succeedWhen(() -> {
            h.assertTrue(BatteryItem.charge(b.getItem(0)) > 0, "battery not charged");
            h.assertTrue(m.energy.stored > 0, "machine got no energy from the buffer");
        });
    }

    /** Коксовая печь 3×3×3: уголь → кокс + 500 л креозота за 1800 тиков. */
    static void cokeOven(GameTestHelper h) {
        BlockPos center = new BlockPos(4, 2, 4);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    h.setBlock(center.offset(dx, dy, dz), LvContent.COKE_OVEN_BRICKS.get());
                }
        BlockPos ctrl = center.north();
        h.setBlock(ctrl, LvContent.COKE_OVEN.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        CokeOvenBlockEntity o = h.getBlockEntity(ctrl, CokeOvenBlockEntity.class);
        o.setItem(CokeOvenBlockEntity.SLOT_IN, new ItemStack(Items.COAL));
        h.succeedWhen(() -> {
            h.assertTrue(o.getItem(CokeOvenBlockEntity.SLOT_OUT).is(LvContent.COAL_COKE.get()), "no coke yet");
            h.assertTrue(o.tank().amount() == 500, "creosote " + o.tank().amount());
        });
    }

    /** Промывка: дроблёная руда + 1000 л воды → очищенная руда. */
    static void oreWasher(GameTestHelper h) {
        ElectricMachineBlockEntity m = machine(h, new BlockPos(4, 1, 4), LvMachineType.ORE_WASHER, Direction.NORTH);
        m.tank().setAmount(Fluids.WATER, 16000);
        m.setItem(0, new ItemStack(SelfTest.item("crushed_chalcopyrite")));
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        h.succeedWhen(() -> {
            h.assertTrue(m.getItem(2).is(SelfTest.item("purified_chalcopyrite")), "expected purified chalcopyrite, got " + m.getItem(2));
            h.assertTrue(m.tank().amount() == 15000, "water " + m.tank().amount());
        });
    }

    /** Саженец каучукового дерева вырастает в дерево из нашего ствола (проверка мировой генерации дерева). */
    static void rubberTree(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 4);
        h.setBlock(p.below(), Blocks.DIRT);
        h.setBlock(p, LvContent.RUBBER_SAPLING.get());
        var level = h.getLevel();
        BlockPos abs = h.absolutePos(p);
        boolean grown = LvContent.RUBBER_GROWER.growTree(level, level.getChunkSource().getGenerator(), abs, level.getBlockState(abs), level.getRandom());
        h.assertTrue(grown, "tree did not grow");
        h.assertTrue(h.getBlockState(p).is(LvContent.RUBBER_LOG.get()), "no rubber log at the sapling");
        h.succeed();
    }

    /** Мультиметр видит ток в кабеле (31 В после потери 1 В, 1 А) и средний приход EU в машину. */
    static void multimeter(GameTestHelper h) {
        GeneratorBlockEntity gen = generator(h, new BlockPos(1, 1, 4), GeneratorType.STEAM_TURBINE, Direction.EAST);
        gen.tank().setAmount(IISFluids.STEAM.get(), 24000);
        ElectricMachineBlockEntity m = machine(h, new BlockPos(3, 1, 4), LvMachineType.MACERATOR, Direction.NORTH);
        BlockPos c = new BlockPos(2, 1, 4);
        cable(h, c, "cable_tin_1x");
        h.succeedWhen(() -> {
            CableBlockEntity be = h.getBlockEntity(c, CableBlockEntity.class);
            h.assertTrue(be.state.voltageLast20 == 31 && be.state.amperageLast20 >= 1, "cable reading " + be.state.voltageLast20 + " V " + be.state.amperageLast20 + " A");
            h.assertTrue(m.avgEuIn > 0 && m.lastVoltageIn == 31, "machine flow " + m.avgEuIn + " EU/t at " + m.lastVoltageIn + " V");
            var report = ru.iis.lv.EnergyReadout.report(h.getLevel(), h.absolutePos(c));
            h.assertTrue(report.size() >= 3, "short report");
            IIS.LOGGER.info("[IIS gametest] multimeter: {}", report.stream().map(net.minecraft.network.chat.Component::getString).toList());
        });
    }
}
