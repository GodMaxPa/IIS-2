package ru.iis.space;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Маска, оборудование или баллон: работают, просто лежа в инвентаре (см. {@link OxygenSystem}). */
public class OxygenItem extends Item {
    public OxygenItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.iis.oxygen.hint").withStyle(ChatFormatting.GRAY));
        if (stack.getMaxDamage() > 0) {
            builder.accept(Component.translatable("item.iis.oxygen_tank.level", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()).withStyle(ChatFormatting.AQUA));
        }
    }
}
