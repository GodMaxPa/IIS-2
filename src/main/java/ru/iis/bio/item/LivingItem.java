package ru.iis.bio.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Живой полуфабрикат или посев: показывает линию колонии, из которой он получен. */
public class LivingItem extends Item {
    public LivingItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        lineage(stack, builder);
    }

    public static void lineage(ItemStack stack, Consumer<Component> builder) {
        String line = stack.get(BioItems.LINEAGE.get());
        if (line != null && !line.isEmpty()) {
            builder.accept(Component.translatable("item.iis.bio.lineage", line.length() > 8 ? line.substring(0, 8) : line).withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
