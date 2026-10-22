package ru.iis.bio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import ru.iis.bio.BioContent;
import ru.iis.bio.fluid.BioFluids;
import ru.iis.bio.hydraulics.Pump;
import ru.iis.bio.production.BioFluidHandler;
import ru.iis.bio.production.BioTank;

import java.util.List;
import java.util.Locale;

/**
 * Мышечный насос LV A07 (§20.3, §22.6, D73): берёт жидкость с задней стороны и подаёт в сеть сосудов перед собой.
 * H = 0,200 × max(0, 1 − Q/128) атм; поддержание N1 0,010 мБ/т всегда, работа — E1 {@code 0,010 + 0,050×(H×Q)/(0,140×40)} мБ/т.
 * Буферы N1 и E1 по 250 мБ заполняются вручную (капсула, ковш) или по трубе. Встроенный выходной обратный клапан.
 */
public class MusclePumpBlockEntity extends BlockEntity implements Pump {
    private final BioTank n1 = new BioTank.Dedicated(250, () -> BioFluids.N1.source().get());
    private final BioTank e1 = new BioTank.Dedicated(250, () -> BioFluids.E1.source().get());
    private final BioFluidHandler buffers = new BioFluidHandler(List.of(n1, e1), this::setChanged);
    private double owedN, owedE, lastH, lastQ;
    private boolean starving;

    public MusclePumpBlockEntity(BlockPos pos, BlockState state) {
        super(BioContent.MUSCLE_PUMP_BE.get(), pos, state);
    }

    public Direction outlet() {
        return getBlockState().getValue(MusclePumpBlock.FACING);
    }

    public ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return buffers;
    }

    public BioTank n1() {
        return n1;
    }

    public BioTank e1() {
        return e1;
    }

    public void serverTick() {
        // поддержание N1 каждый тик, дробное — копится
        if (n1.amount() + 1e-9 < owedN + 0.010) {
            starving = true;
            return;
        }
        starving = false;
        owedN += 0.010;
        if (owedN >= 1) owedN -= n1.drain(n1.fluid(), (int) owedN, false);
    }

    // --- Pump ------------------------------------------------------------------------------------------------------
    @Override
    public double h0() {
        return 0.200;
    }

    @Override
    public double qFree() {
        return 128;
    }

    private static double work(double h, double q) {
        return 0.010 + 0.050 * (h * q) / (0.140 * 40);
    }

    @Override
    public boolean canRun(int ticks) {
        return !starving && e1.amount() + 1e-9 >= owedE + work(0.150, 32) * ticks;
    }

    @Override
    public void charge(double h, double q, int ticks) {
        lastH = h;
        lastQ = q;
        owedE += work(h, q) * ticks;
        if (owedE >= 1) owedE -= e1.drain(e1.fluid(), (int) owedE, false);
        setChanged();
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> inlet() {
        if (!(level instanceof ServerLevel sl)) return null;
        Direction back = outlet().getOpposite();
        return sl.getCapability(Capabilities.Fluid.BLOCK, worldPosition.relative(back), back.getOpposite());
    }

    @Override
    public Component name() {
        return Component.translatable("message.iis.bio.network.pump.muscle");
    }

    public Component describe() {
        return Component.translatable(starving ? "message.iis.bio.pump.starving" : "message.iis.bio.pump.info", n1.amount(), e1.amount(),
                String.format(Locale.ROOT, "%.3f", lastH), String.format(Locale.ROOT, "%.1f", lastQ));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        n1.save(out.child("n1"));
        e1.save(out.child("e1"));
        out.putDouble("owedN", owedN);
        out.putDouble("owedE", owedE);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        n1.load(in.childOrEmpty("n1"));
        e1.load(in.childOrEmpty("e1"));
        owedN = in.getDoubleOr("owedN", 0);
        owedE = in.getDoubleOr("owedE", 0);
    }
}
