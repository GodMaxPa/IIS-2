package ru.iis.equipment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.iis.lv.item.BatteryItem;
import ru.iis.net.IISNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Выстрел, перезарядка, взрыв (сервер). Числа — в {@link Weapon}. */
public final class GunActions {
    private GunActions() {}

    /** Когда можно снова стрелять: только на время игры, поэтому не в предмете. */
    private static final Map<Entity, Long> NEXT_SHOT = new WeakHashMap<>();

    public static boolean reloading(ItemStack stack, long now) {
        return stack.getOrDefault(EquipmentContent.GUN_RELOAD.get(), 0L) > now;
    }

    public static int loaded(ItemStack stack) {
        return stack.getOrDefault(EquipmentContent.GUN_AMMO.get(), 0);
    }

    public static boolean free(Entity shooter) {
        return shooter instanceof Player p && p.getAbilities().instabuild;
    }

    public static int countAmmo(Player p, Weapon.Ammo ammo) {
        var item = EquipmentContent.AMMO.get(ammo).get();
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    public static int takeAmmo(Player p, Weapon.Ammo ammo, int want) {
        var item = EquipmentContent.AMMO.get(ammo).get();
        int taken = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && taken < want; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item)) continue;
            int d = Math.min(want - taken, s.getCount());
            s.shrink(d);
            taken += d;
        }
        return taken;
    }

    /** Начать перезарядку: true — пошла. */
    public static boolean startReload(ServerLevel level, LivingEntity shooter, ItemStack stack, Weapon w) {
        if (w.electric() || reloading(stack, level.getGameTime())) return false;
        if (loaded(stack) >= w.magazine) return false;
        if (shooter instanceof Player p && !free(p) && countAmmo(p, w.ammo) <= 0) return false;
        stack.set(EquipmentContent.GUN_RELOAD.get(), level.getGameTime() + w.reload);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        return true;
    }

    /** Конец перезарядки: тикает, пока предмет в инвентаре. */
    public static void tickReload(ServerLevel level, Entity owner, ItemStack stack, Weapon w) {
        long end = stack.getOrDefault(EquipmentContent.GUN_RELOAD.get(), 0L);
        if (end == 0 || end > level.getGameTime()) return;
        stack.remove(EquipmentContent.GUN_RELOAD.get());
        int have = loaded(stack);
        int got;
        if (owner instanceof Player p && !free(p)) got = takeAmmo(p, w.ammo, w.reloadAmount(have, countAmmo(p, w.ammo)));
        else got = w.magazine - have;
        stack.set(EquipmentContent.GUN_AMMO.get(), have + got);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /** Выстрел, если можно. click — нажатие, а не удержание: при пустом магазине сразу начнёт перезарядку. */
    public static boolean tryFire(ServerLevel level, LivingEntity shooter, ItemStack stack, Weapon w, boolean click) {
        long now = level.getGameTime();
        if (NEXT_SHOT.getOrDefault(shooter, 0L) > now) return false;
        if (w.electric()) {
            long c = BatteryItem.charge(stack);
            if (c < w.euPerShot && !free(shooter)) {
                if (click) dry(level, shooter);
                return false;
            }
            if (!free(shooter)) BatteryItem.setCharge(stack, c - w.euPerShot);
        } else {
            if (reloading(stack, now)) return false;
            int a = loaded(stack);
            if (a <= 0) {
                if (!startReload(level, shooter, stack, w) && click) dry(level, shooter);
                return false;
            }
            stack.set(EquipmentContent.GUN_AMMO.get(), a - 1);
        }
        NEXT_SHOT.put(shooter, now + w.interval);
        fire(level, shooter, w);
        return true;
    }

    /** Осечка: пусто. */
    private static void dry(ServerLevel level, LivingEntity shooter) {
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    /** Выстрел с заданного стрелка: снаряд или мгновенный луч. */
    public static void fire(ServerLevel level, LivingEntity shooter, Weapon w) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        RandomSource rnd = level.getRandom();
        boolean aim = shooter.isShiftKeyDown();
        double spread = Math.toRadians(w.spreadDegrees(aim));
        Vec3 muzzle = muzzle(shooter, eye, look);
        Vec3 end = muzzle;
        if (w.projectile()) {
            Vec3 d = jitter(look, spread, rnd);
            ShotEntity shot = new ShotEntity(EquipmentContent.SHOT.get(), level);
            shot.setOwner(shooter);
            shot.setup(w, null);
            shot.setPos(muzzle);
            shot.setDeltaMovement(d.scale(w.projectileSpeed()));
            shot.alignToMotion();
            level.addFreshEntity(shot);
        } else {
            for (int i = 0; i < w.pellets; i++) {
                Vec3 d = jitter(look, spread, rnd);
                end = ray(level, shooter, eye, d, w);
                send(level, shooter, w, muzzle, end, i == 0);
            }
        }
        if (w.projectile()) send(level, shooter, w, muzzle, muzzle, true);
        sound(level, shooter, w, rnd);
        if (w.era.equals("steam") || w.era.equals("lv")) {
            // чёрный порох: облако дыма
            level.sendParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 8, 0.15, 0.15, 0.15, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private static Vec3 muzzle(LivingEntity shooter, Vec3 eye, Vec3 look) {
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        return eye.add(look.scale(0.9)).add(right.scale(0.28)).add(0, -0.22, 0);
    }

    private static Vec3 jitter(Vec3 dir, double radians, RandomSource rnd) {
        if (radians <= 0) return dir;
        return dir.add(rnd.nextGaussian() * radians * 0.5, rnd.nextGaussian() * radians * 0.5, rnd.nextGaussian() * radians * 0.5).normalize();
    }

    private static void send(ServerLevel level, LivingEntity shooter, Weapon w, Vec3 from, Vec3 to, boolean kick) {
        IISNetwork.ShotFx fx = new IISNetwork.ShotFx(shooter.getId(), w.ordinal(), (float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z, kick);
        PacketDistributor.sendToPlayersNear(level, null, from.x, from.y, from.z, 96, fx);
    }

    private static void sound(ServerLevel level, LivingEntity shooter, Weapon w, RandomSource rnd) {
        SoundEvent s;
        float vol = 1.2F, pitch;
        switch (w.kind) {
            case LASER -> {
                s = SoundEvents.BEACON_DEACTIVATE;
                pitch = w == Weapon.LASER_CANNON ? 1.2F : 1.9F;
                vol = 0.7F;
            }
            case PLASMA -> {
                s = SoundEvents.BLAZE_SHOOT;
                pitch = w == Weapon.PLASMA_CANNON ? 0.55F : 1.25F;
            }
            case ROCKET, GRENADE -> {
                s = SoundEvents.FIREWORK_ROCKET_LAUNCH;
                pitch = 0.6F;
            }
            default -> {
                s = w.damage * w.pellets >= 14 ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.FIREWORK_ROCKET_BLAST;
                pitch = w.damage * w.pellets >= 14 ? 1.7F : 1.5F + rnd.nextFloat() * 0.2F;
                vol = w.damage * w.pellets >= 14 ? 0.7F : 1.2F;
            }
        }
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), s, SoundSource.PLAYERS, vol, pitch);
    }

    private static Holder<DamageType> type(ServerLevel level, ResourceKey<DamageType> key) {
        return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
    }

    public static DamageSource source(ServerLevel level, Weapon.Kind kind, Entity direct, Entity owner) {
        ResourceKey<DamageType> key = switch (kind) {
            case LASER -> EquipmentContent.DAMAGE_LASER;
            case PLASMA -> EquipmentContent.DAMAGE_PLASMA;
            case GRENADE, ROCKET -> EquipmentContent.DAMAGE_BLAST;
            default -> EquipmentContent.DAMAGE_BULLET;
        };
        return new DamageSource(type(level, key), direct, owner);
    }

    /** Луч от стрелка: первое препятствие или предел дальности. Вернёт точку конца. */
    public static Vec3 ray(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, Weapon w) {
        Vec3 far = from.add(dir.scale(w.range));
        BlockHitResult block = level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        double limit = from.distanceTo(stop);
        Vec3 end = stop;
        Entity vehicle = shooter.getRootVehicle();
        List<Hit> hits = new ArrayList<>();
        AABB area = new AABB(from, stop).inflate(1);
        for (Entity e : level.getEntities(shooter, area, e -> e.isPickable() && !e.isSpectator() && e != vehicle && !e.isPassengerOfSameVehicle(shooter))) {
            var hit = e.getBoundingBox().inflate(0.15).clip(from, stop);
            if (hit.isPresent()) hits.add(new Hit(e, from.distanceTo(hit.get())));
        }
        hits.sort(Comparator.comparingDouble(Hit::dist));
        int left = w.pierce + 1;
        DamageSource src = source(level, w.kind, shooter, shooter);
        for (Hit h : hits) {
            if (left-- <= 0 || h.dist > limit) break;
            hurt(level, h.entity, src, (float) w.damageAt(h.dist), w);
            end = from.add(dir.scale(h.dist));
        }
        if (left > 0 && block.getType() != HitResult.Type.MISS) {
            BlockPos pos = block.getBlockPos();
            var state = level.getBlockState(pos);
            if (w.kind == Weapon.Kind.LASER) level.sendParticles(ParticleTypes.LAVA, stop.x, stop.y, stop.z, 2, 0.02, 0.02, 0.02, 0);
            else if (!state.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), stop.x, stop.y, stop.z, 5, 0.05, 0.05, 0.05, 0.1);
            end = stop;
        } else if (left > 0) {
            end = stop;
        }
        return end;
    }

    private record Hit(Entity entity, double dist) {}

    /** Урон без неуязвимости после удара: иначе автомат бил бы раз в полсекунды. */
    public static void hurt(ServerLevel level, Entity e, DamageSource src, float amount, Weapon w) {
        e.invulnerableTime = 0;
        e.hurtServer(level, src, amount);
        e.invulnerableTime = 0;
        if (w.kind == Weapon.Kind.LASER) e.igniteForSeconds(2);
        else if (w.kind == Weapon.Kind.PLASMA) e.igniteForSeconds(5);
        if (e instanceof LivingEntity) level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY(0.6), e.getZ(), 2, 0.1, 0.1, 0.1, 0.1);
    }

    /** Взрыв снаряда: урон с падением к краю, стены заслоняют. Блоки не ломает, владелец получает половину. */
    public static void blast(ServerLevel level, Vec3 at, Entity direct, Entity owner, Weapon w) {
        double r = w.blast;
        DamageSource src = source(level, w.kind, direct, owner);
        for (Entity e : level.getEntities(direct, new AABB(at, at).inflate(r), e -> e.isPickable() && !e.isSpectator())) {
            double d = e.getBoundingBox().distanceToSqr(at);
            d = Math.sqrt(d);
            double dmg = w.blastAt(d);
            if (dmg <= 0) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            if (level.clip(new ClipContext(at, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, direct)).getType() != HitResult.Type.MISS && d > 1) continue;
            if (e == owner) dmg *= 0.5;
            hurt(level, e, src, (float) dmg, w);
            Vec3 away = c.subtract(at);
            if (away.lengthSqr() > 1e-4 && !(e instanceof ru.iis.equipment.vehicle.Driveable)) e.push(away.normalize().scale(0.4 * (1 - d / r)).add(0, 0.15, 0));
        }
        boolean plasma = w.kind == Weapon.Kind.PLASMA;
        level.sendParticles(r > 3.5 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(plasma ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, at.x, at.y, at.z, (int) (r * 12), r * 0.25, r * 0.25, r * 0.25, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, (int) (r * 6), r * 0.25, r * 0.25, r * 0.25, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, (float) Math.min(4, r * 0.8), plasma ? 1.4F : 1.0F);
    }

    /** Клавиша перезарядки (сервер). */
    public static void reloadKey(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof Gun g && !g.weapon().electric()) startReload(player.level(), player, stack, g.weapon());
    }

    /** Общее у обоих видов оружия. */
    public interface Gun {
        Weapon weapon();
    }
}
