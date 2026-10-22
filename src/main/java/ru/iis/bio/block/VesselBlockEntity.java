package ru.iis.bio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import ru.iis.bio.BioContent;
import ru.iis.bio.hydraulics.VesselNetwork;
import ru.iis.bio.production.BioFluidHandler;
import ru.iis.bio.production.BioTank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Участок сети сосудов (§20.2, §20.11, D73): свой внутренний объём, износ D, голод. Раз в 5 тиков один участок сети
 * (первый, до кого дошла очередь) считает всю сеть {@link VesselNetwork}; остальные в этот тик только хранят результат.
 */
public class VesselBlockEntity extends BlockEntity {
    private final BioTank tank;
    private final BioFluidHandler handler;
    /** Стороны, откуда пришла текущая жидкость от места/органа: туда не отдаём (сбрасывается, когда сеть опустела). */
    private int receivedFrom;
    private boolean starving;
    private int starvingSteps;
    /** Износ D (0…100), §20.11. */
    private double damage;
    // результат последнего расчёта сети (для ПКМ)
    public long solvedAt = -1;
    public double pressure, flow;
    public VesselNetwork.Summary summary;
    /** Жидкость сети на последнем шаге: другую участок не принимает (без смешения). */
    public Fluid networkFluid = Fluids.EMPTY;

    public VesselBlockEntity(BlockPos pos, BlockState state) {
        super(BioContent.VESSEL_BE.get(), pos, state);
        VesselBlock.Spec s = spec(state);
        this.tank = new BioTank(s.volume(), true) {
            @Override
            public int space(Fluid f) {
                if (networkFluid != Fluids.EMPTY && f != networkFluid) return 0;
                return super.space(f);
            }
        };
        this.handler = new BioFluidHandler(List.of(tank), this::setChanged, true);
    }

    public static VesselBlock.Spec spec(BlockState state) {
        return state.getBlock() instanceof VesselBlock v ? v.spec : VesselBlock.SPECS.getFirst();
    }

    public VesselBlock.Spec spec() {
        return spec(getBlockState());
    }

    public VesselBlock block() {
        return (VesselBlock) getBlockState().getBlock();
    }

    public ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return handler;
    }

    public BioTank tank() {
        return tank;
    }

    public void markSource(Direction side) {
        receivedFrom |= 1 << side.ordinal();
    }

    public boolean fromSource(Direction side) {
        return (receivedFrom & (1 << side.ordinal())) != 0;
    }

    public void clearSources() {
        receivedFrom = 0;
    }

    public boolean starving() {
        return starving;
    }

    public double damage() {
        return damage;
    }

    /** Действующие пределы с учётом износа (§20.11). */
    public double pSafe() {
        return spec().pSafe() * (1 - 0.005 * damage);
    }

    public double pBurst() {
        return spec().pBurst() * (1 - 0.005 * damage);
    }

    /** Поддержание живого участка за шаг; голод — после 60 с без питания износ 0,02 D/с (§20.11). */
    public void upkeep(@Nullable ColonyCoreBlockEntity core, int ticks) {
        VesselBlock.Spec s = spec();
        if (!s.living()) {
            starving = false;
            return;
        }
        starving = core == null || !core.drainN0(s.upkeep() * ticks);
        if (starving) {
            starvingSteps += ticks;
            if (starvingSteps > 60 * 20) damage = Math.min(100, damage + 0.02 * ticks / 20.0);
        } else {
            starvingSteps = 0;
        }
    }

    /** Нагрузка давлением за шаг; true — участок разорвался или отказал. */
    public boolean load(double p, int ticks) {
        double safe = pSafe(), burst = pBurst();
        if (p >= burst || damage >= 100) return true;
        if (p > safe) damage = Math.min(100, damage + 0.50 * Math.pow((p - safe) / (burst - safe), 2) * ticks / 20.0);
        return damage >= 100;
    }

    /** Утечка износившегося участка при положительном давлении, мБ/т (§20.11). */
    public double leak(double p) {
        return p > 0 && damage >= 60 ? 0.02 * spec().flow() * (damage - 60) / 40 : 0;
    }

    /** Заплатка мягкой тканью: −20 D, при D ≥ 80 не помогает (§20.11, упрощённо). */
    public boolean repair() {
        if (damage <= 0 || damage >= 80) return false;
        damage = Math.max(0, damage - 20);
        setChanged();
        return true;
    }

    public void serverTick() {
        if (level instanceof net.minecraft.server.level.ServerLevel sl && sl.getGameTime() % VesselNetwork.STEP == 0 && solvedAt != sl.getGameTime()) {
            VesselNetwork.solve(sl, this);
        }
    }

    public List<Component> describe() {
        List<Component> out = new ArrayList<>();
        Component what = tank.fluid() == Fluids.EMPTY ? Component.translatable("gui.iis.empty") : tank.fluid().getFluidType().getDescription();
        out.add(Component.translatable("message.iis.bio.vessel.content", what, tank.amount(), tank.capacity()));
        out.add(Component.translatable("message.iis.bio.vessel.hydraulics", fmt(pressure), fmt(pSafe()), fmt(pBurst()), fmt(flow), spec().flow(),
                String.format(Locale.ROOT, "%.1f", damage)));
        if (starving) out.add(Component.translatable("message.iis.bio.vessel.starving"));
        if (pressure > pSafe()) out.add(Component.translatable("message.iis.bio.vessel.overpressure"));
        if (summary != null) out.add(summary.describe());
        return out;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.save(out.child("tank"));
        out.putDouble("damage", damage);
        out.putInt("starving", starvingSteps);
        out.putInt("sources", receivedFrom);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.load(in.childOrEmpty("tank"));
        damage = in.getDoubleOr("damage", 0);
        starvingSteps = in.getIntOr("starving", 0);
        receivedFrom = in.getIntOr("sources", 0);
    }
}
