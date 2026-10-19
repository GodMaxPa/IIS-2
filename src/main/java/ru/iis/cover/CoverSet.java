package ru.iis.cover;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Покрытия на шести сторонах блока (GT5 BaseMetaTileEntity: mCoverSides / mCoverData / mSidedRedstone)
 * и их поведение (gregtech.common.covers.*). Стороны — по Direction.get3DDataValue (как в GT: 0 низ, 1 верх, 2 север…).
 */
public final class CoverSet {
    /** GT_Cover_Arm: старший бит — «уже в новом формате», следующий — выдача; по 14 бит на слоты. */
    static final int ARM_CONVERTED = 0x80000000, ARM_EXPORT = 0x40000000, ARM_SLOT = 0x3FFF;
    /** GT_Cover_ItemMeter. */
    static final int METER_CONVERTED = 0x80000000, METER_INVERT = 0x40000000, METER_SLOT = 0x0FFFFFFF;
    public static final int CONVEYOR_RATE = 400, PUMP_RATE = 32, ARM_RATE = 400;

    private final @Nullable CoverKind[] kind = new CoverKind[6];
    private final int[] data = new int[6];
    private final int[] redstone = new int[6];

    public @Nullable CoverKind get(Direction side) {
        return kind[side.get3DDataValue()];
    }

    public int data(Direction side) {
        return data[side.get3DDataValue()];
    }

    public boolean any() {
        for (CoverKind k : kind) if (k != null) return true;
        return false;
    }

    public int redstoneOut(Direction side) {
        return kind[side.get3DDataValue()] == null ? 0 : redstone[side.get3DDataValue()];
    }

    public void attach(Direction side, CoverKind k) {
        int i = side.get3DDataValue();
        kind[i] = k;
        data[i] = k == CoverKind.ARM ? ARM_CONVERTED | (1 << 14) | ARM_EXPORT : k == CoverKind.ITEM_DETECTOR ? METER_CONVERTED : 0;
        redstone[i] = 0;
    }

    public @Nullable CoverKind remove(CoverHost host, Direction side) {
        int i = side.get3DDataValue();
        CoverKind k = kind[i];
        if (k == CoverKind.CONTROLLER && host.isMachine()) host.setAllowedToWork(true); // GT_Cover_ControlsWork.onCoverRemoval
        kind[i] = null;
        data[i] = 0;
        redstone[i] = 0;
        return k;
    }

    // --- что пропускает сторона (GT letsItemsIn/Out, letsFluidIn/Out, letsEnergyIn/Out) --------------------------
    private static boolean condBlocks(CoverHost h, int v) {
        // GT_Cover_Conveyor/Pump: режимы 2..5 и 8..11 — по разрешению работы машины
        return v % 6 > 1 && h.isMachine() && h.allowedToWork() != (v % 6 < 4);
    }

    private static boolean shutter(CoverHost h, int v, boolean in) {
        if (v >= 2) return v == (in ? 3 : 2);
        return !h.isMachine() || (h.allowedToWork() ? v % 2 == 0 : v % 2 != 0);
    }

    public boolean letsItemsIn(CoverHost h, Direction side) {
        CoverKind k = get(side);
        if (k == null) return true;
        int v = data(side);
        return switch (k) {
            case CONVEYOR -> v >= 6 || v % 2 != 0;
            case SHUTTER -> shutter(h, v, true);
            case DRAIN, CRAFTING -> false;
            default -> true;
        };
    }

    public boolean letsItemsOut(CoverHost h, Direction side) {
        CoverKind k = get(side);
        if (k == null) return true;
        int v = data(side);
        return switch (k) {
            case CONVEYOR -> v >= 6 || v % 2 == 0;
            case SHUTTER -> shutter(h, v, false);
            case DRAIN, CRAFTING -> false;
            default -> true;
        };
    }

