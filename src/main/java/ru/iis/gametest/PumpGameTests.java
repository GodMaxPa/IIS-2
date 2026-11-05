package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import ru.iis.command.SelfTest;
import ru.iis.fluid.GtFluids;
import ru.iis.fluid.IISFluids;
import ru.iis.lv.LvContent;
import ru.iis.lv.LvMachineType;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.lv.item.ProgrammedCircuitItem;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.block.PipeBlock;
import ru.iis.multi.MultiContent;
import ru.iis.multi.block.*;

/** Водяные насосы и машина-фильтр (D107). Биом тестового мира не река: вид воды задаётся {@link WaterPumpBlockEntity#forcedKind}. */
final class PumpGameTests {
    private PumpGameTests() {}

    private static final BlockPos CTRL = new BlockPos(4, 1, 4), INTAKE = new BlockPos(4, 1, 7);

    private static BlockState pipe(String id, Direction... connected) {
        BlockState s = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("iis", id)).defaultBlockState();
        for (Direction d : connected) s = s.setValue(PipeBlock.PROPS.get(d), true);
        return s;
    }

    /** Насос смотрит на север, сзади — две трубы к заборнику, за заборником — пруд 5×4 в каменной рамке (20 блоков воды). */
    private static void build(GameTestHelper h, int tier, String pipeId, HatchType input) {
        h.setBlock(CTRL, MultiContent.WATER_PUMPS.get(tier).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        h.setBlock(CTRL.west(), MultiGameTests.hatch(input, Direction.WEST));
        h.setBlock(CTRL.west().above(), MultiGameTests.hatch(HatchType.OUTPUT_HATCH, Direction.WEST));
        h.setBlock(CTRL.above(), pipe(pipeId));
        h.setBlock(CTRL.south(), pipe(pipeId, Direction.NORTH, Direction.SOUTH));
        h.setBlock(CTRL.south(2), pipe(pipeId, Direction.NORTH));
        h.setBlock(INTAKE, MultiContent.PUMP_INTAKES.get(tier).get());
        for (int x = 1; x <= 7; x++) {
            for (int z = 7; z <= 12; z++) {
                BlockPos p = new BlockPos(x, 1, z);
                if (p.equals(INTAKE)) continue;
                boolean border = x == 1 || x == 7 || z == 7 || z == 12;
                h.setBlock(p, border ? Blocks.STONE : Blocks.WATER);
            }
        }
    }

    /** Примитивный насос на реке: 40 л пара за цикл, из пруда 20 + 1 блок — 21/128 от 6,25 л/т, вода в выходном люке. */
    static void primitivePumpGivesWater(GameTestHelper h) {
        build(h, 0, "pipe_medium_bronze", HatchType.INPUT_HATCH);
        WaterPumpBlockEntity e = h.getBlockEntity(CTRL, WaterPumpBlockEntity.class);
        e.forcedKind = WaterPumpBlockEntity.Kind.RIVER;
        FluidHatchBlockEntity steam = h.getBlockEntity(CTRL.west(), FluidHatchBlockEntity.class);
        h.onEachTick(() -> steam.tank().setAmount(IISFluids.STEAM.get(), 8000));
        FluidHatchBlockEntity out = h.getBlockEntity(CTRL.west().above(), FluidHatchBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "pump not formed");
            h.assertTrue(e.waterSurface() == 21, "surface " + e.waterSurface());
            h.assertTrue(out.tank().amount() > 0 && out.tank().resource().getFluid().isSame(Fluids.WATER), "water " + out.tank().amount());
        });
    }

    /** Насос LV на океане: солёная вода; берёт энергию из люка. */
    static void electricPumpGivesSaltWater(GameTestHelper h) {
        build(h, 1, "pipe_medium_bronze", HatchType.ENERGY);
        WaterPumpBlockEntity e = h.getBlockEntity(CTRL, WaterPumpBlockEntity.class);
        e.forcedKind = WaterPumpBlockEntity.Kind.OCEAN;
        h.onEachTick(() -> {
            if (h.getLevel().getBlockEntity(h.absolutePos(CTRL.west())) instanceof EnergyHatchBlockEntity hatch) hatch.energy.stored = hatch.energy.capacity;
        });
        FluidHatchBlockEntity out = h.getBlockEntity(CTRL.west().above(), FluidHatchBlockEntity.class);
        h.succeedWhen(() -> {
            h.assertTrue(e.formed(), "pump not formed");
            h.assertTrue(out.tank().amount() > 0 && out.tank().resource().getFluid().isSame(GtFluids.get("saltwater").source().get()), "salt " + out.tank().amount());
        });
    }

    /** Заборник чужого уровня: насос не собирается. */
    static void pumpNeedsOwnIntake(GameTestHelper h) {
        build(h, 0, "pipe_medium_bronze", HatchType.INPUT_HATCH);
        h.setBlock(INTAKE, MultiContent.PUMP_INTAKES.get(1).get());
        WaterPumpBlockEntity e = h.getBlockEntity(CTRL, WaterPumpBlockEntity.class);
        e.forcedKind = WaterPumpBlockEntity.Kind.RIVER;
        h.runAtTickTime(40, () -> {
            h.assertTrue(!e.formed(), "formed with a foreign intake");
            h.succeed();
        });
    }

    /** Фильтр LV: схема 1, 1000 л грязной воды → 900 л воды и крошечные пыли (50 тиков, 12 EU/t). */
    static void filterCleansDirtyWater(GameTestHelper h) {
        ElectricMachineBlockEntity m = LvGameTests.machine(h, new BlockPos(4, 1, 4), LvMachineType.FILTER, Direction.NORTH);
        h.onEachTick(() -> m.energy.stored = m.energy.capacity);
        m.setItem(0, ProgrammedCircuitItem.withConfig(new ItemStack(LvContent.PROGRAMMED_CIRCUIT.get()), 1));
        m.tank().setAmount(GtFluids.get("dirtywater").source().get(), 1000);
        h.succeedWhen(() -> {
            h.assertTrue(m.outTank().amount() == 900 && m.outTank().resource().getFluid().isSame(Fluids.WATER), "water " + m.outTank().amount());
            boolean dust = false;
            for (int i = 1; i < 7; i++) dust |= m.getItem(i).is(SelfTest.item("tiny_dust_silicon_dioxide"));
            h.assertTrue(dust, "no byproduct");
        });
    }
}
