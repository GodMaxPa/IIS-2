package ru.iis.space;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

import java.util.Map;

/**
 * Ракета. Числа GC: бак 1000 × rocketFuelFactor (ИИС: 200) = 200000 л, предстартовый отсчёт 400 тиков, высота перехода 1200,
 * 1 единица × factor за 2 тика с поправкой измерения (Луна 0.7).
 * Отличия: посадка своим ходом, без спускаемого аппарата GC, ракета остаётся стоять на планете и взлетает без площадки;
 * цель определяется уровнем и измерением: у 1-го уровня она одна, 2-й с Земли летит на Марс или Луну по кнопке в окне груза, без карты планет (D131).
 */
public class RocketEntity extends Entity {
    public static final int FUEL_FACTOR = 200, CAPACITY_T1 = 1000 * FUEL_FACTOR, CAPACITY_T2 = 1500 * FUEL_FACTOR, COUNTDOWN = 400, ARRIVAL_ALTITUDE = 1200, LANDING_ALTITUDE = 260;
    public static final int IDLE = 0, IGNITION = 1, FLYING = 2, LANDING = 3;
    private static final double MAX_SPEED = 2.0, ACCELERATION = 0.02, LANDING_SPEED = -0.8;
    /** Куда летит ракета 1-го уровня. */
    private static final Map<ResourceKey<Level>, ResourceKey<Level>> ROUTES_T1 = Map.of(Level.OVERWORLD, SpaceContent.MOON, SpaceContent.MOON, Level.OVERWORLD);

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    /** Цель 2-го уровня с Земли. */
    public static final int TARGET_MARS = 0, TARGET_MOON = 1;

