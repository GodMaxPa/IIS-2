package ru.iis.multi;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;
import ru.iis.core.material.MaterialData;
import ru.iis.multi.logic.LargeTurbineLogic;

import java.util.function.Consumer;

/**
 * Ротор большой турбины (GT_MetaGenerated_Tool_01 TURBINE_SMALL … TURBINE_HUGE, D89): материал — в компоненте
 * {@code iis:rotor_material}, износ — обычная прочность предмета (MaxDamage GT = 100 × прочность материала × множитель размера).
 */
public class TurbineRotorItem extends Item {
    public final LargeTurbineLogic.Size size;

    public TurbineRotorItem(Properties properties, LargeTurbineLogic.Size size) {
        super(properties);
        this.size = size;
    }

    public static MaterialData.@Nullable ToolStats material(ItemStack stack) {
        String id = stack.get(MultiContent.ROTOR_MATERIAL.get());
        return id == null ? null : MaterialData.toolStats().get(id);
    }

    /** Ротор этого размера из материала (GT getToolWithStats). */
    public ItemStack create(String materialId) {
        ItemStack s = new ItemStack(this);
        s.set(MultiContent.ROTOR_MATERIAL.get(), materialId);
        MaterialData.ToolStats m = MaterialData.toolStats().get(materialId);
        if (m != null) s.set(DataComponents.MAX_DAMAGE, (int) Math.min(Integer.MAX_VALUE, LargeTurbineLogic.maxDamage(size, m.durability())));
        return s;
    }

    public int optimalFlow(ItemStack stack) {
        MaterialData.ToolStats m = material(stack);
        return m == null ? 0 : LargeTurbineLogic.optimalFlow(size, m.speed());
    }

    public int baseEfficiency(ItemStack stack) {
        MaterialData.ToolStats m = material(stack);
        return m == null ? 0 : LargeTurbineLogic.baseEfficiency(size, m.quality());
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = stack.get(MultiContent.ROTOR_MATERIAL.get());
        if (id == null) return super.getName(stack);
        return Component.translatable(getDescriptionId() + ".named", Component.translatable("material.iis." + id));
    }

    /** GT_MetaGenerated_Tool#addAdditionalToolTips: КПД турбины и оптимальный поток пара. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        if (material(stack) == null) return;
        builder.accept(Component.translatable("item.iis.turbine.efficiency", baseEfficiency(stack) / 100).withStyle(ChatFormatting.BLUE));
        builder.accept(Component.translatable("item.iis.turbine.flow", optimalFlow(stack), optimalFlow(stack) * 20).withStyle(ChatFormatting.LIGHT_PURPLE));
        builder.accept(Component.translatable("item.iis.turbine.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
    }
}