    public boolean letsFluidIn(CoverHost h, Direction side) {
        CoverKind k = get(side);
        if (k == null) return true;
        int v = data(side);
        return switch (k) {
            case PUMP -> !(v > 1 && condBlocks(h, v)) && (v >= 6 || v % 2 != 0);
            case SHUTTER -> shutter(h, v, true);
            case DRAIN -> (!h.isMachine() || h.allowedToWork()) == (v < 2);
            case CRAFTING -> false;
            default -> true;
        };
    }

    public boolean letsFluidOut(CoverHost h, Direction side) {
        CoverKind k = get(side);
        if (k == null) return true;
        int v = data(side);
        return switch (k) {
            case PUMP -> !(v > 1 && condBlocks(h, v)) && (v >= 6 || v % 2 == 0);
            case SHUTTER -> shutter(h, v, false);
            case DRAIN, CRAFTING -> false;
            default -> true;
        };
    }

    public boolean letsEnergyIn(CoverHost h, Direction side) {
        CoverKind k = get(side);
        if (k == null) return true;
        return switch (k) {
            case SHUTTER -> shutter(h, data(side), true);
            case DRAIN, CRAFTING -> false;
            default -> true;
        };
    }

    public boolean letsEnergyOut(CoverHost h, Direction side) {
        CoverKind k = get(side);
        if (k == null) return true;
        return switch (k) {
            case SHUTTER -> shutter(h, data(side), false);
            case DRAIN, CRAFTING -> false;
            default -> true;
        };
    }

    // --- работа ------------------------------------------------------------------------------------------------
    private static int tickRate(CoverKind k, int v) {
        return switch (k) {
            case CONVEYOR, ARM -> CONVEYOR_RATE;
            case PUMP, CONTROLLER -> 1;
            case ACTIVITY_DETECTOR, ITEM_DETECTOR, FLUID_DETECTOR -> 5;
            case ENERGY_DETECTOR -> 20;
            case DRAIN -> v < 3 ? 50 : 1;
            case SHUTTER, CRAFTING -> 0;
        };
    }

    /** Вызывается каждый тик хозяина. Возвращает true, если изменился выход красного сигнала. */
    public boolean tick(CoverHost h, ServerLevel level, long timer) {
        boolean redstoneChanged = false;
        for (Direction side : Direction.values()) {
            int i = side.get3DDataValue();
            CoverKind k = kind[i];
            if (k == null) continue;
            int rate = tickRate(k, data[i]);
            if (rate <= 0 || timer % rate != 0) continue;
            int before = redstone[i];
            switch (k) {
                case CONVEYOR -> conveyor(h, level, side, data[i]);
                case PUMP -> pump(h, level, side, data[i]);
                case ARM -> arm(h, level, side, data[i]);
                case CONTROLLER -> controller(h, level, side, data[i]);
                case ACTIVITY_DETECTOR -> redstone[i] = activity(h, data[i]);
                case ITEM_DETECTOR -> redstone[i] = itemMeter(h, side, data[i]);
                case FLUID_DETECTOR -> redstone[i] = fluidMeter(h, data[i]);
                case ENERGY_DETECTOR -> redstone[i] = euMeter(h, data[i]);
                case DRAIN -> drain(h, level, side, data[i]);
                default -> {}
            }
            if (redstone[i] != before) redstoneChanged = true;
        }
        return redstoneChanged;
    }

    private static boolean exportsOrFreeSide(int v, Direction side) {
        // GT: costsEnergy = (export || side != верх) && (import || side != низ)
        return (v % 2 == 0 || side != Direction.UP) && (v % 2 != 0 || side != Direction.DOWN);
    }

    private static @Nullable ResourceHandler<ItemResource> neighbourItems(ServerLevel l, BlockPos pos, Direction side) {
        return l.getCapability(Capabilities.Item.BLOCK, pos.relative(side), side.getOpposite());
    }

    private static @Nullable ResourceHandler<FluidResource> neighbourFluids(ServerLevel l, BlockPos pos, Direction side) {
        return l.getCapability(Capabilities.Fluid.BLOCK, pos.relative(side), side.getOpposite());
    }

