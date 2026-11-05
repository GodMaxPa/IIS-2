package ru.iis.space;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import ru.iis.IIS;

/**
 * Лунная тяжесть. GC WorldProviderMoon: getGravity 0.062 (вычитается из ванильных 0.08, остаётся 0.018), getFallDamageModifier 0.18.
 * Переход между измерениями тоже вызывает вход в мир, поэтому модификаторы снимаются сами.
 */
public final class LowGravity {
    private LowGravity() {}

    public static final double MOON_GRAVITY = 0.062, VANILLA_GRAVITY = 0.08, MOON_FALL = 0.18;
    private static final Identifier GRAVITY_ID = Identifier.fromNamespaceAndPath(IIS.MODID, "moon_gravity");
    private static final Identifier FALL_ID = Identifier.fromNamespaceAndPath(IIS.MODID, "moon_fall");

    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) apply(event.getEntity(), event.getLevel().dimension() == SpaceContent.MOON);
    }

    public static void apply(Entity entity, boolean moon) {
        if (!(entity instanceof LivingEntity living)) return;
        set(living.getAttribute(Attributes.GRAVITY), GRAVITY_ID, moon, -MOON_GRAVITY / VANILLA_GRAVITY);
        set(living.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), FALL_ID, moon, MOON_FALL - 1);
    }

    private static void set(AttributeInstance a, Identifier id, boolean on, double value) {
        if (a == null) return;
        a.removeModifier(id);
        if (on) a.addTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
