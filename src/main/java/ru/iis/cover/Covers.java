package ru.iis.cover;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import ru.iis.IIS;
import ru.iis.multi.block.MaintenanceHatchBlockEntity;
import ru.iis.multi.logic.Maintenance;

import java.util.Map;

/** Предметы-покрытия, установка/снятие/настройка и фильтры сторон (GT5 BaseMetaTileEntity.onRightclick). */
public final class Covers {
    private Covers() {}

    /** Предмет → вид покрытия (GT_MetaGenerated_Item_01: registerCover). */
    public static final Map<String, CoverKind> ITEMS = Map.ofEntries(
            Map.entry("conveyor_module_lv", CoverKind.CONVEYOR),
            Map.entry("electric_pump_lv", CoverKind.PUMP),
            Map.entry("robot_arm_lv", CoverKind.ARM),
            Map.entry("cover_shutter", CoverKind.SHUTTER),
            Map.entry("cover_controller", CoverKind.CONTROLLER),
            Map.entry("cover_activity_detector", CoverKind.ACTIVITY_DETECTOR),
            Map.entry("cover_item_detector", CoverKind.ITEM_DETECTOR),
            Map.entry("cover_fluid_detector", CoverKind.FLUID_DETECTOR),
            Map.entry("cover_energy_detector", CoverKind.ENERGY_DETECTOR),
            Map.entry("cover_drain", CoverKind.DRAIN),
            Map.entry("cover_crafting", CoverKind.CRAFTING));

    public static @Nullable CoverKind kindOf(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(IIS.MODID) ? ITEMS.get(id.getPath()) : null;
    }

    public static Item itemOf(CoverKind k) {
        for (var e : ITEMS.entrySet()) {
            if (e.getValue() == k) return BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(IIS.MODID, e.getKey()));
        }
        throw new IllegalStateException("no item for " + k);
    }

    /** GT_Utility.getClickedFacingCoords: первая координата точки клика на грани (0..1) — для «правой половины». */
    static float faceU(Direction side, Vec3 local) {
        return (float) switch (side) {
            case DOWN, UP -> local.x;
            case NORTH -> 1 - local.x;
            case SOUTH -> local.x;
            case WEST -> local.z;
            case EAST -> 1 - local.z;
        };
    }

    /**
     * ПКМ по блоку-хозяину. null — не наше действие (дальше — обычная логика блока).
     * front — сторона, на которую покрытие ставить нельзя (лицевая сторона машины).
     */
    public static @Nullable InteractionResult use(CoverHost host, ItemStack stack, Player player, InteractionHand hand, BlockHitResult hit, @Nullable Direction front) {
        Level level = host.coverLevel();
        if (level == null) return null;
        Direction side = hit.getDirection();
        CoverSet covers = host.covers();
        CoverKind present = covers.get(side);
        CoverKind newKind = kindOf(stack);
        if (present == null) {
            if (newKind == null || side == front) return null;
            if (!level.isClientSide()) {
                covers.attach(side, newKind);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                host.coversChanged();
                player.sendOverlayMessage(Component.translatable("message.iis.cover.attached", Component.translatable(itemOf(newKind).getDescriptionId())));
            }
            return InteractionResult.SUCCESS;
        }
        Maintenance.Problem tool = stack.isEmpty() ? null : MaintenanceHatchBlockEntity.problemFor(stack);
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(host.coverPos()));
        boolean right = faceU(side, local) >= 0.5f;
        if (tool == Maintenance.Problem.SCREWDRIVER) {
            if (!level.isClientSide()) {
                Component msg = covers.screwdriver(host, side, player.isShiftKeyDown(), right);
                host.coversChanged();
                player.sendOverlayMessage(msg);
                if (!player.getAbilities().instabuild) stack.hurtAndBreak(4, player, hand);
            }
            return InteractionResult.SUCCESS;
        }
        if (tool == Maintenance.Problem.CROWBAR) {
            if (!level.isClientSide()) {
                CoverKind k = covers.remove(host, side);
                host.coversChanged();
                if (k != null) Block.popResourceFromFace(level, host.coverPos(), side, new ItemStack(itemOf(k)));
                if (!player.getAbilities().instabuild) stack.hurtAndBreak(20, player, hand);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty()) {
            if (present == CoverKind.ARM) {
                if (!level.isClientSide()) {
                    player.sendOverlayMessage(covers.armClick(side, right));
                    host.coversChanged();
                }
                return InteractionResult.SUCCESS;
            }
            if (present == CoverKind.CRAFTING) {
                if (!level.isClientSide()) {
                    BlockPos pos = host.coverPos();
                    player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CraftingMenu(id, inv, ContainerLevelAccess.create(level, pos)) {
                        @Override
                        public boolean stillValid(Player pl) {
                            return pl.distanceToSqr(Vec3.atCenterOf(pos)) <= 64;
                        }
                    }, Component.translatable("container.crafting")));
                }
                return InteractionResult.SUCCESS;
            }
        }
        return null;
    }

    /** Покрытия падают вместе с блоком. */
    public static void dropAll(CoverHost host, Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            CoverKind k = host.covers().get(d);
            if (k != null) Block.popResource(level, pos, new ItemStack(itemOf(k)));
        }
    }

    // --- фильтры возможностей (capabilities) по сторонам -----------------------------------------------------------
    public static @Nullable ResourceHandler<ItemResource> items(CoverHost host, @Nullable Direction side, @Nullable ResourceHandler<ItemResource> h) {
        if (h == null || side == null || host.covers().get(side) == null) return h;
        return new DelegatingResourceHandler<>(h) {
            @Override
            public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
                return host.covers().letsItemsIn(host, side) ? super.insert(index, resource, amount, tx) : 0;
            }

            @Override
            public int insert(ItemResource resource, int amount, TransactionContext tx) {
                return host.covers().letsItemsIn(host, side) ? super.insert(resource, amount, tx) : 0;
            }

            @Override
            public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
                return host.covers().letsItemsOut(host, side) ? super.extract(index, resource, amount, tx) : 0;
            }

            @Override
            public int extract(ItemResource resource, int amount, TransactionContext tx) {
                return host.covers().letsItemsOut(host, side) ? super.extract(resource, amount, tx) : 0;
            }
        };
    }

    public static @Nullable ResourceHandler<FluidResource> fluids(CoverHost host, @Nullable Direction side, @Nullable ResourceHandler<FluidResource> h) {
        if (h == null || side == null || host.covers().get(side) == null) return h;
        return new DelegatingResourceHandler<>(h) {
            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
                return host.covers().letsFluidIn(host, side) ? super.insert(index, resource, amount, tx) : 0;
            }

            @Override
            public int insert(FluidResource resource, int amount, TransactionContext tx) {
                return host.covers().letsFluidIn(host, side) ? super.insert(resource, amount, tx) : 0;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
                return host.covers().letsFluidOut(host, side) ? super.extract(index, resource, amount, tx) : 0;
            }

            @Override
            public int extract(FluidResource resource, int amount, TransactionContext tx) {
                return host.covers().letsFluidOut(host, side) ? super.extract(resource, amount, tx) : 0;
            }
        };
    }
}
