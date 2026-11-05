package ru.iis.reactor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Деталь реактора. Прочность — износ стержня или накопленное тепло. Стержни с ураном и MOX облучают того, кто их носит (GT sRadiation = 1), кроме полного защитного костюма. */
public class ReactorPartItem extends Item {
    public final ReactorCore.Part part;

    public ReactorPartItem(ReactorCore.Part part, Properties properties) {
        super(properties);
        this.part = part;
    }

    private boolean radioactive() {
        return part.kind() == ReactorCore.Kind.FUEL && part.energy() >= 1F;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (radioactive() && entity instanceof LivingEntity living && !ReactorContent.fullHazmat(living)) {
            living.addEffect(new MobEffectInstance(ReactorContent.RADIATION, 20, 10));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String key = switch (part.kind()) {
            case FUEL -> "item.iis.reactor.fuel";
            case REFLECTOR -> "item.iis.reactor.reflector";
            case IRIDIUM_REFLECTOR -> "item.iis.reactor.reflector_iridium";
            case PLATING -> "item.iis.reactor.plating";
            case SPREAD_VENT -> "item.iis.reactor.spread";
            default -> "item.iis.reactor.heat";
        };
        builder.accept(switch (part.kind()) {
            case FUEL -> Component.translatable(key, part.capacity() - stack.getDamageValue(), part.capacity(), part.cells());
            case REFLECTOR -> Component.translatable(key, part.capacity() - stack.getDamageValue());
            case PLATING -> Component.translatable(key, part.maxHeatAdd());
            case SPREAD_VENT -> Component.translatable(key, part.sideVent());
            case IRIDIUM_REFLECTOR -> Component.translatable(key);
            default -> Component.translatable(key, stack.getDamageValue(), part.capacity());
        });
        if (part.kind() == ReactorCore.Kind.VENT) builder.accept(Component.translatable("item.iis.reactor.vent", part.reactorVent(), part.selfVent()).withStyle(ChatFormatting.GRAY));
        if (part.kind() == ReactorCore.Kind.SWITCH) builder.accept(Component.translatable("item.iis.reactor.switch", part.sideSwitch(), part.reactorSwitch()).withStyle(ChatFormatting.GRAY));
    }
}
