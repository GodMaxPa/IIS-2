package ru.iis.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Предел скорости вагонетки на путях ИИС и режим высокой скорости Railcraft (D102).
 * Ванильная вагонетка в 26.1 знает только 0.4 (OldMinecartBehavior#getMaxSpeed) — предел подменяет mixin
 * {@code ru.iis.rail.mixin.OldMinecartBehaviorMixin}.
 */
public final class RailSpeed {
    private RailSpeed() {}

    /** Флаг Railcraft «HighSpeed»: вагонетка разогнана ускорителем скоростного пути. */
    public static final String HIGH_SPEED = "iis_high_speed";
    /** Railcraft {@code SpeedControllerHighSpeed.LOOK_AHEAD_DIST}: на сколько путей вперёд ищется уклон. */
    static final int LOOK_AHEAD = 2;

    public static boolean isHighSpeed(AbstractMinecart cart) {
        return cart.getPersistentData().getBooleanOr(HIGH_SPEED, false);
    }

    public static void setHighSpeed(AbstractMinecart cart, boolean on) {
        if (on) cart.getPersistentData().putBoolean(HIGH_SPEED, true);
        else cart.getPersistentData().remove(HIGH_SPEED);
    }

    /** Предел скорости на пути под вагонеткой; null — путь не ИИС (ванильный предел). */
    public static @Nullable Double maxSpeed(Level level, AbstractMinecart cart) {
        BlockPos pos = cart.getCurrentBlockPosOrRailBelow();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof IisRail rail) || !(state.getBlock() instanceof BaseRailBlock base)) return null;
        RailShape shape = base.getRailDirection(state, level, pos, cart);
        return switch (rail.kind()) {
            case HIGH_SPEED -> !isHighSpeed(cart) ? TrackKind.SPEED_CUTOFF : slopeAhead(level, pos, shape) ? TrackKind.HIGH_SPEED.cornerSpeed : TrackKind.HIGH_SPEED.maxSpeed;
            case REINFORCED -> RailShapes.isCurve(shape) ? TrackKind.REINFORCED.cornerSpeed : TrackKind.REINFORCED.maxSpeed;
            default -> rail.kind().maxSpeed;
        };
    }

    /** SpeedControllerHighSpeed#speedForNextTrack: перед уклоном (свой или в пределах двух путей) скорость 0.45. */
    static boolean slopeAhead(Level level, BlockPos pos, RailShape shape) {
        if (shape.isSlope()) return true;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = pos;
            for (int i = 0; i < LOOK_AHEAD; i++) {
                p = p.relative(d);
                BlockPos at = railAt(level, p);
                if (at == null) break;
                BlockState s = level.getBlockState(at);
                if (s.getBlock() instanceof BaseRailBlock b && b.getRailDirection(s, level, at, null).isSlope()) return true;
                p = at;
            }
        }
        return false;
    }

    private static @Nullable BlockPos railAt(Level level, BlockPos p) {
        if (BaseRailBlock.isRail(level, p)) return p;
        if (BaseRailBlock.isRail(level, p.above())) return p.above();
        if (BaseRailBlock.isRail(level, p.below())) return p.below();
        return null;
    }

    /** Путь выдерживает высокую скорость; незагруженный чанк считается безопасным (как в Railcraft). */
    public static boolean highSpeedCapable(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return true;
        return level.getBlockState(pos).getBlock() instanceof IisRail r && r.kind().highSpeedCapable();
    }

    /** TrackSpeed#isTrackSafeForHighSpeed: прямой путь, по обе стороны — тоже скоростной. */
    public static boolean safeForHighSpeed(Level level, BlockPos pos, RailShape shape) {
        if (RailShapes.isCurve(shape)) return false;
        Direction.Axis axis = shape == RailShape.EAST_WEST || shape == RailShape.ASCENDING_EAST || shape == RailShape.ASCENDING_WEST
                ? Direction.Axis.X : Direction.Axis.Z;
        Direction a = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return capableNear(level, pos.relative(a)) && capableNear(level, pos.relative(a.getOpposite()));
    }

    private static boolean capableNear(Level level, BlockPos p) {
        return highSpeedCapable(level, p) || highSpeedCapable(level, p.above()) || highSpeedCapable(level, p.below());
    }

    public static boolean slowerThan(AbstractMinecart cart, double v) {
        Vec3 m = cart.getDeltaMovement();
        return Math.abs(m.x) < v && Math.abs(m.z) < v;
    }

    /** TrackSpeed#testCartSpeedForBasic: на скоростном пути без режима — не быстрее 0.39, в режиме — проверка безопасности. */
    public static void basicPass(ServerLevel level, BlockPos pos, RailShape shape, AbstractMinecart cart) {
        if (isHighSpeed(cart)) {
            if (!safeForHighSpeed(level, pos, shape)) explode(level, cart);
        } else {
            Vec3 m = cart.getDeltaMovement();
            cart.setDeltaMovement(clamp(m.x, TrackKind.SPEED_CUTOFF), m.y, clamp(m.z, TrackKind.SPEED_CUTOFF));
        }
    }

    /** TrackSpeed#testCartSpeedForBooster: ускоритель на безопасном участке включает режим высокой скорости. */
    public static void boosterPass(ServerLevel level, BlockPos pos, RailShape shape, AbstractMinecart cart) {
        if (isHighSpeed(cart)) {
            if (!safeForHighSpeed(level, pos, shape)) explode(level, cart);
            return;
        }
        if (!safeForHighSpeed(level, pos, shape)) return;
        Vec3 m = cart.getDeltaMovement();
        double x = m.x, z = m.z;
        boolean on = false;
        if (Math.abs(x) > TrackKind.SPEED_CUTOFF) { x = Math.copySign(0.4, x); on = true; }
        if (Math.abs(z) > TrackKind.SPEED_CUTOFF) { z = Math.copySign(0.4, z); on = true; }
        if (on) {
            cart.setDeltaMovement(x, m.y, z);
            setHighSpeed(cart, true);
        }
    }

    private static double clamp(double v, double max) {
        return Math.copySign(Math.min(max, Math.abs(v)), v);
    }

    /** CartUtils#explodeCart: взрыв силой 3 с огнём, вагонетка исчезает с вероятностью 1/2. */
    public static void explode(ServerLevel level, AbstractMinecart cart) {
        if (cart.isRemoved()) return;
        setHighSpeed(cart, false);
        cart.setDeltaMovement(0, cart.getDeltaMovement().y, 0);
        cart.ejectPassengers();
        level.explode(cart, cart.getX(), cart.getY(), cart.getZ(), 3.0f, true, Level.ExplosionInteraction.TNT);
        if (level.getRandom().nextBoolean()) cart.discard();
    }
}
