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
 * Батарейка GT (Battery_RE_*: перезаряжаемая, Battery_SU_*: одноразовая — только разряжается, GT setElectricStats aSpecialData
 * −3 / −2). Заряд хранится в компоненте iis:charge.
 */
public class BatteryItem extends Item {
    public final long capacity;
    public final long transferLimit;
    public final int tier;
    /** false — одноразовая (кислотная, ртутная, D101): заряжается только при изготовлении в фасовщике жидкостей. */
    public final boolean rechargeable;

    public BatteryItem(long capacity, long transferLimit, int tier, Properties properties) {
        this(capacity, transferLimit, tier, true, properties);
    }

    public BatteryItem(long capacity, long transferLimit, int tier, boolean rechargeable, Properties properties) {
        super(properties);
        this.capacity = capacity;
        this.transferLimit = transferLimit;
        this.tier = tier;
        this.rechargeable = rechargeable;
    }

    /** GT_MetaGenerated_Item_Renderer:136-148: индекс значка заряда — 1 пустая, 8 полная, иначе 7 − min(5, (макс − заряд)·6 / макс). */
    public static int chargeIcon(long charge, long capacity) {
        if (charge <= 0) return 1;
        if (charge >= capacity) return 8;
        return 7 - (int) Math.max(0L, Math.min(5L, (capacity - charge) * 6L / capacity));
    }

    /** Батарейка (отдаёт энергию буферу и машинам). Электроинструменты — нет, их только заряжают. */
    public boolean isBattery() {
        return true;
    }

    public static long charge(ItemStack stack) {
        return stack.getOrDefault(LvContent.CHARGE.get(), 0L);
    }

    public static void setCharge(ItemStack stack, long eu) {
        stack.set(LvContent.CHARGE.get(), Math.max(0, eu));
    }

    /** Ёмкость этого предмета; у электроинструмента зависит от батареи, с которой его собрали (GT MaxCharge). */
    public long capacity(ItemStack stack) {
        return capacity;
    }

    /** Зарядить до amount EU (не больше предела передачи); вернуть принятое. */
    public long charge(ItemStack stack, long amount) {
        if (!rechargeable) return 0;
        long c = charge(stack);
        long d = Math.min(Math.min(amount, transferLimit), capacity(stack) - c);
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
        return (int) Math.round(13.0 * charge(stack) / Math.max(1, capacity(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.55f, 0.8f, 1.0f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.iis.battery_lv.tooltip", charge(stack), capacity(stack)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