    /** GT_Cover_Conveyor.doCoverThings. */
    private static void conveyor(CoverHost h, ServerLevel l, Direction side, int v) {
        if (condBlocks(h, v)) return;
        ResourceHandler<ItemResource> own = h.rawItems(side), other = neighbourItems(l, h.coverPos(), side);
        if (own == null || other == null) return;
        boolean costs = exportsOrFreeSide(v, side) && h.euCapacity() >= 128;
        if (costs && h.euStored() < 256) return;
        int moved;
        try (Transaction tx = Transaction.openRoot()) {
            moved = v % 2 == 0 ? ResourceHandlerUtil.moveStacking(own, other, r -> true, 64, tx)
                    : ResourceHandlerUtil.moveStacking(other, own, r -> true, 64, tx);
            tx.commit();
        }
        if (costs && moved > 0) h.useEu(4L * moved);
    }

    /** GT_Cover_Pump.doCoverThings: 32 л/тик; 1 EU, если перекачано ≥ 10 л. */
    private static void pump(CoverHost h, ServerLevel l, Direction side, int v) {
        if (condBlocks(h, v)) return;
        ResourceHandler<FluidResource> own = h.rawFluids(side), other = neighbourFluids(l, h.coverPos(), side);
        if (own == null || other == null) return;
        ResourceHandler<FluidResource> from = v % 2 == 0 ? own : other, to = v % 2 == 0 ? other : own;
        int can;
        try (Transaction tx = Transaction.openRoot()) {
            can = ResourceHandlerUtil.move(from, to, r -> true, PUMP_RATE, tx); // пробно, без фиксации
        }
        if (can <= 0) return;
        long cost = Math.min(1, can / 10);
        if (exportsOrFreeSide(v, side) && h.euCapacity() >= cost) {
            if (h.euStored() < cost) return;
            h.useEu(cost);
        }
        try (Transaction tx = Transaction.openRoot()) {
            ResourceHandlerUtil.move(from, to, r -> true, can, tx);
            tx.commit();
        }
    }

    /** GT_Cover_Arm.doCoverThings: перенос между определёнными слотами. */
    private static void arm(CoverHost h, ServerLevel l, Direction side, int v) {
        if (h.isMachine() && !h.allowedToWork()) return;
        boolean usePower = false;
        if (h.euCapacity() >= 128) {
            if (h.euStored() < 256) return;
            usePower = true;
        }
        boolean export = (v & ARM_EXPORT) != 0;
        int intSlot = v & ARM_SLOT, adjSlot = (v >> 14) & ARM_SLOT;
        ResourceHandler<ItemResource> ownAll = h instanceof net.minecraft.world.Container c
                ? net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(c) : null;
        ResourceHandler<ItemResource> ownSide = h.rawItems(side), other = neighbourItems(l, h.coverPos(), side);
        if (ownSide == null || other == null) return;
        int fromSlot = export ? intSlot : adjSlot, toSlot = export ? adjSlot : intSlot;
        ResourceHandler<ItemResource> fromAll = export ? ownAll : other, toAll = export ? other : ownAll;
        ResourceHandler<ItemResource> fromSide = export ? ownSide : other, toSide = export ? other : ownSide;
        int moved;
        try (Transaction tx = Transaction.openRoot()) {
            if (fromSlot > 0 && toSlot > 0) {
                moved = slotToSlot(fromAll, fromSlot - 1, toAll, toSlot - 1, tx);
            } else if (toSlot > 0) {
                moved = handlerToSlot(fromSide, toAll, toSlot - 1, tx);
            } else if (fromSlot > 0) {
                moved = slotToHandler(fromAll, fromSlot - 1, toSide, tx);
            } else {
                moved = ResourceHandlerUtil.moveStacking(fromSide, toSide, r -> true, 64, tx);
            }
            tx.commit();
        }
        if (usePower && moved > 0) h.useEu(4L * moved);
    }

    private static int slotToSlot(@Nullable ResourceHandler<ItemResource> from, int fs, @Nullable ResourceHandler<ItemResource> to, int ts, Transaction tx) {
        if (from == null || to == null || fs >= from.size() || ts >= to.size()) return 0;
        ItemResource r = from.getResource(fs);
        if (r.isEmpty()) return 0;
        int amount = (int) Math.min(64, from.getAmountAsLong(fs));
        int ins = to.insert(ts, r, amount, tx);
        return ins > 0 ? from.extract(fs, r, ins, tx) : 0;
    }

