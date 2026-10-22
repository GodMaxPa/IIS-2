package ru.iis.bio.production;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;

/**
 * Баки места или органа Био как общий обработчик жидкостей NeoForge (D72): сосуды Био, трубы Кибера, капсулы и резервуары
 * видят одну и ту же ёмкость. Снаружи можно наливать во входные баки (и в выделенный бак N0 колонии), забирать — из баков
 * продукта. Отмена транзакции возвращает баки в прежнее состояние.
 */
public class BioFluidHandler implements ResourceHandler<FluidResource> {
    private final List<BioTank> tanks;
    private final Runnable changed;
    private final Journal journal = new Journal();
    /** Проходная ёмкость (сосуд): наливать и забирать можно из любого бака. */
    private final boolean passThrough;
    /** Какие жидкости вообще принимаются (место берёт только то, что есть в его рецептах). */
    private java.util.function.Predicate<net.minecraft.world.level.material.Fluid> accepts = f -> true;

    public BioFluidHandler accepting(java.util.function.Predicate<net.minecraft.world.level.material.Fluid> filter) {
        this.accepts = filter;
        return this;
    }

    public BioFluidHandler(List<BioTank> tanks, Runnable changed) {
        this(tanks, changed, false);
    }

    public BioFluidHandler(List<BioTank> tanks, Runnable changed, boolean passThrough) {
        this.tanks = tanks;
        this.changed = changed;
        this.passThrough = passThrough;
    }

    private record Snap(Fluid[] fluids, int[] amounts) {}

    private final class Journal extends SnapshotJournal<Snap> {
        @Override
        protected Snap createSnapshot() {
            Fluid[] f = new Fluid[tanks.size()];
            int[] a = new int[tanks.size()];
            for (int i = 0; i < f.length; i++) {
                f[i] = tanks.get(i).fluid();
                a[i] = tanks.get(i).amount();
            }
            return new Snap(f, a);
        }

        @Override
        protected void revertToSnapshot(Snap s) {
            for (int i = 0; i < s.fluids.length; i++) tanks.get(i).setRaw(s.fluids[i], s.amounts[i]);
        }

        @Override
        protected void onRootCommit(Snap original) {
            changed.run();
        }
    }

    @Override
    public int size() {
        return tanks.size();
    }

    @Override
    public FluidResource getResource(int index) {
        Fluid f = tanks.get(index).fluid();
        return f == Fluids.EMPTY ? FluidResource.EMPTY : FluidResource.of(f);
    }

    @Override
    public long getAmountAsLong(int index) {
        return tanks.get(index).amount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return tanks.get(index).capacity();
    }

    private boolean fillable(BioTank t) {
        return passThrough || !t.isOutput() || t.dedicated();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        BioTank t = tanks.get(index);
        return fillable(t) && (resource.isEmpty() || t.space(resource.getFluid()) > 0 || t.fluid() == resource.getFluid());
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
        if (resource.isEmpty() || amount <= 0) return 0;
        BioTank t = tanks.get(index);
        if (!fillable(t)) return 0;
        if (!t.dedicatedTo(resource.getFluid()) && !accepts.test(resource.getFluid())) return 0;
        // жидкость, под которую есть отдельный бак (буфер N1/E1), идёт только туда
        if (!t.dedicated()) for (BioTank o : tanks) if (o != t && o.dedicatedTo(resource.getFluid()) && !o.isOutput()) return 0;
        int n = t.fill(resource.getFluid(), amount, true);
        if (n <= 0) return 0;
        journal.updateSnapshots(tx);
        return t.fill(resource.getFluid(), n, false);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
        if (resource.isEmpty() || amount <= 0) return 0;
        BioTank t = tanks.get(index);
        if ((!passThrough && !t.isOutput()) || t.fluid() != resource.getFluid()) return 0;
        int n = t.drain(resource.getFluid(), amount, true);
        if (n <= 0) return 0;
        journal.updateSnapshots(tx);
        return t.drain(resource.getFluid(), n, false);
    }
}
