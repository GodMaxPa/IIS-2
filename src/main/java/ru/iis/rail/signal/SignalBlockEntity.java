package ru.iis.rail.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import ru.iis.rail.RailContent;

/**
 * Состояние сигнала: пара (для блок-сигнала — второй блок-сигнал, для приёмника — его контроллер), показание,
 * у приёмника — показание, на которое он подаёт сигнал. Пересчёт раз в 4 тика, мигание — каждые 10 тиков.
 */
public class SignalBlockEntity extends BlockEntity {
    public static final int UPDATE_INTERVAL = 4, BLINK_INTERVAL = 10;
    /** Наибольшее расстояние между спаренными блоками. */
    public static final int MAX_PAIR_DISTANCE = 128;

    private @Nullable BlockPos partner;
    private SignalAspect aspect = SignalAspect.BLINK_RED;
    private SignalAspect trigger = SignalAspect.GREEN;
    private boolean powered;
    private int clock;

    public SignalBlockEntity(BlockPos pos, BlockState state) {
        super(RailContent.SIGNAL_BE.get(), pos, state);
    }

    public SignalBlock.Type type() {
        return ((SignalBlock) getBlockState().getBlock()).type;
    }

    public @Nullable BlockPos partner() {
        return partner;
    }

    public void setPartner(@Nullable BlockPos p) {
        partner = p;
        setChanged();
    }

    public SignalAspect aspect() {
        return aspect;
    }

    public SignalAspect trigger() {
        return trigger;
    }

    public boolean powered() {
        return powered;
    }

    /** Приёмник: зелёный → жёлтый → красный → мигающий красный → зелёный. */
    public SignalAspect cycleTrigger() {
        trigger = switch (trigger) {
            case GREEN -> SignalAspect.YELLOW;
            case YELLOW -> SignalAspect.RED;
            case RED -> SignalAspect.BLINK_RED;
            default -> SignalAspect.GREEN;
        };
        setChanged();
        return trigger;
    }

    /** Может ли блок управлять приёмником (контроллер или блок-сигнал). */
    public boolean isController() {
        return type() != SignalBlock.Type.RECEIVER_BOX;
    }

    void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        clock++;
        if (clock % UPDATE_INTERVAL == 0) {
            SignalAspect a = switch (type()) {
                case BLOCK_SIGNAL -> blockSignalAspect(sl);
                case CONTROLLER_BOX -> sl.hasNeighborSignal(worldPosition) ? SignalAspect.RED : SignalAspect.GREEN;
                case RECEIVER_BOX -> partner != null && sl.isLoaded(partner) && sl.getBlockEntity(partner) instanceof SignalBlockEntity c && c.isController()
                        ? c.aspect() : SignalAspect.BLINK_RED;
            };
            if (a != aspect) {
                aspect = a;
                setChanged();
            }
            boolean p = type() == SignalBlock.Type.RECEIVER_BOX && aspect == trigger;
            if (p != powered) {
                powered = p;
                sl.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        }
        SignalAspect.Lamp lamp = aspect.lamp((clock / BLINK_INTERVAL) % 2 == 0);
        BlockState s = getBlockState();
        if (s.getValue(SignalBlock.LAMP) != lamp) sl.setBlock(worldPosition, s.setValue(SignalBlock.LAMP, lamp), 2);
    }

    /** SignalBlock Railcraft: пара должна быть взаимной, оба сигнала — видеть путь; вагонетка между путями — красный. */
    private SignalAspect blockSignalAspect(ServerLevel sl) {
        if (partner == null || !sl.isLoaded(partner) || !(sl.getBlockEntity(partner) instanceof SignalBlockEntity other)
                || other.type() != SignalBlock.Type.BLOCK_SIGNAL || !worldPosition.equals(other.partner)) return SignalAspect.BLINK_RED;
        BlockPos a = trackNear(sl, worldPosition), b = trackNear(sl, partner);
        if (a == null || b == null) return SignalAspect.BLINK_RED;
        AABB box = new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
        return sl.getEntitiesOfClass(AbstractMinecart.class, box, c -> c.isAlive()).isEmpty() ? SignalAspect.GREEN : SignalAspect.RED;
    }

    /** TrackLocator: путь в соседней клетке (в том числе по диагонали) на уровне сигнала или на 1–2 ниже. */
    public static @Nullable BlockPos trackNear(ServerLevel sl, BlockPos pos) {
        for (int dy = 0; dy >= -2; dy--) {
            for (int r = 0; r <= 1; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos p = pos.offset(dx, dy, dz);
                        if (sl.getBlockState(p).getBlock() instanceof BaseRailBlock) return p;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (partner != null) out.putLong("partner", partner.asLong());
        out.putInt("aspect", aspect.ordinal());
        out.putInt("trigger", trigger.ordinal());
        out.putBoolean("powered", powered);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        partner = in.getLong("partner").map(BlockPos::of).orElse(null);
        aspect = SignalAspect.values()[Math.floorMod(in.getIntOr("aspect", SignalAspect.BLINK_RED.ordinal()), SignalAspect.values().length)];
        trigger = SignalAspect.values()[Math.floorMod(in.getIntOr("trigger", 0), SignalAspect.values().length)];
        powered = in.getBooleanOr("powered", false);
    }
}
