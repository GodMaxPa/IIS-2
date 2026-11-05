package ru.iis.auto;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.machine.block.MachineBlock;

/**
 * Квантовый сундук GT5-II (GT_MetaTileEntity_DigitalChestBase, D108): один вид предметов, LV 4 млн … IV 64 млн. Слот 0 — вход,
 * слот 1 — выход; снаружи кладут в первый и забирают из второго, с любой стороны.
 */
public class QuantumChestBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, MachineBlock.TickingMachine {
    /** Содержимое в предмете сундука. */
    public record Content(ItemStack stack, int count) {
        public static final Codec<Content> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("stack").forGetter(Content::stack),
                Codec.INT.fieldOf("count").forGetter(Content::count)).apply(i, Content::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Content> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Content::stack, ByteBufCodecs.VAR_INT, Content::count, Content::new);
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private ItemStack stored = ItemStack.EMPTY;
    private int count;

    public QuantumChestBlockEntity(BlockPos pos, BlockState state) {
        super(AutoContent.QUANTUM_CHEST_BE.get(), pos, state);
    }

    public int capacity() {
        return getBlockState().getBlock() instanceof QuantumChestBlock b ? b.capacity : 0;
    }

    /** Всего предметов: внутри и в обоих слотах. */
    public int total() {
        return count + items.get(0).getCount() + items.get(1).getCount();
    }

    public ItemStack stored() {
        return stored;
    }

    /** GT onPostTick. */
    @Override
    public void serverTick() {
        if (count <= 0 && !stored.isEmpty()) {
            stored = ItemStack.EMPTY;
            count = 0;
            setChanged();
        }
        ItemStack in = items.get(0), out = items.get(1);
        if (stored.isEmpty() && !in.isEmpty()) stored = in.copyWithCount(1);
        boolean changed = false;
        if (!in.isEmpty() && count < capacity() && ItemStack.isSameItemSameComponents(in, stored)) {
            int moved = Math.min(in.getCount(), capacity() - count);
            count += moved;
            in.shrink(moved);
            changed = true;
        }
        if (stored.isEmpty()) return;
        if (out.isEmpty()) {
            int n = Math.min(stored.getMaxStackSize(), count);
            if (n > 0) {
                items.set(1, stored.copyWithCount(n));
                count -= n;
                changed = true;
            }
        } else if (count > 0 && ItemStack.isSameItemSameComponents(out, stored) && out.getCount() < out.getMaxStackSize()) {
            int n = Math.min(count, out.getMaxStackSize() - out.getCount());
            out.grow(n);
            count -= n;
            changed = true;
        }
        if (changed) setChanged();
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{0, 1};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == 0 && (stored.isEmpty() || ItemStack.isSameItemSameComponents(stored, stack));
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == 1;
    }

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            int t = total();
            return switch (i) {
                case 0 -> t & 0xFFFF;
                case 1 -> (t >> 16) & 0xFFFF;
                case 2 -> capacity() & 0xFFFF;
                case 3 -> (capacity() >> 16) & 0xFFFF;
                case 4 -> stored.isEmpty() ? 0 : BuiltInRegistries.ITEM.getId(stored.getItem()) & 0xFFFF;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return QuantumChestMenu.DATA;
        }
    };

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new QuantumChestMenu(id, inventory, this, data);
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
        return 2;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!stored.isEmpty() && count > 0) components.set(AutoContent.QUANTUM_CONTENT.get(), new Content(stored.copyWithCount(1), count));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Content c = components.get(AutoContent.QUANTUM_CONTENT.get());
        if (c != null) {
            stored = c.stack().copy();
            count = c.count();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("count", count);
        if (!stored.isEmpty()) out.store("stored", ItemStack.CODEC, stored);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        count = in.getIntOr("count", 0);
        stored = in.read("stored", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}
