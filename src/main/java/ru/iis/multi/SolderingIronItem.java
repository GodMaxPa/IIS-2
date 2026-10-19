package ru.iis.multi;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import ru.iis.IIS;
import ru.iis.lv.item.BatteryItem;

import java.util.function.Consumer;

/**
 * Паяльник LV (GT_MetaGenerated_Tool_01.SOLDERING_IRON_LV): 100000 EU, заряжается в буфере.
 * Использование (GT_ModHandler#useSolderingIron): нужен заряд больше 1000 EU и одна единица припоя
 * (слиток, стержень или тонкая проволока олова, свинца или паяльного сплава); тратит 10000 EU.
 */
public class SolderingIronItem extends BatteryItem {
    public static final TagKey<Item> SOLDER = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IIS.MODID, "solder"));
    public static final long PER_USE = 10000;

    public SolderingIronItem(Properties properties) {
        super(100000, 32, 1, properties);
    }

    @Override
    public boolean isBattery() {
        return false;
    }

    public static boolean use(ItemStack iron, Player player) {
        if (player.isCreative()) return true;
        if (!(iron.getItem() instanceof SolderingIronItem) || charge(iron) <= 1000) return false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(SOLDER)) {
                s.shrink(1);
                long c = charge(iron);
                setCharge(iron, Math.max(0, c - PER_USE));
                return c >= PER_USE;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("item.iis.soldering_iron_lv.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
