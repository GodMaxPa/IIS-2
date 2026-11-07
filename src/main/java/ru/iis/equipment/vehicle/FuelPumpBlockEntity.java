package ru.iis.equipment.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import ru.iis.equipment.EquipmentContent;
import ru.iis.machine.block.FluidTank;

import java.util.List;

/** Бак на 32 ведра любого топлива машин; заправляет стоящую рядом машину, 100 л в тик, если топливо ей подходит. Лишнее не льётся. */
public class FuelPumpBlockEntity extends BlockEntity {
    public static final int CAPACITY = 32000, RATE = 100;
    public final FluidTank tank;

    public FuelPumpBlockEntity(BlockPos pos, BlockState state) {
        super(EquipmentContent.FUEL_PUMP_BE.get(), pos, state);
        tank = new FluidTank(CAPACITY, f -> Driveable.liquidValue(f) > 0, false, this::setChanged);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FuelPumpBlockEntity be) {
        if (!(level instanceof ServerLevel) || be.tank.amount() <= 0) return;
        Fluid fluid = be.tank.resource().getFluid();
        List<Driveable> near = level.getEntitiesOfClass(Driveable.class, new AABB(pos).inflate(5, 3, 5), d -> d.fuel() < d.kind.tank);
        for (Driveable d : near) {
            int moved = d.refill(fluid, Math.min(RATE, be.tank.amount()));
            if (moved > 0) {
                be.tank.setAmount(fluid, be.tank.amount() - moved);
                return;
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Amount", tank.amount());
        if (tank.amount() > 0) out.putString("Fluid", BuiltInRegistries.FLUID.getKey(tank.resource().getFluid()).toString());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        int amount = in.getIntOr("Amount", 0);
        Fluid f = BuiltInRegistries.FLUID.getValue(Identifier.parse(in.getStringOr("Fluid", "minecraft:empty")));
        if (amount > 0 && f != Fluids.EMPTY) tank.setAmount(f, amount);
    }
}
