package ru.iis.bio.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

/** Туша: разделывается на площадке ножом. Показывает, что ещё в ней есть (кожа, шерсть, перо). */
public class CarcassItem extends Item {
    public CarcassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        List<ItemStack> extras = stack.get(BioItems.CARCASS_EXTRAS.get());
        if (extras == null) return;
        for (ItemStack e : extras) {
            builder.accept(Component.literal("+ ").append(e.getHoverName()).append(e.getCount() > 1 ? " ×" + e.getCount() : "").withStyle(ChatFormatting.GRAY));
        }
    }
}
