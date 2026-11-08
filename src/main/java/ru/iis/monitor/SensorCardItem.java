package ru.iis.monitor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Набор с датчиком GregTech и карта датчика (GT Behaviour_SensorKit, D141): набор ПКМ по блоку с данными (машина, реактор, накопитель)
 * становится картой, привязанной к этому блоку; карта вставляется в информационную панель.
 */
public class SensorCardItem extends Item {
    private final boolean kit;

    public SensorCardItem(boolean kit, Properties properties) {
        super(properties);
        this.kit = kit;
    }

    public record Target(BlockPos pos, ResourceKey<Level> dim) {}

    public static @Nullable Target target(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        var t = d.copyTag();
        if (!t.contains("tx")) return null;
        return new Target(new BlockPos(t.getIntOr("tx", 0), t.getIntOr("ty", 0), t.getIntOr("tz", 0)),
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.getStringOr("tdim", "minecraft:overworld"))));
    }

    public static ItemStack card(BlockPos pos, ResourceKey<Level> dim) {
        ItemStack card = new ItemStack(MonitorContent.SENSOR_CARD.get());
        CustomData.update(DataComponents.CUSTOM_DATA, card, t -> {
            t.putInt("tx", pos.getX());
            t.putInt("ty", pos.getY());
            t.putInt("tz", pos.getZ());
            t.putString("tdim", dim.identifier().toString());
        });
        return card;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!kit || ctx.getLevel().getBlockEntity(ctx.getClickedPos()) == null || ctx.getPlayer() == null) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide()) {
            ItemStack card = card(ctx.getClickedPos(), ctx.getLevel().dimension());
            ctx.getItemInHand().shrink(1);
            if (!ctx.getPlayer().getInventory().add(card)) ctx.getPlayer().drop(card, false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Target t = target(stack);
        if (t != null) builder.accept(Component.translatable("item.iis.gt_sensor_card.target", t.pos().getX(), t.pos().getY(), t.pos().getZ()).withStyle(ChatFormatting.GRAY));
    }
}
