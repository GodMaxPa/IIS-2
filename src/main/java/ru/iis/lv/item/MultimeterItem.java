package ru.iis.lv.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.EnergyReadout;

/**
 * Мультиметр: правый клик — подробный замер провода или машины в чат; пока прибор в руке —
 * живые показания того, на что смотришь (раз в полсекунды над панелью быстрого доступа).
 * В GT5 похожий прибор (Portable Scanner) появлялся только в HV — здесь он с LV (отличие, см. PORTING_NOTES).
 */
public class MultimeterItem extends Item {
    public MultimeterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel() instanceof ServerLevel sl && ctx.getPlayer() != null) {
            for (var line : EnergyReadout.report(sl, ctx.getClickedPos())) ctx.getPlayer().sendSystemMessage(line);
            // GT Portable Scanner тоже показывал загрязнение чанка
            if (ru.iis.pollution.Pollution.enabled()) {
                ctx.getPlayer().sendSystemMessage(net.minecraft.network.chat.Component.translatable("meter.iis.pollution",
                        ru.iis.pollution.Pollution.get(sl, ctx.getClickedPos())).withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) return;
        if (!(owner instanceof ServerPlayer p) || level.getGameTime() % 10 != 0) return;
        HitResult hit = p.pick(6.0, 0, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = bh.getBlockPos();
        var line = EnergyReadout.line(level, pos);
        if (line != null) p.sendOverlayMessage(line);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<net.minecraft.network.chat.Component> builder, net.minecraft.world.item.TooltipFlag flag) {
        for (int i = 0; i < 2; i++) {
            builder.accept(net.minecraft.network.chat.Component.translatable("item.iis.multimeter.tooltip." + i).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
