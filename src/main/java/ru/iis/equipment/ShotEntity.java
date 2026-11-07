package ru.iis.equipment;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Летящий снаряд: граната, ракета, сгусток плазмы, снаряд танка. Взрывается при встрече с чем угодно или через 8 секунд. */
public class ShotEntity extends Projectile {
    private static final EntityDataAccessor<Integer> DATA_WEAPON = SynchedEntityData.defineId(ShotEntity.class, EntityDataSerializers.INT);
    private static final int LIFE = 160;
    /** Снаряд танка считается как ракета, у него только своя модель. */
    private static final EntityDataAccessor<Boolean> DATA_TANK = SynchedEntityData.defineId(ShotEntity.class, EntityDataSerializers.BOOLEAN);

    private int age;
    private float scale = 1F;
    private @Nullable Entity ignored;

    public ShotEntity(EntityType<? extends ShotEntity> type, Level level) {
        super(type, level);
        noPhysics = false;
    }

    public void setup(Weapon w, @Nullable Entity ignore) {
        entityData.set(DATA_WEAPON, w.ordinal());
        ignored = ignore;
    }

    public void setScale(float s) {
        scale = s;
    }

    public void markTank() {
        entityData.set(DATA_TANK, true);
    }

    public boolean tank() {
        return entityData.get(DATA_TANK);
    }

    public Weapon weapon() {
        return Weapon.values()[Math.floorMod(entityData.get(DATA_WEAPON), Weapon.values().length)];
    }

    public void alignToMotion() {
        Vec3 v = getDeltaMovement();
        double h = v.horizontalDistance();
        setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(v.y, h) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(DATA_WEAPON, Weapon.ROCKET_LAUNCHER.ordinal());
        data.define(DATA_TANK, false);
    }

    @Override
    public void tick() {
        super.tick();
        Weapon w = weapon();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !level().isClientSide()) {
            onHit(hit);
            return;
        }
        Vec3 v = getDeltaMovement();
        Vec3 next = position().add(v);
        setPos(next);
        double g = w.projectileGravity();
        if (g > 0) setDeltaMovement(v.x * 0.995, v.y - g, v.z * 0.995);
        alignToMotion();
        if (level().isClientSide()) {
            switch (w.kind) {
                case ROCKET -> {
                    level().addParticle(ParticleTypes.FLAME, getX() - v.x * 0.4, getY() - v.y * 0.4, getZ() - v.z * 0.4, 0, 0, 0);
                    level().addParticle(ParticleTypes.LARGE_SMOKE, getX() - v.x * 0.7, getY() - v.y * 0.7, getZ() - v.z * 0.7, 0, 0.01, 0);
                }
                case GRENADE -> level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
                case PLASMA -> level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), 0, 0, 0);
                default -> {}
            }
        } else if (++age > LIFE) {
            explode();
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (e == ignored || (ignored != null && e.isPassengerOfSameVehicle(ignored))) return false;
        Entity o = getOwner();
        if (o != null && (e == o || e == o.getVehicle())) return false;
        return e.isPickable() && !e.isSpectator();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        explode();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        setPos(result.getLocation());
        explode();
    }

    private void explode() {
        if (isRemoved() || !(level() instanceof ServerLevel sl)) return;
        Entity owner = getOwner();
        GunActions.blast(sl, position(), this, owner == null ? this : owner, weapon(), scale);
        discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("Weapon", entityData.get(DATA_WEAPON));
        out.putInt("Age", age);
        out.putBoolean("Tank", tank());
        out.putFloat("Scale", scale);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(DATA_WEAPON, in.getIntOr("Weapon", Weapon.ROCKET_LAUNCHER.ordinal()));
        age = in.getIntOr("Age", 0);
        entityData.set(DATA_TANK, in.getBooleanOr("Tank", false));
        scale = in.getFloatOr("Scale", 1F);
    }
}
