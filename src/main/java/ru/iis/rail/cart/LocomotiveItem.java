package ru.iis.rail.cart;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** Ставит локомотив на путь (как MinecartItem) передом туда, куда смотрит игрок (Railcraft ItemLocomotive). */
public class LocomotiveItem extends Item {
    private final Supplier<? extends EntityType<? extends Locomotive>> type;

    public LocomotiveItem(Supplier<? extends EntityType<? extends Locomotive>> type, Properties p) {
        super(p);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.RAILS)) return InteractionResult.FAIL;
        ItemStack stack = context.getItemInHand();
        double offset = state.getBlock() instanceof BaseRailBlock r && r.getRailDirection(state, level, pos, null).isSlope() ? 0.5 : 0;
        if (level instanceof ServerLevel sl) {
            AbstractMinecart cart = AbstractMinecart.createMinecart(level, pos.getX() + 0.5, pos.getY() + 0.0625 + offset, pos.getZ() + 0.5,
                    type.get(), EntitySpawnReason.DISPENSER, stack, context.getPlayer());
            if (!(cart instanceof Locomotive loco)) return InteractionResult.FAIL;
            Vec3 look = context.getPlayer() != null ? context.getPlayer().getLookAngle() : new Vec3(context.getHorizontalDirection().getStepX(), 0, context.getHorizontalDirection().getStepZ());
            sl.addFreshEntity(loco);
            loco.setHeading(new Vec3(look.x, 0, look.z));
            sl.gameEvent(GameEvent.ENTITY_PLACE, pos, GameEvent.Context.of(context.getPlayer(), sl.getBlockState(pos.below())));
        }
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
