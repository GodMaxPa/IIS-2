package ru.iis.defense;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import org.jspecify.annotations.Nullable;
import ru.iis.lv.block.ElectricBlockEntity;

import java.util.UUID;
import java.util.stream.IntStream;

/**
 * База турели OMT (TurretBase, D140): запас энергии (MaxCharge конфига ИИС 1 в RF, здесь в EU при 8 RF = 1 EU), 9 слотов патронов,
 * 9 слотов улучшений и дополнений; турели ставятся на её грани. Владелец — поставивший; свои — владелец и его команда.
 * Режим целей (Shift + ПКМ владельца): монстры; + животные; + игроки; все чужие.
 */
public class TurretBaseBlockEntity extends ElectricBlockEntity {
    public static final int AMMO = 9, SLOTS = 18, RF_PER_EU = 8, MAX_UPGRADES = 4;
    /** openmodularturrets.cfg ИИС 1: MaxCharge баз 1–5 (RF). */
    public static final long[] MAX_RF = {500, 50000, 150000, 500000, 10000000};

    public final int tier;
    private @Nullable UUID owner;
    private String ownerName = "";
    public int mode;
    private int rfRemainder;

    public TurretBaseBlockEntity(BlockPos pos, BlockState state) {
        super(DefenseContent.TURRET_BASE_BE.get(), pos, state, SLOTS, MAX_RF[tier(state) - 1] / RF_PER_EU, ru.iis.energy.VoltageTier.values()[Math.max(1, Math.min(4, tier(state) - 1))].voltage());
        this.tier = tier(state);
    }

    private static int tier(BlockState s) {
        return s.getBlock() instanceof TurretBaseBlock b ? b.tier : 1;
    }

    // --- владелец -------------------------------------------------------------------------------------------------------------
    public void setOwner(Player p) {
        owner = p.getUUID();
        ownerName = p.getGameProfile().name();
        setChanged();
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean isOwner(Player p) {
        return owner == null || owner.equals(p.getUUID());
    }

    /** Свои: владелец и игроки его команды. */
    public boolean trusted(Player p) {
        if (isOwner(p)) return true;
        Player o = owner == null || level == null ? null : level.getPlayerByUUID(owner);
        return o != null && o.getTeam() != null && o.getTeam().isAlliedTo(p.getTeam());
    }

    public @Nullable Player ownerPlayer() {
        return owner == null || level == null ? null : level.getPlayerByUUID(owner);
    }

    public void cycleMode(Player p) {
        mode = (mode + 1) % 4;
        p.sendOverlayMessage(Component.translatable("message.iis.turret.mode." + mode));
        setChanged();
    }

    public boolean attacksNeutrals() {
        return mode == 1 || mode == 3;
    }

    public boolean attacksPlayers() {
        return mode >= 2;
    }

    // --- энергия --------------------------------------------------------------------------------------------------------------
    /** Тратит RF; база хранит EU, остаток меньше 1 EU копится. */
    public boolean useRf(int rf) {
        long stock = energy.stored * RF_PER_EU + rfRemainder;
        if (stock < rf) return false;
        stock -= rf;
        energy.stored = stock / RF_PER_EU;
        rfRemainder = (int) (stock % RF_PER_EU);
        setChanged();
        return true;
    }

    public void addRf(int rf) {
        long stock = Math.min(energy.capacity * RF_PER_EU, energy.stored * RF_PER_EU + rfRemainder + rf);
        energy.stored = stock / RF_PER_EU;
        rfRemainder = (int) (stock % RF_PER_EU);
    }

    // --- предметы -------------------------------------------------------------------------------------------------------------
    public int count(Item item) {
        int n = 0;
        for (int i = AMMO; i < SLOTS; i++) if (items.get(i).is(item)) n += items.get(i).getCount();
        return Math.min(MAX_UPGRADES, n);
    }

    /** Берёт один патрон из слотов патронов; false — нет. */
    public boolean takeAmmo(Item item, boolean keep) {
        for (int i = 0; i < AMMO; i++) {
            if (items.get(i).is(item)) {
                if (!keep) items.get(i).shrink(1);
                setChanged();
                return true;
            }
        }
        return false;
    }

    public boolean hasAmmo(Item item) {
        for (int i = 0; i < AMMO; i++) if (items.get(i).is(item)) return true;
        return false;
    }

    @Override
    protected void work(ServerLevel sl) {
        int solar = count(DefenseContent.ADDON_SOLAR.get());
        if (solar > 0 && sl.isBrightOutside() && sl.canSeeSky(worldPosition.above())) addRf(10 * solar);
        if (count(DefenseContent.ADDON_REDSTONE.get()) > 0 && energy.capacity * RF_PER_EU - (energy.stored * RF_PER_EU + rfRemainder) >= 1550 && takeAmmo(Items.REDSTONE, false)) {
            addRf(1550);
        }
    }

    @Override
    public boolean inputsEnergyFrom(Direction side) {
        return true;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? null : new WorldlyContainerWrapper(this, side);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return IntStream.range(0, AMMO).toArray();
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < AMMO;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x2, id, inventory, this, 2);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (owner != null) out.store("owner", UUIDUtil.CODEC, owner);
        out.putString("ownerName", ownerName);
        out.putInt("mode", mode);
        out.putInt("rfRemainder", rfRemainder);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        ownerName = in.getStringOr("ownerName", "");
        mode = in.getIntOr("mode", 0);
        rfRemainder = in.getIntOr("rfRemainder", 0);
    }
}
