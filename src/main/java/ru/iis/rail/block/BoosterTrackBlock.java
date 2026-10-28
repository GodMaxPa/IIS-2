package ru.iis.rail.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.iis.rail.IisRail;
import ru.iis.rail.RailSpeed;
import ru.iis.rail.TrackKind;

/**
 * Ускоритель (Railcraft {@code *_BOOSTER}, {@code SPEED_BOOST}) и переходный скоростной путь ({@code SPEED_TRANSITION}).
 * Разгон и торможение — ванильные у пути с питанием (вагонетка видит {@link PoweredRailBlock}).
 * Скоростной ускоритель с питанием включает режим высокой скорости; переходный без питания выводит из него
 * (не быстрее 0.39) — так поезд безопасно сходит со скоростной линии.
 */
public class BoosterTrackBlock extends PoweredRailBlock implements IisRail {
    private final TrackKind kind;
    private final boolean transition;
    private final MapCodec<PoweredRailBlock> codec;

    public BoosterTrackBlock(TrackKind kind, boolean transition, Properties properties) {
        super(properties, true);
        this.kind = kind;
        this.transition = transition;
        this.codec = simpleCodec(p -> new BoosterTrackBlock(kind, transition, p));
    }

    @Override
    public MapCodec<PoweredRailBlock> codec() {
        return codec;
    }

    @Override
    public TrackKind kind() {
        return kind;
    }

    public boolean isTransition() {
        return transition;
    }

    @Override
    public void onCartPass(BlockState state, ServerLevel level, BlockPos pos, AbstractMinecart cart) {
        if (kind != TrackKind.HIGH_SPEED) return;
        boolean powered = state.getValue(POWERED);
        if (powered) {
            RailSpeed.boosterPass(level, pos, getRailDirection(state, level, pos, cart), cart);
        } else if (transition) {
            Vec3 m = cart.getDeltaMovement();
            double c = TrackKind.SPEED_CUTOFF;
            cart.setDeltaMovement(Math.copySign(Math.min(c, Math.abs(m.x)), m.x), m.y, Math.copySign(Math.min(c, Math.abs(m.z)), m.z));
            RailSpeed.setHighSpeed(cart, false);
        } else {
            RailSpeed.basicPass(level, pos, getRailDirection(state, level, pos, cart), cart);
        }
    }
}
