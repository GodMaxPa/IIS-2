package ru.iis.machine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import ru.iis.machine.IISMachines;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Предметная труба (GT_MetaPipeEntity_Item, IMetaTileEntityItemPipe.Util#scanPipes, D123). Раз в 10 тиков, если за период ничего не пришло, труба ищет соседние трубы
 * от ближних к дальним по сумме шагов и отдаёт свои предметы прямо в инвентарь рядом с найденной трубой. Между трубами предметы не лежат: ничего не «едет» по пути.
 * Счётчик порций сбрасывается раз в 20 тиков: труба с N слотами пропускает N порций в секунду, считая и чужие.
 */
public class ItemPipeBlockEntity extends BlockEntity implements ru.iis.cover.CoverHost, WorldlyContainer {
    /** GT: нет последней стороны. */
    private static final int NONE = 6;
    public static final int TICK_TIME = 20, CHECK_EVERY = 10;

    private final NonNullList<ItemStack> items;
    private final ru.iis.cover.CoverSet covers = new ru.iis.cover.CoverSet();
    private final Map<Direction, ResourceHandler<ItemResource>> sided = new EnumMap<>(Direction.class);
    private int transferred, lastReceivedFrom = NONE, oldLastReceivedFrom = NONE;

    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(IISMachines.ITEM_PIPE_BE.get(), pos, state);
        items = NonNullList.withSize(spec().slots(), ItemStack.EMPTY);
    }

    public ItemPipeBlock.Spec spec() {
        return getBlockState().getBlock() instanceof ItemPipeBlock b ? b.spec : ItemPipeBlock.SPECS.getFirst();
    }

    private boolean connected(Direction d) {
        return getBlockState().getBlock() instanceof ItemPipeBlock && getBlockState().getValue(ItemPipeBlock.PROPS.get(d));
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        if (side == null || !connected(side)) return null;
        return ru.iis.cover.Covers.items(this, side, rawItems(side));
    }

    // --- работа -------------------------------------------------------------------------------------------------------
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        tickCovers(sl);
        long tick = sl.getGameTime();
        if (tick % CHECK_EVERY != 0) return;
        if (tick % TICK_TIME == 0) transferred = 0;
        if (oldLastReceivedFrom == lastReceivedFrom) {
            // GT onPostTick: пройти трубы от ближних, каждой отдать сколько возьмёт; переполнение трубы на пути начинает поиск заново
            boolean again = true;
            List<ItemPipeBlockEntity> passed = new ArrayList<>();
            while (again && !isEmpty() && capacityCheck()) {
                again = false;
                passed.clear();
                for (ItemPipeBlockEntity pipe : sortedPipes(sl)) {
                    if (again) break;
                    passed.add(pipe);
                    while (!again && !isEmpty() && pipe.send(this)) {
                        for (ItemPipeBlockEntity p : passed) if (!p.increment()) again = true;
                    }
                }
            }
        }
        if (isEmpty()) lastReceivedFrom = NONE;
        oldLastReceivedFrom = lastReceivedFrom;
    }

    /** GT pipeCapacityCheck: первая порция всегда, дальше по числу слотов. */
    public boolean capacityCheck() {
        return transferred <= 0 || transferred < spec().slots();
    }

    private boolean increment() {
        transferred++;
        return capacityCheck();
    }

    /** GT scanPipes: сумма шагов от этой трубы, по соединениям и покрытиям; полные трубы не проходятся. */
    public Map<ItemPipeBlockEntity, Long> scan(ServerLevel sl) {
        Map<ItemPipeBlockEntity, Long> best = new LinkedHashMap<>();
        ArrayDeque<ItemPipeBlockEntity> queue = new ArrayDeque<>();
        if (capacityCheck()) {
            best.put(this, (long) spec().step());
            queue.add(this);
        }
        while (!queue.isEmpty()) {
            ItemPipeBlockEntity p = queue.poll();
            long at = best.get(p);
            for (Direction d : Direction.values()) {
                if (!p.connected(d) || !p.covers.letsItemsOut(p, d)) continue;
                BlockPos n = p.worldPosition.relative(d);
                if (!sl.isLoaded(n) || !(sl.getBlockEntity(n) instanceof ItemPipeBlockEntity q)) continue;
                Direction back = d.getOpposite();
                if (!q.connected(back) || !q.covers.letsItemsIn(q, back) || !q.capacityCheck()) continue;
                long step = at + q.spec().step();
                Long old = best.get(q);
                if (old == null || old > step) {
                    best.put(q, step);
                    queue.add(q);
                }
            }
        }
        return best;
    }

    private List<ItemPipeBlockEntity> sortedPipes(ServerLevel sl) {
        List<Map.Entry<ItemPipeBlockEntity, Long>> l = new ArrayList<>(scan(sl).entrySet());
        l.sort(Map.Entry.comparingByValue(Comparator.naturalOrder()));
        List<ItemPipeBlockEntity> out = new ArrayList<>(l.size());
        for (var e : l) out.add(e.getKey());
        return out;
    }

    /** GT sendItemStack: случайная сторона первой; своя труба не отдаёт назад, откуда взяла. */
    private boolean send(ItemPipeBlockEntity sender) {
        if (!capacityCheck() || level == null) return false;
        Direction[] all = Direction.values();
        int off = level.getRandom().nextInt(6);
        for (int i = 0; i < 6; i++) {
            Direction d = all[(i + off) % 6];
            if (connected(d) && (isEmpty() || d.ordinal() != lastReceivedFrom || sender != this) && insert(sender, d)) return true;
        }
        return false;
    }

    /** GT insertItemStackIntoTileEntity и moveOneItemStack: одна порция из первого подходящего слота отправителя в инвентарь рядом; не в трубу и не в воронку, глядящую сюда. */
    private boolean insert(ItemPipeBlockEntity sender, Direction d) {
        if (level == null || !covers.letsItemsOut(this, d)) return false;
        BlockPos n = worldPosition.relative(d);
        if (!level.isLoaded(n) || level.getBlockEntity(n) instanceof ItemPipeBlockEntity) return false;
        BlockState ns = level.getBlockState(n);
        if (ns.getBlock() instanceof HopperBlock && ns.getValue(HopperBlock.FACING) == d.getOpposite()) return false;
        ResourceHandler<ItemResource> to = level.getCapability(Capabilities.Item.BLOCK, n, d.getOpposite());
        if (to == null) return false;
        for (int s = 0; s < sender.items.size(); s++) {
            ItemStack st = sender.items.get(s);
            if (st.isEmpty()) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int moved = ResourceHandlerUtil.insertStacking(to, ItemResource.of(st), Math.min(64, st.getCount()), tx);
                if (moved <= 0) continue;
                tx.commit();
                st.shrink(moved);
                sender.setChanged();
                return true;
            }
        }
        return false;
    }

    // --- инвентарь: GT allowPutStack, allowPullStack ------------------------------------------------------------------
    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] all = new int[items.size()];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return connected(side) ? all : new int[0];
    }

    /** GT allowPutStack: пока в трубе что-то есть, принимает только с той же стороны; в занятый слот не кладёт. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (side == null || !connected(side)) return false;
        if (isEmpty()) lastReceivedFrom = side.ordinal();
        return lastReceivedFrom == side.ordinal() && items.get(slot).isEmpty();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return connected(side);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack r = ContainerHelper.removeItem(items, slot, amount);
        if (!r.isEmpty()) setChanged();
        return r;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // --- покрытия -------------------------------------------------------------------------------------------------------
    @Override
    public ru.iis.cover.CoverSet covers() {
        return covers;
    }

    @Override
    public @Nullable ResourceHandler<ItemResource> rawItems(Direction side) {
        return sided.computeIfAbsent(side, s -> new WorldlyContainerWrapper(this, s));
    }

    @Override
    public int slotCount() {
        return items.size();
    }

    @Override
    public ItemStack slot(int i) {
        return items.get(i);
    }

    @Override
    public net.minecraft.network.protocol.@Nullable Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, this);
            ru.iis.cover.Covers.dropAll(this, level, pos);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("Transferred", transferred);
        out.putInt("LastFrom", lastReceivedFrom);
        covers.save(out);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ContainerHelper.loadAllItems(in, items);
        transferred = in.getIntOr("Transferred", 0);
        lastReceivedFrom = in.getIntOr("LastFrom", NONE);
        oldLastReceivedFrom = lastReceivedFrom;
        covers.load(in);
    }
}
