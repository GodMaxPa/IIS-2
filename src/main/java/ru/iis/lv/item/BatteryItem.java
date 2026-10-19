package ru.iis.lv.item;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import ru.iis.lv.LvContent;

import java.util.function.Consumer;

/**
 * Перезаряжаемая батарейка LV (GT Battery_RE_LV_Sodium: 50000 EU, уровень 1, предел передачи 32 EU/t).
 * Заряд хранится в компоненте iis:charge.
 */
public class BatteryItem extends Item {
    public final long capacity;
    public final long transferLimit;
    public final int tier;

    public BatteryItem(long capacity, long transferLimit, int tier, Properties properties) {
        super(properties);
        this.capacity = capacity;
        this.transferLimit = transferLimit;
        this.tier = tier;
    }

    public static long charge(ItemStack stack) {
        return stack.getOrDefault(LvContent.CHARGE.get(), 0L);
    }

    public static void setCharge(ItemStack stack, long eu) {
        stack.set(LvContent.CHARGE.get(), Math.max(0, eu));
    }

    /** Зарядить до amount EU (не больше предела передачи); вернуть принятое. */
    public long charge(ItemStack stack, long amount) {
        long c = charge(stack);
        long d = Math.min(Math.min(amount, transferLimit), capacity - c);
        if (d <= 0) return 0;
        setCharge(stack, c + d);
        return d;
    }

    /** Разрядить до amount EU; вернуть отданное. */
    public long discharge(ItemStack stack, long amount) {
        long c = charge(stack);
        long d = Math.min(Math.min(amount, transferLimit), c);
        if (d <= 0) return 0;
        setCharge(stack, c - d);
        return d;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return (int) Math.round(13.0 * charge(stack) / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.55f, 0.8f, 1.0f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.iis.battery_lv.tooltip", charge(stack), capacity).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