    private static int handlerToSlot(ResourceHandler<ItemResource> from, @Nullable ResourceHandler<ItemResource> to, int ts, Transaction tx) {
        if (to == null || ts >= to.size()) return 0;
        for (int i = 0; i < from.size(); i++) {
            ItemResource r = from.getResource(i);
            if (r.isEmpty()) continue;
            int amount = (int) Math.min(64, from.getAmountAsLong(i));
            int ins = to.insert(ts, r, amount, tx);
            if (ins > 0) return from.extract(i, r, ins, tx);
        }
        return 0;
    }

    private static int slotToHandler(@Nullable ResourceHandler<ItemResource> from, int fs, ResourceHandler<ItemResource> to, Transaction tx) {
        if (from == null || fs >= from.size()) return 0;
        ItemResource r = from.getResource(fs);
        if (r.isEmpty()) return 0;
        int amount = (int) Math.min(64, from.getAmountAsLong(fs));
        int ins = ResourceHandlerUtil.insertStacking(to, r, amount, tx);
        return ins > 0 ? from.extract(fs, r, ins, tx) : 0;
    }

    /** GT_Cover_ControlsWork: работа по красному сигналу. */
    private static void controller(CoverHost h, ServerLevel l, Direction side, int v) {
        if (!h.isMachine()) return;
        int in = l.getSignal(h.coverPos().relative(side), side);
        h.setAllowedToWork((in > 0) == (v == 0) && v != 2);
    }

    /** GT_Cover_DoesWork. */
    private static int activity(CoverHost h, int v) {
        if (!h.isMachine()) return 0;
        if (v < 2) {
            int scale = h.maxProgress() / 15;
            if (scale > 0 && h.maxProgress() > 0) return v % 2 == 0 ? h.progress() / scale : 15 - h.progress() / scale;
            return v % 2 == 0 ? 0 : 15;
        }
        return (v % 2 == 0) != (h.maxProgress() == 0) ? 0 : 15;
    }

    /** GT_Cover_ItemMeter: заполненность слотов стороны (или одного слота) 0..15. */
    private static int itemMeter(CoverHost h, Direction side, int v) {
        int slot = v & METER_SLOT;
        int max = 0, used = 0;
        if (slot > 0) {
            if (slot - 1 < h.slotCount()) {
                max = 64;
                ItemStack s = h.slot(slot - 1);
                if (!s.isEmpty()) used = (s.getCount() << 6) / s.getMaxStackSize();
            }
        } else {
            ResourceHandler<ItemResource> own = h.rawItems(side);
            if (own != null) for (int i = 0; i < own.size(); i++) {
                max += 64;
                ItemResource r = own.getResource(i);
                if (!r.isEmpty()) used += (int) ((own.getAmountAsLong(i) << 6) / Math.max(1, r.getMaxStackSize()));
            }
        }
        boolean inv = (v & METER_INVERT) != 0;
        if (used == 0) return inv ? 15 : 0;
        if (used >= max) return inv ? 0 : 15;
        return inv ? 14 - 14 * used / max : 1 + 14 * used / max;
    }

    /** GT_Cover_LiquidMeter. */
    private static int fluidMeter(CoverHost h, int v) {
        ResourceHandler<FluidResource> t = h.rawFluids(null);
        if (t == null) return 0;
        long max = 0, used = 0;
        for (int i = 0; i < t.size(); i++) {
            max += t.getCapacityAsLong(i, t.getResource(i));
            used += t.getAmountAsLong(i);
        }
        if (used == 0) return v != 0 ? 15 : 0;
        if (used >= max) return v != 0 ? 0 : 15;
        return (int) (v != 0 ? 14 - 14 * used / max : 1 + 14 * used / max);
    }

