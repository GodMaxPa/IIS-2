package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import ru.iis.cover.CoverKind;
import ru.iis.fluid.IISFluids;
import ru.iis.fluid.UniversalTankBlockEntity;
import ru.iis.machine.IISMachines;
import ru.iis.machine.SteamMachineType;
import ru.iis.machine.block.FluidTank;
import ru.iis.machine.block.MachineBlock;
import ru.iis.machine.block.PipeBlock;
import ru.iis.machine.block.PipeBlockEntity;
import ru.iis.machine.block.SteamMachineBlockEntity;
import ru.iis.multi.block.FluidHatchBlockEntity;
import ru.iis.multi.block.HatchType;

/** Покрытия GT на паровых машинах, трубах и люках; регулятор и фильтр жидкости (D80). */
final class CoverHostGameTests {
    private CoverHostGameTests() {}

    private static int tryInsert(ResourceHandler<FluidResource> h, net.minecraft.world.level.material.Fluid f, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            return h.insert(FluidResource.of(f), amount, tx);
        }
    }

    /** Регулятор на входном люке, −20 л/тик: люк тянет воду из соседнего резервуара (GT_Cover_FluidRegulator). */
    static void regulatorPullsIntoHatch(GameTestHelper h) {
        BlockPos hp = new BlockPos(3, 1, 4);
        h.setBlock(hp, MultiGameTests.hatch(HatchType.INPUT_HATCH, Direction.NORTH));
        FluidHatchBlockEntity hatch = h.getBlockEntity(hp, FluidHatchBlockEntity.class);
        UniversalTankBlockEntity tank = FluidGameTests.tank(h, new BlockPos(4, 1, 4), "universal_tank_steel");
        FluidGameTests.insert(tank.tank, Fluids.WATER, 10000);
        hatch.covers().attach(Direction.EAST, CoverKind.FLUID_REGULATOR);
        for (int i = 0; i < 20; i++) hatch.covers().regulatorClick(Direction.EAST, false);
        h.assertTrue(hatch.covers().data(Direction.EAST) == -20, "rate " + hatch.covers().data(Direction.EAST));
        h.succeedWhen(() -> h.assertTrue(hatch.tank().amount() >= 200 && hatch.tank().amount() % 20 == 0, "hatch water " + hatch.tank().amount()));
    }

    /** Фильтр жидкости на трубе, режим 0 и вода: снаружи в трубу идёт только вода (GT_Cover_Fluidfilter#letsFluidIn). */
    static void filterOnPipe(GameTestHelper h) {
        BlockPos pp = new BlockPos(4, 1, 4);
        BlockState pipe = IISMachines.PIPES.get("pipe_medium_bronze").get().defaultBlockState().setValue(PipeBlock.PROPS.get(Direction.EAST), true);
        h.setBlock(pp, pipe);
        PipeBlockEntity be = h.getBlockEntity(pp, PipeBlockEntity.class);
        be.covers().attach(Direction.EAST, CoverKind.FLUID_FILTER);
        be.covers().setFilterFluid(Direction.EAST, Fluids.WATER);
        ResourceHandler<FluidResource> side = h.getLevel().getCapability(Capabilities.Fluid.BLOCK, h.absolutePos(pp), Direction.EAST);
        h.assertTrue(side != null, "the pipe side must be connected");
        h.assertTrue(tryInsert(side, Fluids.LAVA, 100) == 0, "lava must be filtered out");
        h.assertTrue(tryInsert(side, Fluids.WATER, 100) > 0, "water must pass");
        h.succeed();
    }

    /** Детектор жидкости на паровой машине: полный бак пара — сигнал 15; покрытие тикает вместе с машиной. */
    static void detectorOnSteamMachine(GameTestHelper h) {
        BlockPos mp = new BlockPos(4, 1, 4);
        h.setBlock(mp, IISMachines.STEAM_MACHINES.get(SteamMachineType.MACERATOR.blockId(SteamMachineType.Tier.BRONZE)).get()
                .defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        SteamMachineBlockEntity m = h.getBlockEntity(mp, SteamMachineBlockEntity.class);
        FluidTank steam = (FluidTank) m.rawFluids(null);
        steam.setAmount(IISFluids.STEAM.get(), steam.capacity());
        m.covers().attach(Direction.EAST, CoverKind.FLUID_DETECTOR);
        h.succeedWhen(() -> h.assertTrue(m.covers().redstoneOut(Direction.EAST) == 15, "signal " + m.covers().redstoneOut(Direction.EAST)));
    }
}
