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
            Map.entry("conveyor_module_mv", CoverKind.CONVEYOR),
            Map.entry("conveyor_module_hv", CoverKind.CONVEYOR),
            Map.entry("conveyor_module_ev", CoverKind.CONVEYOR),
            Map.entry("conveyor_module_iv", CoverKind.CONVEYOR),
            Map.entry("electric_pump_lv", CoverKind.PUMP),
            Map.entry("electric_pump_mv", CoverKind.PUMP),
            Map.entry("electric_pump_hv", CoverKind.PUMP),
            Map.entry("electric_pump_ev", CoverKind.PUMP),
            Map.entry("electric_pump_iv", CoverKind.PUMP),
            Map.entry("robot_arm_lv", CoverKind.ARM),
            Map.entry("robot_arm_mv", CoverKind.ARM),
            Map.entry("robot_arm_hv", CoverKind.ARM),
            Map.entry("robot_arm_ev", CoverKind.ARM),
            Map.entry("robot_arm_iv", CoverKind.ARM),
            Map.entry("cover_shutter", CoverKind.SHUTTER),
            Map.entry("cover_controller", CoverKind.CONTROLLER),
            Map.entry("cover_activity_detector", CoverKind.ACTIVITY_DETECTOR),
            Map.entry("cover_item_detector", CoverKind.ITEM_DETECTOR),
            Map.entry("cover_fluid_detector", CoverKind.FLUID_DETECTOR),
            Map.entry("cover_energy_detector", CoverKind.ENERGY_DETECTOR),
            Map.entry("cover_drain", CoverKind.DRAIN),
            Map.entry("cover_crafting", CoverKind.CRAFTING),
            Map.entry("fluid_regulator_lv", CoverKind.FLUID_REGULATOR),
            Map.entry("fluid_regulator_iv", CoverKind.FLUID_REGULATOR_IV),
            Map.entry("cover_fluid_filter", CoverKind.FLUID_FILTER),
            Map.entry("solar_panel", CoverKind.SOLAR), Map.entry("solar_panel_8v", CoverKind.SOLAR_8V), Map.entry("solar_panel_lv", CoverKind.SOLAR_LV), Map.entry("solar_panel_mv", CoverKind.SOLAR_MV),
            Map.entry("solar_panel_hv", CoverKind.SOLAR_HV), Map.entry("solar_panel_ev", CoverKind.SOLAR_EV), Map.entry("solar_panel_iv", CoverKind.SOLAR_IV), Map.entry("solar_panel_luv", CoverKind.SOLAR_LUV),
            Map.entry("solar_panel_zpm", CoverKind.SOLAR_ZPM), Map.entry("solar_panel_uv", CoverKind.SOLAR_UV));

    public static @Nullable CoverKind kindOf(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(IIS.MODID) ? ITEMS.get(id.getPath()) : null;
    }

    /** Уровень покрытия по id предмета (суффикс _mv/_hv/_ev; у остальных 0 — LV). */
    public static int tierOf(String path) {
        for (int t = CoverSet.TIERS.length - 1; t > 0; t--) if (path.endsWith("_" + CoverSet.TIERS[t])) return t;
        return 0;
    }

    public static int tierOf(ItemStack stack) {
        return tierOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }

    public static Item itemOf(CoverKind k) {
        return itemOf(k, 0);
    }

    public static Item itemOf(CoverKind k, int tier) {
        String best = null;
        for (var e : ITEMS.entrySet()) {
            if (e.getValue() != k) continue;
            if (tierOf(e.getKey()) == tier) { best = e.getKey(); break; }
            if (best == null || tierOf(e.getKey()) == 0) best = e.getKey();
        }
        if (best == null) throw new IllegalStateException("no item for " + k);
        return BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(IIS.MODID, best));
    }

    /** Текстура покрытия: у конвейера, помпы и манипулятора MV–EV — свой корпус (GT MACHINE_CASINGS[tier]). */
    public static String textureId(CoverKind k, int tier) {
        boolean tiered = k == CoverKind.CONVEYOR || k == CoverKind.PUMP || k == CoverKind.ARM;
        return tiered && tier > 0 ? k.id + "_" + CoverSet.TIERS[tier] : k.id;
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
     * GT BaseMetaTileEntity.onRightclick (D101): покрытие ставится и снимается монтировкой на грани по сетке 3×3, как у ключа;
     * отвёртка — по кликнутой грани, если на ней есть покрытие, иначе по сетке; Shift + ПКМ пустой рукой — интерфейс покрытия.
     */
    public static @Nullable InteractionResult use(CoverHost host, ItemStack stack, Player player, InteractionHand hand, BlockHitResult hit, @Nullable Direction front) {
        Level level = host.coverLevel();
        if (level == null) return null;
        Direction grid = ru.iis.machine.block.Wrench.wrenchingSide(host.coverPos(), hit);
        CoverSet covers = host.covers();
        CoverKind newKind = kindOf(stack);
        if (newKind != null && covers.get(grid) == null) {
            if (grid == front) return null;
            if (!level.isClientSide()) {
                int t = tierOf(stack);
                covers.attach(grid, newKind, t);
                player.sendOverlayMessage(Component.translatable("message.iis.cover.attached", Component.translatable(itemOf(newKind, t).getDescriptionId())));
                if (!player.getAbilities().instabuild) stack.shrink(1);
                host.coversChanged();
                level.playSound(null, host.coverPos(), net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.4f);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty() && player.isShiftKeyDown()) {
            Direction gs = covers.get(hit.getDirection()) != null ? hit.getDirection() : grid;
            CoverKind k = covers.get(gs);
            if (k == null || !CoverMenu.hasGui(k)) return null;
            if (!level.isClientSide()) CoverMenu.open(player, host, gs);
            return InteractionResult.SUCCESS;
        }
        Maintenance.Problem tool = stack.isEmpty() ? null : MaintenanceHatchBlockEntity.problemFor(stack);
        if (tool == Maintenance.Problem.CROWBAR && covers.get(grid) != null) {
            if (!level.isClientSide()) {
                int t = covers.tier(grid);
                CoverKind k = covers.remove(host, grid);
                host.coversChanged();
                if (k != null) Block.popResourceFromFace(level, host.coverPos(), grid, new ItemStack(itemOf(k, t)));
                if (!player.getAbilities().instabuild) stack.hurtAndBreak(20, player, hand);
            }
            return InteractionResult.SUCCESS;
        }
        Direction side = hit.getDirection();
        if (tool == Maintenance.Problem.SCREWDRIVER && covers.get(side) == null) side = grid;
        CoverKind present = covers.get(side);
        if (present == null) return null;
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
        if (present == CoverKind.FLUID_FILTER && tool == null) {
            net.minecraft.world.level.material.Fluid fluid = containedFluid(stack);
            if (fluid != null) {
                if (!level.isClientSide()) {
                    player.sendOverlayMessage(covers.setFilterFluid(side, fluid));
                    host.coversChanged();
                }
                return InteractionResult.SUCCESS;
            }
        }
        if (stack.isEmpty()) {
            if (present == CoverKind.FLUID_REGULATOR || present == CoverKind.FLUID_REGULATOR_IV) {
                if (!level.isClientSide()) {
                    player.sendOverlayMessage(covers.regulatorClick(side, right));
                    host.coversChanged();
                }
                return InteractionResult.SUCCESS;
            }
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

    /** Жидкость в ёмкости (капсула ИИС, ведро) — для фильтра жидкости. */
    static net.minecraft.world.level.material.@Nullable Fluid containedFluid(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem b && b.getContent() != net.minecraft.world.level.material.Fluids.EMPTY) {
            return b.getContent();
        }
        if (stack.getItem() instanceof ru.iis.fluid.CapsuleItem) {
            ru.iis.fluid.FluidContent c = ru.iis.fluid.CapsuleItem.content(stack);
            if (c != null && c.amount() > 0) return c.fluidValue();
        }
        return null;
    }

    /** Покрытия падают вместе с блоком. */
    public static void dropAll(CoverHost host, Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            CoverKind k = host.covers().get(d);
            if (k != null) Block.popResource(level, pos, new ItemStack(itemOf(k, host.covers().tier(d))));
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
                return host.covers().letsFluidIn(host, side, resource.getFluid()) ? super.insert(index, resource, amount, tx) : 0;
            }

            @Override
            public int insert(FluidResource resource, int amount, TransactionContext tx) {
                return host.covers().letsFluidIn(host, side, resource.getFluid()) ? super.insert(resource, amount, tx) : 0;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
                return host.covers().letsFluidOut(host, side, resource.getFluid()) ? super.extract(index, resource, amount, tx) : 0;
            }

            @Override
            public int extract(FluidResource resource, int amount, TransactionContext tx) {
                return host.covers().letsFluidOut(host, side, resource.getFluid()) ? super.extract(resource, amount, tx) : 0;
            }
        };
    }
}
