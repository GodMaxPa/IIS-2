package ru.iis.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import ru.iis.fluid.GtFluids;
import ru.iis.machine.block.FluidTank;

import java.util.List;

/** Бак GC 12000 л, только ракетное топливо; заправляет ракету на площадке рядом, 100 л в тик. */
public class FuelLoaderBlockEntity extends BlockEntity {
    public static final int CAPACITY = 12000, RATE = 100;
    public final FluidTank tank;

    public FuelLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContent.FUEL_LOADER_BE.get(), pos, state);
        tank = new FluidTank(CAPACITY, f -> f == rocketFuel(), false, this::setChanged);
    }

    private static Fluid rocketFuel() {
        return GtFluids.get("rocket_fuel").source().get();
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, FuelLoaderBlockEntity be) {
        if (!(level instanceof ServerLevel) || be.tank.amount() <= 0) return;
        List<RocketEntity> rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(pos).inflate(3, 2, 3), r -> r.state() == RocketEntity.IDLE);
        if (rockets.isEmpty()) return;
        RocketEntity rocket = rockets.getFirst();
        int moved = Math.min(RATE, Math.min(be.tank.amount(), RocketEntity.CAPACITY_T1 - rocket.fuel()));
        if (moved <= 0) return;
        rocket.setFuel(rocket.fuel() + moved);
        be.tank.setAmount(rocketFuel(), be.tank.amount() - moved);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Fuel", tank.amount());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        int amount = in.getIntOr("Fuel", 0);
        if (amount > 0) tank.setAmount(rocketFuel(), amount);
    }
}
