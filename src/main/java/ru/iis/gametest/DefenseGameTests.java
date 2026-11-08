package ru.iis.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import ru.iis.defense.DefenseContent;
import ru.iis.defense.ForceShieldItem;
import ru.iis.defense.TurretBaseBlockEntity;
import ru.iis.defense.TurretBlock;
import ru.iis.defense.TurretKind;
import ru.iis.equipment.EquipmentContent;

/** Щиты Кибера и турели (D140). */
final class DefenseGameTests {
    private DefenseGameTests() {}

    private static DamageSource src(GameTestHelper h, ResourceKey<DamageType> key) {
        return new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key), null, null, Vec3.ZERO);
    }

    /** Баллистический щит: пуля — целиком, взрыв — наполовину, лазер — на треть; силовой держит всё и платит зарядом, без заряда не держит. */
    static void shields(GameTestHelper h) {
        BlocksAttacks b = new ItemStack(DefenseContent.BALLISTIC_SHIELD.get()).get(DataComponents.BLOCKS_ATTACKS);
        h.assertTrue(b != null, "у баллистического щита нет блока");
        float bullet = b.resolveBlockedDamage(src(h, EquipmentContent.DAMAGE_BULLET), 10, 0);
        float blast = b.resolveBlockedDamage(src(h, EquipmentContent.DAMAGE_BLAST), 10, 0);
        float laser = b.resolveBlockedDamage(src(h, EquipmentContent.DAMAGE_LASER), 10, 0);
        h.assertTrue(bullet == 10 && Math.abs(blast - 5) < 0.01 && Math.abs(laser - 3) < 0.01, "щит держит пулю/взрыв/лазер " + bullet + "/" + blast + "/" + laser);
        h.assertTrue(b.resolveBlockedDamage(src(h, EquipmentContent.DAMAGE_BULLET), 10, Math.PI) == 0, "держит со спины");
        ItemStack force = new ItemStack(DefenseContent.FORCE_SHIELD.get());
        BlocksAttacks f = force.get(DataComponents.BLOCKS_ATTACKS);
        h.assertTrue(f != null && f.resolveBlockedDamage(src(h, EquipmentContent.DAMAGE_PLASMA), 10, 0) == 10, "силовой не держит плазму");
        Player p = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ForceShieldItem.setCharge(force, 100_000);
        p.setItemInHand(InteractionHand.OFF_HAND, force);
        p.startUsingItem(InteractionHand.OFF_HAND);
        DamageSource s = src(h, EquipmentContent.DAMAGE_PLASMA);
        LivingShieldBlockEvent ev = new LivingShieldBlockEvent(p, new DamageContainer(s, 10), 10, true);
        ForceShieldItem.onBlock(ev);
        h.assertTrue(ev.getBlocked() && ev.getBlockedDamage() == 10 && ForceShieldItem.charge(p.getUseItem()) == 50_000, "заряд после 10 урона " + ForceShieldItem.charge(p.getUseItem()));
        ev = new LivingShieldBlockEvent(p, new DamageContainer(s, 20), 20, true);
        ForceShieldItem.onBlock(ev);
        h.assertTrue(ev.getBlocked() && Math.abs(ev.getBlockedDamage() - 10) < 0.01 && ForceShieldItem.charge(p.getUseItem()) == 0, "частичный блок " + ev.getBlockedDamage());
        ev = new LivingShieldBlockEvent(p, new DamageContainer(s, 5), 5, true);
        ForceShieldItem.onBlock(ev);
        h.assertTrue(!ev.getBlocked(), "держит без заряда");
        h.succeed();
    }

    /** Пулемётная турель на базе 2 уровня стреляет в монстра, не трогает корову (режим «монстры»), тратит патроны и энергию; рельсотрон на ней молчит. */
    static void turretShoots(GameTestHelper h) {
        BlockPos base = new BlockPos(1, 1, 1), gun = base.above(), rail = base.east();
        h.setBlock(base, DefenseContent.BASES.get(1).get());
        h.setBlock(gun, DefenseContent.TURRETS.get(TurretKind.MACHINE_GUN).get().defaultBlockState().setValue(TurretBlock.FACING, Direction.UP));
        h.setBlock(rail.below(), DefenseContent.BASES.get(1).get());
        h.setBlock(rail, DefenseContent.TURRETS.get(TurretKind.RAILGUN).get().defaultBlockState().setValue(TurretBlock.FACING, Direction.UP));
        TurretBaseBlockEntity b = h.getBlockEntity(base, TurretBaseBlockEntity.class);
        TurretBaseBlockEntity rb = h.getBlockEntity(rail.below(), TurretBaseBlockEntity.class);
        b.setItem(0, new ItemStack(EquipmentContent.AMMO.get(ru.iis.equipment.Weapon.Ammo.RIFLE).get(), 8));
        rb.setItem(0, new ItemStack(DefenseContent.FERRO_SLUG.get(), 8));
        h.onEachTick(() -> {
            b.energy.stored = b.energy.capacity;
            rb.energy.stored = rb.energy.capacity;
        });
        Mob husk = EntityType.HUSK.create(h.getLevel(), EntitySpawnReason.TRIGGERED);
        husk.setPos(h.absoluteVec(new Vec3(6.5, 1, 6.5)));
        husk.setNoAi(true);
        h.getLevel().addFreshEntity(husk);
        Mob cow = EntityType.COW.create(h.getLevel(), EntitySpawnReason.TRIGGERED);
        cow.setPos(h.absoluteVec(new Vec3(1.5, 1, 6.5)));
        cow.setNoAi(true);
        h.getLevel().addFreshEntity(cow);
        h.assertTrue(b.getItem(0).getItem() == TurretKind.MACHINE_GUN.ammo(), "патрон пулемёта не " + b.getItem(0));
        h.runAtTickTime(80, () -> {
            h.assertTrue(husk.getHealth() < husk.getMaxHealth() || !husk.isAlive(), "кадавр цел: " + husk.getHealth());
            var t = h.getBlockEntity(gun, ru.iis.defense.TurretBlockEntity.class);
            h.assertTrue(!t.valid(h.getLevel(), b, cow, 18), "корова — цель в режиме «монстры»");
            b.mode = 1;
            h.assertTrue(t.valid(h.getLevel(), b, cow, 18), "корова не цель в режиме «и животные»");
            h.assertTrue(b.getItem(0).getCount() < 8, "патроны не тратятся");
            h.assertTrue(rb.getItem(0).getCount() == 8, "рельсотрон стреляет с базы 2 уровня");
            for (String id : new String[]{"defense/ballistic_shield", "processing/assembler/force_shield", "defense/turret_base_t1", "defense/turret_base_t5",
                    "defense/turret_machine_gun", "defense/turret_railgun", "defense/turret_laser", "defense/turret_upgrade_range", "defense/ferro_slug"}) {
                var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("iis", id));
                h.assertTrue(h.getLevel().recipeAccess().byKey(key).isPresent(), "нет рецепта " + id);
            }
            h.succeed();
        });
    }
}
