package ru.iis.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.block.ElectricBlockEntity;
import ru.iis.lv.block.ElectricMachineBlockEntity;
import ru.iis.reactor.NuclearReactorBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Информационная панель (D141): слоты 0–3 — карты датчиков (у простой панели работает только первый), 4–8 — улучшения дальности и цвета.
 * Раз в секунду читает цели карт: реактор (нагрев, выход), машину (работа), любой накопитель EU (запас, вход и выход).
 * Дальность 16 блоков, +16 за улучшение (до 4) — предложение ИИ; цвет текста — с улучшением цвета, ПКМ красителем.
 */
public class PanelBlockEntity extends BaseContainerBlockEntity {
    public static final int CARDS = 4, SLOTS = 9;
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    public List<Component> lines = List.of();
    public int color = 0x40FF40;
    private int timer;

    public PanelBlockEntity(BlockPos pos, BlockState state) {
        super(MonitorContent.PANEL_BE.get(), pos, state);
    }

    public boolean advanced() {
        return getBlockState().getBlock() instanceof PanelBlock p && p.advanced;
    }

    private int count(net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = CARDS; i < SLOTS; i++) if (items.get(i).is(item)) n += items.get(i).getCount();
        return Math.min(4, n);
    }

    public boolean hasColor() {
        return count(MonitorContent.COLOR_UPGRADE.get()) > 0;
    }

    public void setColor(int c) {
        color = c;
        sync();
    }

    public boolean accepts(ItemStack s) {
        return s.is(MonitorContent.SENSOR_CARD.get()) || s.is(MonitorContent.RANGE_UPGRADE.get()) || s.is(MonitorContent.COLOR_UPGRADE.get());
    }

    public boolean insert(ItemStack s) {
        boolean card = s.is(MonitorContent.SENSOR_CARD.get());
        int from = card ? 0 : CARDS, to = card ? (advanced() ? CARDS : 1) : SLOTS;
        for (int i = from; i < to; i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, s);
                setChanged();
                return true;
            }
        }
        return false;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl) || ++timer % 20 != 1) return;
        List<Component> l = read(sl);
        if (!l.equals(lines)) {
            lines = l;
            sync();
        }
    }

    public List<Component> read(ServerLevel sl) {
        List<Component> l = new ArrayList<>();
        int range = 16 + 16 * count(MonitorContent.RANGE_UPGRADE.get());
        for (int i = 0; i < (advanced() ? CARDS : 1); i++) {
            SensorCardItem.Target t = SensorCardItem.target(items.get(i));
            if (t == null) continue;
            if (t.dim() != sl.dimension() || !t.pos().closerThan(worldPosition, range) || !sl.isLoaded(t.pos())) {
                l.add(Component.translatable("monitor.iis.out_of_range"));
                continue;
            }
            l.add(sl.getBlockState(t.pos()).getBlock().getName());
            describe(sl.getBlockEntity(t.pos()), l);
        }
        if (l.isEmpty()) l.add(Component.translatable("monitor.iis.empty"));
        return l;
    }

    static void describe(@Nullable BlockEntity be, List<Component> l) {
        if (be instanceof NuclearReactorBlockEntity r) {
            l.add(Component.translatable("monitor.iis.heat", r.heat(), r.maxHeat()));
            l.add(Component.translatable("monitor.iis.output", Math.round(r.euPerTick())));
        } else if (be instanceof ElectricMachineBlockEntity m) {
            l.add(Component.translatable("monitor.iis.progress", m.maxProgress() > 0 ? m.progress() * 100 / m.maxProgress() : 0));
            l.add(Component.translatable("monitor.iis.energy", m.euStored(), m.euCapacity()));
        } else if (be instanceof ElectricBlockEntity e) {
            l.add(Component.translatable("monitor.iis.energy", e.euStored(), e.euCapacity()));
            l.add(Component.translatable("monitor.iis.flow", e.avgEuIn, e.avgEuOut));
        } else {
            l.add(Component.translatable("monitor.iis.no_data"));
        }
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
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
        return SLOTS;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new DispenserMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("color", color);
        out.store("lines", ComponentSerialization.CODEC.listOf(), lines);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        color = in.getIntOr("color", 0x40FF40);
        lines = in.read("lines", ComponentSerialization.CODEC.listOf()).orElse(List.of());
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
