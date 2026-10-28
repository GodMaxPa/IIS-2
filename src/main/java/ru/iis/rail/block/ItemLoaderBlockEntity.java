package ru.iis.rail.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import ru.iis.rail.RailContent;
import ru.iis.rail.cart.Locomotive;

import java.util.ArrayList;
import java.util.List;

/** Буфер погрузчика (9 слотов) и перенос по предмету за тик (Railcraft InvTools.moveOneItem). */
public class ItemLoaderBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOTS = 9;
    /** TileLoaderBase.STOP_VELOCITY: вагонетка считается стоящей. */
    static final double STOP_VELOCITY = 0.02;
    /** CartTools.getMinecartOnSide: насколько ужать клетку при поиске вагонетки. */
    static final double SENSITIVITY = 0.1;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private @Nullable AbstractMinecart current;
    private boolean powered;

    public ItemLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(RailContent.LOADER_BE.get(), pos, state);
    }

    public boolean powered() {
        return powered;
    }

    private ItemLoaderBlock block() {
        return (ItemLoaderBlock) getBlockState().getBlock();
    }

    private record Side(Container c, @Nullable Direction face) {}

    void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        Direction side = block().cartSide(getBlockState());
        AbstractMinecart cart = cartOn(sl, worldPosition, side);
        if (cart != current) {
            setPowered(sl, false);
            current = cart;
        }
        if (cart == null) return;
        List<Side> chests = new ArrayList<>();
        chests.add(new Side(this, null));
        for (Direction d : Direction.values()) {
            if (d == side) continue;
            if (sl.getBlockEntity(worldPosition.relative(d)) instanceof Container c && !(c instanceof ItemLoaderBlockEntity)) chests.add(new Side(c, d.getOpposite()));
        }
        Container cartInv = (Container) cart;
        boolean moved = block().unloader ? moveIntoAny(cartInv, chests) : moveFromAny(chests, cartInv);
        if (moved) {
            setPowered(sl, false);
        } else if (cart.getDeltaMovement().horizontalDistance() < STOP_VELOCITY || cart instanceof Locomotive) {
            // режим «сразу» (EnumRedstoneMode.IMMEDIATE): переносить нечего — отпустить вагонетку
            setPowered(sl, true);
        }
    }

    public static @Nullable AbstractMinecart cartOn(ServerLevel level, BlockPos pos, Direction side) {
        List<AbstractMinecart> carts = level.getEntitiesOfClass(AbstractMinecart.class, new AABB(pos.relative(side)).deflate(SENSITIVITY),
                c -> c.isAlive() && c instanceof Container inv && inv.getContainerSize() > 0);
        return carts.isEmpty() ? null : carts.get(0);
    }

    private void setPowered(ServerLevel sl, boolean p) {
        if (powered == p) return;
        powered = p;
        sl.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        for (Direction d : Direction.values()) sl.updateNeighborsAt(worldPosition.relative(d), getBlockState().getBlock());
    }

    /** Погрузка: один предмет из буфера или соседних инвентарей в вагонетку. */
    private static boolean moveFromAny(List<Side> from, Container to) {
        for (Side s : from) {
            for (int i = 0; i < s.c.getContainerSize(); i++) {
                ItemStack st = s.c.getItem(i);
                if (st.isEmpty()) continue;
                if (s.c instanceof WorldlyContainer w && s.face != null && !w.canTakeItemThroughFace(i, st, s.face)) continue;
                if (insertOne(s.c, i, to, null)) return true;
            }
        }
        return false;
    }

    /** Разгрузка: один предмет из вагонетки в буфер или соседний инвентарь. */
    private static boolean moveIntoAny(Container from, List<Side> to) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            if (from.getItem(i).isEmpty()) continue;
            for (Side s : to) if (insertOne(from, i, s.c, s.face)) return true;
        }
        return false;
    }

    private static boolean insertOne(Container from, int slot, Container to, @Nullable Direction face) {
        ItemStack one = from.getItem(slot).copyWithCount(1);
        ItemStack rest = HopperBlockEntity.addItem(from, to, one, face);
        if (!rest.isEmpty()) return false;
        from.removeItem(slot, 1);
        from.setChanged();
        return true;
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
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new DispenserMenu(id, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putBoolean("powered", powered);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        powered = in.getBooleanOr("powered", false);
    }
}
