package ru.iis.defense;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.iis.equipment.EquipmentContent;

import java.util.Comparator;
import java.util.List;

/**
 * Турель OMT (TurretHead, D140): стоит на грани базы, ищет ближайшую видимую цель в радиусе (без цели — раз в 10 тиков, как в конфиге ИИС 1),
 * поворачивается к ней и стреляет, пока у базы есть энергия и патроны. Улучшения базы: точность −20 %, энергия −8 %, пауза −10 %, дальность +2
 * (конфиг ИИС 1), до 4 каждого. Утилизатор: 10 % за штуку сберечь патрон. Пули — мгновенный луч, гранаты и ракеты рвутся в точке попадания.
 */
public class TurretBlockEntity extends BlockEntity {
    public float yaw, pitch;
    /** Клиент: плавный поворот головы. */
    public float shownYaw, shownPitch;
    private @Nullable LivingEntity target;
    private int cooldown, search;
    private float sentYaw = 999, sentPitch = 999;

    public TurretBlockEntity(BlockPos pos, BlockState state) {
        super(DefenseContent.TURRET_BE.get(), pos, state);
    }

    public TurretKind kind() {
        return getBlockState().getBlock() instanceof TurretBlock b ? b.kind : TurretKind.DISPOSABLE;
    }

    public @Nullable TurretBaseBlockEntity base() {
        if (level == null) return null;
        Direction f = getBlockState().getValue(TurretBlock.FACING);
        return level.getBlockEntity(worldPosition.relative(f.getOpposite())) instanceof TurretBaseBlockEntity b ? b : null;
    }

    public Vec3 center() {
        return Vec3.atCenterOf(worldPosition);
    }

