package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import ru.iis.command.SelfTest;
import ru.iis.fluid.GtFluids;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.GeneratorType;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.lv.block.GeneratorBlockEntity;
import ru.iis.lv.item.ProgrammedCircuitItem;

/** Провода 8x–16x и новые материалы проводов (makeWires, ProcessingWire; D79). */
final class WireGameTests {
    private WireGameTests() {}

    private static ElectricMachineBlockEntity assembler(GameTestHelper h) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.ASSEMBLER, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        return m;
    }

    private static ItemStack circuit(int n) {
        return ProgrammedCircuitItem.withConfig(new ItemStack(LvContent.PROGRAMMED_CIRCUIT.get()), n);
    }

    /** Сборщик: 8 оловянных проводов 1x + схема 8 → провод 8x (ProcessingWire:54); схема остаётся. */
    static void assemblerJoinsWires(GameTestHelper h) {
        ElectricMachineBlockEntity m = assembler(h);
        m.setItem(0, new ItemStack(SelfTest.item("wire_tin_1x"), 8));
        m.setItem(1, circuit(8));
        h.succeedWhen(() -> {
            ItemStack o = m.getItem(m.type().inputs);
            h.assertTrue(o.is(SelfTest.item("wire_tin_8x")), "8x tin wire: " + o);
            h.assertTrue(ProgrammedCircuitItem.config(m.getItem(1)) == 8, "the circuit must stay");
        });
    }

    /** Сборщик: медный провод 8x + схема 24 + 432 мБ расплава резины → кабель 8x (ProcessingWire:113, cost = 8/4 + 1). */
    static void assemblerInsulatesCopper(GameTestHelper h) {
        ElectricMachineBlockEntity m = assembler(h);
        m.setItem(0, new ItemStack(SelfTest.item("wire_copper_8x")));
        m.setItem(1, circuit(24));
        m.tank().setAmount(GtFluids.get("molten_rubber").source().get(), 432);
        h.succeedWhen(() -> {
            ItemStack o = m.getItem(m.type().inputs);
            h.assertTrue(o.is(SelfTest.item("cable_copper_8x")), "8x copper cable: " + o);
            h.assertTrue(m.tank().amount() == 0, "rubber used: " + m.tank().amount());
        });
    }

    /** Ток идёт по стальному кабелю 16x и алюминиевому 8x (новые материалы) от турбины к дробителю. */
    static void newWiresCarryPower(GameTestHelper h) {
        GeneratorBlockEntity gen = LvGameTests.generator(h, new BlockPos(1, 1, 4), GeneratorType.STEAM_TURBINE, Direction.EAST);
        gen.tank().setAmount(IISFluids.STEAM.get(), 24000);
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(5, 1, 4), LvMachineType.MACERATOR, Direction.NORTH);
        LvGameTests.cable(h, new BlockPos(2, 1, 4), "wire_steel_16x");
        LvGameTests.cable(h, new BlockPos(3, 1, 4), "cable_aluminium_8x");
        LvGameTests.cable(h, new BlockPos(4, 1, 4), "cable_silver_1x");
        h.succeedWhen(() -> h.assertTrue(m.energy.stored > 0, "the macerator got no energy over the new cables"));
    }
}
