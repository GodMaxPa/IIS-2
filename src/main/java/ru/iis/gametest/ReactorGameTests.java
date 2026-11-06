package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import ru.iis.cover.CoverKind;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.reactor.*;

/** Реактор и солнечные панели (D117). */
final class ReactorGameTests {
    private ReactorGameTests() {}

    private static final BlockPos POS = new BlockPos(4, 2, 4);

    private static NuclearReactorBlockEntity reactor(GameTestHelper h, boolean powered) {
        h.setBlock(POS, ReactorContent.NUCLEAR_REACTOR.get());
        if (powered) h.setBlock(POS.above(), Blocks.REDSTONE_BLOCK);
        return h.getBlockEntity(POS, NuclearReactorBlockEntity.class);
    }

    private static ItemStack part(String id) {
        return new ItemStack(ReactorContent.PARTS.get(id).get());
    }

    /** Стержень при сигнале даёт 10 EU/t и 4 тепла в секунду; износ идёт в предмет. */
    static void rodProducesEnergyAndHeat(GameTestHelper h) {
        NuclearReactorBlockEntity r = reactor(h, true);
        r.setItem(0, part("rod_uranium_1"));
        h.succeedWhen(() -> {
            h.assertTrue(r.heat() >= 4, "heat " + r.heat());
            h.assertTrue(r.euPerTick() == 10F, "eu " + r.euPerTick());
            h.assertTrue(r.getItem(0).getDamageValue() >= 1, "rod not worn");
        });
    }

    /** Без красного камня реактор молчит. */
    static void needsRedstone(GameTestHelper h) {
        NuclearReactorBlockEntity r = reactor(h, false);
        r.setItem(0, part("rod_uranium_1"));
        h.runAtTickTime(50, () -> {
            h.assertTrue(r.heat() == 0 && r.euPerTick() == 0, "working without a signal");
            h.succeed();
        });
    }

    /** Камеры добавляют столбцы: 3 + 6. */
    static void chambersAddColumns(GameTestHelper h) {
        NuclearReactorBlockEntity r = reactor(h, false);
        h.assertTrue(r.columns() == 3, "base " + r.columns());
        for (Direction d : Direction.values()) h.setBlock(POS.relative(d), ReactorContent.REACTOR_CHAMBER.get());
        h.assertTrue(r.columns() == 9, "with chambers " + r.columns());
        h.succeed();
    }

