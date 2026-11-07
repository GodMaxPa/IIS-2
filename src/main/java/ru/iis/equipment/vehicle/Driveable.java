package ru.iis.equipment.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.iis.equipment.EquipmentContent;
import ru.iis.equipment.GunActions;
import ru.iis.equipment.ShotEntity;
import ru.iis.equipment.Weapon;
import ru.iis.fluid.CapsuleItem;
import ru.iis.fluid.FluidContent;
import ru.iis.gear.GearContent;
import ru.iis.lv.Fuels;

import java.util.function.Function;

/**
 * Транспорт (D128). Движение считает клиент водителя (как лодка), сервер получает положение пакетом; без водителя считает сервер.
 * ПКМ — сесть, с топливом — заправить, с изолентой — починить, Shift+ПКМ — окно с баком и грузом. Запас топлива хранится в EU:
 * литр даёт столько, сколько в генераторе GT, поэтому разные жидкости в одном баке смешиваются сами.
 */
public class Driveable extends VehicleEntity {
    private static final EntityDataAccessor<Long> DATA_FUEL = SynchedEntityData.defineId(Driveable.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(Driveable.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_ENGINE = SynchedEntityData.defineId(Driveable.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_KIT = SynchedEntityData.defineId(Driveable.class, EntityDataSerializers.BOOLEAN);

    /** Задаёт клиент: что нажал местный игрок. На сервере не вызывается. */
    public static Function<Player, VehiclePhysics.Input> clientInput = p -> VehiclePhysics.Input.NONE;

    public final VehicleKind kind;
    public final SimpleContainer cargo;
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    private final VehiclePhysics.State state = new VehiclePhysics.State();
    private float lastYaw;
    private float deltaYaw;
    /** Для игровых тестов: водитель без игрока. */
    private VehiclePhysics.@Nullable Input testInput;

    // только клиент, для модели
    public float distance, distanceO, propAngle, propAngleO, roll, rollO, steerAngle, steerAngleO, terrainPitch, terrainPitchO;
    private double lastX, lastZ;
    // сервер: столкновения
    private final Vec3[] trail = new Vec3[9];
    private int trailHead;
    private int gunCooldown;

    public Driveable(EntityType<? extends Driveable> type, Level level, VehicleKind kind) {
        super(type, level);
        this.kind = kind;
        // место сразу и под грузовой комплект: иначе при установке пришлось бы переносить содержимое
        this.cargo = new SimpleContainer(1 + kind.cargoSlots(true));
        this.blocksBuilding = true;
        entityData.set(DATA_HEALTH, (float) kind.health);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(DATA_FUEL, 0L);
        data.define(DATA_HEALTH, 40F);
        data.define(DATA_ENGINE, false);
        data.define(DATA_KIT, false);
    }

    public long fuel() {
        return entityData.get(DATA_FUEL);
    }

    public void setFuel(long v) {
        entityData.set(DATA_FUEL, Math.max(0, Math.min(kind.tank, v)));
    }

    public float vehicleHealth() {
        return entityData.get(DATA_HEALTH);
    }

    public void setVehicleHealth(float v) {
        entityData.set(DATA_HEALTH, Math.max(0, Math.min(kind.health, v)));
    }

    public boolean hasKit() {
        return entityData.get(DATA_KIT);
    }

    public void setKit(boolean on) {
        entityData.set(DATA_KIT, on);
    }

    public int cargoSlots() {
        return kind.cargoSlots(hasKit());
    }

    public boolean engineOn() {
        return entityData.get(DATA_ENGINE);
    }

    public VehiclePhysics.State physics() {
        return state;
    }

    public void setTestInput(VehiclePhysics.@Nullable Input in) {
        testInput = in;
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Override
    public float maxUpStep() {
        return kind.step;
    }

    @Override
    public boolean isFlyingVehicle() {
        return kind.flies();
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return (other.canBeCollidedWith(this) || other.isPushable()) && !isPassengerOfSameVehicle(other);
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }

    @Override
    public void tick() {
        if (getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
        if (getDamage() > 0) setDamage(getDamage() - 1F);
        super.tick();
        interpolation.interpolate();
        lastYaw = getYRot();
        if (isLocalInstanceAuthoritative()) {
            state.yaw = getYRot();
            if (kind.flies()) state.pitch = getXRot();
            state.onGround = onGround() || verticalCollisionBelow;
            state.horizontalCollision = horizontalCollision;
            double depth = getFluidHeight(FluidTags.WATER);
            state.inWater = depth > 0.05;
            state.depth = depth;
            state.speedFactor = hasKit() ? VehicleKind.KIT_SPEED : 1;
            state.accelFactor = hasKit() ? VehicleKind.KIT_ACCEL : 1;
            VehiclePhysics.Input in;
            if (getControllingPassenger() instanceof Player p && level().isClientSide()) in = clientInput.apply(p);
            else if (testInput != null) in = testInput;
            else in = VehiclePhysics.Input.NONE;
            if (in != VehiclePhysics.Input.NONE) in = new VehiclePhysics.Input(in.forward(), in.back(), in.left(), in.right(), in.up(), in.down(), in.lookYaw(), in.lookPitch(), fuel() > 0, in.occupied());
            double[] m = VehiclePhysics.step(kind, state, in);
            setYRot(state.yaw);
            if (kind.flies()) setXRot(state.pitch);
            setDeltaMovement(m[0], m[1], m[2]);
            double x0 = getX(), y0 = getY(), z0 = getZ();
            move(MoverType.SELF, getDeltaMovement());
            state.actual = Math.hypot(getX() - x0, getZ() - z0);
            state.climbed = getY() - y0 > 0.3;
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        deltaYaw = Mth.wrapDegrees(getYRot() - lastYaw);
        if (level() instanceof ServerLevel sl) serverTick(sl);
        else clientTick();
    }

    private void serverTick(ServerLevel level) {
        boolean driven = getControllingPassenger() != null || testInput != null;
        boolean on = driven && fuel() > 0;
        entityData.set(DATA_ENGINE, on);
        // расход: на холостых мало, под нагрузкой полностью
        if (on) {
            boolean load;
            if (getControllingPassenger() instanceof ServerPlayer p) {
                var i = p.getLastClientInput();
                load = i.forward() || i.backward() || i.jump() || (kind.move == VehicleKind.Move.PLANE && !onGround());
            } else {
                load = testInput != null && (testInput.forward() || testInput.up());
            }
            int use = load ? kind.burn : kind.idle;
            setFuel(fuel() - (hasKit() ? Math.round(use * VehicleKind.KIT_BURN) : use));
        }
        refuelFromSlot();
        // столкновения и наезд на мобов считаем по смещению: оно одно и у водителя, и без него
        // положение от водителя приходит неровно (0, потом сразу за два тика), поэтому средняя скорость за четыре тика
        Vec3 now = position();
        trail[trailHead] = now;
        Vec3 mid = trail[Math.floorMod(trailHead - 4, trail.length)], old = trail[Math.floorMod(trailHead - 8, trail.length)];
        trailHead = (trailHead + 1) % trail.length;
        double speed = 0;
        if (mid != null) speed = now.distanceTo(mid) / 4;
        if (mid != null && old != null) {
            double before = mid.distanceTo(old) / 4;
            if (VehiclePhysics.crashed(before, speed)) {
                crash(level, before);
                java.util.Arrays.fill(trail, null);
            }
        }
        if (speed > 0.2 && !kind.flies()) hitMobs(level, speed);
        if (kind.crushes && speed > 0.03) crushLeaves(level);
        if (gunCooldown > 0) gunCooldown--;
        // катер на суше и всё в лаве горит; дождь не мешает
        if (isInLava()) {
            hurtVehicle(level, 2F);
        }
    }

    /** Листва на пути ломается: иначе широкий корпус застревал бы в первом же дереве. Стволы держат. */
    private void crushLeaves(ServerLevel level) {
        Vec3 look = new Vec3(-Math.sin(Math.toRadians(getYRot())), 0, Math.cos(Math.toRadians(getYRot())));
        var box = getBoundingBox().move(look.scale(0.5)).inflate(0.1, 0, 0.1);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (level.getBlockState(p).is(BlockTags.LEAVES)) level.destroyBlock(p, false, this);
        }
    }

    private void crash(ServerLevel level, double speed) {
        float dmg = (float) ((speed - 0.5) * 30);
        if (kind.flies() && !isUnderWater()) dmg *= 1.5F;
        hurtVehicle(level, dmg);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.7F, 0.6F);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY(0.5), getZ(), 12, getBbWidth() * 0.4, 0.3, getBbWidth() * 0.4, 0.2);
        if (speed > 1) {
            for (Entity e : getPassengers()) e.hurtServer(level, damageSources().flyIntoWall(), (float) ((speed - 1) * 8));
        }
    }

    private void hitMobs(ServerLevel level, double speed) {
        for (Mob e : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(0.3), e -> !e.isPassengerOfSameVehicle(this))) {
            e.hurtServer(level, damageSources().mobAttack(getControllingPassenger() instanceof LivingEntity l ? l : e), (float) (speed * 20));
            Vec3 m = getDeltaMovement();
            Vec3 away = e.position().subtract(position());
            e.push(away.x * 0.3, 0.3, away.z * 0.3);
            if (m.lengthSqr() > 1e-6) e.push(m.x * 0.5, 0, m.z * 0.5);
        }
    }

    private void hurtVehicle(ServerLevel level, float amount) {
        setVehicleHealth(vehicleHealth() - amount);
        if (vehicleHealth() <= 0) wreck(level);
    }

    /** Разбит: взрыв и развал, груз высыпается. Машина остаётся предметом с пустым баком: терять завод из-за столба слишком жестоко. */
    private void wreck(ServerLevel level) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(0.5), getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(0.5), getZ(), 24, getBbWidth() * 0.4, 0.5, getBbWidth() * 0.4, 0.05);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 2F, 0.8F);
        ejectPassengers();
        dropAll(level, false);
    }

    private void dropAll(ServerLevel level, boolean keepFuel) {
        Containers.dropContents(level, blockPosition(), cargo);
        ItemStack stack = new ItemStack(getDropItem());
        stack.set(EquipmentContent.VEHICLE_FUEL.get(), keepFuel ? fuel() : 0L);
        stack.set(EquipmentContent.VEHICLE_DAMAGE.get(), keepFuel ? kind.health - vehicleHealth() : kind.health * 0.5F);
        if (hasKit()) stack.set(EquipmentContent.VEHICLE_KIT.get(), true);
        spawnAtLocation(level, stack);
        discard();
    }

    private void clientTick() {
        distanceO = distance;
        propAngleO = propAngle;
        rollO = roll;
        steerAngleO = steerAngle;
        double dx = getX() - lastX, dz = getZ() - lastZ;
        lastX = getX();
        lastZ = getZ();
        double yaw = Math.toRadians(getYRot());
        double along = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
        if (Math.abs(along) < 8) distance += (float) along;
        float spin = engineOn() ? 1F : (kind.move == VehicleKind.Move.HELICOPTER ? 0.2F : 0F);
        propAngle += spin * 0.7F;
        // крен и руль модели — из скорости поворота: и у водителя, и у тех, кто смотрит со стороны
        float target = Mth.clamp(deltaYaw * (kind.flies() ? 8F : 3F), -35F, 35F);
        if (!kind.flies() && kind != VehicleKind.MOTORBIKE) target = Mth.clamp(deltaYaw * 0.7F, -3F, 3F);
        if (kind == VehicleKind.HELICOPTER && !onGround()) target = (float) Mth.clamp(state.strafe * 40, -14, 14);
        roll += (target - roll) * 0.15F;
        float wheel = Mth.clamp(deltaYaw * 8F, -28F, 28F);
        steerAngle += (wheel - steerAngle) * 0.3F;
        if (engineOn()) engineSmoke();
        terrainPitchO = terrainPitch;
        if (!kind.flies() && kind.move != VehicleKind.Move.WATER) terrainPitch += (terrainTilt() - terrainPitch) * 0.25F;
    }

    /** Наклон корпуса по земле под передними и задними колёсами: только для вида, машина сама ступенька за ступенькой. */
    private float terrainTilt() {
        double half = Math.max(0.8, getBbWidth());
        double yaw = Math.toRadians(getYRot());
        double fx = -Math.sin(yaw) * half, fz = Math.cos(yaw) * half;
        double front = groundAt(getX() + fx, getZ() + fz), back = groundAt(getX() - fx, getZ() - fz);
        if (Double.isNaN(front) || Double.isNaN(back)) return 0;
        return (float) Mth.clamp(-Math.toDegrees(Math.atan2(front - back, half * 2)), -30, 30);
    }

    private double groundAt(double x, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(getY()) + 3, Mth.floor(z));
        for (int i = 0; i < 7; i++) {
            var shape = level().getBlockState(p).getCollisionShape(level(), p);
            if (!shape.isEmpty()) return p.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            p.move(0, -1, 0);
        }
        return Double.NaN;
    }

    private void engineSmoke() {
        if (kind == VehicleKind.STEAM_CAR && tickCount % 3 == 0) {
            Vec3 p = local(0, 49, -14);
            level().addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 0, 0.06, 0);
        } else if ((kind == VehicleKind.TRUCK) && tickCount % 4 == 0) {
            Vec3 p = local(15, 46, 23);
            level().addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.04, 0);
        } else if (kind == VehicleKind.JET) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 p = local(s * 5.8, 16.6, -72);
                level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0, 0);
            }
        } else if (kind == VehicleKind.BOAT && state.speed > 0.2 && isInWater()) {
            Vec3 p = local(0, 2, -36);
            level().addParticle(ParticleTypes.BUBBLE_POP, p.x, p.y, p.z, 0, 0.05, 0);
            level().addParticle(ParticleTypes.SPLASH, p.x, p.y + 0.2, p.z, 0, 0.1, 0);
        }
    }

    /** Точка в координатах модели (пиксели) → мир. */
    public Vec3 local(double x, double y, double z) {
        return position().add(new Vec3(x / 16, y / 16, z / 16).yRot(-getYRot() * Mth.DEG_TO_RAD));
    }

    // --- посадка ------------------------------------------------------------------------------------------------------
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < kind.seats.length;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
        int i = getPassengers().indexOf(passenger);
        double[] seat = kind.seats[Math.max(0, Math.min(i, kind.seats.length - 1))];
        return new Vec3(seat[0] / 16, seat[1] / 16, seat[2] / 16).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        super.positionRider(passenger, move);
        passenger.setYRot(passenger.getYRot() + deltaYaw);
        passenger.setYHeadRot(passenger.getYHeadRot() + deltaYaw);
        if (!kind.flies()) clampRotation(passenger);
    }

    private void clampRotation(Entity passenger) {
        passenger.setYBodyRot(getYRot());
        float d = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float limit = kind == VehicleKind.TANK ? 180F : 130F;
        float c = Mth.clamp(d, -limit, limit);
        passenger.yRotO += c - d;
        passenger.setYRot(passenger.getYRot() + c - d);
        passenger.setYHeadRot(passenger.getYRot());
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        if (!kind.flies()) clampRotation(passenger);
        else passenger.setYBodyRot(getYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 side = getCollisionHorizontalEscapeVector(getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(), getYRot() + 90F);
        Vec3 at = new Vec3(getX() + side.x, getY() + 0.2, getZ() + side.z);
        if (level().noCollision(passenger, passenger.getBoundingBox().move(at.subtract(passenger.position())))) return at;
        return super.getDismountLocationForPassenger(passenger);
    }

    // --- взаимодействие ---------------------------------------------------------------------------------------------------
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack held = player.getItemInHand(hand);
        boolean refillable = canFuel(held);
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (held.is(EquipmentContent.CARGO_KIT.get())) {
            if (hasKit()) {
                player.sendOverlayMessage(Component.translatable("message.iis.vehicle.kit_already"));
            } else {
                setKit(true);
                if (!player.getAbilities().instabuild) held.shrink(1);
                playSound(SoundEvents.ANVIL_USE, 0.6F, 1.2F);
                player.sendOverlayMessage(Component.translatable("message.iis.vehicle.kit_on", cargoSlots()));
            }
            return InteractionResult.CONSUME;
        }
        if (held.is(GearContent.DUCT_TAPE.get()) && vehicleHealth() < kind.health) {
            setVehicleHealth(vehicleHealth() + kind.health * 0.25F);
            if (!player.getAbilities().instabuild) held.shrink(1);
            playSound(SoundEvents.WOOL_PLACE, 1F, 0.8F);
            return InteractionResult.CONSUME;
        }
        if (refillable) {
            refuel(player, hand, held);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            openMenu(player);
            return InteractionResult.CONSUME;
        }
        if (canAddPassenger(player) && !player.isPassenger()) {
            player.setYRot(getYRot());
            player.setXRot(0);
            player.startRiding(this);
            if (kind.flies() && player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable(kind.move == VehicleKind.Move.PLANE ? "message.iis.vehicle.plane" : "message.iis.vehicle.helicopter"));
        }
        return InteractionResult.CONSUME;
    }

    public void openMenu(Player player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new VehicleMenu(id, inv, cargo, this), getDisplayName()), buf -> {
            buf.writeVarInt(getId());
            buf.writeVarInt(cargoSlots());
        });
    }

    /** Топливо из слота окна: понемногу, пока есть место. */
    private void refuelFromSlot() {
        ItemStack s = cargo.getItem(0);
        if (s.isEmpty() || tickCount % 10 != 0) return;
        long value = fuelValue(s);
        if (value <= 0 || fuel() + value > kind.tank) return;
        setFuel(fuel() + value);
        ItemStack rest = emptied(s);
        s.shrink(1);
        if (!rest.isEmpty()) {
            if (s.isEmpty()) cargo.setItem(0, rest);
            else Containers.dropItemStack(level(), getX(), getY(), getZ(), rest);
        }
    }

    public boolean canFuel(ItemStack stack) {
        return fuelValue(stack) > 0;
    }

    /** EU в одной единице предмета: 0 — не топливо для этой машины. */
    public long fuelValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (kind.fuel == VehicleKind.Fuel.SOLID) return stack.getBurnTime(null, level().fuelValues()) * 10L;
        Fluid f = Fluids.EMPTY;
        int amount = 0;
        if (stack.getItem() instanceof BucketItem b) {
            f = b.getContent();
            amount = 1000;
        } else if (stack.getItem() instanceof CapsuleItem) {
            FluidContent c = CapsuleItem.content(stack);
            f = c.fluidValue();
            amount = c.amount();
        }
        return liquidValue(kind, f) * amount;
    }

    /** EU на литр: жидкое топливо дизельного и газового генератора GT. */
    public static long liquidValue(Fluid f) {
        return Math.max(Fuels.power("diesel", f), Fuels.power("gas", f));
    }

    private static final java.util.Set<String> GASOLINE = java.util.Set.of("gasoline", "highoctanegasoline");
    private static final java.util.Set<String> BURNING_GAS = java.util.Set.of("gas_gas", "gas_natural_gas", "liquid_lpg", "methane", "ethane", "propane", "butane");

    /** EU на литр для этой машины: винтовому самолёту бензин или горючий газ, реактивному только высокооктановый (D130). */
    public static long liquidValue(VehicleKind kind, Fluid f) {
        if (f == Fluids.EMPTY) return 0;
        String id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f).getPath();
        return switch (kind.fuel) {
            case SOLID -> 0;
            case LIQUID -> liquidValue(f);
            case AVIATION -> GASOLINE.contains(id) || BURNING_GAS.contains(id) ? liquidValue(f) : 0;
            case JET -> id.equals("highoctanegasoline") ? liquidValue(f) : 0;
        };
    }

    /** Принять литры из заправщика; вернёт, сколько взяла. */
    public int refill(Fluid f, int liters) {
        long value = liquidValue(kind, f);
        if (value <= 0) return 0;
        int take = (int) Math.min(liters, (kind.tank - fuel()) / value);
        if (take > 0) setFuel(fuel() + take * value);
        return take;
    }

    private static ItemStack emptied(ItemStack s) {
        if (s.getItem() instanceof BucketItem) return new ItemStack(Items.BUCKET);
        if (s.getItem() instanceof CapsuleItem) return CapsuleItem.withContent(s.copyWithCount(1), FluidContent.EMPTY);
        var rem = s.getItem().getCraftingRemainder(s);
        return rem == null ? ItemStack.EMPTY : rem.create();
    }

    private void refuel(Player player, InteractionHand hand, ItemStack held) {
        long v = fuelValue(held);
        if (fuel() + v > kind.tank) {
            player.sendOverlayMessage(Component.translatable("message.iis.vehicle.tank_full"));
            return;
        }
        setFuel(fuel() + v);
        playSound(kind.fuel == VehicleKind.Fuel.SOLID ? SoundEvents.FIRECHARGE_USE : SoundEvents.BUCKET_EMPTY, 0.8F, 1F);
        if (player.getAbilities().instabuild) return;
        ItemStack rest = emptied(held);
        held.shrink(1);
        if (!rest.isEmpty()) {
            if (held.isEmpty()) player.setItemInHand(hand, rest);
            else if (!player.getInventory().add(rest)) player.drop(rest, false);
        }
    }

    // --- пушка танка -----------------------------------------------------------------------------------------------------
    /** Клавиша выстрела: у танка снаряд из груза, туда, куда смотрит водитель. */
    public void fireMainGun(ServerPlayer player) {
        if (kind != VehicleKind.TANK || getControllingPassenger() != player || gunCooldown > 0 || !(level() instanceof ServerLevel sl)) return;
        Item shell = EquipmentContent.AMMO.get(Weapon.Ammo.TANK).get();
        boolean free = player.getAbilities().instabuild;
        if (!free) {
            int slot = -1;
            for (int i = 1; i < cargo.getContainerSize(); i++) if (cargo.getItem(i).is(shell)) slot = i;
            if (slot < 0) {
                player.sendOverlayMessage(Component.translatable("message.iis.vehicle.no_shells"));
                return;
            }
            cargo.getItem(slot).shrink(1);
        }
        gunCooldown = 60;
        Vec3 dir = player.getLookAngle();
        // ствол на башне: дуло впереди по взгляду
        Vec3 pivot = local(0, 29, 0);
        Vec3 muzzle = pivot.add(dir.scale(4.6));
        ShotEntity shot = new ShotEntity(EquipmentContent.SHOT.get(), sl);
        shot.setOwner(player);
        shot.setup(Weapon.ROCKET_LAUNCHER, this);
        shot.markTank();
        shot.setPos(muzzle);
        shot.setDeltaMovement(dir.scale(3.0));
        shot.alignToMotion();
        sl.addFreshEntity(shot);
        sl.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3F, 0.7F);
        sl.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 12, 0.3, 0.3, 0.3, 0.03);
    }

    // --- урон -------------------------------------------------------------------------------------------------------------
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved()) return true;
        if (isInvulnerableToBase(source)) return false;
        // пули броню танка не берут
        if (kind == VehicleKind.TANK && source.is(EquipmentContent.DAMAGE_BULLET)) return false;
        boolean creative = source.getEntity() instanceof Player p && p.getAbilities().instabuild;
        boolean melee = source.getEntity() instanceof Player && source.getDirectEntity() == source.getEntity() && !source.is(EquipmentContent.DAMAGE_BULLET) && !source.is(EquipmentContent.DAMAGE_LASER);
        if (creative && melee && getPassengers().isEmpty()) {
            discard();
            return true;
        }
        // рукой пассажир машину не сломает
        if (melee && !getPassengers().isEmpty()) return false;
        setHurtDir(-getHurtDir());
        setHurtTime(10);
        markHurt();
        setDamage(Math.min(40F, getDamage() + amount * 2));
        if (melee) amount *= 2;
        setVehicleHealth(vehicleHealth() - amount);
        if (vehicleHealth() <= 0) {
            if (melee) dropAll(level, true);
            else wreck(level);
        }
        return true;
    }

    @Override
    protected Item getDropItem() {
        return EquipmentContent.VEHICLES.get(kind).get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putLong("Fuel", fuel());
        out.putFloat("Health", vehicleHealth());
        out.putDouble("Throttle", state.throttle);
        out.putBoolean("Kit", hasKit());
        ContainerHelper.saveAllItems(out, cargo.getItems());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        setFuel(in.getLongOr("Fuel", 0));
        setVehicleHealth(in.getFloatOr("Health", kind.health));
        state.throttle = in.getDoubleOr("Throttle", 0);
        setKit(in.getBooleanOr("Kit", false));
        ContainerHelper.loadAllItems(in, cargo.getItems());
    }
}
