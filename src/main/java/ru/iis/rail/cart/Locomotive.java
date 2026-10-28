package ru.iis.rail.cart;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import ru.iis.rail.RailContent;
import ru.iis.rail.RailSpeed;
import ru.iis.rail.menu.LocomotiveMenu;

/**
 * Локомотив Railcraft ({@code EntityLocomotive}, D102). Режимы: работа, холостой ход, остановлен; скорости: полная,
 * медленно (0.3), медленнее (0.2), медленнейшая (0.1), задний ход (0.1). Тяга на работе — horsepower × 0.006 за тик
 * вдоль направления движения (в сборке ИИС 1 horsepower = 15), на полной скорости в режиме высокой скорости ×3.5;
 * своё торможение 0.9. Топливо расходуется раз в 8 тиков: 8/6/4/2 единицы по скорости, 1 на холостом ходу.
 */
public abstract class Locomotive extends AbstractMinecart {
    public enum Mode { RUNNING, IDLE, SHUTDOWN }

    public enum Speed {
        MAX(0.4, 8), SLOW(0.3, 6), SLOWER(0.2, 4), SLOWEST(0.1, 2), REVERSE(0.1, 2);

        public final double limit;
        public final int fuelUse;

        Speed(double limit, int fuelUse) {
            this.limit = limit;
            this.fuelUse = fuelUse;
        }
    }

