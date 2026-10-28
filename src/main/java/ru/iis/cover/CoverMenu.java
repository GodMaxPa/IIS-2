package ru.iis.cover;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.LvContent;

/**
 * Интерфейс покрытия (GT_GUICover, D101): Shift + ПКМ пустой рукой по покрытию. Конвейер и помпа — кнопки GT
 * (вывод/ввод, условие по работе машины, пропуск внутрь); простые покрытия — список режимов отвёртки.
 */
public class CoverMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final Direction side;
    public final CoverKind kind;
    public final int tier;
    private final @Nullable CoverHost host;
    private final ContainerData data;

    private CoverMenu(int id, BlockPos pos, Direction side, CoverKind kind, int tier, @Nullable CoverHost host, ContainerData data) {
        super(LvContent.COVER_MENU.get(), id);
        this.pos = pos;
        this.side = side;
        this.kind = kind;
        this.tier = tier;
        this.host = host;
        this.data = data;
        addDataSlots(data);
    }

    public static CoverMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction side = Direction.from3DDataValue(buf.readByte());
        CoverKind kind = CoverKind.values()[buf.readByte()];
        int tier = buf.readByte();
        return new CoverMenu(id, pos, side, kind, tier, null, new SimpleContainerData(1));
    }

    /** Есть ли у покрытия интерфейс. */
    public static boolean hasGui(CoverKind k) {
        return iconButtons(k) || modeCount(k) > 0;
    }

    /** Конвейер и помпа: семь кнопок GT_Cover_Conveyor.GUI / GT_Cover_Pump.GUI. */
    public static boolean iconButtons(CoverKind k) {
        return k == CoverKind.CONVEYOR || k == CoverKind.PUMP;
    }

    /** Число режимов отвёртки у покрытий со списком (0 — списка нет). */
    public static int modeCount(CoverKind k) {
        return switch (k) {
            case SHUTTER, ACTIVITY_DETECTOR -> 4;
            case CONTROLLER -> 3;
            case DRAIN -> 6;
            case FLUID_DETECTOR -> 2;
            case FLUID_FILTER -> 8;
            default -> 0;
        };
    }

    /** Подпись режима из списка (те же строки, что сообщения отвёртки). */
    public static Component modeName(CoverKind k, int mode) {
        return switch (k) {
            case SHUTTER -> Component.translatable(CoverKind.msg(82 + mode));
            case CONTROLLER -> Component.translatable(CoverKind.msg(3 + mode));
            case ACTIVITY_DETECTOR -> Component.translatable(CoverKind.msg(18 + mode));
            case DRAIN -> Component.translatable(CoverKind.msg(22 + mode));
            case FLUID_DETECTOR -> Component.translatable(CoverKind.msg(mode == 0 ? 55 : 54));
            case FLUID_FILTER -> mode < 4 ? Component.translatable(CoverKind.msg(43 + mode)) : Component.translatable("cover.iis.fluid_filter.mode." + mode);
            default -> Component.empty();
        };
    }

    /** Текущий режим из данных покрытия (у фильтра жидкости — младшие 3 бита). */
    public static int modeOf(CoverKind k, int v) {
        return k == CoverKind.FLUID_FILTER ? v & 7 : v;
    }

    public static void open(Player player, CoverHost host, Direction side) {
        CoverKind k = host.covers().get(side);
        if (k == null) return;
        int tier = host.covers().tier(side);
        BlockPos pos = host.coverPos();
        ContainerData data = new ContainerData() {
            @Override
            public int get(int i) {
                return host.covers().data(side);
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return 1;
            }
        };
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CoverMenu(id, pos, side, k, tier, host, data),
                Component.translatable(Covers.itemOf(k, tier).getDescriptionId())), buf -> {
            buf.writeBlockPos(pos);
            buf.writeByte(side.get3DDataValue());
            buf.writeByte(k.ordinal());
            buf.writeByte(tier);
        });
    }

    public int value() {
        return data.get(0);
    }

    /** GT_Cover_Conveyor.GUI.getNewCoverVariable: кнопки 0 вывод, 1 ввод, 2 без условия, 3 по условию, 4 обратное, 5 пропуск, 6 запрет. */
    public static int newVar(int v, int button) {
        return switch (button) {
            case 0 -> v & ~0x1;
            case 1 -> v | 0x1;
            case 2 -> v > 5 ? 0x6 | (v & ~0xE) : v & ~0xE;
            case 3 -> v > 5 ? 0x8 | (v & ~0xE) : 0x2 | (v & ~0xE);
            case 4 -> v > 5 ? 0xA | (v & ~0xE) : 0x4 | (v & ~0xE);
            case 5 -> v <= 5 ? v + 6 : v;
            case 6 -> v > 5 ? v - 6 : v;
            default -> v;
        };
    }

    /** GT_Cover_Conveyor.GUI.getClickable: нажатая кнопка (текущее состояние) недоступна. */
    public static boolean clickable(int v, int button) {
        if (v < 0 || v > 11) return false;
        return switch (button) {
            case 0, 1 -> (v & 0x1) != button;
            case 2 -> v % 6 >= 2;
            case 3 -> v % 6 < 2 || v % 6 >= 4;
            case 4 -> v % 6 < 4;
            case 5 -> v < 6;
            case 6 -> v >= 6;
            default -> false;
        };
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (host == null || host.covers().get(side) != kind) return false;
        int v = host.covers().data(side), nv;
        if (iconButtons(kind)) {
            if (!clickable(v, id)) return false;
            nv = newVar(v, id);
        } else {
            if (id < 0 || id >= modeCount(kind)) return false;
            nv = kind == CoverKind.FLUID_FILTER ? (v & ~7) | id : id;
        }
        host.covers().setData(side, nv);
        host.coversChanged();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return host instanceof net.minecraft.world.level.block.entity.BlockEntity be && !be.isRemoved()
                && host.covers().get(side) == kind && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64;
    }
}
