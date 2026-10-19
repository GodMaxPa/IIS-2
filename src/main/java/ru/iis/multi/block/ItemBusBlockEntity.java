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
public class ItemBusBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private NonNullList<ItemStack> items = NonNullList.withSize(HatchType.BUS_SLOTS, ItemStack.EMPTY);

    public ItemBusBlockEntity(BlockPos pos, BlockState state) {
        super(MultiContent.ITEM_BUS_BE.get(), pos, state);
    }

    public boolean isInput() {
        return getBlockState().getBlock() instanceof HatchBlock h && h.type == HatchType.INPUT_BUS;
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
        return side == null ? null : new WorldlyContainerWrapper(this, side);
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
        return !isInput();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isInput();
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
        return new ItemBusMenu(id, inventory, this, isInput());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(HatchType.BUS_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
    }
}
