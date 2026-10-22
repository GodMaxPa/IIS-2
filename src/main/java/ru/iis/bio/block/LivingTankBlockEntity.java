package ru.iis.bio.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import ru.iis.bio.BioContent;
import ru.iis.bio.BioSounds;
import ru.iis.bio.production.BioFluidHandler;
import ru.iis.bio.production.BioTank;

import java.util.List;

/**
 * Живой резервуар LV, 8 000 мБ (§20.3, §22.7, D73): открытый мешок (p_g = 0, сам не создаёт давления). Режим «приём» —
 * сеть сосудов наливает в него; «выдача» — он сам сливает в прилегающие сосуды. Живёт на N0 колонии (оболочка 0,005 мБ/т;
 * по спецификации — N1, пока снабжение LV-оболочек идёт от колонии, D73). Без питания не принимает и не отдаёт.
 */
public class LivingTankBlockEntity extends BlockEntity {
    public static final int CAPACITY = 8000;
    public static final double UPKEEP = 0.005, P_BURST = 0.320;
    private final BioTank tank = new BioTank(CAPACITY, true) {
        @Override
        public int space(net.minecraft.world.level.material.Fluid f) {
            return starving ? 0 : super.space(f);
        }
    };
    private final BioFluidHandler handler = new BioFluidHandler(List.of(tank), this::setChanged, true);
    private boolean supplying, starving;

    public LivingTankBlockEntity(BlockPos pos, BlockState state) {
        super(BioContent.LIVING_TANK_BE.get(), pos, state);
    }

    public ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return handler;
    }

    public BioTank tank() {
        return tank;
    }

    public boolean supplying() {
        return supplying;
    }

    public void toggle() {
        supplying = !supplying;
        setChanged();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl) || sl.getGameTime() % 5 != 0) return;
        ColonyCoreBlockEntity core = ColonyCoreBlockEntity.findOwnerByTerritory(sl, worldPosition);
        starving = core == null || !core.drainN0(UPKEEP * 5);
        if (starving || !supplying || tank.amount() <= 0) return;
        FluidResource r = FluidResource.of(tank.fluid());
        for (Direction d : Direction.values()) {
            if (tank.amount() <= 0) break;
            if (!(sl.getBlockEntity(worldPosition.relative(d)) instanceof VesselBlockEntity v)) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int moved = v.fluidHandler(d.getOpposite()).insert(r, Math.min(tank.amount(), 128 * 5), tx);
                if (moved > 0) {
                    tx.commit();
                    tank.drain(tank.fluid(), moved, false);
                    v.markSource(d.getOpposite());
                    setChanged();
                }
            }
        }
    }

    /** Давление в подключённом сосуде выше разрывного — мешок лопается, содержимое теряется. */
    public void rupture() {
        if (!(level instanceof ServerLevel sl)) return;
        sl.playSound(null, worldPosition, BioSounds.TEAR, SoundSource.BLOCKS, 1.2f, 0.6f);
        sl.sendParticles(ParticleTypes.SPLASH, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, 60, 0.5, 0.5, 0.5, 0.3);
        sl.destroyBlock(worldPosition, false);
    }

    public Component describe() {
        Component what = tank.fluid() == Fluids.EMPTY ? Component.translatable("gui.iis.empty") : tank.fluid().getFluidType().getDescription();
        String key = starving ? "message.iis.bio.living_tank.starving" : supplying ? "message.iis.bio.living_tank.supply" : "message.iis.bio.living_tank.receive";
        return Component.translatable(key, what, tank.amount(), CAPACITY);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.save(out.child("tank"));
        out.putBoolean("supplying", supplying);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.load(in.childOrEmpty("tank"));
        supplying = in.getBooleanOr("supplying", false);
    }
}