    /** GT_Cover_EUMeter (режимы пара 4/5 — у наших электроблоков пара нет, сигнал 0/15). */
    private static int euMeter(CoverHost h, int v) {
        long stored, scale;
        switch (v / 2) {
            case 0, 1 -> { stored = h.euStored(); scale = h.euCapacity(); }
            case 3 -> { stored = h.avgEuIn(); scale = h.inputVoltage(); }
            case 4 -> { stored = h.avgEuOut(); scale = h.outputVoltage(); }
            case 5 -> { long[] b = h.batteryCharge(); stored = h.euStored() + b[0]; scale = h.euCapacity() + b[1]; }
            default -> { stored = 0; scale = 0; }
        }
        long step = scale / 15;
        if (step <= 0) return v % 2 == 0 ? 0 : 15;
        int s = (int) Math.min(15, stored / step);
        return v % 2 == 0 ? s : 15 - s;
    }

    /** GT_Cover_Drain: дождь сверху и жидкие источники рядом в бак; режимы 3..5 — только убирают жидкость. */
    private static void drain(CoverHost h, ServerLevel l, Direction side, int v) {
        if (v % 3 > 1 && h.isMachine() && h.allowedToWork() != (v % 3 < 2)) return;
        BlockPos pos = h.coverPos(), at = pos.relative(side);
        ResourceHandler<FluidResource> tank = h.rawFluids(null);
        BlockState bs = l.getBlockState(at);
        FluidState fs = l.getFluidState(at);
        if (v < 3 && tank != null) {
            if (side == Direction.UP && l.isRaining() && l.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 2 < pos.getY()) {
                int amount = (int) (l.getBiome(pos).value().getModifiedClimateSettings().downfall() * 10.0f);
                if (amount > 0) fill(tank, FluidResource.of(Fluids.WATER), l.isThundering() ? amount * 2 : amount, true);
            }
            if (fs.isSource() && bs.getBlock() instanceof LiquidBlock) {
                FluidResource r = FluidResource.of(fs.getType());
                int density = fs.getType().getFluidType().getDensity();
                boolean ok = side.getAxis() != Direction.Axis.Y || (side == Direction.DOWN && density <= 0) || (side == Direction.UP && density >= 0);
                if (ok && fill(tank, r, 1000, false) == 1000) {
                    fill(tank, r, 1000, true);
                    l.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        if (v >= 3 && !fs.isEmpty() && bs.getBlock() instanceof LiquidBlock) l.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
    }

    private static int fill(ResourceHandler<FluidResource> tank, FluidResource r, int amount, boolean commit) {
        try (Transaction tx = Transaction.openRoot()) {
            int n = tank.insert(r, amount, tx);
            if (commit) tx.commit();
            return n;
        }
    }

    // --- отвёртка и руки ----------------------------------------------------------------------------------------
    /** Отвёртка: следующий режим (Shift — предыдущий). right — клик по правой половине грани (для манипулятора). */
    public Component screwdriver(CoverHost h, Direction side, boolean sneak, boolean right) {
        int i = side.get3DDataValue();
        CoverKind k = kind[i];
        if (k == null) return Component.empty();
        int v = data[i];
        Component msg;
        switch (k) {
            case CONVEYOR, PUMP -> { v = cycle(v, sneak, 12); msg = Component.translatable(CoverKind.msg(6 + v)); }
            case SHUTTER -> { v = cycle(v, sneak, 4); msg = Component.translatable(CoverKind.msg(82 + v)); }
            case CONTROLLER -> { v = cycle(v, sneak, 3); msg = Component.translatable(CoverKind.msg(3 + v)); }
            case ACTIVITY_DETECTOR -> { v = cycle(v, sneak, 4); msg = Component.translatable(CoverKind.msg(18 + v)); }
            case ENERGY_DETECTOR -> { v = cycle(v, sneak, 12); msg = Component.translatable(CoverKind.msg(31 + v)); }
            case DRAIN -> { v = cycle(v, sneak, 6); msg = Component.translatable(CoverKind.msg(22 + v)); }
            case FLUID_DETECTOR -> { msg = Component.translatable(CoverKind.msg(v == 0 ? 54 : 55)); v = v == 0 ? 1 : 0; }
            case ITEM_DETECTOR -> {
                if (sneak) {
                    boolean inv = (v & METER_INVERT) != 0;
                    v = inv ? v & ~METER_INVERT : v | METER_INVERT;
                    msg = Component.translatable(CoverKind.msg(inv ? 51 : 52));
                } else {
                    int slot = (v & METER_SLOT) + 1;
                    if (slot > h.slotCount()) slot = 0;
                    v = METER_CONVERTED | (v & METER_INVERT) | slot;
                    msg = Component.translatable(CoverKind.msg(53)).append(slot == 0 ? Component.translatable("cover.iis.all_slots") : Component.literal(String.valueOf(slot - 1)));
                }
            }
            case ARM -> { v = armStep(v, right ? (sneak ? 256 : 16) : (sneak ? -256 : -16)); msg = armMsg(v); }
            default -> { return Component.empty(); }
        }
        data[i] = v;
        return msg;
    }

    /** Пустая рука по манипулятору: шаг ±1 по слоту (GT_Cover_Arm.onCoverRightclick). */
    public Component armClick(Direction side, boolean right) {
        int i = side.get3DDataValue();
        data[i] = armStep(data[i], right ? 1 : -1);
        return armMsg(data[i]);
    }

    private static int cycle(int v, boolean back, int n) {
        v = (v + (back ? -1 : 1)) % n;
        return v < 0 ? n - 1 : v;
    }

    private static Component armMsg(int v) {
        return (v & ARM_EXPORT) != 0
                ? Component.translatable(CoverKind.msg(1)).append(String.valueOf(((v >> 14) & ARM_SLOT) - 1))
                : Component.translatable(CoverKind.msg(2)).append(String.valueOf((v & ARM_SLOT) - 1));
    }

    /** GT_Cover_Arm.getNewVar. */
    static int armStep(int var, int step) {
        int intSlot = var & ARM_SLOT, adjSlot = (var >> 14) & ARM_SLOT;
        if ((var & ARM_EXPORT) == 0) {
            int x = intSlot + step;
            if (x > ARM_SLOT) return armVar(0, ARM_SLOT, 0);
            if (x < 1) return armVar(-step - intSlot + 1, 0, ARM_EXPORT);
            return armVar(0, x, 0);
        }
        int x = adjSlot - step;
        if (x > ARM_SLOT) return armVar(ARM_SLOT, 0, ARM_EXPORT);
        if (x < 1) return armVar(0, step - adjSlot + 1, 0);
        return armVar(x, 0, ARM_EXPORT);
    }

    private static int armVar(int adjSlot, int intSlot, int export) {
        return ARM_CONVERTED | export | ((adjSlot & ARM_SLOT) << 14) | (intSlot & ARM_SLOT);
    }

    // --- сохранение ----------------------------------------------------------------------------------------------
    public void save(ValueOutput out) {
        if (!any()) return;
        ValueOutput c = out.child("covers");
        for (Direction d : Direction.values()) {
            CoverKind k = get(d);
            if (k == null) continue;
            ValueOutput s = c.child(d.getName());
            s.putString("kind", k.id);
            s.putInt("data", data(d));
            s.putInt("rs", redstone[d.get3DDataValue()]);
        }
    }

    public void load(ValueInput in) {
        for (Direction d : Direction.values()) {
            int i = d.get3DDataValue();
            kind[i] = null;
            data[i] = 0;
            redstone[i] = 0;
        }
        in.child("covers").ifPresent(c -> {
            for (Direction d : Direction.values()) {
                c.child(d.getName()).ifPresent(s -> {
                    String id = s.getStringOr("kind", "");
                    for (CoverKind k : CoverKind.values()) {
                        if (k.id.equals(id)) {
                            int i = d.get3DDataValue();
                            kind[i] = k;
                            data[i] = s.getIntOr("data", 0);
                            redstone[i] = s.getIntOr("rs", 0);
                        }
                    }
                });
            }
        });
    }
}
