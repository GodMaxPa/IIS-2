package ru.iis.lv.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.item.BatteryItem;

/** Значок заряда батарейки GT (D101): индекс картинки 1…8 для модели {@code minecraft:range_dispatch}, см. {@link BatteryItem#chargeIcon}. */
public record BatteryChargeProperty() implements RangeSelectItemModelProperty {
    public static final MapCodec<BatteryChargeProperty> CODEC = MapCodec.unit(new BatteryChargeProperty());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        if (!(stack.getItem() instanceof BatteryItem b)) return 0;
        return BatteryItem.chargeIcon(BatteryItem.charge(stack), b.capacity(stack));
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return CODEC;
    }
}
