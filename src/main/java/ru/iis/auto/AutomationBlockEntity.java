package ru.iis.auto;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import ru.iis.energy.VoltageTier;
import ru.iis.lv.block.ElectricBlockEntity;

import java.util.List;

/**
 * Буферы, фильтры, регулятор и распределитель GT5-II (GT_MetaTileEntity_Buffer и наследники, D108). Предметы входят с любой
 * стороны, кроме задней, и выталкиваются назад, 1 EU за предмет (регулятор — 3 EU); машине нужен запас от 512 EU. Запас
 * 512 + 50 × V, вход 2 А; кнопка пропускает энергию назад (2 А). Отвёртка по задней стороне задаёт размер порции.
 */
public class AutomationBlockEntity extends ElectricBlockEntity {
    public static final int MIN_STORED = 512, DATA = 18;
    /** Виды предметов для фильтра по типу: начало id предмета (в GT — OrePrefixes). */
    public static final List<String> TYPE_PREFIXES = List.of("ore", "ingot", "dust", "impure_dust", "pure_dust", "small_dust", "tiny_dust", "crushed", "purified",
            "centrifuged", "nugget", "plate", "double_plate", "rod", "long_rod", "bolt", "screw", "ring", "gear", "small_gear", "gem", "foil", "fine_wire", "block");

    public final AutomationType kind;
    public boolean output, redstoneIfFull, invert, filterInvert, ignoreNbt, allowed = true;
    public int targetStack, typeIndex;
    public final int[] targetSlots = new int[9];
    public final byte[] itemsPerSide = new byte[6];
    private byte currentSide, currentSideCount;
    private int success, signal;
    private boolean modified = true;

    public AutomationBlockEntity(BlockPos pos, BlockState state) {
        super(AutoContent.AUTOMATION_BE.get(), pos, state, kind(state).slots(), 512 + tier(state).voltage() * 50, tier(state).voltage());
        this.kind = kind(state);
        energy.maxAmperesIn = 2;
    }

    private static AutomationType kind(BlockState s) {
        return s.getBlock() instanceof AutomationBlock b ? b.kind : AutomationType.CHEST_BUFFER;
    }

    private static VoltageTier tier(BlockState s) {
        return s.getBlock() instanceof AutomationBlock b ? b.tier : VoltageTier.LV;
    }

    private Direction back() {
        return front().getOpposite();
    }

    public int signal() {
        return signal;
    }

    public void toggle(int button) {
        switch (button) {
            case 0 -> output = !output;
            case 1 -> redstoneIfFull = !redstoneIfFull;
            case 2 -> invert = !invert;
            case 3 -> filterInvert = !filterInvert;
            case 4 -> ignoreNbt = !ignoreNbt;
            default -> {}
        }
        setChanged();
    }

    public void cycleType(int step) {
        typeIndex = Math.floorMod(typeIndex + step, TYPE_PREFIXES.size());
        setChanged();
    }

    /** GT onScrewdriverRightClick: у буферов — размер порции по задней стороне, у распределителя — предметов на сторону. */
    public void screwdriver(Direction side, Player player) {
        boolean sneak = player.isShiftKeyDown();
        if (kind == AutomationType.REGULATOR) return;
        if (kind == AutomationType.ITEM_DISTRIBUTOR) {
            int i = side.get3DDataValue();
            itemsPerSide[i] = (byte) Math.floorMod(itemsPerSide[i] + (sneak ? -1 : 1), 128);
            player.sendOverlayMessage(Component.translatable("message.iis.auto.items_per_side", itemsPerSide[i]));
            setChanged();
            return;
        }
        if (side != back()) return;
        targetStack = (targetStack + (sneak ? -1 : 1)) % 65;
        if (targetStack < 0) targetStack = 64;
        player.sendOverlayMessage(targetStack == 0 ? Component.translatable("message.iis.auto.target_off")
                : Component.translatable("message.iis.auto.target", targetStack));
        setChanged();
    }

    // --- энергия ----------------------------------------------------------------------------------------------------
    @Override
    public boolean inputsEnergyFrom(Direction side) {
        if (kind == AutomationType.ITEM_DISTRIBUTOR) return side == front() || itemsPerSide[side.get3DDataValue()] == 0;
        return side != back();
    }

    @Override
    public boolean outputsEnergyTo(Direction side) {
        return output && !inputsEnergyFrom(side);
    }

    @Override
    public long outputVoltage() {
        return output ? energy.inputVoltage : 0;
    }

    @Override
    public long outputAmperes() {
        return 2;
    }

    @Override
    public long minimumStored() {
        return MIN_STORED;
    }

    @Override
    public boolean isMachine() {
        return true;
    }

    @Override
    public boolean allowedToWork() {
        return allowed;
    }

