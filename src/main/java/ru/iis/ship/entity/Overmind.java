package ru.iis.ship.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import ru.iis.ship.ShipContent;

/**
 * Ядро сверхорганизма — босс второго яруса материнского корабля (CONCEPT §6.2; ответ владельца 16.11: «настоящий босс-существо»).
 * Центральный орган живой системы: с места не сходит, бьёт на расстоянии спорами всё, что видит, и зовёт охрану —
 * ползунов, а ниже половины здоровья ещё и супермутантов. Не исчезает; полоса здоровья видна тем, кто рядом.
 * Числа — черновик ИИ (D156): 400 здоровья, броня 8, споры 7 урона раз в 2,5 с на 24 блока, охрана раз в 12 с (не больше 6 рядом).
 */
public class Overmind extends Monster {
    public static final int SPORE_INTERVAL = 50, GUARD_INTERVAL = 240, GUARD_CAP = 6;
    public static final float SPORE_DAMAGE = 7.0F;
    public static final double RANGE = 24.0;

    private final ServerBossEvent bossEvent;

    public Overmind(EntityType<? extends Overmind> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        this.bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        this.setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 400.0).add(Attributes.MOVEMENT_SPEED, 0.0).add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ARMOR, 8.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        if (this.tickCount % SPORE_INTERVAL == 0 && this.distanceToSqr(target) <= RANGE * RANGE && this.hasLineOfSight(target)) spores(level, target);
        if (this.tickCount % GUARD_INTERVAL == 0) callGuard(level);
    }

    /** Облако спор от ядра к цели и урон. */
    private void spores(ServerLevel level, LivingEntity target) {
        double sx = getX(), sy = getY() + getBbHeight() * 0.6, sz = getZ();
        double tx = target.getX(), ty = target.getY() + target.getBbHeight() * 0.5, tz = target.getZ();
        for (int i = 1; i <= 12; i++) {
            double t = i / 12.0;
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, sx + (tx - sx) * t, sy + (ty - sy) * t, sz + (tz - sz) * t, 3, 0.15, 0.15, 0.15, 0.0);
        }
        target.hurtServer(level, this.damageSources().indirectMagic(this, this), SPORE_DAMAGE);
        this.playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1.6F);
    }

    /** Охрана: два ползуна, ниже половины здоровья — ещё супермутант; рядом не больше {@link #GUARD_CAP}. */
    private void callGuard(ServerLevel level) {
        int near = level.getEntitiesOfClass(Monster.class, this.getBoundingBox().inflate(20.0), e -> e instanceof Crawler || e instanceof Brute).size();
        if (near >= GUARD_CAP) return;
        summon(level, ShipContent.CRAWLER.get());
        summon(level, ShipContent.CRAWLER.get());
        if (this.getHealth() < this.getMaxHealth() * 0.5F) summon(level, ShipContent.BRUTE.get());
        this.playSound(SoundEvents.WARDEN_ROAR, 1.0F, 1.4F);
    }

    private void summon(ServerLevel level, EntityType<? extends Monster> type) {
        Monster m = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (m == null) return;
        double a = this.random.nextDouble() * Math.PI * 2, r = 3.0 + this.random.nextDouble() * 2.0;
        m.snapTo(getX() + Math.cos(a) * r, getY(), getZ() + Math.sin(a) * r, this.random.nextFloat() * 360.0F, 0.0F);
        m.setTarget(this.getTarget());
        level.addFreshEntity(m);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, m.getX(), m.getY() + 0.5, m.getZ(), 12, 0.4, 0.4, 0.4, 0.0);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_HEARTBEAT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }
}