    private int timer, cargoSlots, target = TARGET_MARS;
    private SimpleContainer cargo = new SimpleContainer(0);
    private boolean teleporting;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
    }

    public void setup(RocketItem.Stored stored) {
        cargoSlots = stored.cargo();
        cargo = new SimpleContainer(cargoSlots);
        setFuel(stored.fuel());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(STATE, IDLE);
        data.define(FUEL, 0);
        data.define(TIER, 1);
    }

    public int state() {
        return entityData.get(STATE);
    }

    public void setState(int s) {
        entityData.set(STATE, s);
    }

    public int fuel() {
        return entityData.get(FUEL);
    }

    public void setFuel(int f) {
        entityData.set(FUEL, Math.max(0, Math.min(capacity(), f)));
    }

    public int tier() {
        return entityData.get(TIER);
    }

    public void setTier(int t) {
        entityData.set(TIER, t);
    }

    /** GC: бак 1-го уровня 1000, 2-го 1500 единиц, умноженных на rocketFuelFactor. */
    public int capacity() {
        return tier() >= 2 ? CAPACITY_T2 : CAPACITY_T1;
    }

    public int target() {
        return target;
    }

    public void setTarget(int t) {
        target = t;
    }

    public int cargoSlots() {
        return cargoSlots;
    }

    public SimpleContainer cargo() {
        return cargo;
    }

    /** GC getFuelUsageMultiplier: Земля 1, Луна 0.7, Марс 0.9. */
    public static double fuelMultiplier(ResourceKey<Level> dim) {
        return dim == SpaceContent.MOON ? 0.7 : dim == SpaceContent.MARS ? 0.9 : 1.0;
    }

    public static double fuelMultiplierMoon() {
        return fuelMultiplier(SpaceContent.MOON);
    }

    /** Топлива на подъём: разгон до MAX_SPEED и остаток пути, FUEL_FACTOR за 2 тика с поправкой измерения. */
    public static int flightCost(ResourceKey<Level> dim, double fromY) {
        double accelTicks = MAX_SPEED / ACCELERATION, accelPath = MAX_SPEED * accelTicks / 2;
        double ticks = accelTicks + Math.max(0, ARRIVAL_ALTITUDE - fromY - accelPath) / MAX_SPEED;
        return (int) Math.ceil(ticks * FUEL_FACTOR / 2 * fuelMultiplier(dim));
    }

    public ResourceKey<Level> destination() {
        ResourceKey<Level> here = level().dimension();
        if (tier() < 2) return ROUTES_T1.get(here);
        if (here == Level.OVERWORLD) return target == TARGET_MOON ? SpaceContent.MOON : SpaceContent.MARS;
        return here == SpaceContent.MOON || here == SpaceContent.MARS ? Level.OVERWORLD : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            applyMotion();
            return;
        }
        switch (state()) {
            case IDLE -> tickIdle();
            case IGNITION -> tickIgnition();
            case FLYING -> tickFlying();
            default -> tickLanding();
        }
        applyMotion();
    }

    private void applyMotion() {
        noPhysics = state() == FLYING;
        if (noPhysics) {
            setPos(getX(), getY() + getDeltaMovement().y, getZ());
        } else {
            move(MoverType.SELF, getDeltaMovement());
        }
    }

    private void tickIdle() {
        setDeltaMovement(0, onGround() ? 0 : Math.max(getDeltaMovement().y - 0.04, -0.8), 0);
        if (getFirstPassenger() instanceof ServerPlayer player) {
            int cost = flightCost(level().dimension(), getY());
            if (player.getLastClientInput().jump() && destination() != null) {
                if (fuel() >= cost) {
                    timer = COUNTDOWN;
                    setState(IGNITION);
                } else if (tickCount % 20 == 0) {
                    player.sendOverlayMessage(Component.translatable("message.iis.rocket.no_fuel", fuel(), cost));
                }
            } else if (tickCount % 40 == 0) {
                player.sendOverlayMessage(fuel() >= cost ? Component.translatable("message.iis.rocket.ready")
                        : Component.translatable("message.iis.rocket.fuel", fuel(), capacity(), cost));
            }
        }
    }

    private void tickIgnition() {
        setDeltaMovement(Vec3.ZERO);
        if (getFirstPassenger() == null) {
            setState(IDLE);     // все вышли: отсчёт отменён
            return;
        }
        if (timer % 20 == 0 && getFirstPassenger() instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable("message.iis.rocket.countdown", timer / 20));
        }
        if (--timer <= 0) setState(FLYING);
    }

    private void tickFlying() {
        setDeltaMovement(0, Math.min(MAX_SPEED, getDeltaMovement().y + ACCELERATION), 0);
        // GC: 1 единица × factor раз в 2 / множитель тиков; здесь то же средним за тик
        setFuel(fuel() - (int) Math.ceil(FUEL_FACTOR / 2.0 * fuelMultiplier(level().dimension())));
        if (getY() >= ARRIVAL_ALTITUDE) arrive();
        else if (fuel() <= 0) setState(LANDING);    // топливо кончилось: садится, откуда взлетела
    }

    private void arrive() {
        ResourceKey<Level> to = destination();
        ServerLevel target = to == null ? null : ((ServerLevel) level()).getServer().getLevel(to);
        if (target == null) {
            setState(LANDING);
            return;
        }
        setState(LANDING);
        teleporting = true;
        Vec3 pos = new Vec3(getX(), LANDING_ALTITUDE, getZ());
        teleport(new TeleportTransition(target, pos, new Vec3(0, LANDING_SPEED, 0), getYRot(), getXRot(), TeleportTransition.DO_NOTHING));
        teleporting = false;
    }

    private void tickLanding() {
        setDeltaMovement(0, LANDING_SPEED, 0);
        if (onGround() || getY() <= level().getMinY() + 1) {
            setDeltaMovement(Vec3.ZERO);
            setState(IDLE);
        }
    }

    /** GC: из летящей ракеты не выйти. */
    public static void onDismount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof RocketEntity r && !r.teleporting && !r.isRemoved() && r.state() >= FLYING) {
            event.setCanceled(true);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (state() != IDLE) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer sp) {
                sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new RocketMenu(id, inv, cargo, this), getDisplayName()), buf -> {
                    buf.writeVarInt(cargoSlots);
                    buf.writeVarInt(tier());
                });
            }
            return InteractionResult.CONSUME;
        }
        if (getPassengers().isEmpty()) player.startRiding(this);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (state() != IDLE || !(source.getEntity() instanceof Player) || !getPassengers().isEmpty()) return false;
        ItemStack stack = new ItemStack(tier() >= 2 ? SpaceContent.ROCKET_T2.get() : SpaceContent.ROCKET_T1.get());
        stack.set(SpaceContent.ROCKET_DATA.get(), new RocketItem.Stored(cargoSlots, fuel()));
        spawnAtLocation(level, stack);
        Containers.dropContents(level, BlockPos.containing(position()), cargo);
        discard();
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("Tier", tier());
        out.putInt("Target", target);
        out.putInt("State", state());
        out.putInt("Fuel", fuel());
        out.putInt("Timer", timer);
        out.putInt("CargoSlots", cargoSlots);
        net.minecraft.world.ContainerHelper.saveAllItems(out, cargo.getItems());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        setTier(in.getIntOr("Tier", 1));
        target = in.getIntOr("Target", TARGET_MARS);
        setState(in.getIntOr("State", IDLE));
        setFuel(in.getIntOr("Fuel", 0));
        timer = in.getIntOr("Timer", 0);
        cargoSlots = in.getIntOr("CargoSlots", 0);
        cargo = new SimpleContainer(cargoSlots);
        net.minecraft.world.ContainerHelper.loadAllItems(in, cargo.getItems());
    }

    /** Точка посадки в тестах: над поверхностью. */
    public static BlockPos surface(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
    }
}