    @Override
    public void setAllowedToWork(boolean a) {
        if (allowed != a) {
            allowed = a;
            setChanged();
        }
    }

    // --- работа -----------------------------------------------------------------------------------------------------
    @Override
    public void setChanged() {
        modified = true;
        super.setChanged();
    }

    @Override
    protected void work(ServerLevel sl) {
        energy.maxAmperesIn = 2;
        if (!allowed || energy.stored < MIN_STORED || !(modified || tickTimer % 200 == 0 || success > 0)) return;
        modified = false;
        success--;
        switch (kind) {
            case REGULATOR -> moveRegulator(sl);
            case ITEM_DISTRIBUTOR -> moveDistributor(sl);
            case CHEST_BUFFER, SUPER_BUFFER -> {
                compact();
                moveBuffer(sl);
                compact();
            }
            default -> moveBuffer(sl);
        }
        updateSignal(sl);
    }

    private void updateSignal(ServerLevel sl) {
        int s = invert ? 15 : 0;
        if (redstoneIfFull) {
            s = invert ? 0 : 15;
            for (int i = 0; i < kind.storage; i++) {
                if (items.get(i).isEmpty()) {
                    s = invert ? 15 : 0;
                    energy.stored = Math.max(0, energy.stored - 1);
                    break;
                }
            }
        }
        if (s != signal) {
            signal = s;
            sl.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    /** GT fillStacksIntoFirstSlots: собрать предметы в начало. */
    private void compact() {
        boolean changed = false;
        for (int i = 0; i < kind.storage; i++) {
            ItemStack a = items.get(i);
            if (!a.isEmpty() && a.getCount() >= a.getMaxStackSize()) continue;
            for (int j = i + 1; j < kind.storage; j++) {
                ItemStack b = items.get(j);
                if (b.isEmpty()) continue;
                a = items.get(i);
                if (a.isEmpty()) {
                    items.set(i, b);
                    items.set(j, ItemStack.EMPTY);
                    changed = true;
                } else if (ItemStack.isSameItemSameComponents(a, b)) {
                    int m = Math.min(b.getCount(), a.getMaxStackSize() - a.getCount());
                    if (m > 0) {
                        a.grow(m);
                        b.shrink(m);
                        changed = true;
                    }
                }
                if (items.get(i).getCount() >= items.get(i).getMaxStackSize()) break;
            }
        }
        if (changed) super.setChanged();
    }

    private @Nullable ResourceHandler<ItemResource> target(ServerLevel sl, Direction side) {
        return sl.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(side), side.getOpposite());
    }

    /** GT moveOneItemStack: одна порция из первого подходящего слота; exact — только целая порция. */
    private int moveOne(ServerLevel sl, Direction side, int max, boolean exact) {
        ResourceHandler<ItemResource> to = target(sl, side);
        if (to == null) return 0;
        for (int s = 0; s < kind.storage; s++) {
            ItemStack st = items.get(s);
            if (st.isEmpty() || (exact && st.getCount() < max)) continue;
            int want = Math.min(st.getCount(), max);
            try (Transaction tx = Transaction.openRoot()) {
                int moved = ResourceHandlerUtil.insertStacking(to, ItemResource.of(st), want, tx);
                if (moved <= 0 || (exact && moved < want)) continue;
                tx.commit();
                st.shrink(moved);
                return moved;
            }
        }
        return 0;
    }

    private void moveBuffer(ServerLevel sl) {
        int moved = moveOne(sl, back(), targetStack == 0 ? 64 : targetStack, targetStack != 0);
        if (moved > 0) {
            success = 50;
            energy.stored = Math.max(0, energy.stored - moved);
            super.setChanged();
        }
    }

    /** GT Regulator#moveItems: образец задаёт вид и размер порции, настройка — номер слота цели. */
    private void moveRegulator(ServerLevel sl) {
        ResourceHandler<ItemResource> to = target(sl, back());
        if (to == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack g = items.get(9 + i);
            if (g.isEmpty() || targetSlots[i] >= to.size()) continue;
            for (int s = 0; s < 9; s++) {
                ItemStack st = items.get(s);
                if (st.getCount() < g.getCount() || !ItemStack.isSameItemSameComponents(st, g)) continue;
                try (Transaction tx = Transaction.openRoot()) {
                    int moved = to.insert(targetSlots[i], ItemResource.of(st), g.getCount(), tx);
                    if (moved < g.getCount()) continue;
                    tx.commit();
                    st.shrink(moved);
                    success = 50;
                    energy.stored = Math.max(0, energy.stored - moved * 3L);
                    super.setChanged();
                    return;
                }
            }
        }
    }

    /** GT ItemDistributor#moveItems: стороны по кругу, на каждую — заданное число предметов. */
    private void moveDistributor(ServerLevel sl) {
        compact();
        int inspected = 0;
        while (itemsPerSide[currentSide] == 0) {
            currentSide = (byte) ((currentSide + 1) % 6);
            currentSideCount = 0;
            if (++inspected == 6) return;
        }
        int moved = moveOne(sl, Direction.from3DDataValue(currentSide), itemsPerSide[currentSide] - currentSideCount, false);
        currentSideCount += (byte) moved;
        if (currentSideCount >= itemsPerSide[currentSide]) {
            currentSide = (byte) ((currentSide + 1) % 6);
            currentSideCount = 0;
        }
        if (moved > 0) {
            success = 50;
            energy.stored = Math.max(0, energy.stored - moved);
            super.setChanged();
        }
        compact();
    }

    // --- предметы снаружи -------------------------------------------------------------------------------------------
    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] all = new int[kind.storage];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
    }

    private boolean sameAsGhost(int ghost, ItemStack stack) {
        ItemStack g = items.get(ghost);
        if (g.isEmpty()) return false;
        return ignoreNbt ? g.is(stack.getItem()) : ItemStack.isSameItemSameComponents(g, stack);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (slot >= kind.storage) return false;
        if (kind == AutomationType.ITEM_DISTRIBUTOR) return side == front();
        if (side == back()) return false;
        return switch (kind) {
            case FILTER -> {
                if (!filterInvert) yield sameAsGhost(slot + 9, stack);
                for (int i = 9; i < 18; i++) if (sameAsGhost(i, stack)) yield false;
                yield true;
            }
            case REGULATOR -> ItemStack.isSameItemSameComponents(items.get(slot + 9), stack);
            case TYPE_FILTER -> (ignoreNbt || stack.getComponentsPatch().isEmpty()) && typeMatches(stack) != filterInvert;
            default -> true;
        };
    }

    /** Фильтр по типу: id предмета начинается с вида (у руд — и в другой породе). */
    public boolean typeMatches(ItemStack stack) {
        String prefix = TYPE_PREFIXES.get(typeIndex), path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (prefix.equals("ore")) return path.startsWith("ore_") || path.contains("_ore_") || path.endsWith("_ore");
        return path.startsWith(prefix + "_");
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot < kind.storage;
    }

    // --- GUI и сохранение -------------------------------------------------------------------------------------------
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> output ? 1 : 0;
                case 1 -> redstoneIfFull ? 1 : 0;
                case 2 -> invert ? 1 : 0;
                case 3 -> filterInvert ? 1 : 0;
                case 4 -> ignoreNbt ? 1 : 0;
                case 5 -> typeIndex;
                case 6 -> targetStack;
                case 7, 8, 9, 10, 11, 12, 13, 14, 15 -> targetSlots[i - 7];
                case 16 -> (int) (energy.stored & 0xFFFF);
                case 17 -> (int) ((energy.stored >> 16) & 0xFFFF);
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA;
        }
    };

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new AutomationMenu(id, inventory, this, data, kind);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean("output", output);
        out.putBoolean("redstoneIfFull", redstoneIfFull);
        out.putBoolean("invert", invert);
        out.putBoolean("filterInvert", filterInvert);
        out.putBoolean("ignoreNbt", ignoreNbt);
        out.putBoolean("allowed", allowed);
        out.putInt("targetStack", targetStack);
        out.putInt("typeIndex", typeIndex);
        out.putIntArray("targetSlots", targetSlots);
        out.putIntArray("itemsPerSide", new int[]{itemsPerSide[0], itemsPerSide[1], itemsPerSide[2], itemsPerSide[3], itemsPerSide[4], itemsPerSide[5]});
        out.putInt("currentSide", currentSide);
        out.putInt("currentSideCount", currentSideCount);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        output = in.getBooleanOr("output", false);
        redstoneIfFull = in.getBooleanOr("redstoneIfFull", false);
        invert = in.getBooleanOr("invert", false);
        filterInvert = in.getBooleanOr("filterInvert", false);
        ignoreNbt = in.getBooleanOr("ignoreNbt", false);
        allowed = in.getBooleanOr("allowed", true);
        targetStack = in.getIntOr("targetStack", 0);
        typeIndex = Math.floorMod(in.getIntOr("typeIndex", 0), TYPE_PREFIXES.size());
        in.getIntArray("targetSlots").ifPresent(a -> System.arraycopy(a, 0, targetSlots, 0, Math.min(a.length, 9)));
        in.getIntArray("itemsPerSide").ifPresent(a -> {
            for (int i = 0; i < Math.min(a.length, 6); i++) itemsPerSide[i] = (byte) a[i];
        });
        currentSide = (byte) in.getIntOr("currentSide", 0);
        currentSideCount = (byte) in.getIntOr("currentSideCount", 0);
    }
}
