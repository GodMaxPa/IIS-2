package ru.iis.lv.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import ru.iis.lv.block.ElectricBlockEntity;

/** Комплект влагозащиты GT (Behaviour_WaterProofUpgrade, D132): ПКМ по машине, и дождь с грозой ей больше не вредят. */
public class WaterproofKitItem extends Item {
    public WaterproofKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (!(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof ElectricBlockEntity machine)) return InteractionResult.PASS;
        if (c.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        boolean fresh = !machine.isWaterproof();
        if (fresh) {
            machine.setWaterproof();
            c.getLevel().playSound(null, c.getClickedPos(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1F, 1F);
            if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
        }
        if (c.getPlayer() != null) c.getPlayer().sendOverlayMessage(Component.translatable(fresh ? "item.iis.waterproof_kit.done" : "item.iis.waterproof_kit.already"));
        return InteractionResult.CONSUME;
    }
}
