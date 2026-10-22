package ru.iis.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Универсальный резервуар (D72): одна любая жидкость; принимает со всех сторон, отдаёт со всех сторон, а сам сливает вниз
 * (резервуары и трубы под ним наполняются, башня из резервуаров сливается сверху вниз). При поломке содержимое остаётся в
 * предмете.
 */
public class UniversalTankBlockEntity extends BlockEntity {
    public final Tank tank;

    public final class Tank extends FluidStacksResourceHandler {
        Tank(int capacity) {
            super(1, capacity);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            setChanged();
        }

        public int amount() {
            return getAmountAsInt(0);
        }

        public Fluid fluid() {
            return getResource(0).isEmpty() ? Fluids.EMPTY : getResource(0).getFluid();
        }

        public int capacity() {
            return capacity;
        }

        void setDirect(Fluid f, int amount) {
            if (amount <= 0 || f == Fluids.EMPTY) set(0, FluidResource.EMPTY, 0);
            else set(0, FluidResource.of(f), Math.min(amount, capacity));
        }
    }

    public UniversalTankBlockEntity(BlockPos pos, BlockState state) {
        super(FluidContainers.TANK_BE.get(), pos, state);
        int cap = state.getBlock() instanceof UniversalTankBlock b ? b.capacity : 32000;
        this.tank = new Tank(cap);
    }

    public ResourceHandler<FluidResource> fluidHandler(Direction side) {
        return tank;
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % 5 != 0 || tank.amount() <= 0) return;
        ResourceHandler<FluidResource> below = level.getCapability(Capabilities.Fluid.BLOCK, worldPosition.below(), Direction.UP);
        if (below == null) return;
        FluidResource r = FluidResource.of(tank.fluid());
        try (Transaction tx = Transaction.openRoot()) {
            int moved = below.insert(r, Math.min(1000, tank.amount()), tx);
            if (moved > 0) {
                try (Transaction inner = Transaction.open(tx)) {
                    tank.extract(r, moved, inner);
                    inner.commit();
                }
                tx.commit();
            }
        }
    }

    public Component describe() {
        Component what = tank.amount() <= 0 ? Component.translatable("gui.iis.empty") : tank.fluid().getFluidType().getDescription();
        return Component.translatable("message.iis.tank.info", what, tank.amount(), tank.capacity());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (tank.amount() > 0) components.set(FluidContainers.CONTENT.get(), FluidContent.of(tank.fluid(), tank.amount()));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        FluidContent c = components.get(FluidContainers.CONTENT.get());
        if (c != null) tank.setDirect(c.fluidValue(), c.amount());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.serialize(out.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.deserialize(in.childOrEmpty("tank"));
    }
}
