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

    /** D122: молот разведывает (GT_Tool_HardHammer:141, Behaviour_Prospecting(1, 1000)): единица прочности за стук. */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        net.minecraft.world.InteractionResult special = ToolBehaviours.useOn(this, ctx);   // D144: плуг, вантуз
        if (special != null) return special;
        if (!toolType().equals("hammer") || ctx.getPlayer() == null) return super.useOn(ctx);
        if (!(ctx.getLevel() instanceof net.minecraft.server.level.ServerLevel sl)) return net.minecraft.world.InteractionResult.SUCCESS;
        java.util.List<net.minecraft.network.chat.Component> lines = HammerProspecting.knock(sl, ctx.getClickedPos(), ctx.getClickedFace(), def.quality());
        if (lines == null) return super.useOn(ctx);
        lines.forEach(c -> ctx.getPlayer().sendSystemMessage(c));
        ctx.getItemInHand().hurtAndBreak(1, ctx.getPlayer(), ctx.getHand());
        sl.playSound(null, ctx.getClickedPos(), net.minecraft.sounds.SoundEvents.ANVIL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.5f);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

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
