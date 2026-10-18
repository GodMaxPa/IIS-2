package ru.iis.core.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import ru.iis.core.material.IISMaterial;
import ru.iis.core.material.MaterialData;

import java.util.function.Consumer;

/**
 * Предмет-форма материала GT (дроблёная руда, пыль, слиток, пластина...).
 * Подсказка с химической формулой, как в GT5.
 * Дроблёная руда и грязная/чистая пыль, брошенные в котёл с водой, промываются
 * (GT_MetaGenerated_Item_01#onEntityItemUpdate): дроблёная -> промытая, грязная/чистая пыль -> пыль.
 */
public class MaterialItem extends Item {
    private final String prefix;
    private final IISMaterial material;

    public MaterialItem(Properties properties, String prefix, IISMaterial material) {
        super(properties);
        this.prefix = prefix;
        this.material = material;
    }

    public String prefix() { return prefix; }
    public IISMaterial material() { return material; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        if (material.formula() != null && !material.formula().isEmpty()) {
            builder.accept(Component.literal(material.formula()).withStyle(ChatFormatting.YELLOW));
        }
        if (washResult() != null) {
            builder.accept(Component.translatable("iis.tooltip.cauldron_wash").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Во что превращается при промывке в котле, или null. */
    private String washResult() {
        return switch (prefix) {
            case "crushed" -> MaterialData.forms().get("purified/" + material.id());
            case "impure_dust", "pure_dust" -> MaterialData.forms().get("dust/" + material.id());
            default -> null;
        };
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (level.isClientSide()) return false;
        String target = washResult();
        if (target == null) return false;
        BlockPos pos = entity.blockPosition();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.WATER_CAULDRON) || state.getValue(LayeredCauldronBlock.LEVEL) <= 0) return false;
        Item result = BuiltInRegistries.ITEM.getValue(Identifier.parse(target));
        entity.setItem(new ItemStack(result, stack.getCount()));
        LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        return false;
    }
}
