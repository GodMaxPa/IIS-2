package ru.iis.multi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.multi.MultiContent;
import ru.iis.multi.menu.ItemBusMenu;

/** Входная/выходная шина LV (4 слота). Входная принимает предметы снаружи, выходная — только отдаёт. */
public class ItemBusBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ru.iis.cover.CoverHost, ru.iis.machine.block.MachineBlock.TickingMachine {
    private NonNullList<ItemStack> items;
    private final ru.iis.cover.CoverSet covers = new ru.iis.cover.CoverSet();

    // --- покрытия (CoverHost, D80) ----------------------------------------------------------------------------------
    @Override
    public ru.iis.cover.CoverSet covers() {
        return covers;
    }

    @Override
    public @Nullable ResourceHandler<ItemResource> rawItems(Direction side) {
        return new net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper(this, side);
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
    public void serverTick() {
        if (level instanceof net.minecraft.server.level.ServerLevel sl) tickCovers(sl);
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
        if (level != null) ru.iis.cover.Covers.dropAll(this, level, pos);
    }


    public ItemBusBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.ITEM_BUS_BE.get(), pos, state);
        items = NonNullList.withSize(slots(state), ItemStack.EMPTY);
    }

    /** Слотов у шины: LV 4, MV 9, HV 16, EV 25, IV 36 (D103). */
    public static int slots(BlockState state) {
        return state.getBlock() instanceof HatchBlock h ? h.type.slots() : HatchType.BUS_SLOTS;
    }

    public boolean isInput() {
        return getBlockState().getBlock() instanceof HatchBlock h && h.type.kind() == HatchType.INPUT_BUS;
    }

    /** GT_MetaTileEntity_Hatch_DataAccess (D110): флешки кладёт только игрок. */
    public boolean isDataAccess() {
        return getBlockState().getBlock() instanceof HatchBlock h && h.type.kind() == HatchType.DATA_ACCESS;
    }

    public NonNullList<ItemStack> stacks() {
        return items;
    }

    /** BaseMetaTileEntity#addStackToSlot: положить предмет в слот (пустой или такой же, есть место). */
    public boolean addToSlot(int slot, ItemStack single) {
        ItemStack cur = items.get(slot);
        if (cur.isEmpty()) {
            items.set(slot, single.copy());
            setChanged();
            return true;
        }
        if (ItemStack.isSameItemSameComponents(cur, single) && cur.getCount() + single.getCount() <= cur.getMaxStackSize()) {
            cur.grow(single.getCount());
            setChanged();
            return true;
        }
        return false;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : ru.iis.cover.Covers.items(this, side, new WorldlyContainerWrapper(this, side));
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{0, 1, 2, 3};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return isInput();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return !isInput() && !isDataAccess();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isInput() || (isDataAccess() && stack.getItem() instanceof ru.iis.lv.item.DataStickItem);
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ItemBusMenu(id, inventory, this, isInput() || isDataAccess());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        covers.save(out);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(slots(getBlockState()), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        covers.load(in);
    }
}
