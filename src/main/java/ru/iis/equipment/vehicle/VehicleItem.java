package ru.iis.equipment.vehicle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.iis.equipment.EquipmentContent;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Ставит машину туда, куда смотрит игрок: носом от него. Катер — только на воду. Запас топлива и повреждения хранятся в предмете. */
public class VehicleItem extends Item {
    public final VehicleKind kind;
    private final Supplier<? extends EntityType<? extends Driveable>> type;

    public VehicleItem(VehicleKind kind, Supplier<? extends EntityType<? extends Driveable>> type, Properties p) {
        super(p);
        this.kind = kind;
        this.type = type;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean water = kind.move == VehicleKind.Move.WATER;
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 2));
        BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.OUTLINE, water ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        if (water && !level.getFluidState(pos).is(FluidTags.WATER)) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.iis.vehicle.needs_water"));
            return InteractionResult.FAIL;
        }
        Vec3 at = water ? new Vec3(hit.getLocation().x, pos.getY() + 0.7, hit.getLocation().z) : hit.getLocation();
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        Driveable v = type.get().create(sl, EntitySpawnReason.SPAWN_ITEM_USE);
        if (v == null) return InteractionResult.FAIL;
        v.snapTo(at.x, at.y, at.z, player.getYRot(), 0);
        if (!sl.noCollision(v, v.getBoundingBox())) {
            player.sendOverlayMessage(Component.translatable("message.iis.vehicle.no_room"));
            return InteractionResult.FAIL;
        }
        v.setFuel(stack.getOrDefault(EquipmentContent.VEHICLE_FUEL.get(), 0L));
        v.setVehicleHealth(kind.health - stack.getOrDefault(EquipmentContent.VEHICLE_DAMAGE.get(), 0F));
        v.setKit(stack.getOrDefault(EquipmentContent.VEHICLE_KIT.get(), false));
        sl.addFreshEntity(v);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.iis.vehicle.speed", VehicleKind.kmh(kind.maxSpeed), kind.seats.length).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.iis.vehicle." + kind.fuel.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY));
        long fuel = stack.getOrDefault(EquipmentContent.VEHICLE_FUEL.get(), 0L);
        if (fuel > 0) out.accept(Component.translatable("tooltip.iis.vehicle.fuel", 100 * fuel / kind.tank).withStyle(ChatFormatting.GRAY));
        boolean kit = stack.getOrDefault(EquipmentContent.VEHICLE_KIT.get(), false);
        out.accept(Component.translatable(kit ? "tooltip.iis.vehicle.cargo_kit" : "tooltip.iis.vehicle.cargo", kind.cargoSlots(kit)).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.iis.vehicle.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
