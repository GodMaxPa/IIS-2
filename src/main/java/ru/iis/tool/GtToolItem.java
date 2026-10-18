package ru.iis.tool;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import ru.iis.core.material.MaterialData;

/**
 * Инструмент GT5 (молот, напильник, пила, ступка, ключ, кирка...).
 * В рецептах крафта инструмент не тратится, а теряет прочность (GT5 getToolDamagePerContainerCraft).
 */
public class GtToolItem extends Item {
    private final MaterialData.ToolDef def;

    public GtToolItem(Properties properties, MaterialData.ToolDef def) {
        super(properties);
        this.def = def;
    }

    public MaterialData.ToolDef def() { return def; }

    public String toolType() { return def.tool(); }

    @Override
    public ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        if (!(instance instanceof ItemStack stack) || stack.isEmpty()) return null;
        ItemStack copy = stack.copyWithCount(1);
        int damage = copy.getOrDefault(DataComponents.DAMAGE, 0) + def.craftDamage();
        if (damage >= copy.getMaxDamage()) return null; // инструмент сломался
        copy.set(DataComponents.DAMAGE, damage);
        return ItemStackTemplate.fromNonEmptyStack(copy);
    }
}
