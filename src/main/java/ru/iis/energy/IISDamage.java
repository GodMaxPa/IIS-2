package ru.iis.energy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import ru.iis.IIS;

/** Урон током (GT_DamageSources#getElectricDamage: игнорирует броню). Тип описан в data/iis/damage_type/electricity.json. */
public final class IISDamage {
    private IISDamage() {}

    public static final ResourceKey<DamageType> ELECTRICITY = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(IIS.MODID, "electricity"));
}