    public int range(TurretBaseBlockEntity base) {
        return kind().range + 2 * base.count(DefenseContent.UPGRADE_RANGE.get());
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        TurretBaseBlockEntity base = base();
        if (base == null || base.tier < kind().tier) return;
        if (cooldown > 0) cooldown--;
        int range = range(base);
        if (target != null && !valid(sl, base, target, range)) target = null;
        if (target == null) {
            if (--search > 0) return;
            search = 10;
            target = find(sl, base, range);
            if (target == null) return;
        }
        Vec3 aimAt = target.position().add(0, target.getBbHeight() * 0.55, 0);
        Vec3 dir = aimAt.subtract(center()).normalize();
        yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
        pitch = (float) (-Math.asin(dir.y) * Mth.RAD_TO_DEG);
        if (Math.abs(Mth.wrapDegrees(yaw - sentYaw)) > 2 || Math.abs(pitch - sentPitch) > 2) {
            sentYaw = yaw;
            sentPitch = pitch;
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        if (cooldown > 0) return;
        fire(sl, base, target, dir);
    }

    /** Выстрел, если хватает энергии и патронов; возвращает, был ли он. */
    public boolean fire(ServerLevel sl, TurretBaseBlockEntity base, LivingEntity t, Vec3 dir) {
        TurretKind k = kind();
        Item ammo = k.ammo();
        if (ammo != null && !base.hasAmmo(ammo)) return false;
        int rf = (int) Math.ceil(k.rf * Math.max(0.2, 1 - 0.08 * base.count(DefenseContent.UPGRADE_EFFICIENCY.get())));
        if (!base.useRf(rf)) return false;
        RandomSource r = sl.getRandom();
        if (ammo != null) base.takeAmmo(ammo, r.nextInt(100) < 10 * base.count(DefenseContent.ADDON_RECYCLER.get()));
        cooldown = Math.max(1, Math.round(k.cooldown * (1 - 0.1F * base.count(DefenseContent.UPGRADE_FIRE_RATE.get()))));
        double spread = k.accuracy / 100.0 * 0.15 * Math.max(0, 1 - 0.2 * base.count(DefenseContent.UPGRADE_ACCURACY.get()));
        Vec3 d = dir.add(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread).normalize();
        Vec3 from = center().add(d.scale(0.7));
        Entity owner = base.ownerPlayer();
        switch (k) {
            case RELATIVISTIC -> {
                t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3));
                trail(sl, from, t.position().add(0, t.getBbHeight() / 2, 0), ParticleTypes.END_ROD, 0.6);
                sound(sl, SoundEvents.CONDUIT_ATTACK_TARGET);
            }
            case TELEPORTER -> {
                teleportAway(sl, base, t);
                sound(sl, SoundEvents.ENDERMAN_TELEPORT);
            }
            default -> {
                Vec3 end = ray(sl, from, d, range(base), k, owner, base);
                ParticleOptions p = switch (k) {
                    case LASER -> new DustParticleOptions(0xFF3020, 1.0F);
                    case RAILGUN -> ParticleTypes.ELECTRIC_SPARK;
                    case INCENDIARY -> ParticleTypes.FLAME;
                    case GRENADE, ROCKET -> ParticleTypes.SMOKE;
                    default -> ParticleTypes.CRIT;
                };
                trail(sl, from, end, p, k == TurretKind.LASER || k == TurretKind.RAILGUN ? 0.25 : 0.7);
                sound(sl, switch (k) {
                    case DISPOSABLE, POTATO -> SoundEvents.DISPENSER_LAUNCH;
                    case MACHINE_GUN -> SoundEvents.FIREWORK_ROCKET_BLAST;
                    case INCENDIARY -> SoundEvents.BLAZE_SHOOT;
                    case GRENADE, ROCKET -> SoundEvents.FIREWORK_ROCKET_LAUNCH;
                    case LASER -> SoundEvents.GUARDIAN_ATTACK;
                    default -> SoundEvents.TRIDENT_THUNDER.value();
                });
            }
        }
        return true;
    }

    private void sound(ServerLevel sl, SoundEvent s) {
        sl.playSound(null, worldPosition, s, SoundSource.BLOCKS, 0.4F, 1.0F + (sl.getRandom().nextFloat() - 0.5F) * 0.2F);
    }

    private DamageSource source(ServerLevel sl, ResourceKey<DamageType> key, @Nullable Entity owner, Vec3 at) {
        return new DamageSource(sl.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key), null, owner, at);
    }

    /** Луч: первая цель (рельсотрон — все на линии) или стена; гранаты и ракеты рвутся в точке. */
    private Vec3 ray(ServerLevel sl, Vec3 from, Vec3 d, int range, TurretKind k, @Nullable Entity owner, TurretBaseBlockEntity base) {
        Vec3 far = from.add(d.scale(range));
        var block = sl.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        List<Entity> hits = sl.getEntities((Entity) null, new AABB(from, stop).inflate(1), e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator()
                && !(e instanceof Player p && base.trusted(p)) && e.getBoundingBox().inflate(0.2).clip(from, stop).isPresent());
        hits.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        Vec3 end = stop;
        ResourceKey<DamageType> key = k == TurretKind.LASER ? EquipmentContent.DAMAGE_LASER : EquipmentContent.DAMAGE_BULLET;
        for (Entity e : hits) {
            Vec3 at = e.getBoundingBox().inflate(0.2).clip(from, stop).orElse(e.position());
            if (k.explosive()) {
                end = at;
                break;
            }
            e.invulnerableTime = 0;
            e.hurtServer(sl, source(sl, key, owner, from), k.damage);
            if (k == TurretKind.INCENDIARY) e.igniteForSeconds(5);
            if (k == TurretKind.LASER) e.igniteForSeconds(2);
            end = at;
            if (k != TurretKind.RAILGUN) break;
            end = stop;
        }
        if (k.explosive()) blast(sl, end, k, owner, base);
        return end;
    }

    /** Взрыв без разрушения блоков: урон спадает к краю, стены заслоняют, своих не задевает. */
    private void blast(ServerLevel sl, Vec3 at, TurretKind k, @Nullable Entity owner, TurretBaseBlockEntity base) {
        double r = k.blastRadius();
        for (Entity e : sl.getEntities((Entity) null, new AABB(at, at).inflate(r), e -> e instanceof LivingEntity && !e.isSpectator())) {
            if (e instanceof Player p && base.trusted(p)) continue;
            double dist = Math.sqrt(e.getBoundingBox().distanceToSqr(at));
            if (dist > r) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            if (dist > 1 && sl.clip(new ClipContext(at, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() != HitResult.Type.MISS) continue;
            e.invulnerableTime = 0;
            e.hurtServer(sl, source(sl, EquipmentContent.DAMAGE_BLAST, owner, at), (float) (k.damage * (1 - dist / r * 0.7)));
        }
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    /** Турель-телепортатор: цель переносится на землю в 8–16 блоках от базы (куда — в OMT неизвестно, предложение). */
    private void teleportAway(ServerLevel sl, TurretBaseBlockEntity base, LivingEntity t) {
        RandomSource r = sl.getRandom();
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 8 + r.nextDouble() * 8;
            int x = Mth.floor(base.getBlockPos().getX() + Math.cos(a) * dist), z = Mth.floor(base.getBlockPos().getZ() + Math.sin(a) * dist);
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= sl.getMinY()) continue;
            sl.sendParticles(ParticleTypes.PORTAL, t.getX(), t.getY() + 1, t.getZ(), 30, 0.3, 0.6, 0.3, 0.2);
            t.teleportTo(x + 0.5, y, z + 0.5);
            return;
        }
    }

    private static void trail(ServerLevel sl, Vec3 a, Vec3 b, ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.min(80, d.length() / step);
        for (int i = 1; i <= n; i++) {
            Vec3 v = a.add(d.scale(i / (double) n));
            sl.sendParticles(p, v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
    }

    // --- цели -----------------------------------------------------------------------------------------------------------------
    private @Nullable LivingEntity find(ServerLevel sl, TurretBaseBlockEntity base, int range) {
        AABB box = new AABB(worldPosition).inflate(range);
        return sl.getEntitiesOfClass(LivingEntity.class, box, e -> valid(sl, base, e, range)).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(center()))).orElse(null);
    }

    /** Монстры всегда; животные и прочие — в режимах 1 и 3; чужие игроки — в режимах 2 и 3; свои, творческий режим и наблюдатели — никогда. */
    public boolean valid(ServerLevel sl, TurretBaseBlockEntity base, LivingEntity e, int range) {
        if (!e.isAlive() || e.isRemoved() || e.isSpectator() || e instanceof ArmorStand || e.level() != sl) return false;
        if (e.distanceToSqr(center()) > (double) range * range) return false;
        if (e instanceof Player p) {
            if (p.getAbilities().instabuild || base.trusted(p) || !base.attacksPlayers()) return false;
        } else if (!(e instanceof Enemy) && !base.attacksNeutrals()) {
            return false;
        }
        Vec3 aim = e.position().add(0, e.getBbHeight() * 0.55, 0);
        Vec3 from = center().add(aim.subtract(center()).normalize().scale(0.7));
        return sl.clip(new ClipContext(from, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    // --- данные ---------------------------------------------------------------------------------------------------------------
    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putFloat("yaw", yaw);
        out.putFloat("pitch", pitch);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        yaw = in.getFloatOr("yaw", 0);
        pitch = in.getFloatOr("pitch", 0);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