    /** Через камеру детали попадают в реактор: воронка или труба могут стоять у камеры. */
    static void chamberGivesItems(GameTestHelper h) {
        NuclearReactorBlockEntity r = reactor(h, false);
        BlockPos chamber = POS.east();
        h.setBlock(chamber, ReactorContent.REACTOR_CHAMBER.get());
        var handler = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, h.absolutePos(chamber), Direction.UP);
        h.assertTrue(handler != null, "no item access through the chamber");
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int n = handler.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(part("rod_uranium_1")), 1, tx);
            tx.commit();
            h.assertTrue(n == 1, "inserted " + n);
        }
        h.assertTrue(!r.getItem(0).isEmpty(), "the reactor did not get the rod");
        h.assertTrue(ReactorChamberBlock.reactor(h.getLevel(), h.absolutePos(chamber)) == r, "the chamber does not find its reactor");
        h.succeed();
    }

    /** Провод у камеры получает энергию реактора, хотя к самому реактору ничего не подведено. */
    static void chamberGivesEnergy(GameTestHelper h) {
        BlockPos chamber = POS.east(), cable = POS.east(2), sink = POS.east(3);
        h.setBlock(cable, ru.iis.lv.LvContent.CABLES.get("cable_tin_1x").get());
        h.setBlock(sink, ru.iis.lv.LvContent.MACHINES.get(LvMachineType.MACERATOR).get());
        NuclearReactorBlockEntity r = reactor(h, true);
        h.setBlock(chamber, ReactorContent.REACTOR_CHAMBER.get());
        r.setItem(0, part("rod_uranium_1"));
        ElectricMachineBlockEntity m = h.getBlockEntity(sink, ElectricMachineBlockEntity.class);
        h.succeedWhen(() -> h.assertTrue(m.energy.stored > 0, "no energy through the chamber"));
    }

    /** Камеру убрали: деталь из исчезнувшего столбца выпадает, а не пропадает внутри. */
    static void dropsUnfitting(GameTestHelper h) {
        NuclearReactorBlockEntity r = reactor(h, false);
        h.setBlock(POS.east(), ReactorContent.REACTOR_CHAMBER.get());
        r.setItem(3, part("rod_uranium_1"));
        h.setBlock(POS.east(), Blocks.AIR);
        h.succeedWhen(() -> {
            h.assertTrue(r.getItem(3).isEmpty(), "still inside");
            h.assertItemEntityPresent(ReactorContent.PARTS.get("rod_uranium_1").get(), POS.above(), 2.0);
        });
    }

    /** Перегрев: реактора нет, блоки вокруг разрушены. */
    static void overheatExplodes(GameTestHelper h) {
        NuclearReactorBlockEntity r = reactor(h, true);
        h.setBlock(POS.east(2), Blocks.STONE);
        r.setItem(0, part("rod_uranium_4"));
        r.setHeat(r.maxHeat());
        h.succeedWhen(() -> {
            h.assertBlockNotPresent(ReactorContent.NUCLEAR_REACTOR.get(), POS);
            h.assertBlockNotPresent(Blocks.STONE, POS.east(2));
        });
    }

    /** Отработанный стержень меняется на предмет отработанного. */
    static void spentRodBecomesDepleted(GameTestHelper h) {
        NuclearReactorBlockEntity r = reactor(h, true);
        ItemStack rod = part("rod_uranium_1");
        rod.setDamageValue(rod.getMaxDamage() - 1);
        r.setItem(0, rod);
        h.succeedWhen(() -> h.assertTrue(r.getItem(0).getItem() == ReactorContent.DEPLETED.stream().filter(d -> d.getId().getPath().equals("rod_depleted_uranium_1")).findFirst().orElseThrow().get(), "slot " + r.getItem(0)));
    }

    /** Панель-крышка сверху на машине даёт энергию днём. */
    static void solarCoverCharges(GameTestHelper h) {
        h.setTime(6000);
        ElectricMachineBlockEntity m = LvGameTests.machine(h, POS, LvMachineType.MACERATOR, Direction.NORTH);
        m.covers().attach(Direction.UP, CoverKind.SOLAR_LV);
        long before = m.energy.stored;
        h.succeedWhen(() -> {
            h.assertTrue(m.energy.stored >= before + 32, "stored " + m.energy.stored);
        });
    }

    /** Панель сбоку не работает. */
    static void solarCoverOnlyOnTop(GameTestHelper h) {
        h.setTime(6000);
        ElectricMachineBlockEntity m = LvGameTests.machine(h, POS, LvMachineType.MACERATOR, Direction.NORTH);
        m.covers().attach(Direction.EAST, CoverKind.SOLAR_LV);
        long before = m.energy.stored;
        h.runAtTickTime(220, () -> {
            h.assertTrue(m.energy.stored == before, "side panel charged: " + m.energy.stored);
            h.succeed();
        });
    }

    /** Блок-панель копит 1 EU/t днём. */
    static void solarBlockCharges(GameTestHelper h) {
        h.setTime(6000);
        h.setBlock(POS, ReactorContent.SOLAR_PANEL_BLOCK.get());
        SolarPanelBlockEntity s = h.getBlockEntity(POS, SolarPanelBlockEntity.class);
        h.succeedWhen(() -> h.assertTrue(s.energy.stored >= 5, "stored " + s.energy.stored));
    }
}
