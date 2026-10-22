package ru.iis.bio.production;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Ёмкость органа или ручного места Био: одна жидкость, мБ. {@code output} — бак продукта (из него берут ковшом, в него
 * пишет рецепт); входные баки наполняет игрок. Свой класс, потому что Био не зависит от баков машин Кибера (D36).
 */
public class BioTank {
    private final int capacity;
    private final boolean output;
    protected Fluid fluid = Fluids.EMPTY;
    protected int amount;

    public BioTank(int capacity, boolean output) {
        this.capacity = capacity;
        this.output = output;
    }

    public Fluid fluid() {
        return amount <= 0 ? Fluids.EMPTY : fluid;
    }

    public int amount() {
        return amount;
    }

    public int capacity() {
        return capacity;
    }

    public boolean isOutput() {
        return output;
    }

    /** Бак под одну определённую жидкость (запас N0 колонии): продукт этой жидкости идёт сюда в первую очередь. */
    public boolean dedicated() {
        return false;
    }

    public boolean dedicatedTo(Fluid f) {
        return false;
    }

    public int space(Fluid f) {
        if (amount > 0 && fluid != f) return 0;
        return capacity - amount;
    }

    /** Добавить; возвращает принятое. */
    public int fill(Fluid f, int mb, boolean simulate) {
        int n = Math.min(mb, space(f));
        if (n <= 0) return 0;
        if (!simulate) {
            fluid = f;
            amount += n;
            changed();
        }
        return n;
    }

    /** Забрать до mb (любой жидкости, если f == EMPTY); возвращает взятое. */
    public int drain(Fluid f, int mb, boolean simulate) {
        if (amount <= 0 || (f != Fluids.EMPTY && f != fluid)) return 0;
        int n = Math.min(mb, amount);
        if (!simulate) {
            amount -= n;
            if (amount <= 0) {
                amount = 0;
                fluid = Fluids.EMPTY;
            }
            changed();
        }
        return n;
    }

    protected void changed() {}

    /** Прямо задать содержимое (откат транзакции). */
    public void setRaw(Fluid f, int mb) {
        fluid = mb <= 0 ? Fluids.EMPTY : f;
        amount = Math.max(0, Math.min(capacity, mb));
    }

    public void save(ValueOutput out) {
        out.putString("fluid", amount > 0 ? BuiltInRegistries.FLUID.getKey(fluid).toString() : "");
        out.putInt("amount", amount);
    }

    public void load(ValueInput in) {
        String id = in.getStringOr("fluid", "");
        amount = in.getIntOr("amount", 0);
        fluid = id.isEmpty() ? Fluids.EMPTY : BuiltInRegistries.FLUID.getValue(Identifier.parse(id));
        if (fluid == Fluids.EMPTY) amount = 0;
        amount = Math.max(0, Math.min(capacity, amount));
    }

    /** Бак под одну жидкость (буферы N1/E1 LV-органа): другую не принимает. */
    public static class Dedicated extends BioTank {
        private final java.util.function.Supplier<? extends Fluid> only;

        public Dedicated(int capacity, java.util.function.Supplier<? extends Fluid> only) {
            super(capacity, false);
            this.only = only;
        }

        @Override
        public boolean dedicated() {
            return true;
        }

        @Override
        public boolean dedicatedTo(Fluid f) {
            return f == only.get();
        }

        @Override
        public int space(Fluid f) {
            return f == only.get() ? super.space(f) : 0;
        }
    }
}
