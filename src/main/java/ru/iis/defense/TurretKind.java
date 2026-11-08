package ru.iis.defense;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Турели (D140) в духе Open Modular Turrets 2.3.7. Числа — config/openmodularturrets.cfg ИИС 1: точность (разброс, меньше — лучше), урон, пауза
 * между выстрелами (тики), энергия на выстрел (RF; база хранит EU, 1 EU = 8 RF по тому же конфигу), дальность. Патроны — предложение ИИ.
 */
public enum TurretKind {
    DISPOSABLE("disposable", 1, 50, 2, 25, 2, 10, "minecraft:cobblestone"),
    POTATO("potato", 1, 30, 3, 35, 10, 15, "minecraft:potato"),
    MACHINE_GUN("machine_gun", 2, 30, 2, 8, 100, 18, "iis:rifle_round"),
    INCENDIARY("incendiary", 2, 30, 2, 25, 250, 12, "minecraft:blaze_powder"),
    GRENADE("grenade", 3, 30, 8, 40, 3000, 20, "iis:grenade_round"),
    RELATIVISTIC("relativistic", 3, 0, 0, 100, 5000, 20, null),
    ROCKET("rocket", 4, 10, 10, 30, 5000, 30, "iis:rocket_round"),
    TELEPORTER("teleporter", 4, 0, 0, 100, 15000, 20, null),
    LASER("laser", 4, 10, 2, 10, 10000, 25, null),
    RAILGUN("railgun", 5, 0, 25, 100, 25000, 30, "iis:ferro_slug");

    public final String id;
    public final int tier, accuracy, damage, cooldown, rf, range;
    private final @Nullable String ammo;

    TurretKind(String id, int tier, int accuracy, int damage, int cooldown, int rf, int range, @Nullable String ammo) {
        this.id = id;
        this.tier = tier;
        this.accuracy = accuracy;
        this.damage = damage;
        this.cooldown = cooldown;
        this.rf = rf;
        this.range = range;
        this.ammo = ammo;
    }

    public @Nullable Item ammo() {
        if (ammo == null) return null;
        Item i = BuiltInRegistries.ITEM.getValue(Identifier.parse(ammo));
        return i == Items.AIR ? null : i;
    }

    public boolean explosive() {
        return this == GRENADE || this == ROCKET;
    }

    /** Радиус взрыва гранаты и ракеты (в OMT взрыв без разрушения блоков; радиус — предложение). */
    public double blastRadius() {
        return this == ROCKET ? 3.0 : 2.5;
    }
}
