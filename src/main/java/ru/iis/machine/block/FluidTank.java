package ru.iis.machine.block;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.function.Predicate;

/** Бак на одну жидкость с фильтром на залив и запретом/разрешением слива (снаружи). */
public class FluidTank extends FluidStacksResourceHandler {
    private final Predicate<Fluid> insertFilter;
    private final boolean extractable;
    private final Runnable onChange;

    public FluidTank(int capacity, Predicate<Fluid> insertFilter, boolean extractable, Runnable onChange) {
        super(1, capacity);
        this.insertFilter = insertFilter;
        this.extractable = extractable;
        this.onChange = onChange;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return resource.isEmpty() || insertFilter.test(resource.getFluid());
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return extractable ? super.extract(index, resource, amount, transaction) : 0;
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChange.run();
    }

    public int amount() {
        return getAmountAsInt(0);
    }

    public FluidResource resource() {
        return getResource(0);
    }

    public int capacity() {
        return capacity;
    }

    /** Прямая установка количества (из логики машины, вне транзакций). */
    public void setAmount(Fluid fluid, int amount) {
        if (amount <= 0) set(0, FluidResource.EMPTY, 0);
        else set(0, FluidResource.of(fluid), Math.min(amount, capacity));
    }

    /** Внутренний слив (без запрета на слив снаружи). */
    public int drainInternal(int amount) {
        int have = amount();
        int d = Math.min(have, amount);
        if (d > 0) {
            FluidResource r = resource();
            set(0, have - d > 0 ? r : FluidResource.EMPTY, have - d);
        }
        return d;
    }
}