    /** railcraft.cfg ИИС 1: tweaks.carts.locomotive.horsepower = 15. */
    public static final int HORSEPOWER = 15;
    static final double DRAG = 0.9, HS_FORCE_BONUS = 3.5;
    static final int FUEL_USE_INTERVAL = 8;

    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SPEED = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FUEL = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BOOLEAN);

    /** Направление «вперёд» вдоль пути (единичный горизонтальный вектор). */
    protected Vec3 heading = Vec3.ZERO;
    protected int fuel;
    protected int update;

    protected Locomotive(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(DATA_MODE, Mode.SHUTDOWN.ordinal());
        data.define(DATA_SPEED, Speed.MAX.ordinal());
        data.define(DATA_FUEL, false);
    }

    public Mode mode() {
        return Mode.values()[Math.floorMod(entityData.get(DATA_MODE), Mode.values().length)];
    }

    public void setMode(Mode m) {
        entityData.set(DATA_MODE, m.ordinal());
    }

    public Speed speed() {
        return Speed.values()[Math.floorMod(entityData.get(DATA_SPEED), Speed.values().length)];
    }

    public void setSpeed(Speed s) {
        entityData.set(DATA_SPEED, s.ordinal());
    }

    public boolean hasFuel() {
        return entityData.get(DATA_FUEL);
    }

    public boolean isRunning() {
        return fuel > 0 && mode() == Mode.RUNNING;
    }

    public Vec3 heading() {
        return heading;
    }

    /** Направление «вперёд» по горизонтальному вектору, вдоль пути под локомотивом. */
    public void setHeading(Vec3 dir) {
        BlockPos pos = getCurrentBlockPosOrRailBelow();
        BlockState s = level().getBlockState(pos);
        Vec3 axis = dir;
        if (s.getBlock() instanceof BaseRailBlock rail) {
            RailShape shape = rail.getRailDirection(s, level(), pos, this);
            boolean ew = shape == RailShape.EAST_WEST || shape == RailShape.ASCENDING_EAST || shape == RailShape.ASCENDING_WEST;
            axis = ew ? new Vec3(Math.signum(dir.x == 0 ? 1 : dir.x), 0, 0) : new Vec3(0, 0, Math.signum(dir.z == 0 ? 1 : dir.z));
        }
        heading = axis.horizontalDistanceSqr() < 1e-6 ? Vec3.ZERO : axis.normalize();
    }

    /** Новая порция топлива (Railcraft getMoreGoJuice); 0 — нет. */
    protected abstract int moreGoJuice();

    protected int idleFuelUse() {
        return 1;
    }

    protected int fuelUse() {
        return switch (mode()) {
            case RUNNING -> speed().fuelUse;
            case IDLE -> idleFuelUse();
            case SHUTDOWN -> 0;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        update++;
        if (heading.horizontalDistanceSqr() < 1e-6) setHeading(new Vec3(1, 0, 0));
        // EntityLocomotive#updateFuel
        if (update % FUEL_USE_INTERVAL == 0 && fuel > 0) fuel = Math.max(0, fuel - fuelUse());
        while (fuel <= FUEL_USE_INTERVAL && mode() != Mode.SHUTDOWN) {
            int more = moreGoJuice();
            if (more <= 0) break;
            fuel += more;
        }
        entityData.set(DATA_FUEL, fuel > 0);
        if (isRunning()) hitEntities(sl);
    }

    /** tweaks.carts.locomotive.damageMobs = true в ИИС 1: идущий локомотив бьёт и отталкивает мобов на пути (игроков — нет, как EntityLiving в 1.7.10). */
    private void hitEntities(ServerLevel level) {
        Vec3 m = getDeltaMovement();
        if (m.horizontalDistanceSqr() < 0.04) return;
        for (Mob e : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(0.2), e -> !e.isPassenger())) {
            e.hurtServer(level, damageSources().generic(), (float) (10 * m.horizontalDistance()));
            e.push(m.x * 1.5, 0.3, m.z * 1.5);
        }
    }

    @Override
    protected Vec3 applyNaturalSlowdown(Vec3 movement) {
        if (movement.horizontalDistanceSqr() > 0.001 && heading.horizontalDistanceSqr() > 1e-6) {
            Vec3 dir = new Vec3(movement.x, 0, movement.z).normalize();
            heading = heading.dot(dir) < 0 ? dir.scale(-1) : dir;
        }
        Vec3 v = movement.multiply(DRAG, 0, DRAG);
        Speed speed = speed();
        if (isRunning() && heading.horizontalDistanceSqr() > 1e-6) {
            double force = HORSEPOWER * 0.006;
            if (speed == Speed.REVERSE) force = -force;
            else if (speed == Speed.MAX && RailSpeed.isHighSpeed(this)) force *= HS_FORCE_BONUS;
            v = v.add(heading.x * force, 0, heading.z * force);
        }
        if (speed != Speed.MAX) {
            double lim = speed.limit;
            v = new Vec3(Math.copySign(Math.min(Math.abs(v.x), lim), v.x), v.y, Math.copySign(Math.min(Math.abs(v.z), lim), v.z));
        }
        return v;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.getItemInHand(hand).is(RailContent.CROWBARS)) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new LocomotiveMenu(id, inv, this, menuData()), getDisplayName()),
                    buf -> {
                        buf.writeVarInt(getId());
                        buf.writeBoolean(this instanceof SteamLocomotive);
                    });
        }
        return InteractionResult.SUCCESS;
    }

    /** Кнопки интерфейса: 0–2 режим, 3–7 скорость. */
    public void pressButton(int id) {
        if (id >= 0 && id < 3) setMode(Mode.values()[id]);
        else if (id >= 3 && id < 8) setSpeed(Speed.values()[id - 3]);
    }

    public abstract ContainerData menuData();

    @Override
    public boolean isRideable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    public Component getDisplayName() {
        return getDropItem().getName(new ItemStack(getDropItem()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("mode", mode().ordinal());
        out.putInt("speed", speed().ordinal());
        out.putInt("fuel", fuel);
        out.putDouble("headingX", heading.x);
        out.putDouble("headingZ", heading.z);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        setMode(Mode.values()[Math.floorMod(in.getIntOr("mode", Mode.SHUTDOWN.ordinal()), 3)]);
        setSpeed(Speed.values()[Math.floorMod(in.getIntOr("speed", 0), 5)]);
        fuel = in.getIntOr("fuel", 0);
        heading = new Vec3(in.getDoubleOr("headingX", 0), 0, in.getDoubleOr("headingZ", 0));
    }
}
